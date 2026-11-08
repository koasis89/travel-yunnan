#!/usr/bin/env python3
"""데이터 전체 재생성 진입점. 엑셀이나 수동 데이터가 바뀌면 이것만 실행한다.

    python3 tools/build_data.py

1) 엑셀 → JSON  2) 회화 텍스트 → JSON  3) 수동 데이터 복사  4) 고덕지도 링크  5) 검증
"""
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "data" / "json"
MANUAL = ["maps.json", "places.json", "place_links.json", "notifications.json", "lodging_todos.json", "emergency.json", "map_hotspots.json", "template_links.json", "sights.json", "restaurants.json", "food_photos.json", "pretrip.json", "milktea.json"]


def run(*args):
    print("$", " ".join(str(a) for a in args))
    subprocess.run([sys.executable, *map(str, args)], check=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    run(REPO / "tools" / "xlsx_to_json.py", REPO / "data" / "source" / "yunnan_7n8d_itinerary.xlsx", OUT)
    run(REPO / "tools" / "tts_to_phrases.py", REPO / "data" / "source" / "yunnan_chinese_pinyin_tts.txt", OUT)
    for name in MANUAL:
        shutil.copy(REPO / "data" / name, OUT / name)
        print(f"copy {name}")
    run(REPO / "tools" / "amap_links.py", OUT)
    run(REPO / "tools" / "ko_gloss.py", OUT)
    run(REPO / "tools" / "validate.py", OUT)


if __name__ == "__main__":
    main()
