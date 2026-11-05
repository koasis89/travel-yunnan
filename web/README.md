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
`web/data/` 는 직접 고치지 않는다.

## Cloudflare Pages 설정

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
