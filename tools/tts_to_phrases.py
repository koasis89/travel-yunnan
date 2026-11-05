#!/usr/bin/env python3
"""회화 텍스트(중국어·병음 2줄 쌍) → phrases.json / words.json / foodNames.json (S1-2).

사용법:
    python3 tools/tts_to_phrases.py data/source/yunnan_chinese_pinyin_tts.txt data/json

입력 파일 구조:
  - 빈 줄로 블록이 나뉜다. 블록은 "중국어 줄 + 병음 줄" 쌍의 연속이다.
  - 같은 쌍이 반복되는 블록(3회 반복)은 문장 1개로 본다.
  - 첫 번째 목록 블록은 핵심 단어장, 그 뒤 목록 블록은 음식명·식당명이다.

보강 데이터:
  - data/phrases_override.csv : zh,ko,koRead  (한국어 뜻·한글 발음)
  - data/phrases_extra.json   : 엑셀 유의사항·숙소·상세일정에 있던 문장과 위챗 장문
"""
import csv
import json
import re
import sys
from pathlib import Path

def _version():
    f = Path(__file__).resolve().parent.parent / "data" / "version.txt"
    return f.read_text(encoding="utf-8").strip() if f.exists() else "dev"


DATA_VERSION = _version()

# 범주: 해당 범주가 시작되는 첫 문장(앵커). 파일 순서대로 다음 앵커 전까지 같은 범주다.
CATEGORY_ANCHORS = [
    ("car", "你好 我要预订包车"),
    ("incar", "师傅 请开空调"),
    ("hotel", "你好 我要入住"),
    ("restaurant", "服务员 点单"),
    ("ticket", "你好 我要两张成人票"),
    ("market", "这个一斤多少钱"),
    ("station", "你好 我要核对车票"),
    ("order", "我要一份过桥米线"),
]
CATEGORIES = [
    {"id": "basic", "title": "기본 회화", "icon": "basic"},
    {"id": "car", "title": "차량 예약·빵차", "icon": "car"},
    {"id": "incar", "title": "차 안에서", "icon": "seat"},
    {"id": "hotel", "title": "숙소", "icon": "hotel"},
    {"id": "restaurant", "title": "식당", "icon": "restaurant"},
    {"id": "ticket", "title": "매표·관광지", "icon": "ticket"},
    {"id": "market", "title": "시장·쇼핑", "icon": "shop"},
    {"id": "station", "title": "역·보안검색", "icon": "train"},
    {"id": "order", "title": "음식 주문", "icon": "food"},
    {"id": "delivery", "title": "배달·요청", "icon": "delivery"},
    {"id": "message", "title": "위챗 장문", "icon": "message"},
    {"id": "emergency", "title": "긴급·병원", "icon": "emergency"},
]
# 음식명 목록 중 식당 이름 블록(마지막 목록 블록)은 kind=restaurant
RESTAURANT_BLOCK_MARK = "田村"


def norm_space(t):
    return re.sub(r"\s+", " ", t.strip())


def read_blocks(path):
    blocks, cur = [], []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line.strip():
            cur.append(line.rstrip("\n"))
        elif cur:
            blocks.append(cur); cur = []
    if cur:
        blocks.append(cur)
    out = []
    for b in blocks:
        if len(b) % 2:
            raise ValueError(f"홀수 줄 블록: {b[:2]}")
        out.append([(norm_space(b[i]), norm_space(b[i + 1])) for i in range(0, len(b), 2)])
    return out


def load_override(path):
    m = {}
    if Path(path).exists():
        with open(path, encoding="utf-8") as f:
            for r in csv.DictReader(f):
                m[norm_space(r["zh"])] = {"ko": r.get("ko", "").strip(), "koRead": r.get("koRead", "").strip()}
    return m


def main(src, outdir):
    repo = Path(__file__).resolve().parent.parent
    override = load_override(repo / "data" / "phrases_override.csv")
    extra_path = repo / "data" / "phrases_extra.json"
    extra = json.loads(extra_path.read_text(encoding="utf-8")) if extra_path.exists() else {"items": []}
    outdir = Path(outdir); outdir.mkdir(parents=True, exist_ok=True)

    words, food_names, phrases = [], [], []
    anchors = {zh: cat for cat, zh in CATEGORY_ANCHORS}
    cat = None
    by_zh = {}
    list_blocks = 0
    for block in read_blocks(src):
        if len(set(block)) == 1:  # 반복 블록 = 문장
            zh, py = block[0]
            cat = anchors.get(zh, cat)
            if cat is None:
                raise ValueError(f"범주 앵커 전 문장: {zh}")
            if zh in by_zh:  # 같은 문장이 다른 범주에 또 나오면 범주만 추가
                p = by_zh[zh]
                if cat not in p["categories"]:
                    p["categories"].append(cat)
                continue
            p = {"id": f"ph-{len(phrases) + 1:03d}", "zh": zh, "pinyin": py, "categories": [cat], "repeat": len(block)}
            phrases.append(p); by_zh[zh] = p
        else:  # 목록 블록
            list_blocks += 1
            if list_blocks == 1:
                for zh, py in block:
                    words.append({"id": f"w-{len(words) + 1:03d}", "zh": zh, "pinyin": py})
            else:
                kind = "restaurant" if block[0][0] == RESTAURANT_BLOCK_MARK else "food"
                for zh, py in block:
                    if any(f["zh"] == zh for f in food_names):
                        continue
                    food_names.append({"id": f"fn-{len(food_names) + 1:03d}", "zh": zh, "pinyin": py, "kind": kind})

    # 보강 문장(엑셀 유래) 추가
    for e in extra.get("items", []):
        zh = norm_space(e["zh"]) if "\n" not in e["zh"] else e["zh"].strip()
        if zh in by_zh:
            for c in e.get("categories", []):
                if c not in by_zh[zh]["categories"]:
                    by_zh[zh]["categories"].append(c)
            continue
        p = {"id": e["id"], "zh": zh, "pinyin": e.get("pinyin", ""), "categories": e.get("categories", []),
             "repeat": 1, "ko": e.get("ko", ""), "koRead": e.get("koRead", ""),
             "copyOnly": bool(e.get("copyOnly", False)), "source": e.get("source", "")}
        # "after": 특정 문장 바로 뒤에 배치(화면 순서). 없으면 맨 뒤
        after = e.get("after")
        idx = next((i for i, q in enumerate(phrases) if q["id"] == after), None) if after else None
        if idx is None:
            phrases.append(p)
        else:
            phrases.insert(idx + 1, p)
        by_zh[zh] = p

    # 한국어 뜻·한글 발음 병합
    missing = []
    for coll, name in [(phrases, "phrases"), (words, "words"), (food_names, "foodNames")]:
        for it in coll:
            o = override.get(norm_space(it["zh"]))
            if o:
                it["ko"] = o["ko"] or it.get("ko", "")
                it["koRead"] = o["koRead"] or it.get("koRead", "")
            it.setdefault("ko", ""); it.setdefault("koRead", ""); it.setdefault("copyOnly", False)
            if not it["ko"] or (not it["koRead"] and not it["copyOnly"]):
                missing.append(f"{name}:{it['zh'][:20]}")

    files = {
        "phrases.json": {"version": DATA_VERSION, "categories": CATEGORIES, "items": phrases},
        "words.json": {"version": DATA_VERSION, "items": words},
        "foodNames.json": {"version": DATA_VERSION, "items": food_names},
    }
    for name, data in files.items():
        (outdir / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{name:16s} {len(data['items']):4d}")
    if missing:
        print(f"[경고] 뜻/발음 누락 {len(missing)}건: {missing[:10]}")
    else:
        print("뜻/발음 누락 0건")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__); sys.exit(2)
    main(sys.argv[1], sys.argv[2])
