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
    "transport.json": 10, "budget.json": 5, "weather.json": 6, "maps.json": 10, "places.json": 10,
    "place_links.json": 20, "notifications.json": 13, "lodging_todos.json": 10, "map_hotspots.json": 50, "sights.json": 20, "restaurants.json": 20,
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
        for o in x["options"]:
            if not re.search(r"[가-힣]", o["name"]) and not o.get("nameKo"):
                errors.append(f"meals {o['id']}: 중국어 이름만 있음(data/meal_names_ko.json 에 한국어 이름 추가)")
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
    sched_keys = {(r["dayId"], r["timeStart"]) for r in items("schedule.json")}
    for x in data.get("template_links.json", {}).get("items", []):
        if (x["dayId"], x["timeStart"]) not in sched_keys:
            errors.append(f"template_links {x['id']}: 일정 행 없음 {x['dayId']} {x['timeStart']}")
        if x["templateId"] not in {"tpl-tiger", "tpl-shangrila", "tpl-delivery", "tpl-pickup", "tpl-guide", "tpl-guide-confirm", "tpl-guide-arrive", "tpl-guide-late", "tpl-guide-dropoff"}:
            errors.append(f"template_links {x['id']}: 템플릿 {x['templateId']} 없음")
    for x in items("place_links.json"):
        t, k = x["type"], x["key"]
        ok = (t == "lodging" and k in lodging_ids) or (t == "place" and k in place_ids) or \
             (t == "meal" and any(k in n for n in meal_names))
        if not ok:
            errors.append(f"place_links '{x['match']}': 대상 없음 {t}:{k}")
    opt_ids = {o["id"] for m in items("meals.json") for o in m["options"]}
    for x in items("restaurants.json"):
        for i in x["optionIds"]:
            if i not in opt_ids:
                errors.append(f"restaurants {x['id']}: optionId {i}")
        for img in [x.get("image")] + [m.get("image") for m in x["menu"]]:
            if img and not (REPO / "assets" / img).exists():
                errors.append(f"restaurants {x['id']}: 이미지 없음 {img}")
        if not x.get("amapUrl"):
            errors.append(f"restaurants {x['id']}: 지도 링크 없음")
    sight_ids = {s["id"] for s in items("sights.json")}
    for b in json.loads((d / "sights.json").read_text(encoding="utf-8")).get("blogs", []):
        if not b.get("url", "").startswith("https://"):
            errors.append(f"blogs {b.get('id')}: 링크 없음")
        for i in b.get("sightIds", []):
            if i not in sight_ids:
                errors.append(f"blogs {b['id']}: sightId {i}")
    tl_ids = {t["id"] for t in items("template_links.json")}
    for x in items("pretrip.json"):
        if x.get("templateLinkId") and x["templateLinkId"] not in tl_ids:
            errors.append(f"pretrip {x['id']}: templateLinkId {x['templateLinkId']}")
        if not re.match(r"^\d{4}-\d{2}-\d{2}$", x.get("date", "")) or x["date"] >= min(d["date"] for d in items("days.json")):
            errors.append(f"pretrip {x['id']}: 날짜는 여행 첫날 전이어야 한다")
    for x in items("sights.json"):
        if x.get("placeId") and x["placeId"] not in place_ids:
            errors.append(f"sights {x['id']}: placeId {x['placeId']}")
        if x.get("image") and not (REPO / "assets" / x["image"]).exists():
            errors.append(f"sights {x['id']}: 이미지 없음 {x['image']}")
        if x.get("image") and not (x.get("credit") or {}).get("license"):
            errors.append(f"sights {x['id']}: 사진 출처 누락")
        for d in x["dayIds"]:
            if d not in day_ids:
                errors.append(f"sights {x['id']}: dayId {d}")
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
