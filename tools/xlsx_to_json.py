#!/usr/bin/env python3
"""엑셀 일정표(12개 시트) → 앱용 JSON 변환 스크립트 (S1-1).

사용법:
    python3 tools/xlsx_to_json.py data/source/yunnan_7n8d_itinerary.xlsx data/json

엑셀의 열 머리글 행을 찾아 열 위치를 계산하므로 열 순서가 바뀌어도 동작한다.
출력 JSON은 UTF-8, ensure_ascii=False, 들여쓰기 2칸이다.
"""
import json
import re
import sys
from pathlib import Path

import openpyxl

TRIP_YEAR = 2026
DATA_VERSION = "2026-11-04"

# 일차 → 숙소 id (숙소정보 시트 순서와 일치)
DAY_LODGING = {
    "D1": "kunming1", "D2": "mulanyu", "D3": "mulanyu", "D4": "rishine",
    "D5": "comeinn", "D6": "oura", "D7": "kunming2", "D8": None,
}
LODGING_IDS = ["kunming1", "mulanyu", "rishine", "comeinn", "oura", "kunming2"]
# 일차 → 준비물 시트 기온표의 지역명
DAY_WEATHER = {
    "D1": "쿤밍", "D2": "리장", "D3": "옥룡설산", "D4": "리장",
    "D5": "호도협", "D6": "샹그릴라", "D7": "쿤밍", "D8": "쿤밍",
}
# 일차 → 지도 id (data/maps.json 과 일치)
DAY_MAPS = {
    "D1": ["map04"], "D2": ["map02", "map06"], "D3": ["map02", "map06", "ref_cablecar"],
    "D4": ["map02", "map06"], "D5": ["map05"], "D6": ["map05", "map03"],
    "D7": ["map03", "map04"], "D8": ["map04"],
}

CJK = r"一-鿿"
AMAP_RE = re.compile(r"https?://surl\.amap\.com/[A-Za-z0-9]+")
DATE_RE = re.compile(r"(\d{1,2})/(\d{1,2})")


def s(v):
    """셀 값을 문자열로. None → ''."""
    if v is None:
        return ""
    if isinstance(v, float) and v.is_integer():
        v = int(v)
    return str(v).strip()


def num(v):
    if v is None or v == "":
        return None
    try:
        f = float(v)
        return int(f) if f.is_integer() else f
    except (TypeError, ValueError):
        return None


def rows(ws):
    return [[c.value for c in row] for row in ws.iter_rows()]


def find_header(table, first_cell):
    for i, row in enumerate(table):
        if s(row[0]) == first_cell:
            return i
    raise ValueError(f"머리글 '{first_cell}' 행을 찾지 못함")


def col_index(header_row, name, default=None):
    for i, v in enumerate(header_row):
        if s(v) == name:
            return i
    if default is not None:
        return default
    raise ValueError(f"열 '{name}' 없음: {header_row}")


def to_date(md):
    m = DATE_RE.search(md or "")
    if not m:
        return None
    return f"{TRIP_YEAR}-{int(m.group(1)):02d}-{int(m.group(2)):02d}"


def split_time(t):
    t = s(t).replace("~", "~")
    if "~" not in t:
        return None, None
    a, b = t.split("~", 1)
    return norm_time(a), norm_time(b)


def norm_time(t):
    t = t.strip()
    m = re.match(r"^(\d{1,2}):(\d{2})$", t)
    return f"{int(m.group(1)):02d}:{m.group(2)}" if m else (t or None)


def extract_zh(text):
    """문장 안의 중국어 구간(구두점 포함)을 추출한다."""
    out = []
    for m in re.finditer(rf"[{CJK}][{CJK}0-9A-Za-z，,。、：:；;？?！!… ]*", text or ""):
        frag = m.group(0).strip(" ,，")
        # 문장 부호가 있거나 8자 이상인 중국어 구간만 문장으로 본다
        if len(re.findall(rf"[{CJK}]", frag)) >= 8 or re.search(r"[。？?！!]", frag):
            out.append(frag)
    return out


# ---------------------------------------------------------------- 시트별 변환

def conv_days(wb, weather, lodgings):
    t = rows(wb["일정요약"])
    h = find_header(t, "일차")
    hdr = t[h]
    ci = {k: col_index(hdr, k) for k in ["일차", "일자", "경로", "주요 내용", "숙박"]}
    wmap = {w["region"]: w for w in weather}
    out = []
    for row in t[h + 1:]:
        did = s(row[ci["일차"]])
        if not re.match(r"^D\d$", did):
            continue
        w = wmap.get(DAY_WEATHER.get(did, ""), {})
        out.append({
            "id": did,
            "date": to_date(s(row[ci["일자"]])),
            "dateLabel": s(row[ci["일자"]]),
            "route": s(row[ci["경로"]]),
            "summary": s(row[ci["주요 내용"]]),
            "lodgingLabel": s(row[ci["숙박"]]),
            "lodgingId": DAY_LODGING.get(did),
            "region": DAY_WEATHER.get(did),
            "altitude": w.get("altitude"),
            "tempDay": w.get("tempDay"),
            "tempNight": w.get("tempNight"),
            "outfit": w.get("outfit"),
            "mapIds": DAY_MAPS.get(did, []),
        })
    return out


def conv_schedule(wb):
    t = rows(wb["상세일정"])
    h = find_header(t, "일차")
    hdr = t[h]
    ci = {k: col_index(hdr, k) for k in ["일차", "시간", "출발지", "경유지", "도착지", "이동수단", "소요시간", "내용·팁"]}
    tip_col = ci["내용·팁"]
    out, day, seq = [], None, 0
    for row in t[h + 1:]:
        if all(v is None for v in row):
            continue
        d = s(row[ci["일차"]])
        m = re.match(r"^(D\d)", d)
        if m:
            day, seq = m.group(1), 0
        time = s(row[ci["시간"]])
        if not day or not time:
            continue
        seq += 1
        start, end = split_time(time)
        tip = s(row[tip_col])
        extra = [s(v) for v in row[tip_col + 1:] if s(v)]
        message = "\n".join(extra) if extra else None
        urls = AMAP_RE.findall(tip + " " + (message or ""))
        out.append({
            "id": f"{day}-{seq:02d}",
            "dayId": day,
            "seq": seq,
            "timeLabel": time,
            "timeStart": start,
            "timeEnd": end,
            "from": s(row[ci["출발지"]]),
            "via": s(row[ci["경유지"]]),
            "to": s(row[ci["도착지"]]),
            "transport": s(row[ci["이동수단"]]),
            "duration": s(row[ci["소요시간"]]),
            "tip": tip,
            "amapUrl": urls[0] if urls else None,
            "message": message,
        })
    return out


def conv_lodgings(wb):
    t = rows(wb["숙소정보"])
    h = find_header(t, "일자")
    hdr = t[h]
    names = ["일자", "박수", "지역", "숙소명(한글·영문)", "숙소명(중국어)", "중국어 주소(지도앱 입력용)", "영문 주소",
             "전화", "객실", "예약번호", "예약자", "금액(원)", "상태", "비고"]
    ci = {k: col_index(hdr, k) for k in names}
    out, notes = [], []
    for row in t[h + 1:]:
        first = s(row[0])
        if first.startswith("합계"):
            continue
        if first.startswith("※"):
            notes.append(first.lstrip("※ ").strip())
            continue
        if not first or len(out) >= len(LODGING_IDS):
            continue
        dates = [to_date(p) for p in first.split("~")]
        if len(dates) == 2 and dates[0] and dates[1]:
            # 날짜 범위 전개
            from datetime import date, timedelta
            a = date.fromisoformat(dates[0]); b = date.fromisoformat(dates[1])
            dates = [(a + timedelta(days=i)).isoformat() for i in range((b - a).days + 1)]
        phones = [p.strip() for p in s(row[ci["전화"]]).split("/") if p.strip() and p.strip() != "-"]
        zh = s(row[ci["숙소명(중국어)"]])
        out.append({
            "id": LODGING_IDS[len(out)],
            "dates": [d for d in dates if d],
            "nights": num(row[ci["박수"]]),
            "region": s(row[ci["지역"]]),
            "nameKo": s(row[ci["숙소명(한글·영문)"]]),
            "nameZh": zh if zh != "-" else "",
            "addrZh": s(row[ci["중국어 주소(지도앱 입력용)"]]),
            "addrEn": s(row[ci["영문 주소"]]) if s(row[ci["영문 주소"]]) != "-" else "",
            "phones": phones,
            "room": s(row[ci["객실"]]),
            "bookingNo": s(row[ci["예약번호"]]) if s(row[ci["예약번호"]]) != "-" else "",
            "bookedBy": s(row[ci["예약자"]]) if s(row[ci["예약자"]]) != "-" else "",
            "priceKrw": num(row[ci["금액(원)"]]),
            "status": s(row[ci["상태"]]),
            "note": s(row[ci["비고"]]),
            "confirmed": s(row[ci["상태"]]) != "미정",
        })
    return out, notes


def conv_meals(wb):
    t = rows(wb["맛집·식사"])
    h = find_header(t, "일자")
    hdr = t[h]
    names = ["일자", "지역", "구분", "선택①", "선택②", "선택③", "중국어 주소·검색어(고덕지도)", "추천 메뉴", "예상비용(3인, ¥)", "링크·비고"]
    ci = {k: col_index(hdr, k) for k in names}
    out, day, seq, notes = [], None, 0, []
    for row in t[h + 1:]:
        first = s(row[0])
        if first.startswith("식비 합계") or first.startswith("※"):
            if first.startswith("※"):
                notes.append(first.lstrip("※ ").strip())
            continue
        m = re.match(r"^(D\d)", first)
        if m:
            day, seq = m.group(1), 0
        slot = s(row[ci["구분"]])
        if not day or not slot:
            continue
        seq += 1
        opts_raw = [s(row[ci[k]]) for k in ["선택①", "선택②", "선택③"]]
        addrs = [a.strip() for a in s(row[ci["중국어 주소·검색어(고덕지도)"]]).split(" / ")]
        menus = [m_.strip() for m_ in s(row[ci["추천 메뉴"]]).split(" / ")]
        options = []
        for i, name in enumerate(opts_raw):
            if not name or name == "-":
                continue
            addr = addrs[i] if i < len(addrs) else (addrs[0] if len(addrs) == 1 else "")
            urls = AMAP_RE.findall(addr)
            zh_in_name = re.search(rf"[{CJK}][^()（）]*", name)
            options.append({
                "id": f"{day}-{seq:02d}-{i + 1}",
                "name": name,
                "nameZh": zh_in_name.group(0).strip() if zh_in_name else "",
                "addrZh": AMAP_RE.sub("", addr).strip(" /") if addr else "",
                "amapUrl": urls[0] if urls else None,
                "menu": menus[i] if i < len(menus) else (menus[0] if menus else ""),
            })
        extra = [s(v) for v in row[ci["링크·비고"] + 1:] if s(v)]
        out.append({
            "id": f"{day}-{seq:02d}",
            "dayId": day,
            "seq": seq,
            "area": s(row[ci["지역"]]),
            "slot": slot,
            "options": options,
            "budgetCny": num(row[ci["예상비용(3인, ¥)"]]),
            "note": s(row[ci["링크·비고"]]),
            "extra": "\n".join(extra) if extra else None,
        })
    return out, notes


def conv_foods(wb, foods_dir):
    t = rows(wb["음식설명"])
    h = find_header(t, "지역")
    hdr = t[h]
    names = ["지역", "번호", "중국어", "한국어", "음식 설명", "이미지 구분", "출처(Wikimedia Commons)"]
    ci = {k: col_index(hdr, k) for k in names}
    existing = {p.name: p for p in Path(foods_dir).glob("*")} if foods_dir else {}
    out = []
    for r, row in enumerate(t[h + 1:], start=h + 2):
        zh = s(row[ci["중국어"]])
        if not zh:
            continue
        ko = s(row[ci["한국어"]])
        # 이미지 파일명은 엑셀 행 번호 기준 ASCII 이름(food-05.jpeg). 빌드 도구의 경로 인코딩 문제를 피한다.
        img = next((n for n in existing if n.startswith(f"food-{r:02d}.")), None)
        out.append({
            "id": f"food-{r:02d}",
            "region": s(row[ci["지역"]]),
            "no": num(row[ci["번호"]]),
            "nameZh": zh,
            "nameKo": ko,
            "description": s(row[ci["음식 설명"]]),
            "imageKind": s(row[ci["이미지 구분"]]),
            "image": f"foods/{img}" if img else None,
            "source": s(row[ci["출처(Wikimedia Commons)"]]),
        })
    return out


def conv_transport(wb):
    t = rows(wb["교통·투어비"])
    rates = {}
    for row in t[:5]:
        vals = [s(v) for v in row]
        for i in range(0, len(vals) - 1, 2):
            if vals[i].endswith("환율(원)"):
                rates[vals[i][:3]] = num(row[i + 1])
            if vals[i] == "인원(명)":
                rates["persons"] = num(row[i + 1])
    h = find_header(t, "구분")
    hdr = t[h]
    names = ["구분", "항목", "단위", "단가", "통화", "수량", "비고", "총액(원)"]
    ci = {k: col_index(hdr, k) for k in names}
    out, notes = [], []
    for i, row in enumerate(t[h + 1:], start=1):
        first = s(row[0])
        if first.startswith("※"):
            notes.append(first.lstrip("※ ").strip()); continue
        if first.startswith("합계") or not first:
            continue
        out.append({
            "id": f"tr-{i:02d}",
            "group": first,
            "title": s(row[ci["항목"]]),
            "unit": s(row[ci["단위"]]),
            "unitPrice": num(row[ci["단가"]]),
            "currency": s(row[ci["통화"]]),
            "qty": num(row[ci["수량"]]) or 0,
            "note": s(row[ci["비고"]]),
            "totalKrw": num(row[ci["총액(원)"]]) or 0,
        })
    return out, rates, notes


def conv_budget(wb):
    t = rows(wb["총비용"])
    h = find_header(t, "구분")
    out = []
    for row in t[h + 1:]:
        if s(row[0]):
            out.append({"category": s(row[0]), "amountKrw": num(row[1]) or 0, "isTotal": s(row[0]).startswith("합계")})
    return out


def conv_packing(wb):
    t = rows(wb["준비물"])
    h = find_header(t, "지역")
    weather = []
    for row in t[h + 1:]:
        if not s(row[0]) or s(row[0]).startswith("준비물"):
            if s(row[0]).startswith("준비물"):
                break
            continue
        weather.append({
            "region": s(row[0]), "dates": s(row[1]), "altitude": s(row[2]),
            "tempDay": s(row[3]), "tempNight": s(row[4]), "feature": s(row[5]), "outfit": s(row[6]),
        })
    h2 = find_header(t, "분류")
    items, group = [], None
    for i, row in enumerate(t[h2 + 1:], start=1):
        if s(row[0]):
            group = s(row[0])
        name = s(row[1])
        if not name:
            continue
        items.append({"id": f"pk-{i:02d}", "group": group, "name": name, "qty": s(row[2]), "note": s(row[3])})
    return weather, items


def conv_notes(wb):
    t = rows(wb["유의사항"])
    h = find_header(t, "구분")
    out = []
    for i, row in enumerate(t[h + 1:], start=1):
        if not s(row[0]):
            continue
        body = s(row[1])
        days = sorted(set(re.findall(r"\bD\d\b", body)))
        out.append({"id": f"note-{i:02d}", "title": s(row[0]), "body": body, "dayIds": days, "phrasesInline": extract_zh(body)})
    return out


# ---------------------------------------------------------------- main

def main(xlsx, outdir):
    wb = openpyxl.load_workbook(xlsx, data_only=True)
    outdir = Path(outdir); outdir.mkdir(parents=True, exist_ok=True)
    repo = Path(__file__).resolve().parent.parent
    foods_dir = repo / "assets" / "foods"

    weather, packing = conv_packing(wb)
    lodgings, lodging_notes = conv_lodgings(wb)
    days = conv_days(wb, weather, lodgings)
    schedule = conv_schedule(wb)
    meals, meal_notes = conv_meals(wb)
    foods = conv_foods(wb, foods_dir if foods_dir.exists() else None)
    transport, rates, transport_notes = conv_transport(wb)
    budget = conv_budget(wb)
    notes = conv_notes(wb)

    files = {
        "days.json": {"version": DATA_VERSION, "items": days},
        "schedule.json": {"version": DATA_VERSION, "items": schedule},
        "lodgings.json": {"version": DATA_VERSION, "items": lodgings, "notes": lodging_notes},
        "meals.json": {"version": DATA_VERSION, "items": meals, "notes": meal_notes},
        "foods.json": {"version": DATA_VERSION, "items": foods},
        "transport.json": {"version": DATA_VERSION, "items": transport, "notes": transport_notes},
        "rates.json": {"version": DATA_VERSION, **rates},
        "budget.json": {"version": DATA_VERSION, "items": budget},
        "weather.json": {"version": DATA_VERSION, "items": weather},
        "packing.json": {"version": DATA_VERSION, "items": packing},
        "notes.json": {"version": DATA_VERSION, "items": notes},
    }
    for name, data in files.items():
        (outdir / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        n = len(data["items"]) if "items" in data else len(data)
        print(f"{name:16s} {n:4d}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__); sys.exit(2)
    main(sys.argv[1], sys.argv[2])
