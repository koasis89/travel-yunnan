// 영수증·결제 화면 OCR 텍스트 → 금액·가게·날짜·구분 추정(앱 ReceiptParser.kt 와 같은 규칙)
import type { Day } from './types'

export interface ReceiptGuess {
  amount: number | null
  currency: 'CNY' | 'KRW'
  merchant: string
  date: string | null      // YYYY-MM-DD
  time: string | null      // HH:MM
  dayId: string | null
  category: string
  source: string            // 금액을 찾은 근거(화면 표시용)
}

const FW = '０１２３４５６７８９．，：－／'
const HW = '0123456789.,:-/'
/** 전각 숫자·기호를 반각으로, 숫자 사이 공백·쉼표 제거 */
export function normalizeOcr(t: string): string {
  let s = [...t].map((c) => { const i = FW.indexOf(c); return i >= 0 ? HW[i] : c }).join('')
  // 중국어 모델이 ¥ 를 半·羊 으로 읽는 경우도 ¥ 로 본다
  s = s.replace(/[Y半羊]¥/g, '¥').replace(/[￥¥Y半羊](?=[ \t]*\d)/g, '¥').replace(/(\d)[ \t]*[.,][ \t]*(\d{2})(?!\d)/g, '$1.$2')
  s = s.replace(/(\d),(\d{3})/g, '$1$2').replace(/[ \t]*:[ \t]*/g, ':')
  // 한자 사이 공백 제거(OCR 이 글자마다 띄움)
  s = s.replace(/([\u4e00-\u9fff])[ \t]+(?=[\u4e00-\u9fff])/g, '$1')
  return s
}

const AMOUNT_KEYS: [RegExp, number][] = [
  [/实付|实收|实际支付|付款金额|支付金额|消费金额|应付金额|应收|订单金额|Paid|PAID/, 100],
  [/合计|总计|总额|总金额|共计|Total|TOTAL|金额/, 80],
  [/支付成功|交易成功|付款成功|已支付/, 70],
  [/小计|Subtotal/, 40],
]
const NOT_AMOUNT = /电话|手机|tel|TEL|单号|订单号|流水号|编号|卡号|税号|No\.|NO\.|桌号|人数|找零|找赎|优惠|折扣/

interface Cand { v: number; score: number; line: string }

function numbersIn(line: string): number[] {
  const out: number[] = []
  for (const m of line.matchAll(/(?<!\d)(?<!\d[:/.-])-?¥?\s*(\d{1,6}(?:\.\d{1,2})?)(?![\d:/])/g)) {
    const raw = m[1]; const v = Number(raw)
    if (!Number.isFinite(v) || v <= 0 || v >= 100000) continue
    if (/^(19|20)\d\d$/.test(raw) && !raw.includes('.')) continue // 연도
    out.push(v)
  }
  return out
}

export function parseReceipt(text: string, days: Day[] = []): ReceiptGuess {
  const lines = normalizeOcr(text).split(/\r?\n/).map((l) => l.trim()).filter(Boolean)
  const cands: Cand[] = []
  lines.forEach((line, i) => {
    if (NOT_AMOUNT.test(line)) return
    const nums = numbersIn(line)
    const key = AMOUNT_KEYS.find(([re]) => re.test(line))
    const prevKey = i > 0 ? AMOUNT_KEYS.find(([re]) => re.test(lines[i - 1]) && numbersIn(lines[i - 1]).length === 0) : undefined
    for (const v of nums) {
      let score = 0
      if (key) score += key[1]
      else if (prevKey) score += prevKey[1] - 10
      if (/¥|元|RMB|CNY/.test(line)) score += 25
      if (/\.\d{2}\b/.test(line)) score += 10
      if (/^-?¥?\s*\d+(\.\d{1,2})?$/.test(line)) score += 20 // 결제 화면의 큰 금액 한 줄
      if (Number.isInteger(v) && v < 10 && !/¥|元/.test(line)) score -= 30 // 수량·개수
      cands.push({ v, score, line })
    }
  })
  cands.sort((a, b) => b.score - a.score || b.v - a.v)
  const best = cands.find((c) => c.score > 0) ?? null

  // 날짜·시각
  const joined = lines.join('\n')
  const dm = joined.match(/(20\d\d)\s*[-/年.]\s*(\d{1,2})\s*[-/月.]\s*(\d{1,2})/)
  const date = dm ? `${dm[1]}-${dm[2].padStart(2, '0')}-${dm[3].padStart(2, '0')}` : null
  const tm = joined.match(/(?<!\d)([01]?\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?(?!\d)/)
  const time = tm ? `${tm[1].padStart(2, '0')}:${tm[2]}` : null
  const dayId = date ? days.find((d) => d.date === date)?.id ?? null : null

  // 가게 이름
  let merchant = ''
  for (let i = 0; i < lines.length && !merchant; i++) {
    const m = lines[i].match(/(?:商户名称|商户名|商家名称|商家|收款方全称|收款方|收款人|店名|门店|对方)[:：\s]*(.*)$/)
    if (m) merchant = (m[1] || lines[i + 1] || '').trim()
  }
  if (!merchant) {
    merchant = lines.slice(0, 5).find((l) => /[\u4e00-\u9fff]{2,}/.test(l) && /店|馆|酒店|餐厅|饭店|超市|客栈|火锅|米线|咖啡|茶|面|小吃|便利|商行|民宿|景区/.test(l) && !AMOUNT_KEYS.some(([re]) => re.test(l))) ?? ''
  }
  merchant = merchant.replace(/(支付|创建|交易)?时间.*$/, '').replace(/[\s¥\d.:\-]+$/, '').replace(/[ \t]*([()（）])[ \t]*/g, '$1').trim().slice(0, 30)

  return { amount: best?.v ?? null, currency: 'CNY', merchant, date, time, dayId, category: guessCategory(joined + ' ' + merchant), source: best?.line ?? '' }
}

export function guessCategory(t: string): string {
  if (/酒店|宾馆|客栈|民宿|住宿|房费|Hotel|HOTEL|Inn/.test(t)) return '숙박'
  if (/滴滴|出租|打车|的士|出行|高铁|火车|铁路|12306|车票|门票|景区|索道|缆车|观光车|大巴|客运|机场|地铁|公交|包车|Klook/.test(t)) return '교통·투어·입장료'
  if (/餐|饭|菜|炒|火锅|锅|米线|面|粥|汤|鸡|鱼|肉|烧烤|咖啡|奶|茶饮|甜品|面包|蛋挞|小吃|酒楼|食|啤酒|饮料|美团外卖|外卖/.test(t)) return '식비'
  return '쇼핑·기념품·예비비'
}
