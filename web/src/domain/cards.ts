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
/** 식사 선택지의 한국어 이름(중국어로만 적힌 선택지는 data/meal_names_ko.json 의 이름) */
export const optKo = (o: MealOption) => o.nameKo || o.name
/** 식사 선택지의 중국어 이름(한국어 이름이 따로 있으면 원래 이름에서 한글을 뺀 것, 중국어가 없으면 빈칸) */
export const optZh = (o: MealOption) => {
  const z = o.nameKo ? cleanZhAddress(o.name) : o.nameZh || o.name
  return hasChinese(z) ? z : ''
}
export const isTaxiable = (o: MealOption) => hasChinese(o.name) || hasChinese(o.addrZh) || !!o.amapUrl

export function buildTaxiCard(data: TripData, type: TargetType, id: string): TaxiCard | null {
  if (type === 'lodging') {
    const l = data.lodging(id); if (!l) return null
    return { type, id, nameZh: l.nameZh || '昆明 酒店(待定)', addrZh: cleanZhAddress(l.addrZh), nameKo: l.nameKo, phones: l.phones,
      bookingInfo: [l.bookingNo, l.bookedBy].filter(Boolean).join(' · '), amapUrl: l.amapUrl ?? null,
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
  return { type, id: o.id, nameZh: o.nameZh || o.name, addrZh: hasChinese(addr) ? addr : '', nameKo: `${meal.dayId} ${meal.slot} · ${optKo(o)}`,
    phones: [], bookingInfo: '', amapUrl: o.amapUrl, helpers: [],
    warning: hasChinese(o.name + addr) ? null : '중국어 주소가 없는 선택지입니다 · 지도앱 링크를 이용하세요.',
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
      const m = data.findMealOption(l.key); if (m) t = { label: optKo(m[1]).split('(')[0], type: 'meal', id: m[1].id }
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

export interface AmapRow { group: '숙소' | '관광지·장소' | '식당'; nameKo: string; nameZh: string; addrZh: string; url: string; exact: boolean }

/** 지도 링크 모음: 숙소 → 관광지 → 식당(일차 순). exact = 고덕지도에서 공유받은 단축 링크 */
export function amapRows(data: TripData): AmapRow[] {
  const out: AmapRow[] = []; const seen = new Set<string>()
  const push = (r: AmapRow) => { if (!r.url || seen.has(r.url)) return; seen.add(r.url); out.push(r) }
  for (const l of data.lodgings) if (l.amapUrl) push({ group: '숙소', nameKo: l.nameKo, nameZh: l.nameZh, addrZh: cleanZhAddress(l.addrZh), url: l.amapUrl, exact: l.amapUrl.includes('surl.amap') })
  for (const s of data.sights) {
    const p = data.place(s.placeId); const url = s.amapUrl ?? p?.amapUrl
    if (url) push({ group: '관광지·장소', nameKo: s.nameKo, nameZh: p?.nameZh ?? s.nameZh, addrZh: p?.addrZh ?? '', url, exact: url.includes('surl.amap') })
  }
  for (const p of data.places) if (p.amapUrl) push({ group: '관광지·장소', nameKo: p.nameKo, nameZh: p.nameZh, addrZh: p.addrZh, url: p.amapUrl, exact: p.amapUrl.includes('surl.amap') })
  for (const m of [...data.meals].sort((a, b) => a.dayId.localeCompare(b.dayId) || a.seq - b.seq))
    for (const o of m.options) if (o.amapUrl) push({ group: '식당', nameKo: `${m.dayId} ${m.slot} · ${optKo(o)}`, nameZh: optZh(o), addrZh: cleanZhAddress(o.addrZh), url: o.amapUrl, exact: o.amapUrl.includes('surl.amap') })
  return out
}

/** 붙여넣기용 글: 📍이름 / 중국어 이름 / 고덕지도 주소 / 링크 */
export const amapText = (r: AmapRow) =>
  [`📍${r.nameKo}`, ...(r.nameZh ? [`중국어 이름 : ${r.nameZh}`] : []), ...(hasChinese(r.addrZh) ? [`고덕지도 주소 : ${r.addrZh}`] : []), `고덕지도 주소 링크 : ${r.url}`].join('\n')

/** 주소(또는 이름)로 고덕지도 검색. 도시는 주소에서 짐작한다(tools/amap_links.py 와 같은 규칙) */
export function amapSearchUrl(keyword: string): string {
  const t = keyword
  const city = /丽江|白沙|束河|玉龙|古城区/.test(t) ? '丽江' : /香格里拉|迪庆|虎跳峡|独克宗|松赞/.test(t) ? '迪庆藏族自治州' : /昆明|官渡|五华|盘龙|西山/.test(t) ? '昆明' : ''
  return `https://uri.amap.com/search?keyword=${encodeURIComponent(keyword)}${city ? `&city=${encodeURIComponent(city)}` : ''}&src=travelyunnan&callnative=1`
}
