# travel-yunnan

윈난 7박 8일(2026-12-16 ~ 12-23) 가족 여행용 안드로이드 전용 앱 프로젝트이다.

- `docs/앱구성방안.md` : 앱 구성방안(화면·데이터·기술·개발 단계)
- `docs/작업절차서.md` : 단계별 상세 작업절차서(S0~S7)와 진행 기록
- `docs/test_result.md` : 테스트 결과서와 실기기 체크리스트
- `docs/배포안내.md` : 서명·릴리스 빌드·설치·데이터 동결 절차
- `tools/` : 엑셀·회화 텍스트 → JSON 변환, 검증 스크립트 (`python3 tools/build_data.py`)
- `data/json/` : 앱이 읽는 데이터(자동 생성, 수정하지 않는다)
- `android/` : Android Studio 프로젝트 (Kotlin, Jetpack Compose)
- `data/source/` : 원본 일정 엑셀(여권정보 시트 제외), 중국어 회화·병음 텍스트
- `assets/maps/` : 엑셀에서 추출한 일정별 지도 이미지 7장
- `assets/audio/phrases/` : 회화 음성 mp3 157개(edge-tts, `python3 tools/gen_audio.py`)
- `assets/foods/` : 엑셀 음식설명 시트에서 추출한 음식 이미지 24장(`food-<엑셀 행번호>`, Wikimedia Commons CC 라이선스)

여권 등 개인 식별 정보는 리포지토리에 올리지 않는다.

## 빌드

```bash
python3 tools/build_data.py        # 데이터 재생성·검증
python3 tools/gen_audio.py         # 바뀐 문장만 mp3 재생성 (pip install edge-tts)
cd android && ./gradlew assembleRelease   # 서명 APK: android/app/build/outputs/apk/release/ (keystore.properties 필요)
cd android && ./gradlew testDebugUnitTest   # 단위 테스트 + 화면 스크린샷(app/build/outputs/roborazzi)
```

`android/local.properties` 에 `sdk.dir=<Android SDK 경로>` 를 둔다. 빌드 시 `data/json`, `assets/` 가 앱 assets 로 자동 복사된다.
