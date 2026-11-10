import type { TripData } from './types'
import { TEMPLATES } from './templates'

/** 통합 검색: 일정·사전 일정·체크리스트·숙소·식사·식당·관광지·장소·음식·회화·위챗 템플릿·밀크티·메모·준비물·병원 */
export interface SearchHit { kind: string; title: string; sub: string; to: string; text: string; name: string }

/** 이름이 맞을 때 앞에 둘 순서(대상 자체 → 일정·체크리스트) */
const RANK = ['기능', '식당', '관광지', '숙소', '음식', '장소', '회화', '위챗 템플릿', '입국신고', '밀크티', '병원', '일정', '사전 일정', '식사', '체크리스트', '메모', '준비물']

export const KIND_ORDER = ['기능', '일정', '사전 일정', '체크리스트', '숙소', '식사', '식당', '관광지', '장소', '음식', '회화', '위챗 템플릿', '밀크티', '입국신고', '메모', '준비물', '병원']

const clean = (s?: string | null) => (s && s !== '-' ? s : '')
const norm = (s: string) => s.toLowerCase().replace(/\s+/g, '')

/** 기능(화면) 검색: 이름·설명·흔히 쓰는 말 */
export const FEATURES: { title: string; sub: string; to: string; words: string }[] = [
  { title: '오늘', sub: '오늘 일정·지금 할 일', to: '/', words: '홈 현재 다음 일정' },
  { title: '일정', sub: 'D1~D8 시간표·다음날 준비', to: '/schedule', words: '스케줄 시간표 동선 다음날 준비 체크리스트' },
  { title: '사전 일정', sub: '출발 전 할 일·체크리스트', to: '/schedule?day=pre', words: '출국 준비 이심 esim 입국 전' },
  { title: '숙소', sub: '숙소 카드·체크인 문장·할 일', to: '/stay?tab=lodging', words: '호텔 객잔 체크인 예약' },
  { title: '식사', sub: '끼니별 식당 선택지·택시 카드', to: '/stay?tab=meals', words: '점심 저녁 아침 끼니 맛집' },
  { title: '음식 도감', sub: '음식 사진·설명', to: '/stay?tab=foods', words: '요리 메뉴 음식 이름' },
  { title: '음식 사진', sub: '리장 음식 사진 모음', to: '/stay?tab=photos', words: '사진 갤러리' },
  { title: '사진 일기', sub: '앱(안드로이드) 전용 · 웹은 문서함에 사진 보관', to: '/docs', words: '사진일기 일기 다이어리 기록 사진' },
  { title: '숫자 카드', sub: '금액을 크게, 중국어 읽기·흥정 문장', to: '/number', words: '숫자 금액 가격 돈 위안 흥정 깎아 얼마' },
  { title: '경비', sub: '예산 대비 지출·영수증·CSV', to: '/expense', words: '지출 돈 예산 가계부 정산 영수증 환율' },
  { title: '문서함', sub: '바우처·기차표·숙소 예약·영수증 보관, 드라이브 백업', to: '/docs', words: '서류 바우처 기차표 티켓 예약 확인서 영수증 pdf 드라이브 백업 구글' },
  { title: '교통·열차·알림', sub: '열차·픽업·빵차, 캘린더 알림', to: '/transport', words: '기차 열차 교통 알림 캘린더 공항 픽업' },
  { title: '관광지 안내', sub: '관광지 사진과 설명', to: '/sights', words: '관광 명소 볼거리' },
  { title: '맛집 안내', sub: '식당 메뉴판·평점', to: '/eats', words: '식당 맛집 메뉴판 평점' },
  { title: '지도 링크 모음', sub: '고덕지도 링크 복사·공유', to: '/amap', words: '지도 고덕 amap 위치 주소' },
  { title: '준비물·날씨', sub: '준비물 체크리스트·지역별 옷차림', to: '/packing', words: '짐 옷 날씨 기온 준비물' },
  { title: '유의사항', sub: '고산병·열차·빵차·배달', to: '/notes', words: '주의 메모 팁 고산병' },
  { title: '긴급 연락처', sub: '영사콜센터·대사관·병원', to: '/emergency', words: '긴급 응급 병원 대사관 경찰 110 120 여권 분실' },
  { title: '위챗 메시지 템플릿', sub: '빵차·픽업·배달·QR 주고받기 문장', to: '/templates', words: '위챗 메시지 문장 템플릿 예약 요청' },
  { title: '주문 문장 만들기', sub: '음식·수량·요청 조합', to: '/menu', words: '주문 메뉴 포인팅 음료 맥주' },
  { title: '입국신고 QR(알리페이)', sub: '중국 입국신고서 작성 순서·값 복사', to: '/arrival-card', words: '입국 신고 qr 알리페이 입국카드 arrival' },
  { title: '밀크티 종류', sub: '브랜드별 추천·당도·얼음 주문', to: '/milktea', words: '밀크티 음료 차 나이차 카페' },
  { title: '설정', sub: '큰 글씨·날짜 지정·드라이브 백업', to: '/settings', words: '설정 글씨 크기 날짜 드라이브 백업' },
  { title: '업데이트 확인', sub: '더보기 → 업데이트 확인·앱 파일 다시 받기', to: '/more', words: '업데이트 새 버전 새로고침 더보기' },
]

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
  for (const f of FEATURES) add('기능', f.title, f.sub, f.to, f.words)
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
