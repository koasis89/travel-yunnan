export interface Expense { id: number; dayId: string; amount: number; currency: 'CNY' | 'KRW'; category: string; memo: string; method: string; payer: string; at: number; photo?: string; ocr?: boolean }
export const CATEGORIES = ['교통·투어·입장료', '숙박', '식비', '쇼핑·기념품·예비비']
export const METHODS = ['위챗', '알리페이', '현금', '카드']
export const PAYERS = ['공동', '아빠', '첫째', '둘째']

export const budgetKey = (c: string) => c.startsWith('숙박') ? '숙박' : c.startsWith('식비') ? '식비' : c.startsWith('쇼핑') ? '쇼핑·기념품·예비비' : c.startsWith('교통') ? '교통·투어·입장료' : c
export const krw = (e: Expense, rate: number) => (e.currency === 'CNY' ? e.amount * rate : e.amount)
/** 위안으로 기록된 큰 금액(¥5,000 이상 = 약 100만 원)은 원화를 위안으로 잘못 고른 경우가 많다 */
export const SUSPICIOUS_CNY = 5000
export const looksLikeKrw = (e: Expense) => e.currency === 'CNY' && e.amount >= SUSPICIOUS_CNY

const shanghai = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', dateStyle: 'short', timeStyle: 'short' })

export function expensesCsv(list: Expense[], rate: number): string {
  const head = '일시,일차,금액,통화,원화환산,구분,결제수단,지불자,메모'
  const rows = [...list].sort((a, b) => a.at - b.at).map((e) =>
    [shanghai.format(new Date(e.at)), e.dayId, e.amount, e.currency, Math.round(krw(e, rate)), e.category, e.method, e.payer, `"${e.memo.replace(/"/g, "'")}"`].join(','))
  return [head, ...rows].join('\n')
}

/** CSV 한 줄을 칸으로 나눈다(큰따옴표로 감싼 칸 안의 쉼표·"" 처리) */
export function splitCsvLine(line: string): string[] {
  const out: string[] = []; let cur = ''; let q = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (q) { if (c === '"' && line[i + 1] === '"') { cur += '"'; i++ } else if (c === '"') q = false; else cur += c }
    else if (c === '"') q = true
    else if (c === ',') { out.push(cur); cur = '' }
    else cur += c
  }
  out.push(cur)
  return out.map((s) => s.trim())
}

/** 같은 지출인지(가져오기 중복 막기): 같은 분(分)·금액·통화·메모 */
const sameKey = (e: Pick<Expense, 'at' | 'amount' | 'currency' | 'memo'>) => `${Math.floor(e.at / 60000)}|${e.amount}|${e.currency}|${e.memo}`

/**
 * 경비 CSV(이 앱이 내보낸 형식: 일시,일차,금액,통화,원화환산,구분,결제수단,지불자,메모)를 읽는다.
 * 일시는 중국 시간(UTC+8). 이미 있는 지출과 같은 줄은 건너뛴다. 읽을 수 없는 줄은 bad 로 센다.
 */
export function parseExpensesCsv(text: string, existing: Expense[], idBase = Date.now()): { added: Expense[]; skipped: number; bad: number } {
  const lines = text.replace(/^﻿/, '').split(/\r?\n/).filter((l) => l.trim())
  if (!lines.length) return { added: [], skipped: 0, bad: 0 }
  const head = splitCsvLine(lines[0])
  const col = (name: string, i: number) => { const k = head.indexOf(name); return k >= 0 ? k : i }
  const hasHead = head.includes('금액')
  const C = { at: col('일시', 0), day: col('일차', 1), amount: col('금액', 2), cur: col('통화', 3), cat: col('구분', 5), method: col('결제수단', 6), payer: col('지불자', 7), memo: col('메모', 8) }
  const seen = new Set(existing.map(sameKey))
  const added: Expense[] = []; let skipped = 0; let bad = 0
  for (const line of hasHead ? lines.slice(1) : lines) {
    const f = splitCsvLine(line)
    const m = (f[C.at] ?? '').match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{1,2}):(\d{2})/)
    const amount = Number((f[C.amount] ?? '').replace(/[,\s원¥]/g, ''))
    const cur = (f[C.cur] ?? '').toUpperCase()
    if (!m || !Number.isFinite(amount) || amount <= 0 || (cur !== 'CNY' && cur !== 'KRW')) { bad++; continue }
    const at = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4] - 8, +m[5])
    const e: Expense = { id: idBase + added.length, dayId: f[C.day] || 'D1', amount, currency: cur, category: f[C.cat] || CATEGORIES[0],
      memo: f[C.memo] ?? '', method: f[C.method] || METHODS[0], payer: f[C.payer] || PAYERS[0], at }
    if (seen.has(sameKey(e))) { skipped++; continue }
    seen.add(sameKey(e)); added.push(e)
  }
  return { added, skipped, bad }
}
