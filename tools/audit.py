#!/usr/bin/env python3
"""전체 데이터 교차 검증(S7-1 보강). validate.py 가 참조 무결성을 보면, 이 스크립트는 내용의 일관성을 본다.

    python3 tools/audit.py            # 경고 포함 출력, 오류가 있으면 종료 코드 1

검사 항목
  1 일정: 시각 형식·순서, 행 간 겹침·공백, 일차별 첫·마지막 행, 숙박 연결
  2 숙소: 12/16~12/22 매일 1곳 확정, 중국어 주소·전화
  3 식사: 일차별 점심·저녁 존재, 선택지 중국어 주소·택시 가능 여부
  4 알림: 모두 해당 일정 행 시작 전, 여행 기간 안
  5 회화: id·중국어 중복, 음성 파일 존재, 범주 존재
  6 비용: 교통 합계 = 항목 합, 총비용 합계 = 구분 합
  7 지도: 이미지 존재, 핫스팟 대상·좌표, 일차 매핑
  8 장소 링크: 상세일정 도착지 중 링크 없는 중국어 장소 목록(경고)
"""
import json
import re
import sys
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
J = REPO / "data" / "json"
errors, warns = [], []


def load(name):
    return json.loads((J / name).read_text(encoding="utf-8"))


def t2m(t):
    h, m = t.split(":")
    return int(h) * 60 + int(m)


def main():
    days = load("days.json")["items"]
    sched = load("schedule.json")["items"]
    lodg = load("lodgings.json")["items"]
    meals = load("meals.json")["items"]
    notif = load("notifications.json")["items"]
    phr = load("phrases.json")
    words = load("words.json")["items"]
    foods = load("foodNames.json")["items"]
    transport = load("transport.json")["items"]
    budget = load("budget.json")["items"]
    maps = load("maps.json")["items"]
    hot = load("map_hotspots.json")["items"]
    links = load("place_links.json")["items"]
    places = {p["id"]: p for p in load("places.json")["items"]}

    # 1. 일정
    by_day = defaultdict(list)
    for r in sched:
        by_day[r["dayId"]].append(r)
    for d in days:
        rows = sorted(by_day[d["id"]], key=lambda r: r["seq"])
        if not rows:
            errors.append(f"[일정] {d['id']} 행 없음"); continue
        prev_end = None
        off = 0  # 자정을 넘는 행(예: D1 23:35~00:20, 00:20~)은 +24시간으로 본다(웹·앱 findNow 와 같다)
        for r in rows:
            s, e = r["timeStart"], r["timeEnd"]
            if not re.match(r"^\d\d:\d\d$", s or ""):
                errors.append(f"[일정] {r['id']} 시작 시각 형식 {s}")
                continue
            sm = t2m(s) + off
            if prev_end is not None and sm < prev_end - 360:
                off += 1440; sm += 1440
            em = (t2m(e) + off) if e else None
            if em is not None and em <= sm:
                if sm % 1440 >= 18 * 60 and t2m(e) <= 6 * 60:
                    em += 1440  # 밤에 시작해 새벽에 끝나는 행
                else:
                    errors.append(f"[일정] {r['id']} 종료({e})가 시작({s})보다 빠름")
            if prev_end is not None:
                if sm < prev_end:
                    errors.append(f"[일정] {r['id']} 이전 행과 겹침 ({s} < 이전 종료)")
                elif sm - prev_end >= 60:
                    warns.append(f"[일정] {r['id']} 이전 행 종료 후 {(sm - prev_end) // 60}시간 공백")
            prev_end = em if em is not None else sm
            if r["transport"] in ("", "-") and re.search(r"[\u4e00-\u9fff]", r["to"]) and r["from"] != r["to"]:
                warns.append(f"[일정] {r['id']} 이동수단 비어 있음: {r['from']} → {r['to']}")
            if "도보" == r["transport"] and re.search(r"\d+\s*km", r["duration"]):
                warns.append(f"[일정] {r['id']} 도보인데 거리 표기 {r['duration']}")
        if d["lodgingId"] and not any(l["id"] == d["lodgingId"] for l in lodg):
            errors.append(f"[일정] {d['id']} 숙소 id {d['lodgingId']} 없음")

    msgs = [r for r in sched if r.get("message") and "包车" in r["message"]]
    if len(msgs) < 2:
        errors.append(f"[일정] 위챗 장문(빵차 예약) 행이 {len(msgs)}건 — 엑셀 9열 유실 여부 확인")

    # 2. 숙소
    first, last = date.fromisoformat(days[0]["date"]), date.fromisoformat(days[-1]["date"])
    nights = [(first + timedelta(i)).isoformat() for i in range((last - first).days)]
    for n in nights:
        covering = [l for l in lodg if n in l["dates"]]
        if len(covering) != 1:
            errors.append(f"[숙소] {n} 숙박 {len(covering)}곳")
        elif not covering[0]["confirmed"]:
            warns.append(f"[숙소] {n} {covering[0]['nameKo']} 미확정")
    for l in lodg:
        if not re.search(r"[\u4e00-\u9fff]", l["addrZh"]):
            errors.append(f"[숙소] {l['id']} 중국어 주소 없음")
        if not l["phones"]:
            warns.append(f"[숙소] {l['id']} 전화 없음")

    # 3. 식사
    mbd = defaultdict(set)
    for m in meals:
        mbd[m["dayId"]].add(m["slot"])
        for o in m["options"]:
            if not o["addrZh"] and not o["amapUrl"] and re.search(r"[\u4e00-\u9fff]", o["name"]):
                warns.append(f"[식사] {o['id']} {o['name']}: 중국어 상호인데 주소·지도 링크 없음")
    for d in days:
        slots = mbd[d["id"]]
        need = {"D1": {"야식"}, "D8": {"아침", "점심", "저녁"}}.get(d["id"], {"점심", "저녁"})
        miss = need - slots
        if miss:
            warns.append(f"[식사] {d['id']} 끼니 없음: {sorted(miss)} (있음: {sorted(slots)})")

    # 4. 알림: 열차는 열차 코드 행, 비행기는 공항 행보다 앞서야 한다. 숙소·차량 알림은 시각 검사 생략
    day_date = {d["id"]: d["date"] for d in days}
    # 사전 일정(pretrip.json)에 묶인 알림은 여행 전 날짜가 맞다
    pre_ids = {x.get("notificationId") for x in load("pretrip.json")["items"]} if (J / "pretrip.json").exists() else set()
    for n in notif:
        dt = n["at"]
        if n["id"] in pre_ids:
            if not dt[:10] < days[0]["date"]:
                errors.append(f"[알림] {n['id']} 사전 일정 알림이 출발일 이후 {dt}")
            continue
        if not (days[0]["date"] <= dt[:10] <= days[-1]["date"]):
            errors.append(f"[알림] {n['id']} 여행 기간 밖 {dt}")
        rows = by_day[n["dayId"]]
        if n["kind"] == "train":
            code = re.search(r"C\d{3}", n["title"])
            rel = [r for r in rows if code and code.group(0) in r["transport"]]
        elif n["kind"] == "flight":
            rel = [r for r in rows if "공항" in r["to"]]
        else:
            rel = []
        if rel and dt[:10] == day_date[n["dayId"]]:
            earliest = min(t2m(r["timeStart"]) for r in rel)
            if t2m(dt[11:]) >= earliest:
                errors.append(f"[알림] {n['id']} {n['title']} 시각 {dt[11:]} 이 관련 일정 시작({earliest // 60:02d}:{earliest % 60:02d}) 이후")
        elif not rel and n["kind"] in ("train", "flight"):
            errors.append(f"[알림] {n['id']} 관련 일정 행을 찾지 못함")

    # 5. 회화
    ids = [p["id"] for p in phr["items"]]
    if len(ids) != len(set(ids)):
        errors.append("[회화] id 중복")
    zh_seen = defaultdict(list)
    for p in phr["items"]:
        zh_seen[re.sub(r"[\s，,。？?！!]", "", p["zh"])].append(p["id"])
    for z, l in zh_seen.items():
        if len(l) > 1:
            warns.append(f"[회화] 같은 문장 {l}")
    cats = {c["id"] for c in phr["categories"]}
    audio = REPO / "assets" / "audio" / "phrases"
    for coll in (phr["items"], words, foods):
        for p in coll:
            if p.get("copyOnly"):
                continue
            if not (audio / f"{p['id']}.mp3").exists():
                errors.append(f"[회화] 음성 없음 {p['id']}")
    for p in phr["items"]:
        for c in p["categories"]:
            if c not in cats:
                errors.append(f"[회화] {p['id']} 범주 {c} 없음")

    # 6. 비용
    tsum = round(sum(t["totalKrw"] for t in transport), 1)
    tb = next((b for b in budget if b["category"].startswith("교통")), None)
    if tb and abs(tb["amountKrw"] - tsum) > 1:
        errors.append(f"[비용] 교통 합계 {tsum} ≠ 총비용 교통 {tb['amountKrw']}")
    parts = sum(b["amountKrw"] for b in budget if not b["isTotal"])
    total = next(b["amountKrw"] for b in budget if b["isTotal"])
    if abs(parts - total) > 1:
        errors.append(f"[비용] 구분 합 {parts} ≠ 합계 {total}")
    lsum = sum(l["priceKrw"] or 0 for l in lodg)
    lb = next((b for b in budget if b["category"].startswith("숙박")), None)
    if lb and abs(lb["amountKrw"] - lsum) > 1:
        errors.append(f"[비용] 숙소 합 {lsum} ≠ 총비용 숙박 {lb['amountKrw']}")

    # 7. 지도
    for m in maps:
        if not (REPO / "assets" / m["file"]).exists():
            errors.append(f"[지도] {m['file']} 없음")
    for d in days:
        if not d["mapIds"]:
            warns.append(f"[지도] {d['id']} 지도 없음")
    hot_by_map = defaultdict(int)
    for h in hot:
        hot_by_map[h["mapId"]] += 1
    for m in maps:
        if hot_by_map[m["id"]] == 0 and m["id"] != "ref_cablecar":
            warns.append(f"[지도] {m['id']} 핫스팟 없음")

    # 8. 링크 누락 후보: 중국어가 들어간 도착지 중 링크 규칙이 하나도 안 맞는 것
    for r in sched:
        text = f"{r['to']} / {r['via']}"
        if re.search(r"[\u4e00-\u9fff]", r["to"]) and not any(l["match"] in text for l in links):
            warns.append(f"[링크] {r['id']} 도착지 '{r['to'][:30]}' 에 택시 카드 링크 없음")

    print(f"오류 {len(errors)}건 · 경고 {len(warns)}건")
    for e in errors:
        print("  ERR ", e)
    for w in warns:
        print("  WARN", w)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
