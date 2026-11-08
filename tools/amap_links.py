#!/usr/bin/env python3
"""고덕지도(Amap) 링크 채우기 + 링크 모음 문서 만들기. build_data.py 가 마지막에 실행한다.

    python3 tools/amap_links.py [data/json]

- 단축 링크(surl.amap.com)는 고덕지도 앱의 '공유'로만 만들 수 있다. 받은 단축 링크는 data/places.json 의
  amapUrl 에 넣어 두고 그대로 쓴다.
- 단축 링크가 없는 숙소·장소·식당·관광지는 검색 링크를 만든다:
  https://uri.amap.com/search?keyword=<중국어 이름 또는 주소>&city=<도시>&callnative=1
  휴대폰에서 누르면 고덕지도 앱(없으면 웹)이 그 검색 결과로 열린다.
- 중국어로만 적힌 식사 선택지에는 data/meal_names_ko.json 의 한국어 이름을 nameKo 로 넣는다.
- 결과: data/json 의 lodgings·places·meals·sights 에 amapUrl, docs/고덕지도_링크모음.md
"""
import json
import re
import sys
from pathlib import Path
from urllib.parse import quote

REPO = Path(__file__).resolve().parent.parent
CJK = re.compile(r"[一-鿿]")
HANGUL = re.compile(r"[가-힣]+:?")
HAS_KO = re.compile(r"[가-힣]")


def clean(s: str) -> str:
    s = HANGUL.sub("", s or "")
    s = re.sub(r"\(\s*\)|（\s*）", "", s)
    return re.sub(r"\s{2,}", " ", s).strip(" /,")


def city_of(*texts: str) -> str:
    t = " ".join(x or "" for x in texts)
    if re.search(r"瓦當雲咖", t):
        return "丽江"
    if re.search(r"丽江|리장|白沙|束河|玉龙|백사|수허|옥룡|古城区", t):
        return "丽江"
    if re.search(r"香格里拉|迪庆|虎跳峡|샹그릴라|호도협|独克宗|松赞", t):
        return "迪庆藏族自治州"
    if re.search(r"昆明|쿤밍|官渡|五华|盘龙|西山", t):
        return "昆明"
    return ""


def search_url(keyword: str, city: str) -> str:
    q = f"keyword={quote(keyword)}" + (f"&city={quote(city)}" if city else "")
    return f"https://uri.amap.com/search?{q}&src=travelyunnan&callnative=1"


def main(d: Path):
    load = lambda n: json.loads((d / n).read_text(encoding="utf-8"))  # noqa: E731
    save = lambda n, o: (d / n).write_text(json.dumps(o, ensure_ascii=False, indent=2), encoding="utf-8")  # noqa: E731
    rows = {"숙소": [], "관광지·장소": [], "식당": []}

    lod = load("lodgings.json")
    # 숙소 id 와 같은 장소(places.json)에 공유받은 정확한 링크가 있으면 그것을 쓴다(예: oura)
    shared = {p["id"]: p["amapUrl"] for p in load("places.json")["items"] if "surl.amap.com" in (p.get("amapUrl") or "")}
    for l in lod["items"]:
        if l["id"] in shared:
            l["amapUrl"] = shared[l["id"]]
        if not l.get("nameZh"):
            continue
        name = l["nameZh"]
        # '|' 가 섞인 숙소 이름은 검색이 안 돼 주소로 찾는다. 나머지는 이름(지점명 포함)으로 찾는다 —
        # 시골 주소(예: 本地湾9号)는 고덕지도가 마을 중심으로 잡아 엉뚱한 곳이 나오기 쉽다
        kw = clean(l["addrZh"]) if "|" in name and CJK.search(l.get("addrZh", "")) else name
        l["amapUrl"] = l.get("amapUrl") or search_url(kw, city_of(l["addrZh"], l.get("region", "")))
        if l["id"] not in {r[0] for r in rows["숙소"]}:
            rows["숙소"].append((l["id"], l["nameKo"], name, clean(l["addrZh"]), l["amapUrl"]))
    save("lodgings.json", lod)

    pl = load("places.json")
    for p in pl["items"]:
        p["amapUrl"] = p.get("amapUrl") or search_url(p["nameZh"], city_of(p["addrZh"], p["nameZh"]))
    save("places.json", pl)
    place = {p["id"]: p for p in pl["items"]}

    meals = load("meals.json")
    ko_names = json.loads((REPO / "data" / "meal_names_ko.json").read_text(encoding="utf-8"))["names"]
    rest_ko = {i: r["nameKo"] for r in load("restaurants.json")["items"] for i in r["optionIds"]}
    missing = []
    for m in meals["items"]:
        for o in m["options"]:
            # 중국어로만 적힌 선택지에 한국어 이름을 붙인다(표에 없으면 식당 정보 이름)
            if o["name"] in ko_names or not HAS_KO.search(o["name"]):
                o["nameKo"] = ko_names.get(o["name"]) or rest_ko.get(o["id"]) or ""
                if not o["nameKo"]:
                    missing.append(o["name"])
            if o.get("amapUrl"):
                pass
            elif CJK.search(o.get("nameZh") or ""):
                o["amapUrl"] = search_url(clean(o["nameZh"]), city_of(o.get("addrZh", ""), m.get("area", "")))
            elif CJK.search(clean(o.get("addrZh") or "")):
                o["amapUrl"] = search_url(clean(o["addrZh"]), city_of(o["addrZh"], m.get("area", "")))
            else:
                continue
            zh = clean(o["name"]) if o.get("nameKo") else (o.get("nameZh") or "")
            rows["식당"].append((o["id"], f"{m['dayId']} {m['slot']} · {o.get('nameKo') or o['name']}", zh, clean(o.get("addrZh", "")), o["amapUrl"]))
    save("meals.json", meals)

    sights = load("sights.json")
    for s in sights["items"]:
        p = place.get(s.get("placeId"))
        s["amapUrl"] = p["amapUrl"] if p else search_url(s["nameZh"], city_of(s["nameZh"], s.get("region", "")))
        rows["관광지·장소"].append((s["id"], s["nameKo"], p["nameZh"] if p else s["nameZh"], p["addrZh"] if p else "", s["amapUrl"]))
    save("sights.json", sights)

    rest = load("restaurants.json")
    mo = {o["id"]: o for m in meals["items"] for o in m["options"]}
    for r in rest["items"]:
        r["amapUrl"] = next((mo[i]["amapUrl"] for i in r["optionIds"] if mo.get(i, {}).get("amapUrl")), None) or \
            (place[r["placeId"]]["amapUrl"] if r.get("placeId") in place else None) or search_url(r["nameZh"].split("/")[0].strip(), city_of(r["nameZh"], r["region"]))
    save("restaurants.json", rest)

    out = ["# 고덕지도(Amap) 링크 모음", "",
           "> `tools/amap_links.py` 가 만든다(손으로 고치지 않는다). 고덕지도 앱을 깔고 링크를 누르거나 복사해 붙여넣는다.",
           "> `surl.amap.com` 은 고덕지도에서 공유받은 정확한 위치, `uri.amap.com/search` 는 이름·주소 검색 링크다(검색 결과에서 맞는 곳을 고른다).", ""]
    for g, rs in rows.items():
        out += [f"## {g}", ""]
        seen = set()
        for _, ko, zh, addr, url in rs:
            if url in seen:
                continue
            seen.add(url)
            out += [f"📍{ko}"] + ([f"중국어 이름 : {zh}"] if zh else []) + ([f"고덕지도 주소 : {addr}"] if CJK.search(addr or "") else []) + [f"고덕지도 주소 링크 : {url}", ""]
    (REPO / "docs" / "고덕지도_링크모음.md").write_text("\n".join(out), encoding="utf-8")
    if missing:
        print(f"경고: 한국어 이름이 없는 식사 선택지 {len(missing)}건 → data/meal_names_ko.json 에 추가: {missing}")
    n = sum(len(v) for v in rows.values())
    print(f"고덕지도 링크 {n}건(숙소 {len(rows['숙소'])}·관광지 {len(rows['관광지·장소'])}·식당 {len(rows['식당'])}) → docs/고덕지도_링크모음.md")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else REPO / "data" / "json")
