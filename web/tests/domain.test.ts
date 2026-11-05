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
  it('D3 05:50 투어 픽업', () => { const s = nowAt('2026-12-18T05:50'); expect(s.current?.timeStart).toBe('05:45'); expect(s.next?.transport).toContain('투어 차량') })
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
  it('캘린더 파일 13건', () => {
    const ics = buildIcs(data.notifications)
    expect(ics.match(/BEGIN:VEVENT/g)).toHaveLength(data.notifications.length)
    expect(data.notifications.length).toBe(13)
    expect(ics).toContain('DTSTART;TZID=Asia/Shanghai:20261217T063000')
  })
})
