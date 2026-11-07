import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { BookOpen, CarTaxiFront, Copy, MapPin, MessageSquareText, Maximize2, Users } from 'lucide-react'
import { Banner, DayChips, ShowCard, TopBar, copyText } from '../components/ui'
import { useData, useNow } from '../store/data'
import { useStored } from '../store/local'
import { resolvePhase } from '../domain/tripTime'
import { isTaxiable, optKo, optZh } from '../domain/cards'
import { FoodPhotos, LocalPhoto } from './FoodPhotos'
import { usePhotoIds } from '../store/foodPhotos'
import { DRINKS, DRINK_REQUESTS, composeMultiOrder, decodeFoods, type Drink } from '../domain/drinks'

/** 하단 탭 "숙소·식사": 숙소 / 식사 / 음식 도감 / 음식 사진 */
export function Stay() {
  const [sp, setSp] = useSearchParams()
  const tab = sp.get('tab') ?? 'lodging'
  return (
    <div className="page">
      <TopBar title="숙소·식사" back={false} />
      <div className="chips">
        {[['lodging', '숙소'], ['meals', '식사'], ['foods', '음식 도감'], ['photos', '음식 사진']].map(([k, t]) => (
          <button key={k} className={`chip ${tab === k ? 'on' : ''}`} onClick={() => setSp({ tab: k }, { replace: true })}>{t}</button>
        ))}
      </div>
      {tab === 'lodging' ? <Lodgings focus={sp.get('id')} /> : tab === 'meals' ? <Meals dayArg={sp.get('day')} /> : tab === 'photos' ? <FoodPhotos foodId={sp.get('food')} onClearFood={() => setSp({ tab: 'photos' }, { replace: true })} /> : <Foods />}
    </div>
  )
}

function Lodgings({ focus }: { focus: string | null }) {
  const data = useData()
  const [checked, setChecked] = useStored<string[]>('todo', [])
  useEffect(() => { if (focus) document.getElementById(`l-${focus}`)?.scrollIntoView({ block: 'start' }) }, [focus])
  return (
    <div className="stack pad">
      {data.lodgings.map((l) => (
        <div key={l.id} id={`l-${l.id}`} className="card">
          <div className="row"><span className="small bold" style={{ color: 'var(--primary)' }}>{l.dates.map((d) => d.slice(5).replace('-', '/')).join('~')} · {l.nights}박 · {l.region}</span><span className="grow" /><span className="tag">{l.status}</span></div>
          <h3 style={{ marginTop: 6 }}>{l.nameKo}</h3>
          {l.nameZh && <div className="zh">{l.nameZh}</div>}
          <div className="zh" style={{ fontSize: '1.05em' }}>{l.addrZh}</div>
          {l.addrEn && <div className="tiny faint">{l.addrEn}</div>}
          <div className="small" style={{ marginTop: 4 }}>{l.room}</div>
          {l.bookingNo && <div className="small">예약번호 {l.bookingNo} · {l.bookedBy}</div>}
          {l.priceKrw != null && <div className="small">{l.priceKrw.toLocaleString()}원</div>}
          {l.note && <div className="small muted" style={{ marginTop: 4 }}>{l.note}</div>}
          <div className="wrap" style={{ marginTop: 10 }}>
            <Link className="btn" to={`/taxi/lodging/${l.id}`}><CarTaxiFront size={16} />택시 카드</Link>
            {l.amapUrl && <a className="btn" href={l.amapUrl} target="_blank" rel="noreferrer"><MapPin size={16} />지도</a>}
            <button className="btn" onClick={() => copyText(`${l.nameZh} ${l.addrZh}`.trim(), '주소를 복사했다')}><Copy size={16} />주소 복사</button>
            <Link className="btn" to="/show/px-006"><Users size={16} />3인 확인 문장</Link>
          </div>
          {data.todosOf(l.id).length > 0 && <>
            <div className="divider" /><div className="small bold">할 일</div>
            {data.todosOf(l.id).map((t) => (
              <label key={t.id} className="check" style={{ padding: '8px 0' }}>
                <input type="checkbox" checked={checked.includes(t.id)} onChange={() => setChecked((c) => (c.includes(t.id) ? c.filter((x) => x !== t.id) : [...c, t.id]))} />
                <span className="grow small">{t.text}</span>
                {t.phraseId && <Link to={`/show/${t.phraseId}`} aria-label="문장 보기"><MessageSquareText size={20} /></Link>}
              </label>
            ))}
          </>}
        </div>
      ))}
      {data.lodgingNotes.map((n, i) => <div key={i} className="tiny faint">※ {n}</div>)}
    </div>
  )
}

function Meals({ dayArg }: { dayArg: string | null }) {
  const data = useData(); const now = useNow()
  const todayId = (() => { const p = resolvePhase(now, data.days); return p.kind === 'on' ? p.day.id : null })()
  const [day, setDay] = useState(dayArg ?? todayId ?? 'D1')
  const foodFor = (menu: string) => {
    const m = menu.replace(/\s/g, '')
    return data.foods.filter((f) => f.nameKo && m.includes(f.nameKo.split('(')[0].replace(/\s/g, '')))
      .sort((a, b) => b.nameKo.length - a.nameKo.length)[0]
  }
  return (<>
    <DayChips days={data.days} selected={day} todayId={todayId} onSelect={(id) => id && setDay(id)} />
    <div className="stack pad">
      {data.mealsOf(day).map((m) => (
        <div key={m.id} className="card">
          <div className="row"><span className="bold" style={{ color: 'var(--primary)', fontSize: '1.1em' }}>{m.slot}</span><span className="small muted">{m.area}</span><span className="grow" />{m.budgetCny != null && <span className="small bold">약 ¥{m.budgetCny}</span>}</div>
          {m.options.map((o, i) => {
            const f = foodFor(o.menu)
            return (
              <div key={o.id}>
                <div className="divider" />
                <div className="bold">{'①②③'[i]} {optKo(o)}</div>
                {o.nameKo && <div className="small zh">{optZh(o)}</div>}
                {o.addrZh && <div className="small zh">{o.addrZh}</div>}
                {o.menu && <div className="small muted">추천: {o.menu}</div>}
                <div className="wrap" style={{ marginTop: 8 }}>
                  {data.restaurantOfOption(o.id) && <Link className="btn" data-testid="eat-link" to={`/eats/${data.restaurantOfOption(o.id)!.id}`}><BookOpen size={16} />식당 정보·메뉴판</Link>}
                  {isTaxiable(o) && <Link className="btn" to={`/taxi/meal/${o.id}`}><CarTaxiFront size={16} />택시 카드</Link>}
                  {o.addrZh && <button className="btn" onClick={() => copyText(o.addrZh, '주소를 복사했다')}><Copy size={16} />주소</button>}
                  {o.amapUrl && <a className="btn" href={o.amapUrl} target="_blank" rel="noreferrer"><MapPin size={16} />지도</a>}
                  <Link className="btn" to={`/menu${f ? `?food=${encodeURIComponent(f.nameZh)}` : ''}`}>주문{f ? `: ${f.nameZh}` : ' 문장'}</Link>
                </div>
              </div>
            )
          })}
          {m.note && <div style={{ marginTop: 10 }}><Banner>{m.note}</Banner></div>}
        </div>
      ))}
    </div>
  </>)
}

function Foods() {
  const data = useData(); const have = usePhotoIds() ?? []; const nav = useNavigate()
  const regions = useMemo(() => [...new Set(data.foods.map((f) => f.region))], [data])
  const [region, setRegion] = useState(regions[0])
  return (<>
    <div className="chips">{regions.map((r) => <button key={r} className={`chip ${region === r ? 'on' : ''}`} onClick={() => setRegion(r)}>{r}</button>)}
      <Link className="chip" to="/menu"><BookOpen size={14} /> 주문 문장</Link></div>
    <div className="stack pad">
      {data.foods.filter((f) => f.region === region).map((f) => (
        <Link key={f.id} to={`/menu?food=${encodeURIComponent(f.nameZh)}`} className="card food" style={{ textDecoration: 'none', color: 'inherit' }}>
          {(() => {
            const mine = data.photoIdsForFood(f.id, have)
            return f.image ? <img className="ph" src={`${import.meta.env.BASE_URL}images/${f.image}`} alt={f.nameKo} loading="lazy" />
              : mine[0] ? <LocalPhoto className="ph" id={mine[0]} alt={f.nameKo} /> : <div className="ph zh">{f.nameZh.slice(0, 2)}</div>
          })()}
          <div className="grow">
            <div className="bold zh" style={{ fontSize: '1.2em' }}>{f.nameZh}</div>
            <div className="small" style={{ color: 'var(--primary)' }}>{f.nameKo}</div>
            <div className="small">{f.description}</div>
            {f.imageKind === '참고 이미지' && <div className="tiny faint">참고 이미지(같은 종류의 다른 사진)</div>}
            {data.photoIdsForFood(f.id, have).length > 0 && <span className="tiny" style={{ color: 'var(--primary)', textDecoration: 'underline' }} data-testid="food-more-photos"
              onClick={(e) => { e.preventDefault(); e.stopPropagation(); nav(`/stay?tab=photos&food=${f.id}`) }}>사진 {data.photoIdsForFood(f.id, have).length}장 보기</span>}
          </div>
        </Link>
      ))}
    </div>
  </>)
}

const UNITS: [string, string][] = [['一份', '1인분'], ['一个', '하나'], ['一瓶', '한 병'], ['一杯', '한 잔'], ['两份', '2인분'], ['三份', '3인분']]
const REQUESTS: [string, string][] = [['不要辣', '안 맵게'], ['不要香菜', '고수 빼고'], ['少放盐', '소금 적게'], ['打包', '포장']]

export function Menu() {
  const data = useData(); const nav = useNavigate()
  const [sp] = useSearchParams()
  const foods = useMemo(() => [...new Map(data.foods.map((f) => [f.nameZh, f])).values()], [data])
  // 고른 음식(수량). ?food=단일 또는 ?foods=A*1|B*2 (맛집 메뉴판에서 여러 개)
  const [picked, setPicked] = useState<[string, number][]>(() => sp.get('foods') ? decodeFoods(sp.get('foods')) : sp.get('food') ? [[sp.get('food')!, 1]] : [])
  const [drinksOnly, setDrinksOnly] = useState(false)
  const [drinks, setDrinks] = useState<Record<string, number>>({})
  const [unit, setUnit] = useState('一份')
  const [req, setReq] = useState<string[]>([])
  const [show, setShow] = useState(false)
  const count = (z: string) => picked.find(([x]) => x === z)?.[1] ?? 0
  const cycle = (z: string) => { setDrinksOnly(false); setPicked((l) => { const n = (count(z) + 1) % 4; const rest = l.filter(([x]) => x !== z); return n ? (l.some(([x]) => x === z) ? l.map(([x, c]) => [x, x === z ? n : c] as [string, number]) : [...rest, [z, n]]) : rest }) }
  const dsel = DRINKS.map((d) => [d, drinks[d.zh] ?? 0] as [Drink, number]).filter(([, n]) => n > 0)
  const sentence = composeMultiOrder(picked, drinksOnly ? null : unit, dsel, req)
  const koName = (z: string) => foods.find((f) => f.nameZh === z)?.nameKo.split('(')[0] ?? z
  const ko = [...(picked.length ? picked.map(([z, n]) => `${koName(z)} ${n}인분`) : drinksOnly ? [] : [`이거(메뉴를 가리키며) · ${UNITS.find((u) => u[0] === unit)?.[1]}`]),
    ...dsel.map(([d, n]) => `${d.ko} ${n}`)].join(' / ')
  const extra = picked.filter(([z]) => !foods.some((f) => f.nameZh === z))
  if (show) return (
    <ShowCard header="종업원에게 보여주세요" onClose={() => setShow(false)} actions={<button className="btn" onClick={() => copyText(sentence)}><Copy size={16} />복사</button>}>
      <div className="phrase zh">{sentence}</div>
      <div className="sub">{ko}</div>
    </ShowCard>
  )
  return (
    <div className="page">
      <TopBar title="주문 문장 만들기" />
      <div className="pad"><div className="card mint">
        <div className="zh bold" data-testid="order-sentence" style={{ fontSize: '1.6em', lineHeight: 1.35 }}>{sentence}</div>
        <div className="small" style={{ marginTop: 6 }}>{ko}</div>
        <div className="wrap" style={{ marginTop: 10 }}>
          <button className="btn" onClick={() => setShow(true)}><Maximize2 size={16} />크게 보여주기</button>
          <button className="btn" onClick={() => copyText(sentence)}><Copy size={16} />복사</button>
          {(picked.length > 0 || dsel.length > 0) && <button className="btn ghost" onClick={() => { setPicked([]); setDrinks({}); setDrinksOnly(false) }}>모두 지우기</button>}
          <button className="btn ghost" onClick={() => nav(-1)}>닫기</button>
        </div>
      </div></div>
      <div className="section">음식 (여러 개 선택 · 누를 때마다 1→2→3→빼기)</div>
      <div className="wrap pad">
        <button className={`chip ${!picked.length && !drinksOnly ? 'on' : ''}`} onClick={() => { setPicked([]); setDrinksOnly(false) }}>이거(가리키기)</button>
        <button className={`chip ${drinksOnly ? 'on' : ''}`} onClick={() => { setPicked([]); setDrinksOnly(true) }}>음료만</button>
        {extra.map(([z, n]) => <button key={z} data-testid="food" className="chip on" onClick={() => cycle(z)}><span className="zh">{z}</span> ×{n}</button>)}
        {foods.map((f) => { const n = count(f.nameZh); return <button key={f.id} data-testid="food" className={`chip ${n ? 'on' : ''}`} onClick={() => cycle(f.nameZh)}><span className="zh">{f.nameZh}</span> {f.nameKo.split('(')[0]}{n ? ` ×${n}` : ''}</button> })}
      </div>
      {!picked.length && !drinksOnly && <><div className="section">가리키기 수량</div>
        <div className="wrap pad">{UNITS.map(([z, k]) => <button key={z} className={`chip ${unit === z ? 'on' : ''}`} onClick={() => setUnit(z)}><span className="zh">{z}</span> {k}</button>)}</div></>}
      <div className="section">요청</div>
      <div className="wrap pad">{REQUESTS.map(([z, k]) => <button key={z} className={`chip ${req.includes(z) ? 'on' : ''}`} onClick={() => setReq((r) => (r.includes(z) ? r.filter((x) => x !== z) : [...r, z]))}><span className="zh">{z}</span> {k}</button>)}</div>
      <div className="section">음료 (누를 때마다 1→2→3→빼기)</div>
      {[...new Set(DRINKS.map((d) => d.group))].map((g) => (
        <div key={g} className="pad" style={{ marginBottom: 6 }}>
          <div className="tiny muted" style={{ margin: '2px 0 4px' }}>{g}</div>
          <div className="wrap">{DRINKS.filter((d) => d.group === g).map((d) => {
            const n = drinks[d.zh] ?? 0
            return <button key={d.zh} data-testid="drink" className={`chip ${n ? 'on' : ''}`} title={d.note}
              onClick={() => setDrinks((m) => ({ ...m, [d.zh]: ((m[d.zh] ?? 0) + 1) % 4 }))}><span className="zh">{d.zh}</span> {d.ko}{n ? ` ×${n}` : ''}</button>
          })}</div>
        </div>
      ))}
      <div className="wrap pad">{DRINK_REQUESTS.map(([z, k]) => <button key={z} className={`chip ${req.includes(z) ? 'on' : ''}`} onClick={() => setReq((r) => (r.includes(z) ? r.filter((x) => x !== z) : [...r, z]))}><span className="zh">{z}</span> {k}</button>)}</div>
      <p className="pad tiny faint">고량주(백주)는 도수가 40도 이상이라 작은 병(小瓶)으로 고르게 했다. 酥油茶·牦牛酸奶는 샹그릴라, 酸角汁·木瓜水는 윈난 식당 어디서나 흔하다.</p>
      {/* 아래로 내려가 음식·음료를 골라도 문장이 보이게 */}
      <div className="pad" style={{ position: 'sticky', bottom: 72, zIndex: 5 }}>
        <button className="card mint row" data-testid="sticky-order" onClick={() => setShow(true)} style={{ width: '100%', textAlign: 'left', border: 0, gap: 10 }}>
          <span className="zh bold grow" style={{ fontSize: '1.05em' }}>{sentence}</span><Maximize2 size={18} />
        </button>
      </div>
    </div>
  )
}
