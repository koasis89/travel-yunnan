#!/usr/bin/env python3
"""여행 지도 이미지 다시 그리기 (2026-11-05 지도 개편).

    python3 tools/draw_maps.py          # assets/maps/*.png 와 data/map_hotspots.json 갱신

- 날짜별로 1장씩 그린다(쿤밍·리장 2분할 지도를 일차별로 나눔). 폭 1080px, 휴대폰 전체 폭 기준 글씨 크기.
- 핫스팟(눌러서 연관 일정·택시 카드를 띄우는 점)의 픽셀 좌표를 그림과 함께 계산해 map_hotspots.json 을 다시 쓴다.
  그래서 지도를 고치면 핫스팟 좌표를 손으로 맞출 필요가 없다.
- 위치는 개략(경위도 근사·고성은 도식)이다. 실제 위치는 고덕지도로 확인한다.
글꼴: Noto Sans CJK KR (apt install fonts-noto-cjk).
"""
import json
import math
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch, Polygon, Circle, Ellipse  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "assets" / "maps"
HOT = REPO / "data" / "map_hotspots.json"
plt.rcParams["font.family"] = "Noto Sans CJK KR"
plt.rcParams["axes.unicode_minus"] = False

W = 1080          # 출력 폭(px)
DPI = 100         # 1pt = 1.39px
INK = "#1d2430"
MUTED = "#5b6472"
LAND = "#f6f3ec"
WATER = "#8fbbe3"
C = {  # 이동 수단 색
    "train": "#1f4e79", "car": "#e8590c", "taxi": "#c2185b", "walk": "#2e7d32",
    "tour": "#6f42c1", "flight": "#6b7280", "night": "#7b5cc4",
}
KIND = {"lodging": "#d32f2f", "place": "#1f6f6b", "meal": "#e09a2b", "poi": "#4b5563"}
MODE_NAME = {"train": "열차", "car": "전용차·빵차", "taxi": "택시·디디", "walk": "도보", "tour": "투어 차량",
             "flight": "항공", "night": "도보(밤)"}

HOTSPOTS = []


def proj(lon, lat, lat0):
    """경위도 → 평면(가로를 cos 위도로 줄여 모양을 맞춘다)"""
    return lon * math.cos(math.radians(lat0)), lat


class Fig:
    """세로로 쌓는 그림: 머리말 → 지도 → 아래 패널들 → 꼬리말"""

    def __init__(self, map_id, title, sub, xlim, ylim, panels=(), invert_y=False, map_h=None, bg=LAND):
        self.id, self.panels = map_id, []
        dx, dy = xlim[1] - xlim[0], ylim[1] - ylim[0]
        head, foot = 128, 40
        mh = map_h or int(round((W - 40) * dy / dx))
        total = head + mh + sum(h for _, h in panels) + foot
        self.fig = plt.figure(figsize=(W / DPI, total / DPI), dpi=DPI)
        self.fig.patch.set_facecolor("white")
        self.H = total
        f = self.fig
        f.text(28 / W, 1 - 22 / total, title, fontsize=27, fontweight="bold", color=INK, va="top")
        f.text(30 / W, 1 - 80 / total, sub, fontsize=15.5, color=MUTED, va="top")
        y = total - head - mh
        self.ax = f.add_axes([20 / W, y / total, (W - 40) / W, mh / total])
        ax = self.ax
        ax.set_xlim(*xlim)
        ax.set_ylim(*(ylim[::-1] if invert_y else ylim))
        ax.set_aspect("auto")
        ax.set_xticks([]), ax.set_yticks([])
        ax.set_facecolor(bg)
        for s in ax.spines.values():
            s.set_color("#d9d4c7")
        top = y
        for name, h in panels:
            top -= h
            pa = f.add_axes([0, top / total, 1, h / total])
            pa.set_xlim(0, W), pa.set_ylim(h, 0), pa.axis("off")
            self.panels.append(pa)
        f.text(28 / W, 14 / total, "※ 개략도(위치·거리 근사). 실제 위치는 고덕지도(Amap)로 확인한다.", fontsize=12, color="#8a8f98")
        self.points = []
        self.north()

    def north(self):
        self.ax.text(0.975, 0.975, "N ↑", transform=self.ax.transAxes, ha="right", va="top", fontsize=17,
                     fontweight="bold", color=INK)

    # ---- 그리기 도구 ----
    def dot(self, p, kind="poi", size=11, z=6, ring=True):
        self.ax.plot(*p, "o", ms=size, color=KIND.get(kind, kind), mec="white" if ring else KIND.get(kind, kind),
                     mew=2.2, zorder=z)

    def label(self, p, text, pos="r", color=INK, size=17, bold=False, box=True, ec=None, off=10, z=8, ha=None):
        dx, dy, h, v = {
            "r": (off, 0, "left", "center"), "l": (-off, 0, "right", "center"),
            "t": (0, off, "center", "bottom"), "b": (0, -off, "center", "top"),
            "tr": (off * .7, off * .7, "left", "bottom"), "tl": (-off * .7, off * .7, "right", "bottom"),
            "br": (off * .7, -off * .7, "left", "top"), "bl": (-off * .7, -off * .7, "right", "top"),
        }[pos] if isinstance(pos, str) else (pos[0], pos[1], pos[2], pos[3])
        bb = dict(boxstyle="round,pad=0.28,rounding_size=0.35", fc="white", ec=ec or "#cfd6df", lw=1.2, alpha=0.95) if box else None
        self.ax.annotate(text, p, xytext=(dx, dy), textcoords="offset points", ha=ha or h, va=v, fontsize=size,
                         fontweight="bold" if bold else "normal", color=color, bbox=bb, zorder=z, linespacing=1.15)

    def place(self, p, text, kind="place", pos="r", hot=None, size=17, bold=None, dot=11, off=10):
        self.dot(p, kind, dot)
        self.label(p, text, pos, size=size, bold=(kind == "lodging") if bold is None else bold,
                   ec=KIND.get(kind) if kind in ("lodging",) else None, off=off)
        if hot:
            self.points.append((p, hot))

    def hot(self, p, **meta):
        self.points.append((p, meta))

    def seg(self, a, b, mode, rad=0.0, head=True, lw=3.6, z=4, dashed=None, alpha=1):
        style = {"train": (0, (5, 3)), "flight": (0, (2, 2.4))}.get(mode, "solid") if dashed is None else dashed
        ar = FancyArrowPatch(a, b, connectionstyle=f"arc3,rad={rad}", arrowstyle="-|>,head_length=0.75,head_width=0.38" if head else "-",
                             color=C[mode], lw=lw, linestyle=style, shrinkA=7, shrinkB=9, zorder=z, alpha=alpha,
                             mutation_scale=20)
        self.ax.add_patch(ar)

    def path(self, pts, mode, head_each=False, **kw):
        for i in range(len(pts) - 1):
            self.seg(pts[i], pts[i + 1], mode, head=head_each or i == len(pts) - 2, **kw)

    def step(self, p, n, mode="walk", size=14, dx=0, dy=0, z=9):
        self.ax.annotate(str(n), p, xytext=(dx, dy), textcoords="offset points", ha="center", va="center",
                         fontsize=size, fontweight="bold", color="white", zorder=z,
                         bbox=dict(boxstyle="circle,pad=0.28", fc=C.get(mode, mode), ec="white", lw=1.8))

    def tag(self, p, text, mode, dx=0, dy=0, size=14.5, ha="center"):
        """구간 설명(색 글씨, 흰 배경)"""
        self.ax.annotate(text, p, xytext=(dx, dy), textcoords="offset points", ha=ha, va="center", fontsize=size,
                         color=C.get(mode, mode), fontweight="bold", zorder=7, linespacing=1.15,
                         bbox=dict(boxstyle="round,pad=0.2", fc="white", ec="none", alpha=0.88))

    def legend(self, modes, loc="lower left"):
        from matplotlib.lines import Line2D
        hs = [Line2D([], [], color=C[m], lw=4, ls={"train": (0, (5, 3)), "flight": (0, (2, 2.4))}.get(m, "-"))
              for m in modes]
        lg = self.ax.legend(hs, [MODE_NAME[m] for m in modes], loc=loc, fontsize=14, frameon=True, framealpha=0.95,
                            handlelength=2.6, borderpad=0.6)
        lg.set_zorder(20)

    def water(self, pts, lw=9, label=None, lp=None, size=14):
        xs, ys = zip(*pts)
        self.ax.plot(xs, ys, color=WATER, lw=lw, solid_capstyle="round", zorder=1, alpha=0.9)
        if label:
            self.ax.text(*lp, label, color="#3d78b3", fontsize=size, ha="center", va="center", zorder=2, style="italic")

    def mountain(self, p, text, size=16):
        self.ax.text(*p, "▲", fontsize=30, color="#8d97a5", ha="center", va="center", zorder=3)
        self.ax.annotate(text, p, xytext=(0, 20), textcoords="offset points", ha="center", va="bottom", fontsize=size,
                         color="#4b5563", zorder=3)

    def save(self, file):
        self.fig.canvas.draw()
        for p, meta in self.points:
            x, y = self.ax.transData.transform(p)
            HOTSPOTS.append({"mapId": self.id, "x": int(round(x)), "y": int(round(self.H - y)), **meta})
        out = OUT / file
        self.fig.savefig(out, dpi=DPI, facecolor="white")
        plt.close(self.fig)
        from PIL import Image
        im = Image.open(out).convert("RGB")
        im.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(out, optimize=True)
        print(f"{file}  {im.size[0]}x{im.size[1]}  {out.stat().st_size // 1024}KB")


def H(label, type_="poi", key=None, keywords=(), zh="", note=""):
    return {"label": label, "type": type_, "key": key, "keywords": list(keywords), "zh": zh, "note": note}


def text_panel(pa, rows, x=34, y=18, lh=46, size=17, col2=None):
    """rows: (뱃지텍스트, 뱃지색, 본문) 목록"""
    for i, (badge, color, body) in enumerate(rows):
        cx = x if col2 is None or i < col2 else W // 2 + 6
        cy = y + lh * (i if col2 is None or i < col2 else i - col2)
        if badge:
            pa.text(cx, cy, badge, fontsize=size - 2, fontweight="bold", color="white", va="top", ha="left",
                    bbox=dict(boxstyle="round,pad=0.3", fc=color, ec="none"))
        pa.text(cx + (len(badge) * 13 + 30 if badge else 0), cy, body, fontsize=size, color=INK, va="top", ha="left")


# ---------------------------------------------------------------- map01 전체
def map01():
    """도식 배치(실제 축척 아님): 리장 주변을 넓혀 글자가 겹치지 않게 한다"""
    f = Fig("map01", "윈난 7박 8일 전체 이동", "쿤밍 → 리장 → 옥룡설산 → 백사 → 호도협 → 샹그릴라 → 쿤밍 · 빨간 점 = 숙소",
            (0, 100), (0, 100), map_h=980, panels=[("days", 8 * 44 + 36)])
    ax = f.ax
    ax.add_patch(Polygon([(80, 9), (84, 4), (88, 2), (84, 0), (78, 3)], closed=True, fc=WATER, ec="none", zorder=1))
    sgl, tlg, yl, bs = (14, 90), (30, 72), (52, 76), (46, 62)
    lj, ljst, km, ap = (50, 48), (60, 40), (74, 14), (90, 22)
    f.place(sgl, "샹그릴라 · Oura Villa\nD6 숙박 · 3,300m", "lodging", "r",
            H("샹그릴라 · Oura Villa", "lodging", "oura", ["Oura Villa", "샹그릴라"], note="D6 숙박. 3,300m, 가장 춥다"))
    f.place(tlg, "호도협 · Come Inn\nD5 숙박 · 2,345m", "lodging", "l",
            H("호도협 · Come Inn", "lodging", "comeinn", ["Come Inn", "중도객잔"], note="D5 숙박. 트레킹 종점 본지만 마을"))
    f.place(bs, "백사 · Rishine 빌라\nD4 숙박", "lodging", "l",
            H("백사 · Rishine 빌라", "lodging", "rishine", ["Rishine", "백사"], note="D4 숙박. 일조금산 일출·프라이빗 온천"))
    f.place(lj, "리장고성 · 물라뉴 호텔\nD2·D3 숙박 · 2,400m", "lodging", "r",
            H("리장고성 · 물라뉴 탕수 호텔", "lodging", "mulanyu", ["물라뉴", "리장고성", "고성"], note="D2~D3 숙박. 고성 내 차량 진입 불가"))
    f.place(km, "쿤밍 · Gefei VX 호텔\nD1·D7 숙박 · 1,890m", "lodging", "l",
            H("쿤밍 · Gefei VX Hotel", "lodging", "kunming1", ["Gefei", "쿤밍역", "쿤밍 숙소"], note="D1·D7 숙박. 쿤밍역 북광장 360m"))
    f.place(ap, "창수이 공항", "place", "b",
            H("창수이 국제공항", "place", "kmg_airport", ["창수이", "공항"], "昆明长水国际机场", "D1 22:00 도착 · D8 23:55 출발"))
    f.place(yl, "옥룡설산 대索道\n4,506m", "place", "r",
            H("옥룡설산 투어", "place", "yulong_cableway", ["옥룡설산", "대索道"], "玉龙雪山", "D3 투어 · 사방가 픽업"), size=15)
    f.dot(ljst, "poi", 9)
    f.label(ljst, "리장역", "r", size=14.5, box=False, color=MUTED)
    f.seg((99, 34), ap, "flight", rad=0.2)
    f.tag((93, 36), "D1 22:00\n서울→쿤밍", "flight", size=13.5)
    f.seg(ap, km, "taxi", rad=0.2)
    f.seg(km, ljst, "train", rad=0.12)
    f.tag((72, 30), "D2 C446 고속열차\n08:29 → 12:27", "train")
    f.seg(ljst, lj, "taxi")
    f.seg(lj, yl, "tour", rad=-0.35)
    f.seg(yl, lj, "tour", rad=-0.35, head=False)
    f.tag((60, 62), "D3 투어", "tour")
    f.seg(lj, bs, "taxi", rad=-0.3)
    f.tag((40, 54), "D4 택시", "taxi", size=13.5)
    f.seg(bs, tlg, "car", rad=-0.25)
    f.tag((32, 62), "D5 전용차\n1시간 30분", "car", size=14)
    f.seg(tlg, sgl, "car", rad=-0.25)
    f.tag((30, 83), "D6 빵차\n약 3시간", "car", size=14)
    f.seg(sgl, km, "train", rad=0.38)
    f.tag((20, 36), "D7 C644 열차\n07:22 → 12:50", "train")
    f.seg(ap, (99, 8), "flight", rad=-0.2)
    f.tag((93, 6), "D8 23:55 출발", "flight", size=13.5)
    f.legend(["train", "car", "taxi", "tour", "flight"], "upper right")
    rows = [("D1 12/16", C["flight"], "서울 → 쿤밍 22:00 도착, 택시로 쿤밍역 앞 호텔"),
            ("D2 12/17", C["train"], "C446 열차로 리장(12:27), 고성 산책"),
            ("D3 12/18", C["tour"], "옥룡설산 투어(대索道 4,506m·람월곡·인상리장)"),
            ("D4 12/19", C["taxi"], "고성 → 백사 Rishine 빌라, 백사고진·수허고진"),
            ("D5 12/20", C["car"], "상호도협 → 차마객잔~중도객잔 트레킹 → Come Inn"),
            ("D6 12/21", C["car"], "빵차로 샹그릴라, 송찬림사·대형 마니차"),
            ("D7 12/22", C["train"], "C644 열차로 쿤밍(12:50), 남병가 야경"),
            ("D8 12/23", C["flight"], "취호·강무당·원통사·박물관 → 23:55 출발")]
    text_panel(f.panels[0], rows, y=22, lh=44, size=16.5)
    f.save("map01_overall_route.png")


# ---------------------------------------------------------------- 쿤밍 (D1·D7·D8)
KM0 = 25.03
K = lambda lon, lat: proj(lon, lat, KM0)  # noqa: E731
KM_HOTEL, KM_ST = K(102.7232, 25.0205), K(102.7225, 25.0160)


def map_d1():
    x0, _ = K(102.60, 0); x1, _ = K(102.97, 0)
    f = Fig("map_d1", "D1 쿤밍 도착 (12/16 밤)", "창수이 공항 22:00 도착 → 택시·디디 약 45분 → 쿤밍역 북광장 Gefei VX 호텔",
            (x0, x1), (24.97, 25.135), panels=[("note", 150)])
    ax = f.ax
    dian = [K(*q) for q in [(102.62, 24.995), (102.675, 24.985), (102.70, 24.97), (102.62, 24.96)]]
    ax.add_patch(Polygon(dian, closed=True, fc=WATER, ec="none", alpha=0.8, zorder=1))
    ax.text(*K(102.672, 24.976), "전지(滇池)", fontsize=14, color="#3d78b3", ha="center")
    ap = K(102.929, 25.101)
    f.place(ap, "창수이 국제공항\n22:00 도착", "place", "b",
            H("창수이 국제공항", "place", "kmg_airport", ["창수이", "공항"], "昆明长水国际机场", "D1 22:00 도착 · 입국·수하물 약 50분"))
    f.place(KM_HOTEL, "Gefei VX 호텔 (숙소)\n永平路7号 · 쿤밍역 북광장", "lodging", (14, 30, "left", "bottom"),
            H("쿤밍역 · Gefei VX Hotel", "lodging", "kunming1", ["쿤밍역", "Gefei", "숙소"], note="D1·D7 숙박. 역 북광장 360m"))
    f.place(KM_ST, "쿤밍역(昆明站)", "place", "br",
            H("쿤밍역", "place", "kunming_station", ["쿤밍역"], "昆明站", "D2 08:29 C446 출발 · 호텔에서 도보 360m"), size=15)
    cu = K(102.704, 25.048)
    f.dot(cu, "poi", 9)
    f.label(cu, "취호·남병가(시내 중심)", "l", size=14, box=False, color=MUTED)
    f.seg(ap, KM_HOTEL, "taxi", rad=0.18, lw=4.4)
    f.tag(K(102.83, 25.09), "택시·디디 약 45분\n22:50 → 23:40", "taxi", size=16)
    f.legend(["taxi"], "upper left")
    pa = f.panels[0]
    pa.text(34, 16, "도착 후 할 일", fontsize=18, fontweight="bold", color=INK, va="top")
    pa.text(34, 54, "• 도착 로비에서 위챗페이·알리페이·유심 작동 확인 → 디디 호출(짐 3개 고지)\n"
                    "• 23:30 이후 늦은 체크인은 호텔에 미리 알림 · D2 아침 07:30 체크아웃 → 역까지 도보 5분",
            fontsize=15.5, color=INK, va="top", linespacing=1.5)
    f.save("map_d1_kunming_arrival.png")


def map_d7():
    """D7: 오후 호텔 휴식·배달 점심, 저녁에 남병가·금마벽계방 한 곳만"""
    x0, _ = K(102.700, 0); x1, _ = K(102.730, 0)
    f = Fig("map_d7", "D7 쿤밍: 쉬는 날 (12/22)", "12:50 쿤밍역 → 호텔 체크인 → 배달 점심·휴식 → 17:00 남병가·금마벽계방 한 곳 → 19:30 귀가",
            (x0, x1), (25.012, 25.045), panels=[("tl", 250)])
    nan, jm, jxy = K(102.7115, 25.0395), K(102.7085, 25.0335), K(102.7165, 25.0365)
    f.place(KM_HOTEL, "Gefei VX 호텔\n배달 점심·휴식", "lodging", "r",
            H("Gefei VX Hotel", "lodging", "kunming2", ["Gefei", "호텔", "숙소"], note="D7 숙박. 배달은 프런트 수령(放前台)"))
    f.place(KM_ST, "쿤밍역 12:50", "place", "r",
            H("쿤밍역", "place", "kunming_station", ["쿤밍역"], "昆明站", "D7 12:50 C644 도착 → 북광장 도보 360m"), size=16)
    f.place(nan, "남병가", "place", "l", H("남병가", "place", "nanping", ["남병가"], "南屏步行街", "D7 저녁 한 곳만"))
    f.place(jm, "금마벽계방 야경", "place", "l",
            H("금마벽계방", "place", "jinmabiji", ["금마벽계방"], "金马碧鸡坊", "저녁 후 조명 야경"), size=16)
    f.place(jxy, "저녁 건신원 과교미선", "meal", "r",
            H("건신원 과교미선(宝善街)", "meal", "建新园", ["건신원"], "建新园过桥米线(宝善街店)", "D7 저녁 17:20"), size=15.5)
    f.path([KM_ST, KM_HOTEL], "walk", lw=4)
    f.seg(KM_HOTEL, nan, "taxi", rad=-0.2, lw=4)
    f.tag(K(102.722, 25.032), "17:00 택시 20분", "taxi", size=15)
    f.seg(nan, jxy, "walk")
    f.path([jxy, jm], "night", rad=0.2)
    f.seg(jm, KM_HOTEL, "taxi", rad=0.3)
    f.tag(K(102.713, 25.022), "19:00 택시 귀가", "taxi", size=15)
    f.legend(["walk", "night", "taxi"], "lower left")
    pa = f.panels[0]
    rows = [("13:10", C["car"], "점심 배달: 美团外卖·饿了么 → 프런트 수령(放前台)"),
            ("14:00", C["walk"], "호텔 휴식·낮잠·짐 정리(3시간)"),
            ("17:00", C["taxi"], "남병가·금마벽계방 한 곳만, 저녁 과교미선"),
            ("19:00", C["taxi"], "일찍 귀가, 프런트에 내일 짐 보관 요청")]
    text_panel(pa, rows, y=20, lh=50, size=16.5)
    f.save("map_d7_kunming_city.png")


def map_d8():
    """도식 배치(실제 축척 아님)"""
    f = Fig("map_d8", "D8 쿤밍 시내 → 공항 (12/23)", "취호 갈매기 → 강무당 → 원통사 → 운남성박물관·관두고진 → 호텔 짐 → 공항 23:55",
            (0, 100), (0, 100), map_h=860, panels=[("tl", 210)])
    cu, jw, yt, ht = (26, 74), (10, 86), (46, 86), (52, 50)
    mu, gd = (78, 12), (60, 22)
    f.ax.add_patch(Circle(cu, 6, fc=WATER, ec="none", alpha=0.6, zorder=1))
    f.place(ht, "Gefei VX 호텔\n짐 보관·19:00 회수", "lodging", "r",
            H("Gefei VX Hotel", "lodging", "kunming2", ["Gefei", "숙소", "짐"], note="D8 07:30 체크아웃·짐 보관 → 18:30~19:00 회수"))
    f.place(cu, "① 취호공원 08:50\n붉은부리갈매기", "place", "b",
            H("취호공원", "place", "cuihu", ["취호"], "翠湖公园", "12월 붉은부리갈매기 시즌. 翠湖南路67号"), off=14)
    f.place(jw, "② 강무당 10:45", "place", "r",
            H("운남 육군강무당", "place", "jiangwutang", ["강무당"], "云南陆军讲武堂旧址", "翠湖西路22号. 입장 무료(예약제 확인)"))
    f.place(yt, "③ 원통사 13:00", "place", "r",
            H("원통사", "place", "yuantong", ["원통사"], "圆通寺", "圆通街30号 시내 고찰"))
    f.place(mu, "④ 운남성박물관 14:30\n(월요일 휴관 · 12/23 수요일)", "place", "l",
            H("운남성박물관", "place", "yunnan_museum", ["박물관"], "云南省博物馆", "官渡区广福路6393号. 무료 09:00~17:00"), off=14)
    f.place(gd, "⑤ 관두고진 16:30", "place", "l",
            H("관두고진", "place", "guandu", ["관두고진"], "官渡古镇", "금강탑·관두 바바 간식, 이른 저녁"))
    f.seg(ht, cu, "taxi", rad=-0.2)
    f.tag((32, 58), "08:30 택시 20분", "taxi")
    f.seg(cu, jw, "walk", lw=4)
    f.seg(jw, yt, "taxi", rad=-0.3)
    f.tag((28, 96), "점심 후 택시·도보 15분", "taxi", size=13.5)
    f.seg(yt, mu, "taxi", rad=-0.25)
    f.tag((80, 50), "14:00 택시 30분", "taxi")
    f.seg(mu, gd, "walk", lw=4)
    f.seg(gd, ht, "taxi", rad=-0.2)
    f.tag((44, 34), "18:30 택시 30분\n(퇴근길 정체)", "taxi")
    f.seg(ht, (96, 70), "car", rad=-0.15)
    f.tag((84, 76), "19:30 공항으로\n약 50분 →", "car")
    f.legend(["walk", "taxi", "car"], "lower left")
    pa = f.panels[0]
    rows = [("08:30", C["taxi"], "호텔 → 취호공원 남문(택시) · 아침 미선"),
            ("10:45", C["walk"], "취호 → 강무당 도보 5분 · 점심 기과계(汽锅鸡)"),
            ("14:00", C["taxi"], "원통사 → 운남성박물관(택시 30분) → 관두고진 도보"),
            ("19:30", C["car"], "호텔 짐 회수 → 창수이 공항(약 50분) · 23:55 출발")]
    text_panel(pa, rows, y=20, lh=46, size=16.5)
    f.save("map_d8_kunming_city.png")


# ---------------------------------------------------------------- 리장고성 도식 좌표(구 맛집 지도 픽셀 기준)
OT = {
    "hotel": (147, 118), "dsc": (215, 92), "dsq": (219, 168), "sfj": (208, 218), "gyj": (172, 262),
    "mufu": (138, 300), "nm": (208, 392), "zy": (290, 318), "qy": (281, 238), "wy": (310, 268),
    "wgl": (62, 270), "gmk": (189, 203), "park": (232, 62),
}
RIVER = [(214, 40), (212, 95), (208, 140), (214, 185), (220, 235), (226, 290), (236, 345), (246, 410), (252, 470)]
WALL = [(30, 60), (300, 45), (395, 160), (400, 300), (360, 460), (60, 470), (25, 330)]
LANES = [["dsc", "dsq", "sfj"], ["sfj", "gyj", "mufu"], ["sfj", "qy", "wy", "zy"], ["gyj", "nm"], ["zy", "nm"],
         ["hotel", "dsc"], ["sfj", "gmk"], ["mufu", "wgl"]]


def oldtown_base(f, labels=True):
    ax = f.ax
    ax.add_patch(Polygon(WALL, closed=True, fc="#efe6d4", ec="#d8c9aa", lw=2, zorder=0.5))
    xs, ys = zip(*RIVER)
    ax.plot(xs, ys, color=WATER, lw=7, zorder=1, solid_capstyle="round")
    for lane in LANES:
        xs, ys = zip(*[OT[k] for k in lane])
        ax.plot(xs, ys, color="#d9cdb4", lw=9, zorder=1.2, solid_capstyle="round")
    if labels:
        for k, t, pos in [("dsc", "대수차(大水车)", "r"), ("dsq", "대석교", "r"), ("sfj", "사방가(四方街)", "l"),
                          ("mufu", "목부(木府)", "l"), ("nm", "남문광장", "b"), ("zy", "충의시장", "r"),
                          ("qy", "칠일가", "tr"), ("wy", "오일가", "r"), ("wgl", "만고루(사자산)", "b"), ("gyj", "광의가", "bl")]:
            ax.plot(*OT[k], "s", ms=8, color="#8b7d64", zorder=3)
            f.label(OT[k], t, pos, size=14.5, box=False, color="#6b5d45", off=8)


def map_d2():
    f = Fig("map_d2", "D2 리장고성 도보 동선 (12/17 오후)", "리장역에서 택시 30분 → 호텔 체크인 → 대수차 → 사방가 → 목부 → 남문 저녁 → 야경",
            (5, 420), (25, 490), invert_y=True, panels=[("tl", 300)])
    oldtown_base(f)
    o = OT
    f.place(o["hotel"], "물라뉴 탕수 호텔", "lodging", "l",
            H("물라뉴 탕수 호텔(黄山上段18号)", "lodging", "mulanyu", ["물라뉴", "호텔"], note="D2~D3 숙박. 고성 안 차량 진입 불가"))
    ts = (238, 104)
    f.place(ts, "田村 야크훠궈", "meal", "r", H("田村 야크훠궈(대수차점)", "meal", "田村", ["田村"], "田村·酸汤牦牛肉火锅", "D2 늦은 점심"), size=14.5, dot=10)
    sg = (231, 380)
    f.place(sg, "석과어", "meal", "r", H("석과어", "meal", "石锅鱼", ["석과어"], "石锅鱼", "D2 저녁 선택①"), size=14.5, dot=10)
    for k, h in [("dsc", H("대수차", "poi", None, ["대수차"], "大水车", "고성 북쪽 입구 상징. 차량 진입 지점 인근")),
                 ("dsq", H("대석교", "poi", None, ["대석교"], "大石桥", "수로 사진 포인트")),
                 ("sfj", H("사방가", "poi", None, ["사방가"], "四方街", "고성 중심 광장 · D3 투어 픽업(Sifang Street)")),
                 ("mufu", H("목부", "place", "mufu", ["목부"], "木府", "나시족 토사 관저. 입장 ¥40(선택)")),
                 ("gyj", H("광의가", "poi", None, ["광의가"], "光义街", "습전주미 야생버섯훠궈·계두량분 골목")),
                 ("zy", H("충의시장", "poi", None, ["충의시장"], "忠义市场", "D2 저녁 후 아침 빵 구입")),
                 ("nm", H("남문광장", "poi", None, ["남문"], "南门广场", "맛집 지도 기준점"))]:
        f.hot(o[k], **h)
    # 리장역 도착
    f.seg((330, 30), (250, 55), "taxi", lw=4)
    f.tag((350, 36), "리장역 → 택시 30분\n차량 진입 지점 하차", "taxi", size=14)
    f.seg((250, 55), o["hotel"], "walk", rad=0.2, lw=3, dashed=(0, (2, 2)))
    path = [o["hotel"], o["dsc"], o["dsq"], o["sfj"], o["gyj"], o["mufu"]]
    f.path(path, "walk", rad=0.08)
    f.seg(o["mufu"], o["nm"], "walk", rad=0.25)
    f.path([o["nm"], o["zy"], o["sfj"], o["hotel"]], "night", rad=-0.15, lw=3)
    for n, k, dx, dy in [(1, "hotel", 0, -26), (2, "dsc", -26, 4), (3, "dsq", -26, 0), (4, "sfj", 24, 22),
                         (5, "mufu", 0, -26), (6, "nm", -28, 0)]:
        f.step(o[k], n, "walk", dx=dx, dy=dy)
    f.step(o["zy"], 7, "night", dx=0, dy=24)
    f.legend(["walk", "night", "taxi"], "lower right")
    rows = [("1", C["walk"], "13:15 체크인 · 짐 정리 (체크아웃 날 짐 운반도 이때 미리 부탁)"),
            ("2", C["walk"], "14:30 대수차 → 田村 야크훠궈 등 늦은 점심"),
            ("3", C["walk"], "15:30 대석교 → 신화가 수로 따라 사진"),
            ("4", C["walk"], "16:30 사방가 → 광의가 골목(계두량분)"),
            ("5", C["walk"], "17:00 목부 관람(¥40, 선택)"),
            ("6", C["walk"], "18:30 남문 저녁: 석과어·습전주미·운설리"),
            ("7", C["night"], "20:00 충의시장(내일 아침 빵) → 사방가 야경 → 호텔")]
    pa = f.panels[0]
    for i, (n, col, t) in enumerate(rows):
        y = 18 + i * 40
        pa.text(40, y, n, fontsize=14, fontweight="bold", color="white", va="top", ha="center",
                bbox=dict(boxstyle="circle,pad=0.25", fc=col, ec="none"))
        pa.text(66, y, t, fontsize=16, color=INK, va="top")
    f.save("map_d2_lijiang_oldtown_walk.png")


# ---------------------------------------------------------------- D3 옥룡설산 투어
def map_d3():
    """도식 배치. 관광센터·인상리장 공연장은 甘海子 초원 한곳에 모여 있고, 거기서 셔틀로 람월곡·대索道를 오간다.
    순서는 가이드가 정한다"""
    f = Fig("map_d3", "D3 옥룡설산 투어 (12/18 · 예약 확정)", "사방가 픽업 → 甘海子(관광센터·인상리장 공연장) ⇄ 람월곡 · 대索道 빙천공원 4,506m → 고성 복귀",
            (0, 100), (0, 100), map_h=900, panels=[("tl", 470)])
    ax = f.ax
    ax.add_patch(Polygon([(0, 100), (0, 60), (14, 48), (30, 70), (44, 100)], closed=True, fc="#e3e8ef", ec="#c5cfdb", lw=1.5, zorder=0.6))
    ax.text(10, 56, "옥룡설산\n(玉龙雪山)", fontsize=15, color="#56657a", ha="center", zorder=2)
    f.mountain((14, 88), "주봉 5,596m", 14)
    lj, bs, cen = (62, 8), (48, 26), (52, 55)
    gh, imp, low, up, lan = (68, 56), (61, 45), (32, 72), (22, 83), (78, 80)
    # 甘海子 일대: 관광센터·초원·인상리장 공연장이 한곳(서로 걸어서 몇 분)
    ax.add_patch(Ellipse((59, 51), 40, 22, fc="#efe9fb", ec=C["tour"], lw=2, ls=(0, (5, 3)), zorder=0.8))
    ax.text(39, 41, "甘海子 일대(보라 원)\n관광센터·초원·공연장이\n한곳에 모여 있다", fontsize=15, color=C["tour"], fontweight="bold",
            ha="right", va="center", linespacing=1.25, zorder=3)
    f.place(lj, "리장고성 사방가(四方街)\n픽업 05:00~07:00 · 복귀 16~17시", "lodging", "r",
            H("사방가 픽업(Sifang Street)", "place", "sifang", ["Sifang", "사방가", "픽업"], "四方街",
              "D3 투어 무료 픽업. 정확한 시각·만남 위치는 가이드 위챗 안내"))
    f.dot(bs, "poi", 8)
    f.label(bs, "백사 경유", "l", size=14, box=False, color=MUTED)
    f.place(cen, "관광센터\n(셔틀 환승)", "place", "l",
            H("옥룡설산 관광센터", "place", "yulong_center", ["관광센터"], "玉龙雪山游客中心", "셔틀 환승·식당"), size=15, dot=10)
    f.place(gh, "甘海子 초원\n설산 전망 [일정 1]", "place", "r",
            H("감해자(甘海子)", "place", "ganhaizi", ["甘海子", "감해자"], "甘海子", "설산 전망 초원 · 투어 포함"), size=16)
    f.place(lan, "람월곡(블루문밸리)\n옥빛 호수 [일정 1]", "place", "l",
            H("람월곡(블루문밸리)", "place", "lanyuegu", ["람월곡", "蓝月谷", "블루문"], "蓝月谷", "투어 포함 · 전동차·도보"), size=16)
    f.place(low, "대索道 하부역 3,356m", "place", "l",
            H("대索道 하부역", "place", "yulong_cableway", ["대索道", "케이블카", "하부역"], "玉龙雪山大索道",
              "투어 포함. 여권 실명 확인, 대기 길 수 있음"), size=15, dot=10)
    f.place(up, "빙천공원 4,506m\n대索道 [일정 2]", "place", "r",
            H("빙천공원 4,506m", "place", "yulong_cableway", ["빙천공원", "4,506"], "冰川公园",
              "강풍·영하 15도 안팎. 산소통, 천천히 걷기"), size=16)
    f.place(imp, "인상리장 공연장(印象丽江)\n甘海子 안 · 공연 [일정 3]", "place", "r",
            H("인상리장 공연장", "place", "impression", ["인상리장", "印象丽江", "공연"], "印象丽江",
              "야외 공연 약 1시간 · 투어 포함"), size=16)
    f.path([lj, bs, cen], "tour", lw=4.5)
    f.seg(imp, lj, "tour", rad=-0.35, lw=3, dashed=(0, (4, 3)))
    f.tag((78, 24), "투어 차량 약 1시간\n복귀 16:00~17:00", "tour", size=15)
    for p in (lan, low):
        f.seg(cen, p, "tour", lw=2.4, head=False, dashed=(0, (2, 2)))
    for p in (gh, imp):
        f.seg(cen, p, "tour", lw=2, head=False, dashed=(0, (1, 2)))
    f.seg(low, up, "car", lw=4, dashed=(0, (1.5, 1.5)))
    from matplotlib.lines import Line2D
    ax.legend([Line2D([], [], color=C["tour"], lw=4), Line2D([], [], color=C["tour"], lw=2.4, ls=(0, (2, 2))),
               Line2D([], [], color=C["car"], lw=4, ls=(0, (1.5, 1.5)))],
              ["투어 차량", "경내 셔틀(순서는 가이드)", "케이블카(대索道)"], loc="upper right", fontsize=14, framealpha=0.95)
    pa = f.panels[0]
    pa.add_patch(FancyBboxPatch((24, 10), W - 48, 448, boxstyle="round,pad=0,rounding_size=18", fc="#f4f0fb", ec="#d9cdf3", lw=1.5))
    pa.text(48, 30, "바우처 요약 · 리장 옥룡설산 & 람월곡 종일 투어(케이블카 탑승)", fontsize=17.5, fontweight="bold", color=C["tour"], va="top")
    lines = [
        ("픽업", "05:00~07:00 사방가(Sifang Street) 무료 픽업 · 정확한 시각은 가이드 안내"),
        ("일정", "[1] 옥룡설산·甘海子·람월곡 3시간 → [2] 대索道 1시간 → [3] 인상리장 1시간"),
        ("복귀", "차량 약 1시간 → 16:00~17:00 지정 주소 하차(호텔 근처 차량 지점 요청)"),
        ("포함", "차량·기사·가이드, 입장권(옥룡설산·甘海子·람월곡·대索道·인상리장), 보험"),
        ("연락", "★출발 3일 전(12/15)까지 가이드 위챗 18088479095 · 2487812697@qq.com"),
        ("준비", "여권(케이블카 실명제)·두꺼운 패딩·장갑·모자·산소통·선글라스, 점심 각자"),
        ("인원", "성인 3 · 영어 가이드 · 결제 ₩353,600 · 순서는 현장 사정에 따라 바뀔 수 있음"),
    ]
    for i, (k, v) in enumerate(lines):
        y = 82 + i * 52
        pa.text(48, y, k, fontsize=15, fontweight="bold", color="white", va="top",
                bbox=dict(boxstyle="round,pad=0.3", fc=C["tour"], ec="none"))
        pa.text(118, y, v, fontsize=16, color=INK, va="top")
    f.save("map_d3_yulong_tour.png")


# ---------------------------------------------------------------- D4 백사·수허
def map_d4():
    """도식 배치(실제 축척 아님)"""
    f = Fig("map_d4", "D4 리장고성 → 백사 · 수허 (12/19)", "오전 고성 산책 → 11:30 체크아웃·짐 → Rishine 빌라 → 백사고진 → 수허고진 → 빌라 온천",
            (0, 100), (0, 100), map_h=820, panels=[("tl", 250)])
    lj, rs, bs, sh = (70, 12), (28, 80), (62, 66), (34, 40)
    f.place(lj, "리장고성 (물라뉴 호텔)\n11:30 체크아웃·짐 운반", "lodging", "l",
            H("리장고성 · 물라뉴 탕수 호텔", "lodging", "mulanyu", ["물라뉴", "고성", "체크아웃"], note="D4 12:00 전 체크아웃. 짐 운반 미리 요청"))
    f.place(rs, "Rishine 빌라 (숙소)\n丰乐村一组48号", "lodging", "r",
            H("Rishine 빌라", "lodging", "rishine", ["Rishine", "빌라"], note="D4 숙박 · 일조금산 일출·프라이빗 온천"))
    f.place(bs, "백사고진 12:30~14:30\n우육면·기와카페·벽화", "place", "r",
            H("백사고진", "place", "baisha", ["백사고진", "백사"], "白沙古镇", "D4 12:30~14:30 점심·산책"))
    f.place(sh, "수허고진 청룡교\n14:50~17:00", "place", "l",
            H("수허고진 청룡교", "place", "shuhe", ["수허고진", "청룡교"], "束河古镇 青龙桥", "사방청→구정용담→청룡교, 이른 저녁"))
    f.mountain((50, 92), "옥룡설산 방향 (D5 07:50 일조금산 일출)", 14)
    f.seg(lj, rs, "taxi", rad=-0.42, lw=4)
    f.tag((62, 34), "① 11:30\n택시 30~40분", "taxi", size=15)
    f.seg(rs, bs, "taxi", rad=-0.2)
    f.tag((46, 82), "② 10분", "taxi", size=15)
    f.seg(bs, sh, "taxi", rad=-0.2)
    f.tag((55, 48), "③ 20분", "taxi", size=15)
    f.seg(sh, rs, "taxi", rad=-0.3)
    f.tag((18, 62), "④ 17:00\n25분", "taxi", size=15)
    f.legend(["taxi"], "lower left")
    pa = f.panels[0]
    pa.text(34, 14, "오전 고성 산책 (09:00~11:30)", fontsize=18, fontweight="bold", color=C["walk"], va="top")
    pa.text(34, 52, "호텔 → 대수차 → 대석교 → 사방가 → 딩딩당당(官门口42号, 야크 요거트) → 호텔\n"
                    "체크아웃 때 미리 부탁한 짐 운반으로 차량 진입 지점까지 이동 → 택시·디디\n"
                    "빌라 도착 후 짐만 맡기고(얼리 체크인 위챗 확인) 백사고진으로\n"
                    "17:30 빌라 프런트에 내일 09:00 상호도협 전용차 예약 요청",
            fontsize=15.5, color=INK, va="top", linespacing=1.55)
    f.save("map_d4_baisha_shuhe.png")


# ---------------------------------------------------------------- map05 호도협
def map05():
    f = Fig("map05", "D5 호도협 경로 (12/20)", "트레킹은 차마객잔 → 중도객잔 평탄 구간(약 5km, 2시간)만. 28밴드 급경사는 제외",
            (20, 940), (40, 420), invert_y=True, map_h=600, panels=[("profile", 470)])
    ax = f.ax
    xs, ys = zip(*[(60, 320), (220, 330), (350, 352), (620, 364), (920, 370)])
    ax.plot(xs, ys, color=WATER, lw=12, zorder=1)
    ax.text(780, 392, "금사강(진사강)", color="#3d78b3", fontsize=15, ha="center")
    ax.text(470, 55, "▲ 합파설산 (트레일 쪽, 북쪽)", fontsize=14, color=MUTED, ha="center")
    ax.text(470, 412, "▲ 옥룡설산 (강 건너, 남쪽)", fontsize=14, color=MUTED, ha="center")
    P = {"qt": (97, 268), "up": (255, 310), "chama": (431, 148), "view": (527, 160), "zt": (622, 171),
         "come": (662, 131), "gy": (690, 198), "tina": (757, 265), "nx": (213, 210), "b28": (315, 108), "zh": (840, 306)}
    gray = "#a8adb5"
    ax.plot(*zip(P["qt"], P["nx"], P["b28"], P["chama"]), color=gray, lw=3, ls=(0, (3, 2)), zorder=2)
    ax.plot(*zip(P["zt"], P["gy"], P["tina"], P["zh"]), color=gray, lw=3, ls=(0, (3, 2)), zorder=2)
    for k, t, pos in [("nx", "나시객잔", "t"), ("b28", "28밴드 정상 2,670m", "t"), ("gy", "관음폭포", "r"),
                      ("tina", "티나객잔", "r"), ("zh", "중호도협", "b")]:
        ax.plot(*P[k], "o", ms=9, color=gray, zorder=3)
        f.label(P[k], t, pos, size=14, color="#7a808a", box=False)
    ax.text(250, 175, "제외: 28밴드 급경사", fontsize=13.5, color="#7a808a")
    ax.text(700, 238, "제외: 티나객잔 하산", fontsize=13.5, color="#7a808a")
    f.seg((40, 400), P["qt"], "car", lw=4)
    f.tag((70, 412), "09:00 Rishine 빌라 출발", "car", ha="left", size=14)
    f.seg(P["qt"], P["up"], "car", lw=4)
    f.seg(P["up"], P["chama"], "car", rad=-0.2, lw=4)
    f.tag((330, 255), "11:30 차로\n차마객잔까지", "car", size=14)
    ax.plot(*zip(P["chama"], P["view"], P["zt"]), color=C["walk"], lw=8, zorder=4, solid_capstyle="round")
    f.tag((520, 215), "12:00~14:00 트레킹 약 5km (평탄)", "walk", size=16)
    f.seg(P["zt"], P["come"], "walk", lw=4)
    f.seg((672, 120), (800, 52), "car", rad=-0.2, lw=4)
    f.tag((835, 150), "D6 10:30 빵차\n→ 샹그릴라 약 3시간", "car", size=14)
    ax.plot([P["up"][0], 450, 690, P["come"][0]], [P["up"][1], 296, 265, P["come"][1]], color=C["car"], lw=2, ls=":", zorder=2)
    ax.text(480, 312, "큰 짐은 차량으로 Come Inn 전달", fontsize=13.5, color=C["car"], ha="center")
    f.place(P["qt"], "교두 검표소\n입장 ¥45", "poi", "l", H("교두 검표소", "poi", None, ["교두"], "桥头 检票口", "호도협 입구. 입장 ¥45"), size=15)
    f.place(P["up"], "상호도협\n10:30~11:30", "place", "b",
            H("상호도협", "place", "upper_tiger", ["상호도협"], "上虎跳峡", "에스컬레이터 왕복 ¥70, 호도석 전망대, 매점 점심"), size=15)
    f.place(P["chama"], "차마객잔 (출발)", "place", "t",
            H("차마객잔 (트레킹 시작)", "place", "chama", ["차마객잔"], "茶马客栈", "D5 12:00 출발. 짐은 차량으로 Come Inn 전달"), size=15.5)
    f.place(P["view"], "협곡 전망", "poi", "b", H("협곡 전망", "poi", None, ["하이트레일"], "", "등고선길 중간. 절벽길 주의"), size=14)
    f.place(P["zt"], "중도객잔 (도착)", "place", "b",
            H("중도객잔 (트레킹 끝)", "place", "zhongtu", ["중도객잔"], "中途客栈", "D5 14:00 도착. 2층 카페 휴식"), size=15.5)
    f.place(P["come"], "Come Inn (숙소)", "lodging", "t",
            H("Come Inn (숙박)", "lodging", "comeinn", ["Come Inn", "본지만"], note="본지만 9호. D6 10:30 빵차 출발"), size=15.5, off=16)
    for k in ("gy", "tina", "nx", "b28", "zh"):
        z = {"gy": H("관음폭포", "poi", None, ["관음폭포"], "观音瀑布", "제외 구간"),
             "tina": H("티나객잔", "poi", None, ["티나객잔"], "Tina's 客栈", "빵차 대안: 티나객잔 → 샹그릴라 버스 ¥55/인"),
             "nx": H("나시객잔", "poi", None, ["나시객잔"], "纳西雅阁", "제외 구간(28밴드 급경사 시작점)"),
             "b28": H("28밴드 정상 2,670m", "poi", None, ["28밴드"], "28道拐", "제외. 고도차 500m 급경사"),
             "zh": H("중호도협", "poi", None, ["중호도협"], "中虎跳峡", "제외(D6 빵차 이동으로 대체)")}[k]
        f.hot(P[k], **z)
    f.legend(["car", "walk"], "upper left")
    # 고도 단면
    pa = f.panels[0]
    pa.text(28, 14, "고도 단면 (개략, 해발 m)", fontsize=18, fontweight="bold", color=INK, va="top")
    prof = [(0, 1850, "교두"), (3, 2150, "나시객잔"), (6.5, 2670, "28밴드"), (9, 2330, "차마객잔"),
            (14, 2345, "중도객잔"), (15.5, 2250, "관음폭포"), (17, 2080, "티나객잔")]
    X = lambda km: 90 + km * 54  # noqa: E731
    Yy = lambda m: 430 - (m - 1700) * 0.33  # noqa: E731
    pa.fill_between([X(p[0]) for p in prof], [Yy(p[1]) for p in prof], [Yy(1700)] * len(prof), color="#eceef1", zorder=0)
    pa.plot([X(p[0]) for p in prof], [Yy(p[1]) for p in prof], color=gray, lw=3, ls=(0, (3, 2)))
    pa.plot([X(9), X(14)], [Yy(2330), Yy(2345)], color=C["walk"], lw=8, solid_capstyle="round")
    for km, m, t in prof:
        hl = t in ("차마객잔", "중도객잔")
        pa.plot(X(km), Yy(m), "o", ms=10 if hl else 7, color=C["walk"] if hl else gray)
        pa.text(X(km), Yy(m) - 14, f"{t}\n{m:,}m", ha="center", va="bottom", fontsize=15 if hl else 13,
                fontweight="bold" if hl else "normal", color=INK if hl else "#7a808a", linespacing=1.1)
    for m in (1800, 2200, 2600):
        pa.plot([70, W - 40], [Yy(m)] * 2, color="#e3e5e8", lw=1, zorder=-1)
        pa.text(64, Yy(m), f"{m:,}", ha="right", va="center", fontsize=12, color="#8a8f98")
    pa.text(X(11.5), Yy(2345) + 34, "트레킹 구간: 고도차 약 +15m", ha="center", fontsize=16, color=C["walk"], fontweight="bold")
    pa.text(X(3.3), Yy(2500) - 40, "28밴드: 고도차 약 500m\n급경사 → 제외", ha="center", fontsize=14, color="#7a808a")
    f.save("map05_tigerleaping_route_profile.png")


# ---------------------------------------------------------------- map03 샹그릴라
def map03():
    """도식 배치(실제 축척 아님)"""
    f = Fig("map03", "D6·D7 샹그릴라 (12/21~22)", "D6 13:30 빵차 도착 → 송찬림사 → 대형 마니차 야경 · D7 06:10 샌딩 → 07:22 C644",
            (0, 100), (0, 100), map_h=820, panels=[("inset", 330)])
    sz, ou, sq, st = (42, 86), (40, 50), (60, 54), (30, 14)
    f.ax.add_patch(Circle((24, 90), 5, fc=WATER, ec="none", alpha=0.7, zorder=1))
    f.ax.text(24, 82, "라무양호", fontsize=13, color="#3d78b3", ha="center")
    f.place(sz, "송찬림사 (작은 포탈라궁)\n15:20~17:20 · 입장 ¥115", "place", "r",
            H("송찬림사(작은 포탈라궁)", "place", "songzanlin", ["송찬림사"], "噶丹·松赞林寺", "계단 146개 천천히, 입장 ¥115"))
    f.place(ou, "Oura Villa (숙소)", "lodging", "l",
            H("Oura Villa (숙소)", "lodging", "oura", ["Oura Villa", "숙소"], note="龟山巷3号 · 대불사·대형 마니차 바로 옆"))
    f.place(sq, "독극종고성 사방가", "place", "r",
            H("독극종고성 사방가", "place", "dukezong", ["사방가", "고성", "대형 마니차", "대불사"], "独克宗古城 四方街",
              "야크훠궈·고기파이·야크우유 에그타르트"), size=16)
    f.place(st, "샹그릴라역\nD7 07:22 C644 → 쿤밍", "place", "r",
            H("샹그릴라역", "place", "shangrila_station", ["샹그릴라역"], "香格里拉站", "D7 07:22 C644 → 쿤밍 12:50. 06:10 숙소 샌딩"))
    f.seg((96, 6), ou, "car", rad=-0.2, lw=4)
    f.tag((80, 26), "D6 13:30 빵차 도착\n(호도협에서 3시간)", "car", size=14.5)
    f.seg(ou, sz, "taxi", rad=-0.35)
    f.tag((24, 68), "D6 15:00\n택시 20분", "taxi", size=14.5)
    f.seg(sz, sq, "taxi", rad=-0.3)
    f.tag((64, 72), "17:20 택시 20분", "taxi", size=14.5)
    f.seg(ou, st, "taxi", rad=0.25, lw=4)
    f.tag((22, 34), "D7 06:10\n샌딩 25분", "taxi", size=14.5)
    f.seg(st, (8, 2), "train")
    f.legend(["car", "taxi", "train"], "upper right")
    pa = f.panels[0]
    pa.text(34, 12, "고성 안 (숙소 주변, 도보 5~10분)", fontsize=18, fontweight="bold", color=INK, va="top")
    pa.add_patch(FancyBboxPatch((34, 56), W - 68, 255, boxstyle="round,pad=0,rounding_size=14", fc=LAND, ec="#d9d4c7"))
    pts = {"ou": (300, 210), "bud": (300, 120), "sq": (640, 190), "yue": (820, 120), "food": (900, 250)}
    pa.plot(*zip(pts["ou"], pts["bud"]), color=C["walk"], lw=4)
    pa.plot(*zip(pts["ou"], pts["sq"], pts["yue"]), color=C["night"], lw=4)
    for k, t, col, dy in [("ou", "Oura Villa", KIND["lodging"], 26), ("bud", "대불사 · 대형 마니차(구산공원)\n17:40 일몰·야경", C["walk"], -40),
                          ("sq", "사방가 · 14:00 늦은 점심", KIND["place"], 30), ("yue", "월광광장 19:00", C["night"], -30),
                          ("food", "야크훠궈·고기파이·에그타르트", KIND["meal"], 26)]:
        pa.plot(*pts[k], "o", ms=13, color=col, mec="white", mew=2)
        pa.text(pts[k][0], pts[k][1] + dy, t, ha="center", va="center", fontsize=15.5, color=INK)
    f.save("map03_shangrila.png")


# ---------------------------------------------------------------- map06 맛집
PINS = [  # no, 이름, 중국어, 분류, 남문거리, 도식좌표, 핫스팟(type,key,keywords,zh,note)
    (1, "석과어", "石锅鱼", "식사", 150, (231, 380), ("meal", "石锅鱼", ["석과어"], "石锅鱼", "")),
    (2, "운설리 남문점", "云雪丽", "식사", 150, (185, 373), ("meal", "云雪丽·纳西庭院", ["운설리"], "云雪丽 古城南门店", "")),
    (3, "격벽노왕 남문점", "隔壁老王", "식사", 300, (246, 352), ("poi", None, ["격벽노왕"], "隔壁老王 南门店", "누룽지 탕·돼지고기볶음")),
    (4, "습전주미 야생버섯훠궈", "拾全酒美", "식사", 500, (158, 322), ("meal", "拾全酒美", ["습전주미"], "拾全酒美", "光义街忠义巷38号")),
    (5, "작자미선", "勺子米线", "아침", 650, (189, 280), ("meal", "勺子米线", ["작자미선"], "勺子米线", "百岁坊8号 아침")),
    (6, "할머니 계두량분", "奶奶鸡豆凉粉", "간식", 850, (160, 240), ("meal", "奶奶鸡豆凉粉", ["계두량분"], "奶奶鸡豆凉粉", "光义街新院巷30号")),
    (7, "딩딩당당", "丁丁当当", "간식", 1050, (180, 196), ("meal", "丁丁当当", ["딩딩당당"], "丁丁当当酸奶店", "官门口42号 야크 요거트·번")),
    (8, "아파 납배골훠궈", "阿婆腊排骨火锅", "식사", 900, (292, 246), ("meal", "阿婆腊排骨火锅", ["납배골"], "阿婆腊排骨火锅(总店)", "五一街兴仁上段38号")),
    (9, "88소흡", "88小吃", "간식", 1000, (322, 238), ("meal", "88小吃", ["88소흡"], "88小吃", "五一街88号")),
    (10, "천천선", "天天鲜", "식사", 900, (330, 290), ("meal", "天天鲜", ["천천선"], "天天鲜(丽江古城店)", "五一街王家庄巷47号")),
    (11, "ZEW 운남소흡", "ZEW", "간식", 1000, (350, 262), ("poi", None, ["ZEW"], "ZEW", "五一街王家庄巷18-19号")),
    (12, "아마의 납서식당", "阿妈意", "식사", 800, (300, 296), ("poi", None, ["아마"], "阿妈意纳西饮食院", "五一街")),
    (13, "이가미선", "二哥米线", "아침", 1000, (256, 214), ("meal", "二哥米线", ["이가미선"], "二哥米线", "崇仁巷32号 아침")),
    (14, "길상 구운 이과이", "吉祥烤饵块", "야식", 700, (312, 334), ("poi", None, ["이과이"], "吉祥烤饵块", "长水路忠义市场夜市 야식")),
    (15, "田村 야크훠궈(대수차점)", "田村", "식사", 1600, (238, 104), ("meal", "田村", ["田村"], "田村·酸汤牦牛肉火锅(古城大水车店)", "玉河走廊A区37号")),    (16, "관설 설산뷰 식당", "观雪·阅古楼", "식사", 1200, (104, 206), ("place", "guanxue", ["관설", "观雪"], "观雪·阅古楼雪山观景餐厅(万古楼店)", "黄山上段53号 · 설산 전망 · 납배골 훠궈")),
]
PIN_COLOR = {"식사": "#c62828", "아침": "#ef6c00", "간식": "#2e7d32", "야식": "#5e35b1"}
M_PER_PX = 5.6  # 도식 좌표 1px ≈ 5.6m (남문 거리로 역산)


def hotel_dist(p):
    d = math.dist(p, OT["hotel"]) * M_PER_PX
    return int(round(d / 50.0)) * 50


def map06():
    f = Fig("map06", "리장고성 맛집 지도", "번호 색 = 분류(식사·아침·간식·야식). 거리는 남문·호텔 기준 직선 거리(개략)",
            (5, 420), (25, 490), invert_y=True, panels=[("list", len(PINS) * 40 + 70)])
    oldtown_base(f)
    ax = f.ax
    ax.add_patch(Circle(OT["nm"], 1000 / M_PER_PX, fill=False, ec="#b9ad95", lw=1.5, ls=(0, (4, 4)), zorder=1.1))
    ax.text(OT["nm"][0] + 125, OT["nm"][1] - 140, "남문 1km", fontsize=13, color="#9a8d72", rotation=-38)
    f.place(OT["hotel"], "물라뉴 호텔", "lodging", "l", H("물라뉴 탕수 호텔", "lodging", "mulanyu", ["물라뉴"]), size=15.5)
    ax.plot(*OT["nm"], marker="*", ms=26, color="#c62828", mec="white", mew=1.5, zorder=7)
    f.hot(OT["nm"], **H("★ 남문 (기준점)", "poi", None, ["남문"], "南门广场", "거리 기준점"))
    for no, ko, zh, kind, dist, p, (t, key, kws, hz, note) in PINS:
        ax.annotate(str(no), p, ha="center", va="center", fontsize=15, fontweight="bold", color="white", zorder=9,
                    bbox=dict(boxstyle="circle,pad=0.3", fc=PIN_COLOR[kind], ec="white", lw=2))
        nm = f"{'①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳'[no - 1]} {ko}"
        f.hot(p, **H(nm, t, key, kws, hz, f"{kind} · 남문 약 {dist:,}m · 호텔 약 {hotel_dist(p):,}m" + (f". {note}" if note else "")))
    handles = [plt.Line2D([], [], marker="o", ls="", ms=14, color=c, mec="white", mew=1.5) for c in PIN_COLOR.values()]
    ax.legend(handles, list(PIN_COLOR), loc="upper right", fontsize=15, framealpha=0.95, ncol=1, borderpad=0.6,
              bbox_to_anchor=(0.995, 0.93))
    pa = f.panels[0]
    pa.text(34, 12, "번호 · 이름 · 분류 · 남문 / 호텔에서", fontsize=16, fontweight="bold", color=MUTED, va="top")
    for i, (no, ko, zh, kind, dist, p, _) in enumerate(PINS):
        y = 54 + i * 40
        pa.text(52, y + 13, str(no), fontsize=14, fontweight="bold", color="white", ha="center", va="center",
                bbox=dict(boxstyle="circle,pad=0.25", fc=PIN_COLOR[kind], ec="none"))
        pa.text(82, y, f"{ko}  {zh}", fontsize=16.5, color=INK, va="top")
        pa.text(W - 40, y, f"{kind} · {dist:,}m / {hotel_dist(p):,}m", fontsize=15.5, color=MUTED, va="top", ha="right")
    f.save("map06_lijiang_restaurants.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for fn in (map01, map_d1, map_d2, map_d3, map_d4, map05, map03, map_d7, map_d8, map06):
        fn()
    items, seen = [], {}
    for h in HOTSPOTS:
        seen[h["mapId"]] = seen.get(h["mapId"], 0) + 1
        items.append({"id": f"{h['mapId']}-{seen[h['mapId']]:02d}", **h})
    doc = json.loads(HOT.read_text(encoding="utf-8"))
    doc["version"] = "2026-11-05"
    doc["note"] = "tools/draw_maps.py 가 지도와 함께 생성한다(손으로 고치지 않는다). x,y 는 지도 이미지 픽셀. type: lodging|place|meal|poi"
    doc["items"] = items
    HOT.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"핫스팟 {len(items)}개 → {HOT.relative_to(REPO)}")
    maps = json.loads((REPO / "data" / "maps.json").read_text(encoding="utf-8"))
    for m in maps["items"]:
        if m["id"] == "map06":
            m["pins"] = [{"no": no, "nameKo": ko, "nameZh": zh, "kind": kind, "distance": f"약 {dist:,}m",
                          "hotelDistance": f"약 {hotel_dist(p):,}m", "color": PIN_COLOR[kind]} for no, ko, zh, kind, dist, p, _ in PINS]
    (REPO / "data" / "maps.json").write_text(json.dumps(maps, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
