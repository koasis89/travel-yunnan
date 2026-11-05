import type { Meal, MealOption, Phrase, Schedule, TripData } from './types'

export type TargetType = 'lodging' | 'place' | 'meal'
export interface TaxiCard {
  type: TargetType; id: string; nameZh: string; addrZh: string; nameKo: string; phones: string[]; bookingInfo: string
  amapUrl: string | null; helpers: Phrase[]; warning: string | null; siblings: MealOption[]
}

const HANGUL = /[가-힣]+:?/g
const CJK = /[\u4e00-\u9fff]/
export const hasChinese = (s: string) => CJK.test(s)

/** "云雪丽(大水车店) 검색" → "云雪丽(大水车店)" */
export function cleanZhAddress(s: string): string {
  return s.replace(HANGUL, '').replace(/\(\s*\)|（\s*）/g, '').replace(/\(\s+/g, '(').replace(/\s+\)/g, ')')
    .replace(/\s{2,}/g, ' ').replace(/^[\s/,]+|[\s/,]+$/g, '')
}

const LODGING_HELPERS: Record<string, string[]> = { kunming1: ['px-002'], mulanyu: ['px-003'], comeinn: ['px-004'] }
export const isTaxiable = (o: MealOption) => hasChinese(o.name) || hasChinese(o.addrZh) || !!o.amapUrl

export function buildTaxiCard(data: TripData, type: TargetType, id: string): TaxiCard | null {
  if (type === 'lodging') {
    const l = data.lodging(id); if (!l) return null
    return { type, id, nameZh: l.nameZh || '昆明 酒店(待定)', addrZh: cleanZhAddress(l.addrZh), nameKo: l.nameKo, phones: l.phones,
      bookingInfo: [l.bookingNo, l.bookedBy].filter(Boolean).join(' · '), amapUrl: null,
      helpers: (LODGING_HELPERS[id] ?? []).map((p) => data.phrase(p)).filter(Boolean) as Phrase[],
      warning: l.confirmed ? null : `숙소 미정: ${l.note}`, siblings: [] }
  }
  if (type === 'place') {
    const p = data.place(id); if (!p) return null
    return { type, id, nameZh: p.nameZh, addrZh: p.addrZh, nameKo: p.nameKo, phones: [], bookingInfo: '', amapUrl: p.amapUrl ?? null,
      helpers: (p.helperIds ?? []).map((h) => data.phrase(h)).filter(Boolean) as Phrase[], warning: null, siblings: [] }
  }
  const mo = data.mealOption(id); if (!mo) return null
  const [meal, o] = mo
  const addr = cleanZhAddress(o.addrZh)
  return { type, id: o.id, nameZh: o.nameZh || o.name, addrZh: hasChinese(addr) ? addr : '', nameKo: `${meal.dayId} ${meal.slot} · ${o.name}`,
    phones: [], bookingInfo: '', amapUrl: o.amapUrl, helpers: [],
    warning: hasChinese(o.name + addr) ? null : '중국어 주소가 없는 선택지다. 지도앱 링크를 이용한다.',
    siblings: meal.options.filter((x) => x.id !== o.id && isTaxiable(x)) }
}

export const slotFor = (h: number) => (h < 10 ? '아침' : h < 15 ? '점심' : h < 21 ? '저녁' : '야식')

export function mealFor(data: TripData, dayId: string, hour: number): MealOption | null {
  const meals = data.mealsOf(dayId); const want = slotFor(hour)
  const ordered = [...meals.filter((m) => m.slot === want), ...meals.filter((m) => m.slot !== want)]
  for (const m of ordered) { const o = m.options.find(isTaxiable); if (o) return o }
  return null
}

export interface LinkTarget { label: string; type: TargetType; id: string }

export function scheduleLinks(data: TripData, row: Schedule): LinkTarget[] {
  const text = `${row.to} / ${row.via}`
  const out = new Map<string, LinkTarget>()
  for (const l of data.placeLinks) {
    if (!text.includes(l.match)) continue
    let t: LinkTarget | null = null
    if (l.type === 'lodging') {
      const base = data.lodging(l.key)
      if (base) {
        const date = data.day(row.dayId)?.date
        const l2 = data.lodgings.find((x) => x.nameZh === base.nameZh && !!date && x.dates.includes(date)) ?? base
        t = { label: l2.nameKo.split(' (')[0].split('(')[0], type: 'lodging', id: l2.id }
      }
    } else if (l.type === 'place') {
      const p = data.place(l.key); if (p) t = { label: p.nameKo, type: 'place', id: p.id }
    } else {
      const m = data.findMealOption(l.key); if (m) t = { label: m[1].name.split('(')[0], type: 'meal', id: m[1].id }
    }
    if (t && !out.has(`${t.type}:${t.id}`)) out.set(`${t.type}:${t.id}`, t)
  }
  return [...out.values()]
}

export function messagePhrase(data: TripData, row: Schedule): Phrase | null {
  if (!row.message) return null
  const msg = row.message.replace(/\s/g, '')
  return data.phrases.filter((p) => p.categories.includes('message')).find((p) => msg.includes(p.zh.replace(/\s/g, '').slice(0, 15))) ?? null
}

export const mealOf = (data: TripData, optionId: string): Meal | undefined => data.mealOption(optionId)?.[0]
