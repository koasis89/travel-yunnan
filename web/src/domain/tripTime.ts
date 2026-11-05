import type { Day, Schedule } from './types'

const fmt = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })

/** 현지(Asia/Shanghai) 시각 "YYYY-MM-DDTHH:MM". override 가 있으면 그 값 */
export function tripNow(override?: string | null, now: Date = new Date()): string {
  if (override && /^\d{4}-\d\d-\d\dT\d\d:\d\d$/.test(override)) return override
  return fmt.format(now).replace(' ', 'T').slice(0, 16)
}

const toMin = (t: string) => { const [h, m] = t.split(':').map(Number); return h * 60 + m }
const dayNum = (d: string) => { const [y, m, dd] = d.split('-').map(Number); return Date.UTC(y, m - 1, dd) / 86400000 }
const fmtHHMM = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`

export type TripPhase = { kind: 'before'; daysLeft: number } | { kind: 'on'; day: Day } | { kind: 'after' }

export function resolvePhase(now: string, days: Day[]): TripPhase {
  const sorted = [...days].sort((a, b) => a.date.localeCompare(b.date))
  if (!sorted.length) return { kind: 'after' }
  const today = now.slice(0, 10)
  if (today < sorted[0].date) return { kind: 'before', daysLeft: dayNum(sorted[0].date) - dayNum(today) }
  if (today > sorted[sorted.length - 1].date) return { kind: 'after' }
  return { kind: 'on', day: sorted.find((d) => d.date === today)! }
}

export function focusDay(p: TripPhase, days: Day[]): Day {
  const sorted = [...days].sort((a, b) => a.date.localeCompare(b.date))
  return p.kind === 'on' ? p.day : p.kind === 'before' ? sorted[0] : sorted[sorted.length - 1]
}

export interface NowState { current: Schedule | null; next: Schedule | null }

/** 현재 시각이 속한 행. 종료 시각이 없는 행은 다음 행 시작 전까지 진행 중으로 본다 */
export function findNow(rows: Schedule[], time: string): NowState {
  const t = toMin(time)
  const timed = rows.filter((r) => r.timeStart).sort((a, b) => a.seq - b.seq)
  let current: Schedule | null = null
  timed.forEach((r, i) => {
    const s = toMin(r.timeStart!)
    const e = r.timeEnd ? toMin(r.timeEnd) : timed[i + 1]?.timeStart ? toMin(timed[i + 1].timeStart!) : null
    if (t >= s && (e === null || t < e)) current = r
  })
  const next = timed.find((r) => toMin(r.timeStart!) > t && r !== current) ?? null
  return { current, next }
}

const MOVE = ['열차', '투어 차량', '빵차', '전용차량', '픽업']
export const isKeyMove = (r: Schedule) => MOVE.some((k) => r.transport.includes(k))

export interface NextMove { day: Day; row: Schedule; at: string; minutesLeft: number }

export function nextMove(now: string, days: Day[], rowsOf: (id: string) => Schedule[]): NextMove | null {
  const nowAbs = dayNum(now.slice(0, 10)) * 1440 + toMin(now.slice(11, 16))
  for (const d of [...days].sort((a, b) => a.date.localeCompare(b.date))) {
    for (const r of rowsOf(d.id)) {
      if (!r.timeStart || !isKeyMove(r)) continue
      const abs = dayNum(d.date) * 1440 + toMin(r.timeStart)
      if (abs > nowAbs) return { day: d, row: r, at: `${d.date}T${r.timeStart}`, minutesLeft: abs - nowAbs }
    }
  }
  return null
}

export function formatLeft(min: number): string {
  if (min < 0) return '지남'
  if (min < 60) return `${min}분 후`
  if (min < 1440) return `${Math.floor(min / 60)}시간 ${min % 60}분 후`
  return `${Math.floor(min / 1440)}일 ${Math.floor((min % 1440) / 60)}시간 후`
}

export const shiftHours = (dt: string, h: number) => {
  const abs = dayNum(dt.slice(0, 10)) * 1440 + toMin(dt.slice(11, 16)) + h * 60
  const d = new Date(Math.floor(abs / 1440) * 86400000)
  return `${d.toISOString().slice(0, 10)}T${fmtHHMM(((abs % 1440) + 1440) % 1440)}`
}
