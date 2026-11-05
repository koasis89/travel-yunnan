// 문서함: 바우처·기차표·영수증 등 여행 서류. 분류·일차 추정은 파일 이름 기준(순수 함수, 테스트 대상)
import type { Day } from './types'

export const DOC_KINDS = ['바우처', '기차표', '항공권', '숙소 예약', '영수증', '보험·신분', '기타'] as const
export type DocKind = (typeof DOC_KINDS)[number]

export interface DocMeta {
  id: string
  title: string
  kind: DocKind
  dayId: string | null
  note: string
  mime: string
  fileName: string
  size: number
  /** PDF 를 그림으로 펼친 쪽수(그림 파일이면 0) */
  pages: number
  created: number
  /** 목록용 작은 미리보기(data URL) */
  thumb: string
}

const RULES: [DocKind, RegExp][] = [
  ['기차표', /c446|c644|train|rail|火车|铁路|12306|动车|高铁|기차|열차|승차권/i],
  ['항공권', /flight|boarding|airline|航班|机票|登机|항공|탑승권|e-?ticket/i],
  ['바우처', /voucher|klook|kkday|凭证|tour|투어|바우처|입장권|门票/i],
  ['숙소 예약', /hotel|booking|agoda|airbnb|酒店|客栈|民宿|숙소|호텔|예약 ?확인/i],
  ['영수증', /receipt|小票|发票|收据|支付|wechat ?pay|alipay|영수증|결제/i],
  ['보험·신분', /passport|insurance|visa|여권|보험|비자|신분/i],
]

export function guessKind(name: string): DocKind {
  return RULES.find(([, re]) => re.test(name))?.[0] ?? '기타'
}

/** 열차 번호나 날짜(12-18, 12.18, 1218, 12月18)로 일차를 추정한다 */
export function guessDay(name: string, days: Day[]): string | null {
  if (/c446/i.test(name)) return days.find((d) => d.id === 'D2')?.id ?? null
  if (/c644/i.test(name)) return days.find((d) => d.id === 'D7')?.id ?? null
  const m = name.match(/(?:^|[^\d])(1[0-2]|0?[1-9])\s*(?:[-./_]|月)\s*([0-3]?\d)(?:日)?(?!\d)/) ?? name.match(/(?:^|[^\d])(12)([0-3]\d)(?!\d)/)
  if (!m) return null
  const md = `${m[1].padStart(2, '0')}-${m[2].padStart(2, '0')}`
  return days.find((d) => d.date.slice(5) === md)?.id ?? null
}

export const baseTitle = (fileName: string) => fileName.replace(/\.[a-z0-9]{2,5}$/i, '').replace(/[_]+/g, ' ').trim() || '문서'

/** 오늘 일차 문서 → 일차 순 → 일차 없음, 같은 일차는 최근 순 */
export function sortDocs(list: DocMeta[], todayId: string | null): DocMeta[] {
  const rank = (d: DocMeta) => (d.dayId && d.dayId === todayId ? -1 : d.dayId ? Number(d.dayId.slice(1)) : 99)
  return [...list].sort((a, b) => rank(a) - rank(b) || b.created - a.created)
}

export function filterDocs(list: DocMeta[], kind: DocKind | null, dayId: string | null, q = ''): DocMeta[] {
  const s = q.trim().toLowerCase()
  return list.filter((d) => (!kind || d.kind === kind) && (!dayId || d.dayId === dayId) &&
    (!s || `${d.title} ${d.note} ${d.fileName}`.toLowerCase().includes(s)))
}

export const fmtSize = (n: number) => (n >= 1024 * 1024 ? `${(n / 1024 / 1024).toFixed(1)}MB` : `${Math.max(1, Math.round(n / 1024))}KB`)
