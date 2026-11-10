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
  // 실제 구글 드라이브(Apps Script)로는 절대 보내지 않는다. 라이브 사이트에는 드라이브 주소가 기본값으로 들어 있다
  let blockedDrive = 0
  const blockDrive = (route) => { blockedDrive++; return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ok: false, error: 'test-blocked' }) }) }
  await ctx.route('https://script.google.com/**', blockDrive)
  await ctx.route('https://script.googleusercontent.com/**', blockDrive)
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
  ok('홈 D5 지금 일정 = 빌라→Come Inn 직행', await page.isVisible('text=Rishine 빌라 → Come Inn(行者民宿·Come Inn, 本地湾)'))
  await page.screenshot({ path: `${out}/w02_home_d5.png`, fullPage: false })

  const shots = [
    ['#/schedule?day=D4', 'w03_schedule_d4', 'text=위챗: 호도협 Come Inn 직행 전용차 예약 요청'],
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
  ok('캘린더 파일 16건', (ics.match(/BEGIN:VEVENT/g) || []).length === 16)

  // 체크 저장 유지
  await page.goto(base + '#/packing'); await page.locator('.check input').first().check(); await page.reload()
  await page.waitForSelector('.check input')
  ok('체크 상태 새로고침 후 유지', await page.locator('.check input').first().isChecked())

  // 영수증 인식 → 자동 채움 → 저장
  const fx = require('path').join(__dirname, '..', 'fixtures')
  await page.goto(base + '#/expense'); await page.waitForSelector('text=영수증 추가')
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

  // 경비: 영수증 추가·지출 추가 한 줄, CSV 가져오기(다시 넣으면 건너뜀)
  ok('영수증 추가·지출 추가 한 줄', await page.evaluate(() => { const b = [...document.querySelectorAll('[data-testid=expense-actions] button')].map((x) => x.getBoundingClientRect().top); return b.length === 2 && Math.abs(b[0] - b[1]) < 2 }))
  const rowsBefore = await page.locator('.card.flat .li').count()
  await page.setInputFiles('[data-testid=csv-input]', `${fx}/expenses_sample.csv`)
  ok('CSV 가져오기 2건', await page.waitForSelector('text=2건을 추가했다', { timeout: 5000 }).then(() => true).catch(() => false) && await page.isVisible('text=시험용 호텔'))
  await page.waitForTimeout(2500)
  await page.setInputFiles('[data-testid=csv-input]', `${fx}/expenses_sample.csv`)
  ok('같은 CSV 다시 넣으면 건너뜀', await page.waitForSelector('text=이미 있는 2건은 건너뛰었다', { timeout: 5000 }).then(() => true).catch(() => false))
  await page.screenshot({ path: `${out}/w30_expense_csv.png` })

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
  ok('관광지 사진 표시', await page.waitForFunction(() => { const i = document.querySelector('figure img'); return !!i && i.complete && i.naturalWidth > 100 }, null, { timeout: 10000 }).then(() => true).catch(() => false))
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
  ok('메뉴 사진 표시', await page.waitForFunction(() => [...document.querySelectorAll('[data-testid=menu-item] img')].some((i) => i.complete && i.naturalWidth > 50), null, { timeout: 10000 }).then(() => true).catch(() => false))
  await page.click('[data-testid=menu-item] >> nth=0'); await page.click('[data-testid=menu-item] >> nth=1'); await page.click('[data-testid=menu-item] >> nth=1')
  await page.screenshot({ path: `${out}/w34_eat_pick.png` })
  await page.click('[data-testid=make-order]')
  ok('메뉴 여러 개 → 주문 문장', await page.waitForSelector('text=我要一份酸汤牦牛肉火锅、两份牦牛肉。', { timeout: 5000 }).then(() => true).catch(() => false))
  await page.click('[data-testid=order-tabs] >> text=맥주'); await page.click('[data-testid=item][data-zh="冰啤酒"] [data-testid=plus]')
  ok('여러 음식 + 맥주', await page.isVisible('text=我要一份酸汤牦牛肉火锅、两份牦牛肉，再要一瓶冰啤酒。'))
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
  await page.goto(base + '#/menu?food=过桥米线'); await page.waitForSelector('[data-testid=order-tabs]')
  ok('주문 탭(음식·요청사항·음료·맥주·술)', (await page.locator('[data-testid=order-tabs] button').allTextContents()).map((t) => t.replace(/\s*\d+$/, '')).join() === '음식,요청사항,음료,맥주,술')
  await page.click('[data-testid=item][data-zh="过桥米线"] [data-testid=plus]')
  ok('음식 수량 + → 2', (await page.textContent('[data-testid=item][data-zh="过桥米线"] [data-testid=qty]')) === '2' && await page.isVisible('text=我要两份过桥米线。'))
  await page.click('[data-testid=item][data-zh="过桥米线"] [data-testid=minus]')
  await page.click('[data-testid=order-tabs] >> text=맥주')
  await page.click('[data-testid=item][data-zh="冰啤酒"]'); await page.click('[data-testid=item][data-zh="冰啤酒"] [data-testid=plus]')
  await page.click('[data-testid=order-tabs] >> text=음료'); await page.click('[data-testid=item][data-zh="矿泉水"] [data-testid=plus]')
  ok('주문 문장 음료(맥주 2·생수 1)', await page.isVisible('text=我要一份过桥米线，再要一瓶矿泉水、两瓶冰啤酒。'))
  ok('탭에 담은 수 표시', (await page.textContent('[data-testid=order-tabs]')).includes('맥주 2'))
  await page.click('[data-testid=order-tabs] >> text=음식'); await page.click('[data-testid=item][data-zh="过桥米线"] [data-testid=minus]')
  ok('음료만 주문(음식 0)', await page.isVisible('text=我要一瓶矿泉水、两瓶冰啤酒。'))
  await page.click('[data-testid=item][data-zh="这个"] [data-testid=plus]')
  ok('이거(가리키기) 1', await page.isVisible('text=这个要一份，再要一瓶矿泉水、两瓶冰啤酒。'))
  await page.click('[data-testid=order-tabs] >> text=술'); await page.click('[data-testid=item][data-zh="鹤庆乾酒"] [data-testid=plus]')
  ok('술 작은 병', (await page.textContent('[data-testid=order-sentence]')).includes('一小瓶鹤庆乾酒'))
  await page.click('[data-testid=order-tabs] >> text=요청사항'); await page.click('[data-testid=req][data-zh="不要辣"]')
  ok('요청사항 탭 → 문장 끝에 不要辣', (await page.textContent('[data-testid=order-sentence]')).endsWith('不要辣。') && (await page.textContent('[data-testid=order-tabs]')).includes('요청사항 1'))
  await page.screenshot({ path: `${out}/w30_menu_drinks.png` })

  // 사전 일정: 출발 전(12/10)이면 홈 카드·일정 '사전' 칩, 가이드 첫 연락 위챗 문장
  await page.evaluate(() => localStorage.setItem('yn.settings', JSON.stringify({ override: '2026-12-10T10:00', bigFont: false, cnyRate: null, cash: 0 })))
  await page.goto(base); await page.reload(); await page.waitForSelector('text=바로가기', { timeout: 15000 })
  ok('출발 전 홈: 사전 일정 카드(12/15 가이드 연락)', await page.isVisible('[data-testid=home-pretrip]:has-text("옥룡설산 가이드 연락")') && await page.isVisible('[data-testid=home-pretrip]:has-text("12/15(화) 20:00")'))
  await page.click('[data-testid=home-pretrip]')
  ok('일정 사전 칩 → 사전 일정', await page.waitForSelector('[data-testid=pretrip]', { timeout: 5000 }).then(() => true).catch(() => false) && (await page.getAttribute('[data-testid=pre-chip]', 'class')).includes('on'))
  await page.click('[data-testid=pretrip] >> text=위챗: 가이드 첫 연락 문장')
  ok('사전 일정 → 가이드 첫 연락 위챗 문장', await page.waitForSelector('text=请问', { timeout: 5000 }).then(() => true).catch(() => false))
  // 예약자·예약 번호는 개인 정보라 저장소에 없고 빌드 때(PRIVATE_JSON) 넣는다. PRIVATE_JSON_FILE 을 주면 그 값이 들어갔는지 본다
  if (process.env.PRIVATE_JSON_FILE) {
    const pv = JSON.parse(require('fs').readFileSync(process.env.PRIVATE_JSON_FILE, 'utf8'))
    ok('가이드 문장·연락처에 예약자·예약 번호(빌드 때 넣은 값)', await page.isVisible(`text=订单号（Booking reference）：${pv.guideRef}`) && await page.isVisible(`text=预订人：${pv.guideBooker}`) && (await page.textContent('[data-testid=booking-ref]')).includes(pv.guideRef))
  }
  else ok('가이드 템플릿 예약자·예약 번호 입력칸', await page.isVisible('text=대표 예약자(영문)') && await page.isVisible('text=예약 번호(Booking reference)'))
  await page.evaluate(() => localStorage.setItem('yn.settings', JSON.stringify({ override: '2026-12-20T12:30', bigFont: false, cnyRate: null, cash: 0 })))
  await page.reload(); await page.waitForSelector('text=바로가기', { timeout: 15000 }).catch(() => {})

  // D5: 11:30 빌라 → Come Inn 직행, 14:30 차마객잔까지 트레킹, 16:30 빵차 복귀
  await page.goto(base + '#/schedule?day=D5'); await page.waitForSelector('text=차마객잔 → Come Inn', { timeout: 5000 }).catch(() => {})
  const d5 = await page.textContent('body')
  ok('D5 새 일정(직행·얼리체크인·차마객잔 트레킹·빵차 복귀)', ['11:30~14:00', '14:00~14:30', '14:30~16:30', '16:30~17:10'].every((t) => d5.includes(t)) && d5.includes('얼리체크인') && !d5.includes('상호도협 → 상호도협'))
  await page.click('.linkchip:has-text("Come Inn에 얼리체크인·빵차 다시 확인")')
  ok('Come Inn 얼리체크인·빵차 문장·숙소 위챗', await page.waitForSelector('text=可以提前入住吗', { timeout: 5000 }).then(() => true).catch(() => false) && await page.isVisible('text=从茶马客栈接我们回客栈') && await page.isVisible('[data-testid=contact-card] >> text=18987966608'))
  await page.goto(base + '#/schedule?day=D4'); await page.click('.linkchip:has-text("호도협 Come Inn 직행 전용차")')
  ok('D4 전용차 문장 = 11시 반 Come Inn 직행(상호도협 없음)', await page.waitForSelector('text=上午11点半', { timeout: 5000 }).then(() => true).catch(() => false) && !(await page.textContent('body')).includes('上虎跳峡景区'))

  // D6 Oura Villa: 고성 안이 아니라 시내 북쪽 — 고성·대불사는 택시, 고덕지도 공유 링크
  await page.goto(base + '#/schedule?day=D6'); await page.waitForSelector('text=독극종고성(사방가) 고성 식당', { timeout: 5000 }).catch(() => {})
  const d6 = await page.textContent('body')
  ok('D6 숙소→고성 택시(도보 20~25분)·대불사 1.8km', d6.includes('약 5~10분(도보 20~25분)') && d6.includes('대불사·마니차까지 도보 약 1.8km') && !d6.includes('대형 마니차 바로 옆'))
  await page.goto(base + '#/taxi/lodging/oura'); await page.waitForSelector('text=东旺路77-83号', { timeout: 5000 }).catch(() => {})
  ok('Oura Villa 택시 카드 주소·지도 = 공유 링크', await page.isVisible('text=东旺路77-83号') && (await page.getAttribute('a.btn:has-text("지도") >> nth=0', 'href')) === 'https://surl.amap.com/gzk17mG495L')
  await page.goto(base + '#/sights/prayer_wheel'); ok('대불사 지도 = 공유 링크', (await page.getAttribute('a:has-text("지도에서 보기")', 'href')) === 'https://surl.amap.com/lHdr9stCf9n')
  await page.goto(base + '#/schedule?day=D7'); await page.waitForSelector('.linkchip', { timeout: 5000 }).catch(() => {})
  ok('D7 저녁 식당 칩: 上坐 과교미선', await page.isVisible('.linkchip:has-text("상좌 전통 과교미선")'))
  await page.goto(base + '#/eats/shangzuo'); await page.waitForSelector('[data-testid=rating]', { timeout: 5000 }).catch(() => {})
  ok('上坐 식당 정보·지도 = 공유 링크', (await page.textContent('body')).includes('上坐传统过桥米线') && (await page.locator('a[href="https://surl.amap.com/gQLmR7I1ofTW"]').count()) > 0)
  await page.goto(base + '#/taxi/lodging/comeinn'); await page.waitForSelector('text=半山居客栈南侧60米', { timeout: 5000 }).catch(() => {})
  for (const [id, url] of [['zhongtu', 'https://surl.amap.com/kFLiMqd1w1zp'], ['chama', 'https://surl.amap.com/gMKvhpor9KS']]) {
    await page.goto(base + `#/taxi/place/${id}`); await page.waitForSelector('a.btn:has-text("지도")', { timeout: 5000 }).catch(() => {})
    ok(`${id} 택시 카드 지도 = 공유 링크`, (await page.getAttribute('a.btn:has-text("지도") >> nth=0', 'href')) === url)
  }
  await page.goto(base + '#/taxi/lodging/comeinn'); await page.waitForSelector('text=半山居客栈南侧60米', { timeout: 5000 }).catch(() => {})
  ok('Come Inn 택시 카드 주소·지도 = 공유 링크', await page.isVisible('text=半山居客栈南侧60米') && (await page.getAttribute('a.btn:has-text("지도") >> nth=0', 'href')) === 'https://surl.amap.com/4zvUETfO416')

  // 더보기 → 밀크티 종류: 브랜드 6개 × 5잔, 당도·얼음 골라 주문 문장
  await page.goto(base + '#/more'); await page.click('text=밀크티 종류'); await page.waitForSelector('[data-testid=milktea-drink]', { timeout: 5000 }).catch(() => {})
  ok('밀크티 브랜드 6개·첫 브랜드 5잔', (await page.locator('[data-testid=milktea-brands] button').count()) === 6 && (await page.locator('[data-testid=milktea-drink]').count()) === 5 && await page.isVisible('text=伯牙绝弦'))
  await page.click('[data-testid=milktea-brands] >> text=瑞幸咖啡'); ok('브랜드 바꾸기(루이싱 生椰拿铁)', await page.isVisible('text=生椰拿铁') && await page.isVisible('text=코코넛 밀크에 에스프레소를 더한 대표 라떼'))
  await page.click('[data-testid=milktea-sugar] >> text=少糖'); await page.click('[data-testid=milktea-ice] >> text=热的')
  await page.click('[data-testid=milktea-drink]:has-text("生椰拿铁") >> text=주문')
  ok('밀크티 주문 문장(당도·온도)', await page.isVisible('text=你好，我要一杯生椰拿铁，少糖，热的。'))
  await page.screenshot({ path: `${out}/w31_milktea_order.png` })

  // 통합 검색: 아래 탭(더보기 왼쪽)
  await page.goto(base + '#/'); await page.click('.tabbar a:has-text("검색")'); await page.waitForSelector('[data-testid=search-input]', { timeout: 5000 }).catch(() => {})
  const tabs = await page.locator('.tabbar a').allInnerTexts()
  ok('아래 탭 검색(더보기 왼쪽)', tabs.map((t) => t.trim()).join('|') === '오늘|일정|숙소·식사|검색|더보기')
  // 휴대폰 한글 입력(자모 조합)처럼: 조합 중 글자를 여러 번 바꾼 뒤 확정
  {
    const cdp = await page.context().newCDPSession(page)
    await page.click('[data-testid=search-input]')
    for (const [steps, done] of [[['ㅅ', '서', '설'], '설'], [['ㅅ', '사', '산'], '산']]) {
      for (const t of steps) { await cdp.send('Input.imeSetComposition', { text: t, selectionStart: t.length, selectionEnd: t.length }); await page.waitForTimeout(120) }
      await cdp.send('Input.insertText', { text: done }); await page.waitForTimeout(120)
    }
    await page.waitForTimeout(900)
    ok('한글 조합 입력이 풀어지지 않음(설산)', (await page.inputValue('[data-testid=search-input]')) === '설산' && page.url().includes('q=%EC%84%A4%EC%82%B0'))
  }
  await page.fill('[data-testid=search-input]', '산소캔')
  ok('검색: 산소캔 → 체크리스트·일정', (await page.locator('[data-testid=search-hit]').count()) >= 2 && await page.isVisible('[data-testid=search-kinds] >> text=체크리스트'))
  await page.screenshot({ path: `${out}/w38_search.png` })
  await page.fill('[data-testid=search-input]', '숫자카드')
  ok('기능 검색: 숫자카드 → 맨 위 기능', /기능/.test(await page.locator('[data-testid=search-hit] >> nth=0').innerText()))
  await page.click('[data-testid=search-hit] >> nth=0'); await page.waitForSelector('text=一百五十块', { timeout: 5000 }).catch(() => {})
  ok('기능 검색 결과 → 숫자 카드 화면', page.url().includes('#/number'))
  await page.goBack(); await page.waitForSelector('[data-testid=search-input]', { timeout: 5000 }).catch(() => {})
  await page.fill('[data-testid=search-input]', '龙华')
  await page.click('[data-testid=search-hit] >> nth=0'); await page.waitForSelector('text=龙华茶楼(顺城店)', { timeout: 5000 }).catch(() => {})
  ok('검색 결과 누르면 그 화면(중국어 검색 → 용화찻집)', page.url().includes('/eats/longhua'))
  await page.goBack(); await page.waitForSelector('[data-testid=search-input]', { timeout: 5000 }).catch(() => {})
  ok('뒤로 오면 검색어 유지', (await page.inputValue('[data-testid=search-input]')) === '龙华')

  // 쿤밍 딤섬: 용화찻집 — 경로 버튼(호텔→찻집)
  await page.goto(base + '#/eats/longhua'); await page.waitForSelector('text=龙华茶楼(顺城店)', { timeout: 5000 }).catch(() => {})
  ok('용화찻집 식당·경로 버튼 2개', (await page.locator('[data-testid=eat-route]').count()) === 2 && ((await page.getAttribute('[data-testid=eat-route] >> nth=1', 'href')) || '').includes('amap.com/ssr/embed/dir') && await page.isVisible('text=평점·여행자 인기 메뉴 (고덕지도)'))
  await page.screenshot({ path: `${out}/w37_longhua.png` })

  // 위챗 친구 추가·QR 주고받기 템플릿 + 일정 연결
  await page.goto(base + '#/templates?id=tpl-wechat-qr'); await page.waitForSelector('[data-testid=template-line]', { timeout: 5000 }).catch(() => {})
  ok('위챗 QR 주고받기 8문장·각각 복사', (await page.locator('[data-testid=template-line]').count()) === 8 && await page.isVisible('text=请扫我的二维码。') && await page.isVisible('text=내 QR 보여 줄 때'))
  await page.screenshot({ path: `${out}/w39_wechat_qr.png`, fullPage: true })
  await page.goto(base + '#/schedule?day=D5'); await page.waitForSelector('text=위챗: 빵차 기사 위챗 QR 주고받기', { timeout: 5000 }).catch(() => {})
  ok('D5 일정 칩 → 위챗 QR 주고받기', await page.isVisible('text=위챗: 빵차 기사 위챗 QR 주고받기'))

  // 체크리스트: 일정 맨 아래 다음날 준비, 사전 일정 체크·출국 당일, 입국신고 QR 화면
  await page.goto(base + '#/schedule?day=D2'); await page.waitForSelector('[data-testid=nextday-check]', { timeout: 5000 }).catch(() => {})
  ok('D2 맨 아래 내일 옥룡설산 준비(산소캔)', await page.isVisible('text=내일 옥룡설산 투어(4,506m)') && await page.isVisible('text=산소캔 1인 1~2개 구매'))
  await page.click('[data-testid=nextday-check] [data-testid=check-item] >> nth=0')
  await page.reload(); await page.waitForSelector('[data-testid=nextday-check]', { timeout: 5000 }).catch(() => {})
  ok('체크 저장(새로고침 후 유지)', await page.isChecked('[data-testid=nextday-check] input[type=checkbox] >> nth=0'))
  await page.screenshot({ path: `${out}/w35_nextday_check.png`, fullPage: true })
  await page.goto(base + '#/schedule?day=pre'); await page.waitForSelector('[data-testid=pretrip]', { timeout: 5000 }).catch(() => {})
  ok('사전 일정 5개·체크리스트·출국 당일', (await page.locator('[data-testid=pretrip]').count()) === 5 && (await page.locator('[data-testid=pretrip-check]').count()) >= 4 && await page.isVisible('[data-testid=departure-check]') && await page.isVisible('text=eSIM 구매'))
  await page.click('text=방법·붙여 넣을 값'); await page.waitForSelector('[data-testid=arrival-step]', { timeout: 5000 }).catch(() => {})
  ok('입국신고 QR 화면: 순서 7단계·값 복사', (await page.locator('[data-testid=arrival-step]').count()) === 7 && await page.isVisible('text=云南省昆明市官渡区永平路7号 格菲酒店(昆明火车站北广场店)') && (await page.locator('[data-testid=arrival-field] button:has-text("복사")').count()) >= 8)
  await page.fill('[data-testid=arrival-field] input >> nth=0', 'ab 123')
  ok('항공편 입력(대문자·공백 제거)', (await page.inputValue('[data-testid=arrival-field] input >> nth=0')) === 'AB123')
  await page.screenshot({ path: `${out}/w36_arrival_card.png`, fullPage: true })

  // 템플릿 날짜·시각 숫자 칸 + 배달 요청사항(문장별 복사)
  await page.goto(base + '#/templates?link=tl-06'); await page.waitForSelector('[data-testid=dt-date]', { timeout: 5000 }).catch(() => {})
  ok('템플릿 날짜·시각 숫자 칸 4개', (await page.locator('[data-testid=dt-date] input').count()) === 4 && await page.isVisible('text=12月21日（周一）上午10点半'))
  await page.fill('[aria-label="출발 날짜·시각 시"]', '9'); await page.fill('[aria-label="출발 날짜·시각 분"]', '0')
  ok('숫자 바꾸면 문장 바뀜(上午9点)', await page.isVisible('text=12月21日（周一）上午9点，从') && await page.isVisible('text=12월 21일(월) 오전 9시'))
  await page.goto(base + '#/templates?id=tpl-delivery-note'); await page.waitForSelector('[data-testid=template-line]', { timeout: 5000 }).catch(() => {})
  ok('배달 요청사항 2문장·각각 복사', (await page.locator('[data-testid=template-line]').count()) === 2 && (await page.locator('[data-testid=template-line] button:has-text("복사")').count()) === 2 && await page.isVisible('text=直接让机器人送上来就行'))
  await page.screenshot({ path: `${out}/w34_delivery_note.png` })

  // 중국어 아래 한국어 번역
  await page.goto(base + '#/stay?tab=lodging'); await page.waitForSelector('text=물라뉴 탕수 호텔')
  ok('숙소 주소 아래 한국어 번역', (await page.locator('[data-testid=ko-gloss]').count()) >= 5 && await page.isVisible('text=윈난성 디칭 티베트족 자치주 샹그릴라시 젠탕진 동왕로 77-83호 (구산골목 3호)'))
  await page.goto(base + '#/taxi/lodging/oura'); await page.waitForSelector('text=东旺路77-83号', { timeout: 5000 }).catch(() => {})
  ok('택시 카드 주소 한국어 번역', (await page.locator('[data-testid=ko-gloss]').count()) === 1)
  await page.goto(base + '#/number'); await page.waitForSelector('text=一百五十块')
  ok('숫자 카드 위안 표시', await page.isVisible('text=150위안 괜찮아요?'))
  await page.goto(base + '#/emergency'); await page.waitForSelector('[data-testid=hospital]', { timeout: 5000 }).catch(() => {})
  ok('병원 주소 한국어 번역', (await page.locator('[data-testid=hospital] [data-testid=ko-gloss]').count()) >= 1)
  await page.screenshot({ path: `${out}/w33_ko_gloss_info.png` })

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
  // 구글 드라이브 백업(가짜 Apps Script 주소로 확인): 설정 → 연결 확인 → 문서함 한꺼번에 올리기
  {
    const posts = []
    const fakeDrive = async (route) => {
      const req = route.request()
      if (req.method() === 'POST') { const b = JSON.parse(req.postData() || '{}'); posts.push(b); return route.fulfill({ contentType: 'application/json', body: JSON.stringify(b.token === 'k-test' ? { ok: true, id: 'x' } : { ok: false, error: 'token' }) }) }
      const sp = new URL(req.url()).searchParams
      if (sp.get('token') !== 'k-test') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ok: false, error: 'token' }) })
      const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
      if (sp.get('action') === 'list') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ok: true, files: [
        ...posts.map((p, i) => ({ id: `up${i}`, name: p.name, mime: p.mime, size: 10, sub: p.sub, memo: p.memo, created: 1 })),
        { id: 'app1', name: 'D5_차마객잔 빵차 영수증_app77.png', mime: 'image/png', size: 70, sub: 'D5', memo: '영수증 · ¥80 위챗페이', created: 2 }] }) })
      if (sp.get('action') === 'get') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ok: true, name: 'D5_차마객잔 빵차 영수증_app77.png', mime: 'image/png', memo: '영수증 · ¥80 위챗페이', data: png }) })
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ok: true, folder: '윈난 여행 앱 백업' }) })
    }
    await page.route('https://script.google.com/**', fakeDrive)
    ok('문서함: 드라이브 설정 안내 링크 또는(기본값 있으면) 백업 줄', await page.isVisible('[data-testid=drive-setup-link]') || await page.isVisible('[data-testid=drive-bar]'))
    await page.goto(base + '#/settings'); await page.waitForSelector('[data-testid=drive-settings]', { timeout: 5000 }).catch(() => {})
    await page.fill('[data-testid=drive-settings] input >> nth=0', 'https://script.google.com/macros/s/TEST/exec')
    await page.fill('[data-testid=drive-settings] input >> nth=1', 'k-test')
    await page.click('[data-testid=drive-settings] >> text=저장·연결 확인')
    ok('드라이브 연결 확인(폴더 이름)', await page.waitForSelector('text=연결됨 · 폴더 「윈난 여행 앱 백업」', { timeout: 5000 }).then(() => true).catch(() => false))
    await page.goto(base + '#/docs'); await page.waitForSelector('[data-testid=drive-bar]', { timeout: 5000 }).catch(() => {})
    await page.click('[data-testid=drive-bar] button')
    await page.waitForSelector('text=모두 올렸다', { timeout: 10000 }).catch(() => {})
    ok('문서함 → 드라이브 2건 업로드(폴더·일차·이름)', posts.length === 2 && posts.every((p) => p.folder === '문서함' && p.token === 'k-test' && p.data.length > 100 && /^(D\d|공통)_/.test(p.name)))
    // 드라이브에서 지운 뒤 다시 올리기: 이미 올린 것도 다시 보낸다(드라이브 쪽은 같은 이름이면 건너뜀)
    await page.click('[data-testid=drive-reupload]')
    for (let i = 0; i < 60 && posts.length < 4; i++) await page.waitForTimeout(250)
    ok('모두 올렸다 · 다시 올리기 → 2건 다시 보냄', posts.length === 4 && posts.slice(2).every((p) => p.folder === '문서함'))
    await page.click('[data-testid=doc-row] >> nth=0'); await page.waitForSelector('[data-testid=drive-doc]', { timeout: 5000 }).catch(() => {})
    ok('문서 화면: 드라이브에 올렸다 표시', await page.isVisible('text=드라이브에 올렸다'))
    // 드라이브에서 가져오기: 이 기기가 올린 2건은 빼고, 앱이 올린 1건만 가져온다(제목·일차·분류·메모 복원)
    await page.goto(base + '#/docs'); await page.waitForSelector('[data-testid=drive-pull]', { timeout: 5000 }).catch(() => {})
    await page.click('[data-testid=drive-pull]')
    await page.waitForSelector('[data-testid=doc-row]:has-text("차마객잔 빵차 영수증")', { timeout: 10000 }).catch(() => {})
    const pulled = page.locator('[data-testid=doc-row]:has-text("차마객잔 빵차 영수증")')
    ok('드라이브에서 가져오기(앱 문서 1건만, 제목·D5·영수증)', (await page.locator('[data-testid=doc-row]').count()) === 3 && (await pulled.count()) === 1 && /D5/.test(await pulled.innerText()) && /영수증/.test(await pulled.innerText()))
    await page.click('[data-testid=drive-pull]')
    ok('다시 가져오면 새 문서 없음', await page.waitForSelector('text=가져올 새 문서가 없다', { timeout: 5000 }).then(() => true).catch(() => false))
    // 여러 건 지우기: 선택 → 가져온 문서 고르기 → N건 삭제(확인) → 다시 가져와도 되살아나지 않음
    await page.click('[data-testid=doc-select-bar] >> text=선택')
    await pulled.click()
    ok('선택 모드: 누르면 체크(화면 이동 없음)', page.url().endsWith('#/docs') && (await page.locator('[data-testid=doc-pick]:checked').count()) === 1)
    await page.click('[data-testid=doc-select-bar] >> text=1건 삭제')
    await page.waitForSelector('text=1건을 지웠다', { timeout: 5000 }).catch(() => {})
    ok('여러 건 지우기 → 2건 남음', (await page.locator('[data-testid=doc-row]').count()) === 2 && (await pulled.count()) === 0)
    await page.click('[data-testid=drive-pull]')
    ok('지운 문서는 드라이브에서 다시 가져오지 않음', await page.waitForSelector('text=가져올 새 문서가 없다', { timeout: 5000 }).then(() => true).catch(() => false))
    await page.unroute('https://script.google.com/**', fakeDrive)
    await page.evaluate(() => { localStorage.removeItem('yn.drive'); localStorage.removeItem('yn.drive.done'); localStorage.removeItem('yn.drive.deleted'); localStorage.removeItem('yn.drive.imported') })
    await page.goto(base + '#/docs'); await page.waitForSelector('[data-testid=doc-row]', { timeout: 5000 }).catch(() => {})
  }
  const docHref = await page.locator('[data-testid=doc-row]:has-text("옥룡설산")').getAttribute('href')

  // 블로그 후기: 관광지 화면 → PDF 넣기 → 문서함(블로그) → 다시 오면 PDF 보기
  await page.goto(base + '#/sights/yulong'); await page.waitForSelector('[data-testid=blog-card]')
  ok('관광지 블로그 후기 카드·원문 링크', (await page.locator('[data-testid=blog-card]').count()) === 1 && ((await page.getAttribute('[data-testid=blog-card] a:has-text("원문 열기")', 'href')) || '').startsWith('https://blog.naver.com/'))
  await page.setInputFiles('[data-testid=blog-add] input', `${fx}/sample_voucher_1218.pdf`)
  ok('블로그 PDF 넣기 → 문서 화면', await page.waitForURL(/#\/docs\//, { timeout: 30000 }).then(() => true).catch(() => false))
  await page.goto(base + '#/sights/yulong')
  ok('다시 오면 PDF 보기', await page.waitForSelector('[data-testid=blog-pdf]', { timeout: 10000 }).then(() => true).catch(() => false))
  await page.screenshot({ path: `${out}/w27_sight_blog.png`, fullPage: true })

  // 음식 사진: zip 넣기 → 3열 격자·이름 → 크게 보기
  await page.goto(base + '#/stay?tab=photos'); await page.waitForSelector('[data-testid=photo-add]')
  await page.setInputFiles('[data-testid=photo-add] input', `${fx}/food_photos_sample.zip`)
  ok('음식 사진 zip 넣기(3장, __MACOSX 제외)', await page.waitForFunction(() => document.querySelectorAll('[data-testid=photo-cell]').length === 3, null, { timeout: 20000 }).then(() => true).catch(() => false))
  ok('음식 사진 이름 표시(동솥밥·양배추)', await page.isVisible('[data-testid=photo-cell]:has-text("동솥밥")') && await page.isVisible('[data-testid=photo-cell]:has-text("양배추")'))
  ok('음식 사진 1줄 3장', await page.evaluate(() => getComputedStyle(document.querySelector('[data-testid=photo-grid]')).gridTemplateColumns.split(' ').length === 3))
  await page.click('[data-testid=photo-cell] >> nth=0')
  ok('누르면 크게 보기(이름·중국어)', await page.waitForSelector('[data-testid=photo-viewer] img', { timeout: 10000 }).then(() => true).catch(() => false) && await page.isVisible('[data-testid=photo-viewer] >> text=铜锅饭'))
  await page.screenshot({ path: `${out}/w28_food_photo_view.png` })
  await page.click('[aria-label=닫기]')
  await page.click('[data-testid=photo-filter] >> text=이름 모름')
  ok('이름 모름 거르기(1장)', (await page.locator('[data-testid=photo-cell]').count()) === 1)
  await page.click('[data-testid=photo-filter] >> text=전체')
  await page.setInputFiles('[data-testid=photo-add] input', `${fx}/food_photos_sample.docx`)
  ok('음식별 사진 워드(docx) 넣기(+3장)', await page.waitForFunction(() => document.querySelectorAll('[data-testid=photo-cell]').length === 6, null, { timeout: 20000 }).then(() => true).catch(() => false))
  ok('워드 사진 이름 = 음식 도감(납배골 훠궈)', await page.isVisible('[data-testid=photo-cell]:has-text("납배골 훠궈")'))
  await page.goto(base + '#/stay?tab=foods'); await page.waitForSelector('.card.food')
  const lapai = page.locator('.card.food:has-text("腊排骨火锅")').first()
  await lapai.locator('[data-testid=food-more-photos]').waitFor({ timeout: 10000 }).catch(() => {})
  ok('음식 도감 사진 없던 곳 → 기기 사진', await lapai.locator('img.ph').count() === 1 && (await lapai.textContent()).includes('사진 2장 보기'))
  await lapai.locator('[data-testid=food-more-photos]').click()
  ok('음식별 사진 모아 보기(2장)', await page.waitForFunction(() => document.querySelectorAll('[data-testid=photo-cell]').length === 2, null, { timeout: 10000 }).then(() => true).catch(() => false))
  await page.screenshot({ path: `${out}/w29_food_guide_photo.png` })

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
  console.log(`실제 드라이브로 가려던 요청 ${blockedDrive}건은 막았다(시험이 드라이브를 건드리지 않음)`)
  console.log(`\n${results.length - fail} 통과 / ${fail} 실패`)
  process.exit(fail ? 1 : 0)
})().catch((e) => { console.error(e); process.exit(2) })
