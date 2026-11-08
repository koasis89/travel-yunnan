import type { TripData } from './types'
import { TEMPLATES } from './templates'

/** 통합 검색: 일정·사전 일정·체크리스트·숙소·식사·식당·관광지·장소·음식·회화·위챗 템플릿·밀크티·메모·준비물·병원 */
export interface SearchHit { kind: string; title: string; sub: string; to: string; text: string; name: string }

/** 이름이 맞을 때 앞에 둘 순서(대상 자체 → 일정·체크리스트) */
const RANK = ['식당', '관광지', '숙소', '음식', '장소', '회화', '위챗 템플릿', '입국신고', '밀크티', '병원', '일정', '사전 일정', '식사', '체크리스트', '메모', '준비물']

export const KIND_ORDER = ['일정', '사전 일정', '체크리스트', '숙소', '식사', '식당', '관광지', '장소', '음식', '회화', '위챗 템플릿', '밀크티', '입국신고', '메모', '준비물', '병원']

const clean = (s?: string | null) => (s && s !== '-' ? s : '')
const norm = (s: string) => s.toLowerCase().replace(/\s+/g, '')

export function buildSearchIndex(d: TripData): SearchHit[] {
  const out: SearchHit[] = []
  // 중국어는 번역 사전의 한국어도 같이 찾는다
  const add = (kind: string, title: string, sub: string, to: string, ...extra: (string | null | undefined)[]) => {
    const parts = [title, sub, ...extra].map(clean).filter(Boolean)
    const ko = parts.map((p) => d.ko(p)).filter(Boolean)
    out.push({ kind, title, sub, to, text: norm([...parts, ...ko].join(' ')), name: norm(title) })
  }
  /** 이름(한국어·중국어)이 있는 대상: 중국어 이름도 '이름'으로 본다 */
  const addNamed = (kind: string, title: string, zh: string, sub: string, to: string, ...extra: (string | null | undefined)[]) => {
    add(kind, title, sub, to, zh, ...extra); out[out.length - 1].name = norm(`${title} ${zh} ${d.ko(zh)}`)
  }
  for (const r of d.schedule) {
    const day = d.day(r.dayId)
    const title = [clean(r.from), clean(r.to)].filter(Boolean).join(' → ') || clean(r.tip).slice(0, 40)
    add('일정', title, `${r.dayId} ${day?.dateLabel ?? ''} ${r.timeLabel}${clean(r.via) ? ` · 경유 ${r.via}` : ''}`, `/schedule?day=${r.dayId}`, r.tip, r.transport, r.duration)
  }
  for (const p of d.pretrip) {
    add('사전 일정', p.title, `${p.dateLabel} ${p.time}`, '/schedule?day=pre', p.detail, ...p.tips, ...(p.checklist ?? []).map((x) => x.text))
  }
  for (const nd of d.checklists.nextDay) {
    for (const x of nd.items) add('체크리스트', x.text, `${nd.dayId} 맨 아래 · ${nd.title}`, `/schedule?day=${nd.dayId}`)
  }
  for (const x of d.checklists.departure.items) add('체크리스트', x.text, d.checklists.departure.title, '/schedule?day=pre')
  for (const l of d.lodgings) {
    addNamed('숙소', l.nameKo, l.nameZh, `${l.dates.map((x) => x.slice(5)).join('~')} · ${l.region}`, `/stay?tab=lodging&id=${l.id}`, l.addrZh, l.addrEn, l.room, l.note)
  }
  for (const m of d.meals) {
    for (const o of m.options) {
      add('식사', o.nameKo || o.name, `${m.dayId} ${m.slot} · ${m.area}`, `/stay?tab=meals&day=${m.dayId}`, o.name, o.nameZh, o.addrZh, o.menu, m.note)
    }
  }
  for (const r of d.restaurants) {
    addNamed('식당', r.nameKo, r.nameZh, `${r.region} · ${r.kind}`, `/eats/${r.id}`, r.summary, ...r.slots, ...r.tips, ...r.keywords,
      ...r.menu.flatMap((m) => [m.zh, m.ko, m.desc]), ...(r.ratings ?? []).flatMap((g) => [g.name, g.addr, ...g.popular.flatMap((p) => [p.zh, p.ko])]))
  }
  for (const s of d.sights) {
    addNamed('관광지', s.nameKo, s.nameZh, `${s.region} · ${s.dayIds.join('·')}`, `/sights/${s.id}`, s.summary, ...s.highlights, ...s.tips, ...s.keywords, s.hours, s.fee)
  }
  for (const p of d.places) addNamed('장소', p.nameKo, p.nameZh, p.nameZh, `/taxi/place/${p.id}`, p.addrZh)
  for (const f of d.foods) addNamed('음식', f.nameKo, f.nameZh, f.nameZh, `/menu?food=${encodeURIComponent(f.nameZh)}`, f.description, f.region)
  for (const p of d.phrases) addNamed('회화', p.ko, p.zh, p.zh, `/show/${p.id}`, p.pinyin, p.koRead)
  for (const t of TEMPLATES) {
    add('위챗 템플릿', t.title, t.contact?.name ?? '', `/templates?id=${t.id}`, t.render({}), t.renderKo({}), ...(t.lines ?? []).flatMap((l) => [l.zh, l.ko]))
  }
  for (const b of d.milktea.items) {
    for (const x of b.drinks) add('밀크티', `${x.zh} ${x.koRead}`, `${b.nameKo}(${b.nameZh})`, '/milktea', x.desc, x.pinyin, b.nameEn)
  }
  const ac = d.checklists.arrivalCard
  if (ac.title) add('입국신고', ac.title, '알리페이 이민국 12367', '/arrival-card', ac.summary, ...ac.steps.flatMap((s) => [s.title, s.desc]), ...ac.fields.map((f) => `${f.label} ${f.value}`))
  for (const n of d.notes) add('메모', n.title, n.dayIds.join('·'), '/notes', n.body, ...n.phrasesInline)
  for (const p of d.packing) add('준비물', p.name, `${p.group}${p.qty ? ` · ${p.qty}` : ''}`, '/packing', p.note)
  for (const h of d.emergency.hospitals) add('병원', h.nameKo, h.region, '/emergency', h.nameZh, h.addrZh, h.note)
  return out
}

/** 띄어쓰기로 나눈 낱말이 모두 들어간 것만. 제목에 들어가면 앞으로 */
export function searchIndex(index: SearchHit[], q: string, limit = 200): SearchHit[] {
  const terms = q.trim().toLowerCase().split(/\s+/).filter(Boolean)
  if (!terms.length) return []
  const hits = index.filter((h) => terms.every((t) => h.text.includes(t)))
  const score = (h: SearchHit) => (terms.every((t) => h.name.includes(t)) ? 0 : 1) * 100 + RANK.indexOf(h.kind)
  return hits.sort((a, b) => score(a) - score(b)).slice(0, limit)
}
