// 브라우저 실제 렌더링 점검: 로그인·거부·자동 로그인·각 화면·오프라인. node tests/e2e/smoke.cjs [baseUrl] [outDir]
const { chromium } = require('playwright')
const base = process.argv[2] || 'http://localhost:4173/'
const out = process.argv[3] || 'shots'
const results = []
const ok = (name, cond, extra = '') => { results.push([cond ? 'PASS' : 'FAIL', name, extra]); return cond }

;(async () => {
  const browser = await chromium.launch()
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, locale: 'ko-KR', timezoneId: 'Asia/Seoul' })
  const page = await ctx.newPage()
  const errors = []
  page.on('pageerror', (e) => errors.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })

  await page.goto(base)
  await page.waitForSelector('#email')
  await page.screenshot({ path: `${out}/w01_login.png` })

  // 거부
  await page.fill('#email', 'someone@gmail.com'); await page.click('button:has-text("들어가기")')
  await page.waitForSelector('text=등록된 가족 이메일이 아니다', { timeout: 15000 })
  ok('비허용 이메일 거부', true)

  // 허용(대문자·공백)
  await page.fill('#email', '  KOASIS03@Gmail.com '); await page.click('button:has-text("들어가기")')
  ok('허용 이메일 로그인(대소문자·공백 무시)', await page.waitForSelector('text=바로가기', { timeout: 20000 }).then(() => true).catch(() => false))

  // 날짜 지정: D5 12:30
  await page.evaluate(() => localStorage.setItem('yn.settings', JSON.stringify({ override: '2026-12-20T12:30', bigFont: false, cnyRate: null, cash: 0 })))
  await page.reload(); await page.waitForSelector('text=바로가기', { timeout: 15000 })
  ok('자동 로그인(재실행 시 이메일 재입력 없음)', true)
  ok('홈 D5 지금 일정 = 차마객잔→중도객잔', await page.isVisible('text=차마객잔 → 중도객잔(中途客栈)'))
  await page.screenshot({ path: `${out}/w02_home_d5.png`, fullPage: false })

  const shots = [
    ['#/schedule?day=D4', 'w03_schedule_d4', 'text=위챗: 호도협 전용차 예약 요청'],
    ['#/taxi/lodging/mulanyu', 'w04_taxi_mulanyu', 'text=丽江沐蘭遇の汤宿'],
    ['#/stay?tab=lodging', 'w05_stay_lodging', 'text=물라뉴 탕수 호텔'],
    ['#/stay?tab=meals&day=D2', 'w06_meals', 'text=田村·酸汤牦牛肉火锅'],
    ['#/stay?tab=foods', 'w07_foods', 'text=野生菌火锅'],
    ['#/menu?food=过桥米线', 'w08_menu', 'text=我要一份过桥米线。'],
    ['#/transport', 'w09_transport', 'text=캘린더 파일 받기'],
    ['#/expense', 'w10_expense', 'text=지출 추가'],
    ['#/packing', 'w11_packing', 'text=준비물'],
    ['#/notes', 'w12_notes', 'text=유의사항'],
    ['#/emergency', 'w13_emergency', 'text=영사안전콜센터'],
    ['#/templates?link=tl-06', 'w14_template_d5', 'text=上午10点半'],
    ['#/number', 'w15_number', 'text=一百五十块'],
    ['#/more', 'w16_more', 'text=로그아웃'],
    ['#/show/msg-001', 'w17_show_msg', 'text=매니저에게 보여주세요'],
  ]
  for (const [hash, name, sel] of shots) {
    await page.goto(base + hash)
    try { await page.waitForSelector(sel, { timeout: 8000 }); ok(`화면 ${name}`, true) } catch { ok(`화면 ${name}`, false, sel) }
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
    ok(`가로 넘침 없음 ${name}`, overflow <= 1, `${overflow}px`)
    await page.screenshot({ path: `${out}/${name}.png` })
  }

  // 일정 → 템플릿 칩 이동
  await page.goto(base + '#/schedule?day=D5'); await page.click('text=위챗: 샹그릴라 빵차 예약 요청')
  ok('일정 칩 → 템플릿 값 채움', await page.waitForSelector('text=上午10点半', { timeout: 5000 }).then(() => true).catch(() => false))

  // 캘린더 파일
  await page.goto(base + '#/transport')
  const [dl] = await Promise.all([page.waitForEvent('download'), page.click('text=캘린더 파일 받기')])
  const ics = require('fs').readFileSync(await dl.path(), 'utf8')
  ok('캘린더 파일 13건', (ics.match(/BEGIN:VEVENT/g) || []).length === 13)

  // 체크 저장 유지
  await page.goto(base + '#/packing'); await page.locator('.check input').first().check(); await page.reload()
  await page.waitForSelector('.check input')
  ok('체크 상태 새로고침 후 유지', await page.locator('.check input').first().isChecked())

  // 오프라인: 서비스 워커 캐시로 다시 열기
  await page.goto(base); await page.waitForFunction(() => navigator.serviceWorker?.controller != null, null, { timeout: 15000 }).catch(() => {})
  await page.waitForTimeout(1500)
  await ctx.setOffline(true)
  await page.reload(); 
  ok('오프라인 재실행 후 열림', await page.waitForSelector('text=바로가기', { timeout: 15000 }).then(() => true).catch(() => false))
  await page.screenshot({ path: `${out}/w18_offline_home.png` })
  await ctx.setOffline(false)

  // 다크 모드
  const dark = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, colorScheme: 'dark' })
  const dp = await dark.newPage(); await dp.goto(base)
  await dp.fill('#email', 'koasis99@gmail.com'); await dp.click('button:has-text("들어가기")')
  await dp.waitForSelector('text=바로가기', { timeout: 15000 }); await dp.screenshot({ path: `${out}/w19_home_dark.png` })
  ok('다크 모드 로그인·홈', true)

  ok('콘솔 오류 없음', errors.length === 0, errors.slice(0, 3).join(' | '))
  await browser.close()
  for (const r of results) console.log(r.join('  '))
  const fail = results.filter((r) => r[0] === 'FAIL').length
  console.log(`\n${results.length - fail} 통과 / ${fail} 실패`)
  process.exit(fail ? 1 : 0)
})().catch((e) => { console.error(e); process.exit(2) })
