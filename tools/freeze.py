#!/usr/bin/env python3
"""데이터 동결(S7-2·S7-3). 버전을 올리고 데이터·음성을 재생성한 뒤 검증한다.

    python3 tools/freeze.py 2026-12-01
    git add -A && git commit -m "데이터 동결 2026-12-01" && git tag v1.0-data-20261201 && git push --tags
"""
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent


def main():
    if len(sys.argv) != 2:
        print(__doc__); sys.exit(2)
    (REPO / "data" / "version.txt").write_text(sys.argv[1] + "\n", encoding="utf-8")
    subprocess.run([sys.executable, REPO / "tools" / "build_data.py"], check=True)
    subprocess.run([sys.executable, REPO / "tools" / "gen_audio.py"], check=True)
    print(f"동결 완료: 데이터 버전 {sys.argv[1]}. 커밋·태그 후 android 에서 ./gradlew assembleRelease 를 실행한다.")


if __name__ == "__main__":
    main()
