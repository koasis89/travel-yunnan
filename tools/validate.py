#!/usr/bin/env python3
"""data/json 참조 무결성 검사 (S1-5). 오류가 있으면 종료 코드 1.

사용법:
    python3 tools/validate.py [data/json]
"""
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
EXPECT_MIN = {
    "days.json": 8, "schedule.json": 60, "lodgings.json": 6, "meals.json": 25, "foods.json": 30,
    "phrases.json": 57, "words.json": 52, "foodNames.json": 39, "packing.json": 40, "notes.json": 15,
    "transport.json": 10, "budget.json": 5, "weather.json": 6, "maps.json": 7, "places.json": 10,
    "place_links.json": 20, "notifications.json": 10, "lodging_todos.json": 10, "map_hotspots.json": 50,
}


def main(d):
    d = Path(d)
    errors, data = [], {}
    for f in sorted(d.glob("*.json")):
        try:
            data[f.name] = json.loads(f.read_text(encoding="utf-8"))
        except Exception as e:  # noqa: BLE001
            errors.append(f"{f.name}: JSON 파싱 실패 {e}")
    if "emergency.json" in data and len(data["emergency.json"].get("contacts", [])) < 5:
        errors.append("emergency.json: 연락처 부족")
    for name, n in EXPECT_MIN.items():
        if name not in data:
            errors.append(f"{name}: 파일 없음"); continue
        cnt = len(data[name].get("items", []))
        if cnt < n:
            errors.append(f"{name}: 건수 {cnt} < 기대 {n}")
    if errors:
        return report(errors)

    items = lambda n: data[n]["items"]  # noqa: E731
    day_ids = {x["id"] for x in items("days.json")}
    lodging_ids = {x["id"] for x in items("lodgings.json")}
    place_ids = {x["id"] for x in items("places.json")}
    phrase_ids = {x["id"] for x in items("phrases.json")}
    map_ids = {x["id"] for x in items("maps.json")}
    cat_ids = {c["id"] for c in data["phrases.json"]["categories"]}
    meal_names = [o["name"] + " " + o.get("nameZh", "") for m in items("meals.json") for o in m["options"]]

    if day_ids != {f"D{i}" for i in range(1, 9)}:
        errors.append(f"days: 일차 집합 이상 {sorted(day_ids)}")
    for x in items("days.json"):
        if not re.match(r"^2026-12-\d\d$", x["date"] or ""):
            errors.append(f"days {x['id']}: 날짜 {x['date']}")
        if x["lodgingId"] and x["lodgingId"] not in lodging_ids:
            errors.append(f"days {x['id']}: lodgingId {x['lodgingId']}")
        for m in x["mapIds"]:
            if m not in map_ids:
                errors.append(f"days {x['id']}: mapId {m}")
    seen = set()
    for x in items("schedule.json"):
        if x["dayId"] not in day_ids:
            errors.append(f"schedule {x['id']}: dayId")
        if x["id"] in seen:
            errors.append(f"schedule {x['id']}: id 중복")
        seen.add(x["id"])
        for k in ("timeStart", "timeEnd"):
            v = x[k]
            if v and not re.match(r"^\d\d:\d\d$", v):
                errors.append(f"schedule {x['id']}: {k}={v}")
        if not x["timeStart"]:
            errors.append(f"schedule {x['id']}: timeStart 없음")
    for x in items("lodgings.json"):
        if x["confirmed"] and not (x["nameZh"] and x["addrZh"] and x["phones"]):
            errors.append(f"lodgings {x['id']}: 확정 숙소에 중국어명·주소·전화 누락")
    for x in items("meals.json"):
        if x["dayId"] not in day_ids:
            errors.append(f"meals {x['id']}: dayId")
        if not x["options"]:
            errors.append(f"meals {x['id']}: 옵션 없음")
    for x in items("foods.json"):
        if x["image"] and not (REPO / "assets" / x["image"]).exists():
            errors.append(f"foods {x['id']}: 이미지 없음 {x['image']}")
    for x in items("phrases.json"):
        if not x["zh"] or not x["ko"]:
            errors.append(f"phrases {x['id']}: zh/ko 누락")
        if not x["copyOnly"] and not x["koRead"]:
            errors.append(f"phrases {x['id']}: koRead 누락")
        for c in x["categories"]:
            if c not in cat_ids:
                errors.append(f"phrases {x['id']}: 범주 {c}")
    for x in items("maps.json"):
        if not (REPO / "assets" / x["file"]).exists():
            errors.append(f"maps {x['id']}: 파일 없음 {x['file']}")
        for dd in x["dayIds"]:
            if dd not in day_ids:
                errors.append(f"maps {x['id']}: dayId {dd}")
    for x in items("places.json"):
        for h in x.get("helperIds", []):
            if h not in phrase_ids:
                errors.append(f"places {x['id']}: helper {h}")
    for x in items("notifications.json"):
        if not re.match(r"^2026-12-\d\dT\d\d:\d\d$", x["at"]) or x["dayId"] not in day_ids:
            errors.append(f"notifications {x['id']}: at/dayId")
    for x in items("lodging_todos.json"):
        if x["lodgingId"] not in lodging_ids or (x.get("phraseId") and x["phraseId"] not in phrase_ids):
            errors.append(f"lodging_todos {x['id']}: 참조")
    from PIL import Image  # noqa: E402
    sizes = {m["id"]: Image.open(REPO / "assets" / m["file"]).size for m in items("maps.json")}
    for x in items("map_hotspots.json"):
        w, h = sizes.get(x["mapId"], (0, 0))
        if not (0 <= x["x"] <= w and 0 <= x["y"] <= h):
            errors.append(f"hotspot {x['id']}: 좌표 범위 밖 ({x['x']},{x['y']}) / {w}x{h}")
        t, k = x["type"], x.get("key")
        ok = (t == "poi") or (t == "lodging" and k in lodging_ids) or (t == "place" and k in place_ids) or \
             (t == "meal" and any(k in n for n in meal_names))
        if not ok:
            errors.append(f"hotspot {x['id']}: 대상 없음 {t}:{k}")
    for x in items("place_links.json"):
        t, k = x["type"], x["key"]
        ok = (t == "lodging" and k in lodging_ids) or (t == "place" and k in place_ids) or \
             (t == "meal" and any(k in n for n in meal_names))
        if not ok:
            errors.append(f"place_links '{x['match']}': 대상 없음 {t}:{k}")
    return report(errors)


def report(errors):
    if errors:
        print(f"오류 {len(errors)}건")
        for e in errors:
            print(" -", e)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else REPO / "data" / "json"))
