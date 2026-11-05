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
