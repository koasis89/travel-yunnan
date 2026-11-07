import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { CarTaxiFront, Copy, Lightbulb, MapPin, Maximize2, MessageSquareText, ShoppingBasket, UtensilsCrossed, Wallet } from 'lucide-react'
import { Banner, DayChips, ShowCard, TopBar, copyText } from '../components/ui'
import { useData, useNow } from '../store/data'
import { resolvePhase } from '../domain/tripTime'
import { isTaxiable } from '../domain/cards'
import type { Restaurant } from '../domain/types'
import { encodeFoods } from '../domain/drinks'

const src = (p?: string | null) => (p ? `${import.meta.env.BASE_URL}images/${p}` : null)

function EatCard({ r }: { r: Restaurant }) {
  const im = src(r.image)
  return (
    <Link to={`/eats/${r.id}`} className="card row" data-testid="eat-card" style={{ padding: 0, overflow: 'hidden', textDecoration: 'none', color: 'inherit', gap: 0, alignItems: 'stretch' }}>
      {im ? <img src={im} alt="" loading="lazy" style={{ width: 110, minHeight: 110, objectFit: 'cover', flex: 'none' }} />
        : <div style={{ width: 110, display: 'grid', placeItems: 'center', flex: 'none' }}><UtensilsCrossed size={30} /></div>}
      <div style={{ padding: '10px 12px' }} className="grow">
        <div className="bold">{r.nameKo}</div>
        <div className="small zh muted">{r.nameZh}</div>
        <div className="tiny" style={{ marginTop: 4 }}>{r.kind} · {r.perPerson}</div>
        <div className="tiny faint">{r.slots.join(' · ')}</div>
      </div>
    </Link>
  )
}

export function Eats() {
  const data = useData(); const now = useNow(); const [sp, setSp] = useSearchParams()
  const p = resolvePhase(now, data.days); const todayId = p.kind === 'on' ? p.day.id : null
  const day = sp.get('day')
  const list = day ? data.restaurants.filter((r) => r.dayIds.includes(day)) : data.restaurants
  const regions = useMemo(() => [...new Set(list.map((r) => r.region))], [list])
  return (
    <div className="page">
      <TopBar title="맛집 안내" />
      <DayChips days={data.days} selected={day} todayId={todayId} allLabel="전체" onSelect={(id) => setSp(id ? { day: id } : {}, { replace: true })} />
      {(() => { const l = data.lodging(data.day(day ?? todayId ?? '')?.lodgingId) ?? data.lodgings.find((x) => x.phones.length); const ph = l?.phones[0]; return (
        <div className="pad stack" style={{ marginTop: 8 }}>{data.restaurantTips.map((t) => <Banner key={t} icon={<Lightbulb size={18} />}>{t}
          {ph && <div style={{ marginTop: 6 }}><button className="btn" data-testid="copy-lodging-phone" style={{ whiteSpace: "normal", textAlign: "left", maxWidth: "100%" }} onClick={() => copyText(ph, '숙소 전화번호를 복사했다')}><Copy size={16} />숙소 번호 복사 · {l!.nameKo.split('(')[0].trim()} {ph}</button></div>}</Banner>)}</div>) })()}
      {regions.map((g) => (
        <div key={g}><div className="section">{g}</div>
          <div className="stack pad">{list.filter((r) => r.region === g).map((r) => <EatCard key={r.id} r={r} />)}</div></div>
      ))}
      <p className="pad tiny faint">사진은 대표 음식 참고 사진이며 그 식당 사진이 아니다. 메뉴는 대표 메뉴 예시로, 가격·구성은 매장마다 다르다. 1인 금액은 여행 계획 기준 참고값이다.</p>
    </div>
  )
}

export function EatView() {
  const { id = '' } = useParams(); const data = useData(); const nav = useNavigate()
  const r = data.restaurant(id)
  const [board, setBoard] = useState(false)
  const [sel, setSel] = useState<Record<string, number>>({})
  if (!r) return <div className="page"><TopBar title="맛집" /><div className="pad muted">찾을 수 없다</div></div>
  const taxiOpt = r.optionIds.map((i) => data.mealOption(i)?.[1]).find((o) => o && isTaxiable(o))
  const taxiPlace = !taxiOpt && r.placeId ? data.place(r.placeId) : undefined
  if (board) return (
    <ShowCard header="종업원에게 보여주세요(가리키며 주문)" onClose={() => setBoard(false)} actions={<span className="small">음식을 가리키며 '这个(이거)'라고 말한다</span>}>
      <div className="note">{r.nameZh}</div>
      {r.menu.map((m) => <div key={m.zh} className="name zh" style={{ margin: '10px 0' }}>{m.zh}<div className="note" style={{ fontSize: '.4em' }}>{m.ko}</div></div>)}
    </ShowCard>
  )
  const hero = src(r.image)
  return (
    <div className="page">
      <TopBar title={r.nameKo} />
      {hero && <figure style={{ margin: 0 }}>
        <img src={hero} alt={r.nameKo} style={{ width: '100%', maxHeight: 300, objectFit: 'cover', display: 'block' }} />
        <figcaption className="tiny faint" style={{ padding: '4px 16px' }}>{r.imageNote}{r.credit ? ` · ${[r.credit.title, r.credit.author, r.credit.license].filter(Boolean).join(' · ')}` : ''}</figcaption>
      </figure>}
      <div className="pad stack" style={{ marginTop: 8 }}>
        <div><div className="bold" style={{ fontSize: '1.3em' }}>{r.nameKo}</div><div className="zh" style={{ fontSize: '1.3em', fontWeight: 700 }}>{r.nameZh}</div></div>
        <p style={{ margin: 0 }}>{r.summary}</p>
        <div className="card flat stack" style={{ gap: 6 }}>
          <div className="row small"><UtensilsCrossed size={16} />{r.kind}</div>
          <div className="row small"><Wallet size={16} />1인 {r.perPerson}(참고)</div>
          <div className="row small"><ShoppingBasket size={16} />{r.slots.join(' · ')}</div>
        </div>
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          {taxiOpt && <Link className="btn primary" to={`/taxi/meal/${taxiOpt.id}`}><CarTaxiFront size={18} />택시 카드</Link>}
          {taxiPlace && <Link className="btn primary" to={`/taxi/place/${taxiPlace.id}`}><CarTaxiFront size={18} />택시 카드</Link>}
          {r.amapUrl && <a className="btn" href={r.amapUrl} target="_blank" rel="noreferrer"><MapPin size={18} />지도</a>}
          <button className="btn" onClick={() => setBoard(true)}><Maximize2 size={18} />메뉴판 크게</button>
        </div>
      </div>
      <div className="section">메뉴판 (누를 때마다 1→2→3→빼기, 여러 개 선택)</div>
      <div className="pad stack">
        {r.menu.map((m) => { const n = sel[m.zh] ?? 0; return (
          <div key={m.zh} className="card row" data-testid="menu-item" onClick={() => setSel((x) => ({ ...x, [m.zh]: ((x[m.zh] ?? 0) + 1) % 4 }))}
            style={{ gap: 12, alignItems: 'center', cursor: 'pointer', outline: n ? '2px solid var(--primary)' : 'none' }}>
            {src(m.image) ? <img src={src(m.image)!} alt="" style={{ width: 72, height: 72, objectFit: 'cover', borderRadius: 10, flex: 'none' }} /> : <div style={{ width: 72, height: 72, display: 'grid', placeItems: 'center', flex: 'none' }}><UtensilsCrossed size={24} /></div>}
            <div className="grow">
              <div className="zh bold" style={{ fontSize: '1.25em' }}>{m.zh}</div>
              <div className="small">{m.ko}</div>
              {m.desc && <div className="tiny muted">{m.desc}</div>}
            </div>
            <span className={`chip ${n ? 'on' : ''}`} style={{ flex: 'none' }}>{n ? `×${n}` : '담기'}</span>
          </div>
        ) })}
      </div>
      {(() => { const picked = r.menu.map((m) => [m.zh, sel[m.zh] ?? 0] as [string, number]).filter(([, n]) => n > 0); return (
        <div className="pad" style={{ position: 'sticky', bottom: 72, zIndex: 5 }}>
          <button className="btn primary block" data-testid="make-order" onClick={() => nav(picked.length ? `/menu?foods=${encodeURIComponent(encodeFoods(picked))}` : '/menu')}>
            <MessageSquareText size={18} />{picked.length ? `고른 ${picked.reduce((a, [, n]) => a + n, 0)}개로 주문 문장 만들기` : '주문 문장 만들기(음료·가리키기)'}</button>
        </div>) })()}
      {r.tips.length > 0 && <><div className="section">팁·주의</div>
        <div className="pad stack" style={{ gap: 6 }}>{r.tips.map((t) => <div key={t} className="row small" style={{ alignItems: 'flex-start' }}><Lightbulb size={16} style={{ flex: 'none', marginTop: 2 }} />{t}</div>)}</div></>}
      <p className="pad tiny faint">메뉴는 대표 메뉴 예시이며 매장 메뉴판과 다를 수 있다. 실제 메뉴판 사진은 문서함에 찍어 두면 다음에 다시 볼 수 있다.</p>
    </div>
  )
}
