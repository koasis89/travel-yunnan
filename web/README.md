# 윈난 여행 웹 버전

안드로이드 앱의 일정·숙소·식사·택시 카드·교통·경비·준비물·유의사항·긴급 연락처·위챗 템플릿·숫자 카드를 웹(PWA)으로 옮긴 것이다. 회화·음성·지도는 제외했다. 절차는 `docs/웹버전_구축절차서.md`.

## 로컬 실행

```bash
cd web && npm install
echo "ALLOWED_EMAILS=a@gmail.com,b@gmail.com" > .env.local   # 허용 이메일(git 제외)
npm run dev          # 개발 서버
npm test             # 도메인 테스트(Vitest)
npm run build        # 암호화 → 타입 검사 → dist/ 생성
```

## 데이터 갱신

엑셀 수정 → `python3 tools/build_data.py` → `python3 tools/export_web.py` → 커밋·푸시 → Cloudflare Pages 자동 배포.
`data/web/` 는 직접 고치지 않는다. 평문 데이터는 절대 `web/` 아래에 두지 않는다.

## Cloudflare 설정 (Workers 정적 자산, `web/wrangler.jsonc`)

| 항목 | 값 |
|---|---|
| 프로젝트 이름 | `travel-yunnan` |
| 빌드 명령 | `npm run build` |
| 배포 명령 | `npx wrangler deploy` |
| 고급 설정 → 루트 디렉터리(경로) | `web` |
| 빌드 변수 | `ALLOWED_EMAILS`(허용 이메일, 쉼표 구분), `NODE_VERSION`=`22` |
| Cloudflare Access로 보호 | 끔(이메일 로그인 + 암호화 사용) |

주소: `https://travel-yunnan.<계정 하위도메인>.workers.dev`

## Cloudflare Pages 설정(대안)

| 항목 | 값 |
|---|---|
| Root directory | `web` |
| Build command | `npm run build` |
| Build output directory | `dist` |
| 환경 변수(Secret) | `ALLOWED_EMAILS` = 허용 이메일(쉼표 구분) |
| 환경 변수 | `NODE_VERSION` = `22` |

배포 결과에는 암호문(`data.enc`)과 포장된 키(`keys.json`)만 있고 이메일·예약번호 원문은 없다.

## 브라우저 점검

```bash
npm run build && npx vite preview --port 4173 &
node tests/e2e/smoke.cjs http://localhost:4173/ ../docs/screenshots/web   # Playwright 필요
```

## 영수증 인식

경비 화면에서 영수증·결제 화면 사진을 고르면 기기 안에서 인식(Tesseract.js, chi_sim)해 금액·상호·일차·분류를 채운다. 인식 파일은 빌드 전에 `scripts/copy-ocr.mjs` 가 `public/ocr` 로 복사하며(리포 제외), 첫 사용 때 약 6MB 를 내려받아 캐시한다. 실제 인식 점검: `node tests/ocr-real.mjs`.
