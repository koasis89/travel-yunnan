/**
 * 위챗 템플릿의 날짜·시각 값: 숫자로만 받아(월/일, 시:분) 중국어·한국어 문장으로 바꾼다.
 * 저장 형식: 날짜 "12/20", 시각 "14:30"(24시간), 날짜·시각 "12/20 11:30".
 * 숫자 형식이 아니면(예전 값·직접 쓴 중국어) 그대로 쓴다.
 */
export type DateKind = 'date' | 'time' | 'datetime'
export interface DTParts { mo: string; d: string; h: string; mi: string }

/** 여행 연도(요일 계산용). 1~5월은 다음 해로 본다 */
export const TRIP_YEAR = 2026
const WD_ZH = ['日', '一', '二', '三', '四', '五', '六']
const WD_KO = ['일', '월', '화', '수', '목', '금', '토']

const RE = /^\s*(?:(\d{0,2})\s*\/\s*(\d{0,2}))?\s*(?:(\d{0,2})\s*:\s*(\d{0,2}))?\s*$/

/** 값을 칸별 숫자로 나눈다(없는 칸은 빈칸) */
export function parseDT(raw: string): DTParts {
  const m = (raw ?? '').match(RE)
  return { mo: m?.[1] ?? '', d: m?.[2] ?? '', h: m?.[3] ?? '', mi: m?.[4] ?? '' }
}

/** 칸별 숫자를 저장 형식으로 합친다 */
export function joinDT(kind: DateKind, p: DTParts): string {
  const date = `${p.mo}/${p.d}`
  const time = `${p.h}:${p.mi}`
  return kind === 'date' ? date : kind === 'time' ? time : `${date} ${time}`
}

interface Valid { mo: number; d: number; h: number; mi: number }
function valid(raw: string, kind: DateKind): Valid | null {
  const m = (raw ?? '').match(RE)
  if (!m) return null
  const needDate = kind !== 'time'; const needTime = kind !== 'date'
  if ((needDate && !(m[1] && m[2])) || (needTime && !(m[3] && m[4]))) return null
  const v = { mo: Number(m[1] ?? 0), d: Number(m[2] ?? 0), h: Number(m[3] ?? 0), mi: Number(m[4] ?? 0) }
  if (needDate && (v.mo < 1 || v.mo > 12 || v.d < 1 || v.d > 31)) return null
  if (needTime && (v.h > 23 || v.mi > 59)) return null
  return v
}

/** 숫자 형식으로 맞게 들어온 값인지 */
export const isDT = (raw: string, kind: DateKind) => valid(raw, kind) !== null

const weekday = (mo: number, d: number) => new Date(Date.UTC(mo < 6 ? TRIP_YEAR + 1 : TRIP_YEAR, mo - 1, d)).getUTCDay()

function zhTime(h: number, mi: number, approx: boolean) {
  const period = h < 5 ? '凌晨' : h < 12 ? '上午' : h === 12 ? '中午' : h < 18 ? '下午' : '晚上'
  const mins = mi === 0 ? '' : mi === 30 ? '半' : `${mi < 10 ? `零${mi}` : mi}分`
  return `${period}${h % 12 || 12}点${mins}${approx ? '左右' : ''}`
}
function koTime(h: number, mi: number) {
  const period = h < 5 ? '새벽' : h < 12 ? '오전' : h === 12 ? '낮' : h < 18 ? '오후' : '저녁'
  return `${period} ${h % 12 || 12}시${mi === 0 ? '' : mi === 30 ? ' 반' : ` ${mi}분`}`
}

/** 저장 값 → 중국어(12月20日（周日）上午11点半). approx 면 시각 뒤에 左右 */
export function zhDT(raw: string, kind: DateKind, approx = false): string {
  const v = valid(raw, kind)
  if (!v) return raw
  const date = `${v.mo}月${v.d}日（周${WD_ZH[weekday(v.mo, v.d)]}）`
  return kind === 'date' ? date : kind === 'time' ? zhTime(v.h, v.mi, approx) : date + zhTime(v.h, v.mi, approx)
}

/** 저장 값 → 한국어(12월 20일(일) 오전 11시 반) */
export function koDT(raw: string, kind: DateKind): string {
  const v = valid(raw, kind)
  if (!v) return raw
  const date = `${v.mo}월 ${v.d}일(${WD_KO[weekday(v.mo, v.d)]})`
  return kind === 'date' ? date : kind === 'time' ? koTime(v.h, v.mi) : `${date} ${koTime(v.h, v.mi)}`
}
