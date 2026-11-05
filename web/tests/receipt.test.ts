import { describe, expect, it } from 'vitest'
import { guessCategory, normalizeOcr, parseReceipt } from '../src/domain/receipt'
import type { Day } from '../src/domain/types'

const days = [{ id: 'D2', date: '2026-12-17' }, { id: 'D4', date: '2026-12-19' }] as Day[]

describe('영수증 해석', () => {
  it('위챗페이 결제 완료 화면', () => {
    const r = parseReceipt('支付成功\n¥128.00\n收款方 田村酸汤牦牛肉火锅\n支付时间 2026年12月17日 14:32:05\n交易单号 4200001234567890', days)
    expect(r.amount).toBe(128); expect(r.merchant).toContain('田村'); expect(r.date).toBe('2026-12-17')
    expect(r.time).toBe('14:32'); expect(r.dayId).toBe('D2'); expect(r.category).toBe('식비')
  })
  it('알리페이 거래 성공(음수 표기)', () => {
    const r = parseReceipt('交易成功\n-45.00\n商品说明 丁丁当当酸奶\n收款方全称 丽江古城丁丁当当\n创建时间 2026-12-19 09:41', days)
    expect(r.amount).toBe(45); expect(r.dayId).toBe('D4')
  })
  it('종이 영수증: 실수령 우선, 소계·수량·전화번호 무시', () => {
    const r = parseReceipt('云雪丽现炒云南菜(大水车店)\n电话 0888-5123456\n松露粥 1 38.00\n炸茄盒 2 56.00\n小计 236.00\n优惠 -10.00\n实收 226.00\n2026/12/17 19:20', days)
    expect(r.amount).toBe(226); expect(r.merchant).toContain('云雪丽'); expect(r.time).toBe('19:20'); expect(r.category).toBe('식비')
  })
  it('OCR 잡음: 전각 숫자·한자 사이 공백·쉼표 소수점', () => {
    expect(normalizeOcr('合 计 ：１２８，００')).toBe('合计:128.00')
    expect(parseReceipt('合 计 ：１２８，００').amount).toBe(128)
  })
  it('디디 택시', () => {
    const r = parseReceipt('滴滴出行\n行程费用\n¥ 32.50\n2026-12-17 12:58')
    expect(r.amount).toBe(32.5); expect(r.category).toBe('교통·투어·입장료')
  })
  it('¥ 를 半 으로 잘못 읽은 경우', () => expect(parseReceipt('行程 费用\n半 32.50').amount).toBe(32.5))
  it('연도·단독 정수는 금액이 아니다', () => expect(parseReceipt('2026\n3\n合计 88.00').amount).toBe(88))
  it('구분 추정', () => {
    expect(guessCategory('丽江沐蘭遇の汤宿 房费')).toBe('숙박')
    expect(guessCategory('玉龙雪山 索道 门票')).toBe('교통·투어·입장료')
    expect(guessCategory('鲜花饼 礼盒')).toBe('쇼핑·기념품·예비비')
  })
  it('금액이 없으면 null', () => expect(parseReceipt('谢谢光临\n欢迎再来').amount).toBeNull())
})
