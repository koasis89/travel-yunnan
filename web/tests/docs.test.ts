import { describe, expect, it } from 'vitest'
import { baseTitle, filterDocs, guessDay, guessKind, sortDocs, type DocMeta } from '../src/domain/docs'
import type { Day } from '../src/domain/types'

const days = ['16', '17', '18', '19', '20', '21', '22', '23'].map((d, i) => ({ id: `D${i + 1}`, date: `2026-12-${d}` })) as unknown as Day[]
const doc = (o: Partial<DocMeta>): DocMeta => ({ id: Math.random().toString(), title: 't', kind: '기타', dayId: null, note: '', mime: 'image/png', fileName: 'a.png', size: 1, pages: 0, created: 0, thumb: '', ...o })

describe('문서 분류 추정', () => {
  it('파일 이름으로 분류', () => {
    expect(guessKind('C446_trip.com.pdf')).toBe('기차표')
    expect(guessKind('Klook_voucher_yulong.pdf')).toBe('바우처')
    expect(guessKind('옥룡설산 투어 바우처.png')).toBe('바우처')
    expect(guessKind('agoda_booking_confirmation.pdf')).toBe('숙소 예약')
    expect(guessKind('微信支付 小票.jpg')).toBe('영수증')
    expect(guessKind('여권 사본.jpg')).toBe('보험·신분')
    expect(guessKind('Screenshot_20261105.png')).toBe('기타')
  })
  it('열차 번호·날짜로 일차', () => {
    expect(guessDay('C446.pdf', days)).toBe('D2')
    expect(guessDay('ticket_C644', days)).toBe('D7')
    expect(guessDay('tour 12-18.png', days)).toBe('D3')
    expect(guessDay('12月20日 车票', days)).toBe('D5')
    expect(guessDay('voucher_1219.pdf', days)).toBe('D4')
    expect(guessDay('Screenshot_20261105_1230.png', days)).toBe(null)
    expect(guessDay('photo.jpg', days)).toBe(null)
  })
  it('제목은 확장자 뺀 파일 이름', () => {
    expect(baseTitle('Klook_voucher.pdf')).toBe('Klook voucher')
    expect(baseTitle('.png')).toBe('문서')
  })
})

describe('정렬·거르기', () => {
  it('오늘 문서가 먼저, 그다음 일차 순, 일차 없음은 끝', () => {
    const l = [doc({ id: 'x', dayId: null }), doc({ id: 'd5', dayId: 'D5' }), doc({ id: 'd2', dayId: 'D2' }), doc({ id: 'd3', dayId: 'D3' })]
    expect(sortDocs(l, 'D3').map((d) => d.id)).toEqual(['d3', 'd2', 'd5', 'x'])
    expect(sortDocs(l, null).map((d) => d.id)).toEqual(['d2', 'd3', 'd5', 'x'])
  })
  it('분류·일차·검색어', () => {
    const l = [doc({ id: 'a', kind: '바우처', dayId: 'D3', title: '옥룡설산' }), doc({ id: 'b', kind: '기차표', dayId: 'D2', note: 'C446 7호차' })]
    expect(filterDocs(l, '바우처', null).map((d) => d.id)).toEqual(['a'])
    expect(filterDocs(l, null, 'D2').map((d) => d.id)).toEqual(['b'])
    expect(filterDocs(l, null, null, '7호차').map((d) => d.id)).toEqual(['b'])
  })
})
