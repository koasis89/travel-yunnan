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
  page.on('console', (m) => { if (m.type() === 'error' && !m.text().startsWith('Warning: Parameter not found')) errors.push(m.text()) }) // Tesseract 엔진 설정 경고는 무해

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
    ['#/more', 'w16_more', 'text=문서함'],
    ['#/sights', 'w26_sights', 'text=송찬림사'],
    ['#/amap', 'w29_amap_links', 'text=一瓦堂咖啡店'],
    ['#/eats', 'w32_eats', 'text=건신원 과교미선(宝善街점)'],
    ['#/eats/jianxinyuan', 'w33_eat_jianxinyuan', '[data-testid=make-order]'],
    ['#/sights/songzanlin', 'w27_sight_songzanlin', 'text=噶丹·松赞林寺'],
    ['#/schedule?day=D6', 'w28_schedule_d6_sight', '.linkchip:has-text("송찬림사")'],
    ['#/schedule?day=D3', 'w22_schedule_d3', 'text=위챗: 가이드 첫 연락: 픽업 시각·장소 문의(12/15까지)'],
    ['#/templates?link=tl-08', 'w23_template_guide', 'text=在四方街哪里集合'],
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
  ok('캘린더 파일 15건', (ics.match(/BEGIN:VEVENT/g) || []).length === 15)

  // 체크 저장 유지
  await page.goto(base + '#/packing'); await page.locator('.check input').first().check(); await page.reload()
  await page.waitForSelector('.check input')
  ok('체크 상태 새로고침 후 유지', await page.locator('.check input').first().isChecked())

  // 영수증 인식 → 자동 채움 → 저장
  const fx = require('path').join(__dirname, '..', 'fixtures')
  await page.goto(base + '#/expense'); await page.waitForSelector('text=영수증으로 추가')
  await page.setInputFiles('[data-testid=receipt-input]', `${fx}/wechat.jpg`)
  const filled = await page.waitForFunction(() => document.querySelector('input[inputmode=decimal]')?.value === '128', null, { timeout: 90000 }).then(() => true).catch(() => false)
  ok('영수증(위챗페이) 금액 자동 채움 128', filled)
  ok('영수증 가게 이름 채움', (await page.inputValue('input[placeholder^="메모"]').catch(() => '')).includes('田村'))
  await page.screenshot({ path: `${out}/w20_receipt_prefill.png` })
  await page.click('button:has-text("저장")')
  ok('영수증 지출 저장·표시', await page.waitForSelector('text=영수증 인식', { timeout: 5000 }).then(() => true).catch(() => false))
  await page.screenshot({ path: `${out}/w21_expense_list.png` })
  // 한 번 받은 인식 데이터로 오프라인 인식
  await page.waitForTimeout(1500); await ctx.setOffline(true)
  await page.setInputFiles('[data-testid=receipt-input]', `${fx}/didi.jpg`)
  ok('오프라인 영수증 인식(디디 32.5)', await page.waitForFunction(() => document.querySelector('input[inputmode=decimal]')?.value === '32.5', null, { timeout: 60000 }).then(() => true).catch(() => false))
  await ctx.setOffline(false)
  await page.click('button:has-text("취소")').catch(() => {})

  // 경비: 원화를 위안으로 잘못 넣은 기록 → 경고·원화로 고치기·수정·삭제 후 총액 즉시 반영
  page.on('dialog', (d) => d.accept())
  const total = async () => (await page.textContent('.card.mint .bold')).replace(/[^\d]/g, '')
  const before = Number(await total())
  await page.click('button:has-text("지출 추가")'); await page.fill('input[placeholder="금액"]', '55000'); await page.click('button:has-text("저장")')
  ok('잘못 고른 통화 경고', await page.waitForSelector('[data-testid=fix-all-krw]', { timeout: 3000 }).then(() => true).catch(() => false))
  const wrong = Number(await total())
  await page.click('button:has-text("원화로 고치기 (55,000원)")')
  ok('원화로 고치기 → 총액 즉시 반영', Number(await total()) === before + 55000 && wrong > before + 10000000, `${before}→${wrong}→${await total()}`)
  await page.locator('button[aria-label=고치기]').first().click(); await page.fill('input[placeholder="금액"]', '60000'); await page.click('button:has-text("저장")')
  ok('지출 수정 → 총액 반영', Number(await total()) === before + 60000)
  await page.locator('button[aria-label=삭제]').first().click()
  ok('지출 삭제 → 총액 즉시 반영', Number(await total()) === before)
  await page.reload(); await page.waitForSelector('.card.mint')
  ok('삭제 새로고침 후 유지', Number(await total()) === before)

  // 관광지: 사진·출처·택시 카드
  await page.goto(base + '#/sights/glacier'); await page.waitForSelector('figcaption')
  ok('관광지 사진 표시', await page.evaluate(() => { const i = document.querySelector('figure img'); return !!i && i.complete && i.naturalWidth > 100 }))
  ok('관광지 사진 출처(라이선스)', (await page.textContent('figcaption')).includes('CC BY'))
  await page.click('text=택시 카드'); ok('관광지 → 택시 카드', await page.waitForSelector('text=玉龙雪山大索道', { timeout: 5000 }).then(() => true).catch(() => false))

  // 앱 업데이트 확인 버튼
  await page.goto(base + '#/more'); await page.waitForSelector('[data-testid=update-row]')
  await page.click('[data-testid=update-row] >> text=업데이트 확인')
  ok('업데이트 확인 → 최신 버전 안내', await page.waitForSelector('text=최신 버전이다', { timeout: 15000 }).then(() => true).catch(() => false))

  // 지도 링크 모음: 숙소·관광지·식당, 공유받은 정확 링크
  await page.goto(base + '#/amap'); await page.waitForSelector('[data-testid=amap-row]')
  ok('지도 링크 80곳 이상', (await page.locator('[data-testid=amap-row]').count()) >= 80)
  ok('공유받은 링크(일와당) 사용', (await page.getAttribute('[data-testid=amap-row]:has-text("一瓦堂") a', 'href')) === 'https://surl.amap.com/1ZGvMhI4fh')
  ok('숙소 지도 링크', ((await page.getAttribute('[data-testid=amap-row]:has-text("丽江沐蘭遇") a', 'href')) || '').startsWith('https://uri.amap.com/search'))
  await page.click('[data-testid=amap-groups] >> text=숙소')
  ok('지도 링크: 숙소만 보기', [4, 5, 6].includes(await page.locator('[data-testid=amap-row]').count()) && !(await page.isVisible('text=톈춘 산탕')))
  await page.click('[data-testid=amap-groups] >> text=식당')
  ok('지도 링크: 식당만 보기(한국어 이름)', await page.isVisible('text=톈춘 산탕 야크고기 훠궈') && !(await page.isVisible('[data-testid=amap-row]:has-text("丽江沐蘭遇")')))
  await page.click('[data-testid=amap-groups] >> text=전체')

  // 옥룡설산 가이드 위챗: 일정 칩 → 템플릿 + 위챗 ID 카드
  await page.goto(base + '#/schedule?day=D2'); await page.click('text=위챗: 가이드에게 내일 집합 시각·장소 확인')
  ok('가이드 전날 확인 문장', await page.waitForSelector('text=我们会提前10分钟到', { timeout: 5000 }).then(() => true).catch(() => false))
  ok('가이드 위챗 ID 카드', (await page.textContent('[data-testid=contact-card]')).includes('18088479095'))
  await page.screenshot({ path: `${out}/w31_guide_template.png` })
  await page.goto(base + '#/schedule?day=D3')
  ok('D3 가이드 메시지 칩 4개', (await page.locator('.linkchip.tpl:has-text("가이드"), .linkchip.tpl:has-text("기사에게")').count()) === 4)

  // 맛집: 식사 탭 → 식당 정보 → 메뉴판 → 주문 문장
  await page.goto(base + '#/stay?tab=meals&day=D2'); await page.click('[data-testid=eat-link] >> nth=0')
  ok('식사 → 식당 정보', await page.waitForSelector('[data-testid=menu-item]', { timeout: 5000 }).then(() => true).catch(() => false))
  await page.goto(base + '#/eats/tiancun'); await page.waitForSelector('[data-testid=menu-item]')
  ok('메뉴 사진 표시', await page.evaluate(() => [...document.querySelectorAll('[data-testid=menu-item] img')].some((i) => i.complete && i.naturalWidth > 50)))
  await page.click('[data-testid=menu-item] >> nth=0'); await page.click('[data-testid=menu-item] >> nth=1'); await page.click('[data-testid=menu-item] >> nth=1')
  await page.screenshot({ path: `${out}/w34_eat_pick.png` })
  await page.click('[data-testid=make-order]')
  ok('메뉴 여러 개 → 주문 문장', await page.waitForSelector('text=我要一份酸汤牦牛肉火锅、两份牦牛肉。', { timeout: 5000 }).then(() => true).catch(() => false))
  await page.click('[data-testid=drink]:has-text("冰啤酒")')
  ok('여러 음식 + 음료', await page.isVisible('text=我要一份酸汤牦牛肉火锅、两份牦牛肉，再要一瓶冰啤酒。'))
  await page.screenshot({ path: `${out}/w35_menu_multi.png` })
  await page.goto(base + '#/schedule?day=D3')
  ok('일정 식당 칩(천천선)', await page.isVisible('.linkchip:has-text("천천선")'))

  // D7 쉬는 날: 배달 점심 행에 배달 주소 템플릿
  await page.goto(base + '#/schedule?day=D7')
  ok('D7 배달 점심·주소 템플릿', await page.isVisible('text=위챗: 배달 받을 주소 요청(호텔 프런트)') && await page.isVisible('text=배달 점심'))

  // 씨트립 평점·인기 메뉴 담기
  await page.goto(base + '#/eats/jianxinyuan'); await page.waitForSelector('[data-testid=rating]')
  ok('평점 표시(★4.5·리뷰 189)', (await page.textContent('[data-testid=rating]')).includes('4.5') && (await page.textContent('[data-testid=rating]')).includes('189'))
  await page.click('[data-testid=popular]:has-text("标准过桥米线")'); await page.click('[data-testid=make-order]')
  ok('인기 메뉴 → 주문 문장', await page.waitForSelector('text=我要一份标准过桥米线。', { timeout: 5000 }).then(() => true).catch(() => false))

  // 고산병 병원·새 가게·쿠폰 팁
  await page.goto(base + '#/emergency'); await page.waitForSelector('[data-testid=hospital]')
  ok('고산병 병원(리장시 인민병원)', (await page.textContent('[data-testid=hospital]')).includes('丽江市人民医院'))
  ok('병원 지도 = 공유 링크', (await page.getAttribute('[data-testid=hospital] a:has-text("지도")', 'href')) === 'https://surl.amap.com/jNlnaOmd1fJ')
  await page.click('text=병원에서 보여줄 문장'); ok('병원 문장 카드', await page.isVisible('text=想看急诊，吸氧'))
  await page.goto(base + '#/eats'); ok('쿠폰 팁·숙소 번호 복사', await page.isVisible('[data-testid=copy-lodging-phone]'))
  await page.goto(base + '#/eats/sanerjiusi'); ok('3294 첨차관 택시 카드', await page.isVisible('a:has-text("택시 카드")'))

  // 주문 문장: 음료
  await page.goto(base + '#/menu?food=过桥米线'); await page.waitForSelector('[data-testid=drink]')
  await page.click('[data-testid=drink]:has-text("冰啤酒")'); await page.click('[data-testid=drink]:has-text("冰啤酒")'); await page.click('[data-testid=drink]:has-text("矿泉水")')
  ok('주문 문장 음료(맥주 2·생수 1)', await page.isVisible('text=我要一份过桥米线，再要一瓶矿泉水、两瓶冰啤酒。'))
  await page.click('.chip:has-text("음료만")')
  ok('음료만 주문', await page.isVisible('text=我要一瓶矿泉水、两瓶冰啤酒。'))
  await page.screenshot({ path: `${out}/w30_menu_drinks.png` })

  // 택시 카드 '지도' 버튼 = 만든 고덕지도 링크
  await page.goto(base + '#/taxi/lodging/mulanyu'); await page.waitForSelector('text=丽江沐蘭遇の汤宿')
  ok('택시 카드(숙소) 지도 = 고덕지도 링크', ((await page.getAttribute('a.btn:has-text("지도")', 'href')) || '').startsWith('https://uri.amap.com/search'))
  ok('택시 카드 지도 검색 버튼', ((await page.getAttribute('a.btn:has-text("지도 검색")', 'href')) || '').includes('keyword='))
  ok('택시 카드 전화 버튼 없음', (await page.locator('a[href^="tel:"]').count()) === 0)
  await page.goto(base + '#/taxi/place/yiwatang'); await page.waitForSelector('text=一瓦堂咖啡店')
  ok('택시 카드(장소) 지도 = 공유받은 링크', (await page.getAttribute('a.btn:has-text("지도")', 'href')) === 'https://surl.amap.com/1ZGvMhI4fh')

  // 문서함: PDF 바우처 → 쪽 그림 펼침·분류/일차 추정, 기차표 사진, 거르기, 일정 화면 바로가기
  await page.goto(base + '#/docs'); await page.waitForSelector('text=아직 문서가 없다')
  await page.setInputFiles('[data-testid=doc-input]', `${fx}/sample_voucher_1218.pdf`)
  ok('PDF 추가 → 정보 편집 화면', await page.waitForSelector('[data-testid=doc-title]', { timeout: 30000 }).then(() => true).catch(() => false))
  ok('PDF 2쪽을 그림으로 펼침', await page.waitForFunction(() => document.querySelectorAll('[data-testid=doc-pages] img').length === 2, null, { timeout: 15000 }).then(() => true).catch(() => false))
  ok('PDF 분류·일차 추정(바우처·D3)', await page.isVisible('.card .chip.on:has-text("바우처")') && await page.isVisible('.card .chip.on:has-text("D3")'))
  await page.fill('[data-testid=doc-title]', '옥룡설산 투어 바우처'); await page.click('button:has-text("저장")')
  await page.screenshot({ path: `${out}/w24_doc_view.png` })
  await page.goto(base + '#/docs')
  await page.setInputFiles('[data-testid=doc-input]', `${fx}/C446_ticket_sample.png`)
  await page.waitForSelector('[data-testid=doc-title]', { timeout: 15000 })
  ok('사진 분류·일차 추정(기차표·D2)', await page.isVisible('.card .chip.on:has-text("기차표")') && await page.isVisible('.card .chip.on:has-text("D2")'))
  await page.click('button:has-text("저장")')
  await page.goto(base + '#/docs'); await page.waitForSelector('[data-testid=doc-row]')
  ok('문서함 목록 2건', (await page.locator('[data-testid=doc-row]').count()) === 2)
  await page.click('.chip:has-text("기차표")')
  ok('분류 거르기(기차표 1건)', (await page.locator('[data-testid=doc-row]').count()) === 1)
  await page.screenshot({ path: `${out}/w25_docs_list.png` })
  await page.goto(base + '#/schedule?day=D3')
  ok('일정 D3 문서 바로가기', await page.waitForSelector('[data-testid=day-docs]:has-text("D3 문서 1건")', { timeout: 5000 }).then(() => true).catch(() => false))
  await page.reload(); await page.goto(base + '#/docs')
  ok('새로고침 후 문서 유지', await page.waitForSelector('[data-testid=doc-row]', { timeout: 5000 }).then(async () => (await page.locator('[data-testid=doc-row]').count()) === 2).catch(() => false))
  const docHref = await page.locator('[data-testid=doc-row]:has-text("옥룡설산")').getAttribute('href')

  // 오프라인: 서비스 워커 캐시로 다시 열기
  await page.goto(base); await page.waitForFunction(() => navigator.serviceWorker?.controller != null, null, { timeout: 15000 }).catch(() => {})
  await page.waitForTimeout(1500)
  await ctx.setOffline(true)
  await page.reload(); 
  ok('오프라인 재실행 후 열림', await page.waitForSelector('text=바로가기', { timeout: 15000 }).then(() => true).catch(() => false))
  await page.screenshot({ path: `${out}/w18_offline_home.png` })
  await page.goto(base + docHref)
  ok('오프라인 문서 열람(바우처 2쪽)', await page.waitForFunction(() => document.querySelectorAll('[data-testid=doc-pages] img').length === 2, null, { timeout: 10000 }).then(() => true).catch(() => false))
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
