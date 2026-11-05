const DIGITS = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九']
const UNITS = ['', '十', '百', '千', '万']

/** 0 ~ 99,999 → 중국어 읽기. 150 → 一百五十, 1050 → 一千零五十, 200 → 两百 */
export function readNumber(n: number): string {
  if (!Number.isInteger(n) || n < 0 || n > 99999) throw new Error('range')
  if (n === 0) return '零'
  if (n >= 10 && n < 20) return '十' + (n % 10 ? DIGITS[n % 10] : '')
  const digits = String(n).split('').map(Number)
  let out = ''; let pendingZero = false
  digits.forEach((d, i) => {
    const pos = digits.length - 1 - i
    if (d === 0) { pendingZero = out.length > 0; return }
    if (pendingZero) { out += '零'; pendingZero = false }
    out += (d === 2 && pos >= 2 ? '两' : DIGITS[d]) + UNITS[pos]
  })
  return out
}

export function readYuan(amount: number): string {
  const whole = Math.floor(amount); const jiao = Math.round((amount - whole) * 10)
  return readNumber(whole) + '块' + (jiao >= 1 && jiao <= 9 ? DIGITS[jiao] + '毛' : '')
}
