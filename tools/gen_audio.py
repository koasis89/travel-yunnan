#!/usr/bin/env python3
"""회화 문장·단어·음식명 mp3 일괄 생성 (S5-2).

사용법:
    python3 tools/gen_audio.py                       # edge-tts, zh-CN-XiaoxiaoNeural
    python3 tools/gen_audio.py --voice zh-CN-YunxiNeural --force
    python3 tools/gen_audio.py --limit 3             # 샘플 3개만 (S5-1 확인용)

입력: data/json/phrases.json, words.json, foodNames.json
출력: assets/audio/phrases/{id}.mp3  +  {id}.sha (텍스트 해시, 바뀐 항목만 다시 생성)
copyOnly(위챗 장문) 항목은 만들지 않는다.

엔진: edge-tts (pip install edge-tts). 프록시 환경이면 HTTPS_PROXY 와
SSL_CERT_FILE(또는 REQUESTS_CA_BUNDLE)을 읽는다.
"""
import argparse
import asyncio
import hashlib
import json
import os
import ssl
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "assets" / "audio" / "phrases"
DATA = REPO / "data" / "json"
DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"


def load_items():
    items = []
    p = json.loads((DATA / "phrases.json").read_text(encoding="utf-8"))
    items += [(x["id"], x["zh"]) for x in p["items"] if not x.get("copyOnly")]
    for name in ("words.json", "foodNames.json"):
        items += [(x["id"], x["zh"]) for x in json.loads((DATA / name).read_text(encoding="utf-8"))["items"]]
    return items


def text_for_tts(zh: str) -> str:
    # "你好 我要预订包车" 처럼 띄어쓰기로 끊은 문장은 쉼표로 바꿔 자연스러운 휴지를 준다
    t = zh.strip()
    if " " in t and not any(c in t for c in "，。？！"):
        t = t.replace(" ", "，")
    return t


def sha(text: str, voice: str, rate: str) -> str:
    return hashlib.sha1(f"{voice}|{rate}|{text}".encode("utf-8")).hexdigest()


async def synth_all(items, voice, rate, concurrency, force):
    import edge_tts
    import edge_tts.communicate as C

    cafile = os.environ.get("SSL_CERT_FILE") or os.environ.get("REQUESTS_CA_BUNDLE")
    if cafile and Path(cafile).exists():
        C._SSL_CTX = ssl.create_default_context(cafile=cafile)
    proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
    OUT.mkdir(parents=True, exist_ok=True)
    sem = asyncio.Semaphore(concurrency)
    done, skipped, failed = 0, 0, []

    async def one(pid, zh):
        nonlocal done, skipped
        text = text_for_tts(zh)
        mp3 = OUT / f"{pid}.mp3"
        shaf = OUT / f"{pid}.sha"
        h = sha(text, voice, rate)
        if not force and mp3.exists() and shaf.exists() and shaf.read_text().strip() == h:
            skipped += 1
            return
        async with sem:
            for attempt in range(3):
                try:
                    com = edge_tts.Communicate(text, voice, rate=rate, proxy=proxy)
                    await com.save(str(mp3))
                    if mp3.stat().st_size < 1000:
                        raise RuntimeError("empty audio")
                    shaf.write_text(h)
                    done += 1
                    print(f"  {pid}  {zh[:24]}")
                    return
                except Exception as e:  # noqa: BLE001
                    if attempt == 2:
                        failed.append((pid, str(e)[:80]))
                    else:
                        await asyncio.sleep(2 * (attempt + 1))

    await asyncio.gather(*(one(pid, zh) for pid, zh in items))
    return done, skipped, failed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--voice", default=DEFAULT_VOICE)
    ap.add_argument("--rate", default="-10%", help="말속도. 예: -10%% (기본, 약간 느리게), +0%%")
    ap.add_argument("--concurrency", type=int, default=4)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()

    items = load_items()
    if a.limit:
        items = items[: a.limit]
    print(f"대상 {len(items)}건, 음성 {a.voice}, 속도 {a.rate}")
    done, skipped, failed = asyncio.run(synth_all(items, a.voice, a.rate, a.concurrency, a.force))
    total = sum(f.stat().st_size for f in OUT.glob("*.mp3"))
    print(f"생성 {done} · 건너뜀 {skipped} · 실패 {len(failed)} · 총 {len(list(OUT.glob('*.mp3')))}개 {total / 1024 / 1024:.1f}MB")
    for pid, err in failed:
        print(f"  실패 {pid}: {err}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
