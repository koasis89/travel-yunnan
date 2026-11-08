#!/usr/bin/env python3
"""웹 버전용 데이터 내보내기 (웹 절차서 W1-1).

    python3 tools/export_web.py            # data/web/ 와 web/public/images/foods/ 갱신
    python3 tools/export_web.py --public   # 예약번호·예약자명 제거(공개 사이트용 B안)

data/json 에서 웹에 필요한 파일만 고르고, 회화·지도 관련 데이터는 뺀다.
data/web 은 빌드 때 암호화되어 data.enc 로만 배포된다(web/scripts/encrypt-data.mjs).
평문은 절대 web/ 아래에 두지 않는다(2026-11-05 배포 사고 재발 방지).
"""
import json
import shutil
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "data" / "json"
OUT = REPO / "data" / "web"  # web/ 밖에 둔다: web 폴더가 통째로 배포돼도 평문이 나가지 않게
FOODS_OUT = REPO / "web" / "public" / "images" / "foods"
SIGHTS_OUT = REPO / "web" / "public" / "images" / "sights"
EATS_OUT = REPO / "web" / "public" / "images" / "eats"

INCLUDE = [
    "days.json", "schedule.json", "lodgings.json", "lodging_todos.json", "meals.json", "foods.json",
    "transport.json", "budget.json", "rates.json", "weather.json", "packing.json", "notes.json",
    "places.json", "place_links.json", "template_links.json", "notifications.json", "emergency.json", "sights.json", "restaurants.json", "food_photos.json", "pretrip.json", "milktea.json",
]
# 회화 중 택시 카드·숙소 할 일·일정 장문에 쓰이는 문장만 남긴다
PHRASE_KEEP_PREFIX = ("px-001", "px-002", "px-003", "px-004", "px-005", "px-006", "px-007", "px-008", "px-009", "msg-")


def main(public: bool):
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        old.unlink()
    for name in INCLUDE:
        data = json.loads((SRC / name).read_text(encoding="utf-8"))
        if public and name == "lodgings.json":
            for l in data["items"]:
                l["bookingNo"] = ""
                l["bookedBy"] = ""
        (OUT / name).write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    ph = json.loads((SRC / "phrases.json").read_text(encoding="utf-8"))
    todo_ids = {t["phraseId"] for t in json.loads((SRC / "lodging_todos.json").read_text(encoding="utf-8"))["items"] if t.get("phraseId")}
    keep = [p for p in ph["items"] if p["id"].startswith(PHRASE_KEEP_PREFIX) or p["id"] in todo_ids]
    (OUT / "phrases.json").write_text(json.dumps({"version": ph["version"], "items": keep}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    version = (REPO / "data" / "version.txt").read_text(encoding="utf-8").strip()
    (OUT / "version.json").write_text(json.dumps({"data": version, "public": public}), encoding="utf-8")

    FOODS_OUT.mkdir(parents=True, exist_ok=True)
    for old in FOODS_OUT.glob("*"):
        old.unlink()
    for f in (REPO / "assets" / "foods").glob("food-*"):
        shutil.copy(f, FOODS_OUT / f.name)

    SIGHTS_OUT.mkdir(parents=True, exist_ok=True)
    for old in SIGHTS_OUT.glob("*"):
        old.unlink()
    for f in (REPO / "assets" / "sights").glob("sight-*"):
        shutil.copy(f, SIGHTS_OUT / f.name)

    EATS_OUT.mkdir(parents=True, exist_ok=True)
    for old in EATS_OUT.glob("*"):
        old.unlink()
    for f in (REPO / "assets" / "eats").glob("eat-*"):
        shutil.copy(f, EATS_OUT / f.name)

    files = sorted(OUT.glob("*.json"))
    size = sum(f.stat().st_size for f in files)
    print(f"웹 데이터 {len(files)}개 {size / 1024:.0f}KB · 회화 {len(keep)}문장 · 음식 사진 {len(list(FOODS_OUT.glob('*')))}장 · 데이터 버전 {version}" + (" · 공개용" if public else ""))


if __name__ == "__main__":
    main("--public" in sys.argv)
