#!/usr/bin/env python3
"""중국어만 있는 화면 글자(주소·상호·일정 목적지·템플릿 값 등)의 한국어 번역 사전 → data/json/ko_gloss.json.
build_data.py 가 마지막에 실행한다. 웹·앱은 이 사전으로 중국어 아래에 한국어를 작게 보여 준다.

    python3 tools/ko_gloss.py [data/json]

- 대상: 숙소·장소·식사·병원 주소, 식당 평점 상호·주소·라벨, 식사 추천 메뉴(중국어만), 일정 출발·경유·목적지(중국어만),
  위챗 템플릿 연결 값, 위챗 템플릿 기본값(tools 에서 웹 templates.ts 를 읽어 모은다).
- 이미 한국어가 함께 보이는 것(음식명·회화·메뉴·인기 메뉴·숙소/식당/관광지 이름)은 넣지 않는다.
- 번역 순서: data/ko_gloss_manual.json(손으로 고친 것) → 이미 만든 사전(data/ko_gloss.json, 저장소에 둔다)
  → 규칙 번역(알려진 지명·낱말은 한국어로, 나머지는 한자음; hanja·opencc 패키지가 있을 때만).
  패키지가 없는 환경에서도 이미 만든 사전으로 빌드된다.
"""
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
CJK = re.compile(r"[一-鿿]")
KO = re.compile(r"[가-힣]")

# 길게 맞는 것부터 바꾼다. 지명은 한국에서 흔히 쓰는 이름, 낱말은 뜻으로
WORDS = {
    "云南省": "윈난성 ", "云南": "윈난 ", "迪庆藏族自治州": "디칭 티베트족 자치주 ", "玉龙纳西族自治县": "위룽 나시족 자치현 ",
    "香格里拉市": "샹그릴라시 ", "香格里拉": "샹그릴라 ", "丽江市": "리장시 ", "丽江古城": "리장고성 ", "丽江站": "리장역", "丽江": "리장 ",
    "昆明市": "쿤밍시 ", "昆明站": "쿤밍역", "昆明火车站": "쿤밍 기차역", "昆明长水国际机场": "쿤밍 창수이 국제공항", "昆明": "쿤밍 ",
    "古城区": "구청구 ", "官渡区": "관두구 ", "五华区": "우화구 ", "盘龙区": "판룽구 ", "西山区": "시산구 ",
    "大研街道办事处": "다옌 가도 판사처 ", "大研街道": "다옌 가도 ", "建塘镇": "젠탕진 ", "虎跳峡镇": "후탸오샤진 ", "虎跳峡": "호도협 ",
    "白沙古镇": "백사고진 ", "白沙镇": "백사진 ", "白沙": "백사 ", "束河古镇": "수허고진 ", "束河": "수허 ",
    "独克宗古城": "독극종고성 ", "玉龙雪山": "옥룡설산 ", "四方街": "사방가", "木府": "목부", "大水车": "대수차", "黑龙潭": "흑룡담",
    "本地湾": "본지만", "中途客栈": "중도객잔", "茶马客栈": "차마객잔", "龟山公园": "구산공원", "大佛寺": "대불사", "月光广场": "월광광장",
    "南屏街": "남병가", "顺城购物中心": "순성 쇼핑센터", "购物中心": "쇼핑센터", "广场": "광장", "商场": "상가",
    "游客中心": "관광객 센터", "景区": "관광지", "停车场": "주차장", "地铁站": "지하철역", "火车站": "기차역", "国际机场": "국제공항",
    "北广场": "북광장", "古城": "고성", "古镇": "고진", "客栈": "객잔", "民宿": "민박", "酒店": "호텔", "餐厅": "식당", "超市": "마트",
    "斜对面": " 비스듬히 맞은편", "对面": " 맞은편", "旁边": " 옆", "附近": " 근처", "南侧": " 남쪽", "北侧": " 북쪽", "东侧": " 동쪽", "西侧": " 서쪽",
    "东北角": " 북동쪽 모퉁이", "里面": " 안", "不在": "아님 ", "最近": "가장 가까운", "离": "", "的": " ",
    "村委会": "촌위원회 ", "居委会": "주민위원회 ", "一组": "1조 ", "二组": "2조 ", "三组": "3조 ",
    "号楼": "동", "号": "호 ", "楼": "루", "层": "층 ", "座": "동 ", "单元": "호",
    "交叉口": " 교차로", "路口": " 교차로", "街口": " 거리 입구", "门口": " 입구", "位置待确认": "위치 확인 필요", "放前台": "프런트에 맡김", "前台": "프런트",
    "蓝月谷": "람월곡", "上虎跳": "상호도협", "大索道": "케이블카(대삭도)", "冰川公园": "빙천공원", "印象丽江": "인상리장", "甘海子": "감해자",
    "火锅": " 훠궈", "过桥米线": "과교미선", "小锅米线": "소과미선", "米线": "미선", "汽锅鸡": "기과계", "牦牛肉": "야크고기", "牦牛": "야크",
    "野生菌": "야생버섯", "腊排骨": "납배골", "酸汤": "새콤한 국물", "鸡豆凉粉": "계두량분", "小吃": "분식", "小锅饭": "소과반",
    "旗舰店": "플래그십점", "总店": "본점", "首店": "1호점", "藏餐": "티베트 요리", "云南菜": "윈난 요리", "云南特色菜": "윈난 특색 요리",
    "纳西": "나시", "风味": "풍미", "美食街": "먹거리 거리", "牌楼下": " 패루 아래", "白沙村委会": "백사 촌위원회 ", "白沙村": "백사촌 ", "综合广场": "종합광장 ", "个行李箱": "캐리어",  "美食集市": "먹거리 장터", "摊位": "노점", "牌楼": "패루", "诊所": "의원",
    "儿童医院": "어린이병원", "医院": "병원", "派出所": "파출소", "背后": " 뒤", "小巷": " 골목", "隔壁": " 옆집", "之间": " 사이",
    "近": "근처: ", "旁": " 옆", "南行": " 남쪽으로 ", "左转": " 왼쪽으로 돌아", "河边": " 냇가", "就是": "", "导航": "길안내: ",
    "地铁": "지하철 ", "号线": "호선", "大酒店": " 호텔", "洲际酒店": " 인터컨티넨탈 호텔", "英迪格酒店": "인디고 호텔", "福音堂": " 교회",
    "街道": " 가도 ", "丨": " | ", "|": " | ",
    "省": "성 ", "市": "시 ", "区": "구 ", "县": "현 ", "镇": "진 ", "乡": "향 ", "村": "촌 ", "路": "로 ", "街": "가 ", "巷": "골목 ",
    "上段": " 상단", "下段": " 하단", "中段": " 중단",
    "我们住在": "우리 숙소는 ", "大约": "약 ", "到": " 도착 ", "今天": "오늘", "明天": "내일", "上午": "오전 ", "下午": "오후 ", "早上": "아침 ",
    "点半": "시 반", "点": "시", "月": "월 ", "日": "일 ", "个人": "명", "位成人": "명(성인)",
    "包车": "대절차", "面包车": "빵차",
}
KEYS = sorted(WORDS, key=len, reverse=True)

try:  # 한자음 읽기(없으면 규칙 번역만 하고, 남는 한자는 그대로 두지 않고 건너뛴다)
    import hanja  # type: ignore
    import opencc  # type: ignore
    _cc = opencc.OpenCC("s2t")
    def hanja_read(s: str) -> str:
        return hanja.translate(_cc.convert(s), "substitution")
except Exception:  # noqa: BLE001
    hanja_read = None


def rule_translate(zh: str) -> str | None:
    zh = re.sub(r"(\d+)米", r"\1m", zh)
    zh = re.sub(r"(\d+)[楼层]", r"\1층 ", zh)
    zh = re.sub(r"([A-Z])口", r"\1 출구", zh)
    zh = re.sub(r"[Ff](\d)\b", r"\1층", zh)
    out, i = [], 0
    while i < len(zh):
        for k in KEYS:
            if zh.startswith(k, i):
                out.append(WORDS[k]); i += len(k); break
        else:
            ch = zh[i]
            if CJK.match(ch):
                j = i
                while j < len(zh) and CJK.match(zh[j]) and not any(zh.startswith(k, j) for k in KEYS):
                    j += 1
                if hanja_read is None:
                    return None
                out.append(hanja_read(zh[i:j])); i = j
            else:
                out.append({"（": "(", "）": ")", "，": ", ", "、": "·", "：": ": ", "。": ". "}.get(ch, ch)); i += 1
    s = "".join(out)
    s = re.sub(r"([가-힣])(\d)", r"\1 \2", s)
    s = re.sub(r"\s+([)\],.·])", r"\1", s)
    s = re.sub(r"([(\[])\s+", r"\1", s)
    return re.sub(r"\s{2,}", " ", s).strip()


def collect(d: Path) -> list[str]:
    load = lambda n: json.loads((d / n).read_text(encoding="utf-8"))  # noqa: E731
    need: list[str] = []
    def add(s):
        if isinstance(s, str) and CJK.search(s) and not KO.search(s) and s not in need:
            need.append(s)
    for l in load("lodgings.json")["items"]:
        add(l.get("addrZh")); add(l.get("nameZh"))
    for p in load("places.json")["items"]:
        add(p.get("addrZh"))
    for m in load("meals.json")["items"]:
        for o in m["options"]:
            add(o.get("addrZh")); add(o.get("menu"))
    for h in load("emergency.json").get("hospitals", []):
        add(h.get("addrZh"))
    for r in load("restaurants.json")["items"]:
        for g in r.get("ratings", []):
            add(g.get("name")); add(g.get("addr")); add(g.get("label"))
    for r in load("schedule.json")["items"]:
        for k in ("from", "via", "to"):
            add(r.get(k))
    for t in load("template_links.json")["items"]:
        for v in t.get("values", {}).values():
            add(v)
    # 위챗 템플릿 기본값(웹 templates.ts 의 def: '...')
    ts = (REPO / "web" / "src" / "domain" / "templates.ts").read_text(encoding="utf-8")
    for v in re.findall(r"def: '([^']+)'", ts):
        add(v)
    return need


def main(d: Path):
    manual = json.loads((REPO / "data" / "ko_gloss_manual.json").read_text(encoding="utf-8")).get("items", {}) if (REPO / "data" / "ko_gloss_manual.json").exists() else {}
    cache_f = REPO / "data" / "ko_gloss.json"
    cache = json.loads(cache_f.read_text(encoding="utf-8")).get("items", {}) if cache_f.exists() else {}
    need = collect(d)
    out, missing = {}, []
    for zh in need:
        ko = manual.get(zh) or cache.get(zh) or rule_translate(zh)
        if ko:
            out[zh] = ko
        else:
            missing.append(zh)
    body = {"note": "중국어만 있는 화면 글자의 한국어 번역(tools/ko_gloss.py 가 만든다. 고칠 것은 data/ko_gloss_manual.json 에)", "items": out}
    cache_f.write_text(json.dumps(body, ensure_ascii=False, indent=1), encoding="utf-8")
    (d / "ko_gloss.json").write_text(json.dumps(body, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"한국어 번역 사전 {len(out)}건" + (f" · 번역 못 함 {len(missing)}건(hanja 패키지 필요 또는 manual 에 추가): {missing[:5]}" if missing else ""))


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else REPO / "data" / "json")
