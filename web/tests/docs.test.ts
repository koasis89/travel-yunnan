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
    expect(guessKind('리장 옥룡설산 케이블카 후기 _ 네이버 블로그.pdf')).toBe('블로그')
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

import { docForBlog, type Blog } from '../src/domain/docs'
describe('블로그 PDF 찾기', () => {
  const b: Blog = { id: 'b1', title: '리장 옥룡설산 케이블카 후기 | 람월곡부터', blog: 'gill', date: '2025-12-29', url: 'https://blog.naver.com/x/1', sightIds: ['yulong'], summary: '', match: ['케이블카 후기'] }
  const doc = (o: Partial<DocMeta>): DocMeta => ({ id: 'd', title: '', kind: '블로그', dayId: null, note: '', mime: 'application/pdf', fileName: '', size: 1, pages: 1, created: 1, thumb: '', ...o })
  it('메모의 원문 주소 → 같은 제목 → 표시 낱말 순으로 찾는다', () => {
    expect(docForBlog(b, [doc({ id: 'a', note: 'gill\nhttps://blog.naver.com/x/1' })])?.id).toBe('a')
    expect(docForBlog(b, [doc({ id: 'c', fileName: '리장 옥룡설산 케이블카 후기 _ 네이버 블로그.pdf' })])?.id).toBe('c')
    expect(docForBlog(b, [doc({ id: 'e', fileName: '다른 글.pdf' })])).toBeUndefined()
  })
})

import { docxImages } from '../src/store/foodPhotos'
import { unzipSync } from 'fflate'
import { readFileSync } from 'node:fs'
describe('음식별 사진 워드(docx)', () => {
  it('번호 제목 아래 그림을 docx-묶음-순서 id 로 꺼낸다', () => {
    const all = unzipSync(new Uint8Array(readFileSync(new URL('./fixtures/food_photos_sample.docx', import.meta.url))))
    expect(docxImages(all)).toEqual([
      { id: 'docx-01-01', media: 'word/media/image1.png' },
      { id: 'docx-01-02', media: 'word/media/image2.png' },
      { id: 'docx-02-01', media: 'word/media/image3.png' },
    ])
  })
})
