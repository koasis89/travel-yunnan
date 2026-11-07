import { readFileSync, readdirSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { TripData } from '../src/domain/types'
import { findNow, focusDay, formatLeft, nextMove, resolvePhase, tripNow } from '../src/domain/tripTime'
import { buildTaxiCard, cleanZhAddress, mealFor, messagePhrase, scheduleLinks } from '../src/domain/cards'
import { composeOrder, templateById } from '../src/domain/templates'
import { readNumber, readYuan } from '../src/domain/chineseNumber'
import { buildIcs } from '../src/domain/ics'
import { expensesCsv, budgetKey } from '../src/domain/expense'

const dir = new URL('../../data/web/', import.meta.url)
const bundle = Object.fromEntries(readdirSync(dir).map((f) => [f, JSON.parse(readFileSync(new URL(f, dir), 'utf8'))]))
const data = new TripData(bundle)
const rows = (id: string) => data.scheduleOf(id)
const nowAt = (s: string) => { const p = resolvePhase(s, data.days); if (p.kind !== 'on') throw new Error('not on'); return findNow(rows(p.day.id), s.slice(11)) }

describe('일차·현재 일정 (앱 DomainTest 와 동일)', () => {
  it('출발 전 D-41', () => {
    const p = resolvePhase('2026-11-05T10:00', data.days)
    expect(p).toEqual({ kind: 'before', daysLeft: 41 })
    expect(focusDay(p, data.days).id).toBe('D1')
  })
  it('D1 22:30 공항 도착', () => { const s = nowAt('2026-12-16T22:30'); expect(s.current?.id).toBe('D1-01'); expect(s.next?.id).toBe('D1-02') })
  it('D3 05:50 투어 픽업', () => { const s = nowAt('2026-12-18T05:50'); expect(s.current?.timeStart).toBe('05:00'); expect(s.next?.transport).toContain('투어 차량') })
  it('D5 12:30 트레킹', () => { const s = nowAt('2026-12-20T12:30'); expect(s.current?.from).toBe('차마객잔'); expect(s.current?.to).toContain('중도객잔') })
  it('D8 19:45 공항 이동', () => {
    expect(nowAt('2026-12-23T19:45').current?.timeStart).toBe('19:30')
    const m = nextMove('2026-12-23T19:00', data.days, rows)
    expect(m?.row.timeStart).toBe('19:30'); expect(m?.minutesLeft).toBe(30)
  })
  it('귀국 후', () => { const p = resolvePhase('2026-12-24T09:00', data.days); expect(p.kind).toBe('after'); expect(focusDay(p, data.days).id).toBe('D8') })
  it('다음 이동 C446·C644', () => {
    const m = nextMove('2026-12-17T06:00', data.days, rows)!
    expect(m.row.transport).toContain('C446'); expect(m.minutesLeft).toBe(149)
    const d7 = nextMove('2026-12-22T05:00', data.days, rows)!
    expect(d7.row.transport).toContain('C644'); expect(d7.row.timeStart).toBe('07:22')
  })
  it('종료 시각 없는 행은 다음 행 전까지', () => expect(nowAt('2026-12-16T23:55').current?.timeStart).toBe('23:40'))
  it('남은 시간 표기', () => { expect(formatLeft(30)).toBe('30분 후'); expect(formatLeft(149)).toBe('2시간 29분 후'); expect(formatLeft(1500)).toBe('1일 1시간 후') })
  it('현지 시각 = 한국 -1시간', () => expect(tripNow(null, new Date('2026-12-20T03:30:00Z'))).toBe('2026-12-20T11:30'))
})

describe('택시 카드·링크', () => {
  it('주소 정리', () => {
    expect(cleanZhAddress('云雪丽(大水车店) 검색')).toBe('云雪丽(大水车店)')
    expect(cleanZhAddress('昆明市官渡区塘子巷 (铁朋小锅米线 검색)')).toBe('昆明市官渡区塘子巷 (铁朋小锅米线)')
  })
  it('숙소·식당 카드', () => {
    const c = buildTaxiCard(data, 'lodging', 'mulanyu')!
    expect(c.nameZh).toBe('丽江沐蘭遇の汤宿'); expect(c.helpers.map((h) => h.id)).toContain('px-003')
    const lunch = mealFor(data, 'D2', 13)!
    expect(lunch.name).toContain('田村')
    expect(buildTaxiCard(data, 'meal', lunch.id)!.addrZh.startsWith('丽江市古城区')).toBe(true)
  })
  it('일정 링크·장문', () => {
    const arrival = rows('D2').find((r) => r.to.includes('물라뉴'))!
    expect(scheduleLinks(data, arrival).some((l) => l.type === 'lodging' && l.id === 'mulanyu')).toBe(true)
    expect(messagePhrase(data, data.schedule.find((r) => r.message?.includes('虎跳峡徒步'))!)?.id).toBe('msg-001')
    expect(messagePhrase(data, data.schedule.find((r) => r.message?.includes('香格里拉的包车'))!)?.id).toBe('msg-002')
  })
  it('쿤밍 두 번째 숙박은 D7 예약으로 연결', () => {
    const r = rows('D7').find((x) => x.to.includes('Gefei'))!
    expect(scheduleLinks(data, r).find((l) => l.type === 'lodging')?.id).toBe('kunming2')
  })
})

describe('템플릿·숫자·경비·캘린더', () => {
  it('일정 배치 템플릿', () => {
    const d5 = rows('D5').find((r) => r.timeStart === '15:15')!
    const link = data.templatesFor(d5)[0]
    expect(templateById(link.templateId)!.render(link.values)).toContain('上午10点半')
    const d1 = rows('D1').find((r) => r.timeStart === '23:40')!
    expect(new Set(data.templatesFor(d1).map((t) => t.templateId))).toEqual(new Set(['tpl-pickup', 'tpl-delivery']))
  })
  it('주문 문장', () => {
    expect(composeOrder('过桥米线', '一份', ['不要辣', '不要香菜'])).toBe('我要一份过桥米线，不要辣，不要香菜。')
    expect(composeOrder(null, '一个', [])).toBe('这个要一个。')
  })
  it('중국어 숫자', () => {
    const cases: [number, string][] = [[0, '零'], [8, '八'], [10, '十'], [15, '十五'], [20, '二十'], [45, '四十五'], [100, '一百'], [105, '一百零五'],
      [150, '一百五十'], [200, '两百'], [1050, '一千零五十'], [2300, '两千三百'], [12000, '一万两千'], [99999, '九万九千九百九十九']]
    for (const [n, s] of cases) expect(readNumber(n)).toBe(s)
    expect(readYuan(150.5)).toBe('一百五十块五毛')
  })
  it('경비 CSV', () => {
    const csv = expensesCsv([{ id: 1, dayId: 'D2', amount: 100, currency: 'CNY', category: '식비', memo: 'a,b', method: '위챗', payer: '공동', at: 1766000000000 }], 200)
    expect(csv.split('\n')).toHaveLength(2); expect(csv).toContain('20000'); expect(csv).toContain('"a,b"')
    expect(budgetKey('식비(맛집·식사 계획)')).toBe('식비')
  })
  it('캘린더 파일 15건(가이드 연락·D7 저녁 포함)', () => {
    const ics = buildIcs(data.notifications)
    expect(ics.match(/BEGIN:VEVENT/g)).toHaveLength(data.notifications.length)
    expect(data.notifications.length).toBe(15)
    expect(ics).toContain('DTSTART;TZID=Asia/Shanghai:20261217T063000')
  })
})

import { looksLikeKrw } from '../src/domain/expense'
describe('경비: 원화를 위안으로 잘못 고른 기록', () => {
  const e = (amount: number, currency: 'CNY' | 'KRW') => ({ id: 1, dayId: 'D1', amount, currency, category: '식비', memo: '', method: '현금', payer: '공동', at: 0 })
  it('¥5,000 이상만 의심', () => {
    expect(looksLikeKrw(e(55000, 'CNY'))).toBe(true)
    expect(looksLikeKrw(e(4999, 'CNY'))).toBe(false)
    expect(looksLikeKrw(e(55000, 'KRW'))).toBe(false)
  })
})

describe('관광지', () => {
  it('23곳, 일정 행 연결, 사진 출처', () => {
    expect(data.sights.length).toBeGreaterThanOrEqual(20)
    const r = data.scheduleOf('D6').find((x) => x.to.includes('송찬림사'))!
    expect(data.sightsFor(r).map((s) => s.id)).toContain('songzanlin')
    expect(data.sights.filter((s) => s.image).every((s) => s.credit?.license)).toBe(true)
    expect(data.sightsOf('D3').map((s) => s.id)).toEqual(expect.arrayContaining(['glacier', 'lanyuegu', 'impression']))
  })
})

import { amapRows, amapText } from '../src/domain/cards'
describe('고덕지도 링크', () => {
  it('숙소 전부·공유 링크 우선·붙여넣기 형식', () => {
    expect(data.lodgings.filter((l) => l.nameZh).every((l) => l.amapUrl?.startsWith('https://'))).toBe(true)
    const rows = amapRows(data)
    expect(rows.find((r) => r.nameZh === '一瓦堂咖啡店')?.url).toBe('https://surl.amap.com/1ZGvMhI4fh')
    expect(amapText(rows[0])).toMatch(/^📍.+\n중국어 이름 : .+\n.*고덕지도 주소 링크 : https:/s)
  })
  it('식당 이름은 모두 한국어로 보이고 중국어 이름은 따로 둔다', () => {
    const food = amapRows(data).filter((r) => r.group === '식당')
    expect(food.every((r) => /[가-힣]/.test(r.nameKo.split(' · ')[1]))).toBe(true)
    const tian = food.find((r) => r.nameZh === '田村·酸汤牦牛肉火锅(古城大水车店)')!
    expect(tian.nameKo).toBe('D2 점심 · 톈춘 산탕 야크고기 훠궈(고성 대수차점)')
    expect(food.find((r) => r.nameKo.includes('리장 바바'))?.nameZh).toBe('丽江粑粑')
    expect(amapText(food.find((r) => !r.nameZh)!)).not.toContain('중국어 이름')
  })
})

import { DRINKS, composeFullOrder } from '../src/domain/drinks'
describe('주문 문장: 음료', () => {
  const d = (zh: string) => DRINKS.find((x) => x.zh === zh)!
  it('음식+음료+요청, 음료만', () => {
    expect(composeFullOrder('过桥米线', '一份', [[d('冰啤酒'), 2], [d('矿泉水'), 1]], ['不要辣'])).toBe('我要一份过桥米线，再要两瓶冰啤酒、一瓶矿泉水，不要辣。')
    expect(composeFullOrder(null, '一份', [[d('云南小粒咖啡'), 3]], [])).toBe('我要三杯云南小粒咖啡。')
    expect(composeFullOrder('', '一份', [], [])).toBe('这个要一份。')
    expect(composeFullOrder(null, '一份', [[d('鹤庆乾酒'), 1]], ['请帮忙开瓶'])).toBe('我要一小瓶鹤庆乾酒，请帮忙开瓶。')
  })
})

describe('옥룡설산 가이드 위챗', () => {
  it('일정 연결과 문장', () => {
    const d3 = data.scheduleOf('D3').find((r) => r.timeStart === '05:00')!
    expect(data.templatesFor(d3).map((t) => t.templateId).sort()).toEqual(['tpl-guide', 'tpl-guide-arrive', 'tpl-guide-late'])
    expect(templateById('tpl-guide-dropoff')!.render({})).toContain('古城大水车附近的停车场')
    expect(templateById('tpl-guide-confirm')!.contact?.wechat).toBe('18088479095')
  })
})

describe('맛집 안내', () => {
  it('식사 선택지·일정 행 연결', () => {
    expect(data.restaurants.length).toBeGreaterThanOrEqual(20)
    expect(data.restaurantOfOption('D7-03-1')?.id).toBe('jianxinyuan')
    const r = data.scheduleOf('D3').find((x) => x.to.includes('천천선'))!
    expect(data.restaurantsFor(r).map((x) => x.id)).toEqual(expect.arrayContaining(['tiantianxian', 'apo']))
    expect(data.restaurants.every((x) => x.amapUrl && x.menu.length > 0)).toBe(true)
  })
})

import { composeMultiOrder, decodeFoods, encodeFoods } from '../src/domain/drinks'
describe('주문 문장: 여러 음식', () => {
  it('여러 음식·수량·음료, 쿼리 왕복', () => {
    const beer = DRINKS.find((x) => x.zh === '冰啤酒')!
    expect(composeMultiOrder([['汽锅鸡', 1], ['三七汽锅鸡', 2]], '一份', [[beer, 3]], ['不要辣'])).toBe('我要一份汽锅鸡、两份三七汽锅鸡，再要三瓶冰啤酒，不要辣。')
    expect(composeMultiOrder([], '两份', [], [])).toBe('这个要两份。')
    expect(composeMultiOrder([], null, [], [])).toBe('你好，我要点菜。')
    expect(decodeFoods(encodeFoods([['汽锅鸡', 1], ['野生菌', 2]]))).toEqual([['汽锅鸡', 1], ['野生菌', 2]])
  })
})

describe('음식 도감 기기 사진', () => {
  it('워드 사진만 음식에 연결하고, 샹그릴라 야크 훠궈는 야크 훠궈 사진을 함께 쓴다', () => {
    const have = ['photo_005', 'docx-01-01', 'docx-02-01', 'docx-12-03']
    expect(data.photoIdsForFood('food-05', have)).toEqual(['docx-01-01'])
    expect(data.photoIdsForFood('food-23', have)).toEqual(['docx-02-01'])
    expect(data.photoIdsForFood('food-15', have)).toEqual([])
    expect(data.docxPhotoIds.has('docx-12-03')).toBe(false)
    expect(data.foodPhotoNames['docx-01-01'].nameZh).toBe('腊排骨火锅')
  })
})

import { parseExpensesCsv, splitCsvLine } from '../src/domain/expense'
describe('경비 CSV 가져오기', () => {
  const csv = '﻿일시,일차,금액,통화,원화환산,구분,결제수단,지불자,메모\n' +
    '2026-11-05 22:48,D3,350000,KRW,350000,교통·투어·입장료,위챗,공동,"투어, 클룩"\n' +
    '2026-12-17 12:05,D2,68,CNY,13600,식비,알리페이,아빠,"점심 ""과교미선"""\n' +
    '깨진 줄,,,\n'
  it('내보낸 형식을 읽고(중국 시간·따옴표·쉼표), 다시 넣으면 건너뛴다', () => {
    expect(splitCsvLine('a,"b, c","d ""e"""')).toEqual(['a', 'b, c', 'd "e"'])
    const r = parseExpensesCsv(csv, [], 1000)
    expect(r.added.length).toBe(2); expect(r.bad).toBe(1)
    expect(r.added[0]).toMatchObject({ dayId: 'D3', amount: 350000, currency: 'KRW', category: '교통·투어·입장료', method: '위챗', payer: '공동', memo: '투어, 클룩' })
    expect(new Date(r.added[1].at).toISOString()).toBe('2026-12-17T04:05:00.000Z')
    expect(r.added[1].memo).toBe('점심 "과교미선"')
    const again = parseExpensesCsv(csv, r.added)
    expect(again.added.length).toBe(0); expect(again.skipped).toBe(2)
    // 내보내기 → 가져오기 왕복
    const round = parseExpensesCsv(expensesCsv(r.added, 200), [])
    expect(round.added.map((e) => [e.amount, e.currency, e.at])).toEqual(r.added.map((e) => [e.amount, e.currency, e.at]).sort((a, b) => (a[2] as number) - (b[2] as number)))
  })
})

import { composeTabOrder, tabOf } from '../src/domain/drinks'
describe('주문 문장(탭·수량)', () => {
  const d = (zh: string) => DRINKS.find((x) => x.zh === zh)!
  it('음식·이거·음료·맥주·술을 수량대로 한 문장으로', () => {
    expect(composeTabOrder([['过桥米线', 2]], 1, [[d('冰啤酒'), 3]], ['不要辣'])).toBe('我要两份过桥米线，这个也要一份，再要三瓶冰啤酒，不要辣。')
    expect(composeTabOrder([], 2, [], [])).toBe('这个要两份。')
    expect(composeTabOrder([], 0, [[d('矿泉水'), 1], [d('鹤庆乾酒'), 1]], [])).toBe('我要一瓶矿泉水、一小瓶鹤庆乾酒。')
    expect(composeTabOrder([], 0, [], [])).toBe('你好，我要点菜。')
    expect(composeTabOrder([['汽锅鸡', 8]], 0, [], [])).toBe('我要8份汽锅鸡。')
    expect([tabOf(d('冰啤酒')), tabOf(d('鹤庆乾酒')), tabOf(d('普洱茶')), tabOf(d('冰美式'))]).toEqual(['beer', 'liquor', 'drink', 'drink'])
  })
})
