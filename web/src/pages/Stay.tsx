import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { BookOpen, CarTaxiFront, Copy, MapPin, MessageSquareText, Check, Maximize2, Minus, Plus, Users } from 'lucide-react'
import { Banner, DayChips, Ko, ShowCard, TopBar, copyText } from '../components/ui'
import { useData, useNow } from '../store/data'
import { useStored } from '../store/local'
import { resolvePhase } from '../domain/tripTime'
import { isTaxiable, optKo, optZh } from '../domain/cards'
import { FoodPhotos, LocalPhoto } from './FoodPhotos'
import { usePhotoIds } from '../store/foodPhotos'
import { DRINKS, ORDER_REQUESTS, ORDER_TABS, composeTabOrder, decodeFoods, tabOf, type Drink, type OrderTab } from '../domain/drinks'

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
          <Ko zh={l.nameZh} />
          <div className="zh" style={{ fontSize: '1.05em' }}>{l.addrZh}</div>
          <Ko zh={l.addrZh} />
          {l.addrEn && <div className="tiny faint">{l.addrEn}</div>}
          <div className="small" style={{ marginTop: 4 }}>{l.room}</div>
          {l.bookingNo && <div className="small">예약번호 {l.bookingNo} · {l.bookedBy}</div>}
          {l.priceKrw != null && <div className="small">{l.priceKrw.toLocaleString()}원</div>}
          {l.note && <div className="small muted" style={{ marginTop: 4 }}>{l.note}</div>}
          <div className="wrap" style={{ marginTop: 10 }}>
            <Link className="btn" to={`/taxi/lodging/${l.id}`}><CarTaxiFront size={16} />택시 카드</Link>
            {l.amapUrl && <a className="btn" href={l.amapUrl} target="_blank" rel="noreferrer"><MapPin size={16} />지도</a>}
            <button className="btn" onClick={() => copyText(`${l.nameZh} ${l.addrZh}`.trim(), '주소 복사 완료')}><Copy size={16} />주소 복사</button>
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
                <Ko zh={o.addrZh} />
                {o.menu && <div className="small muted">추천: {o.menu}{data.ko(o.menu) ? ` (${data.ko(o.menu)})` : ''}</div>}
                <div className="wrap" style={{ marginTop: 8 }}>
                  {data.restaurantOfOption(o.id) && <Link className="btn" data-testid="eat-link" to={`/eats/${data.restaurantOfOption(o.id)!.id}`}><BookOpen size={16} />식당 정보·메뉴판</Link>}
                  {isTaxiable(o) && <Link className="btn" to={`/taxi/meal/${o.id}`}><CarTaxiFront size={16} />택시 카드</Link>}
                  {o.addrZh && <button className="btn" onClick={() => copyText(o.addrZh, '주소 복사 완료')}><Copy size={16} />주소</button>}
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


export function Menu() {
  const data = useData()
  const [sp] = useSearchParams()
  const foods = useMemo(() => [...new Map(data.foods.map((f) => [f.nameZh, f])).values()], [data])
  // 고른 음식(수량). ?food=단일 또는 ?foods=A*1|B*2 (맛집 메뉴판에서 여러 개)
  const [picked, setPicked] = useState<[string, number][]>(() => sp.get('foods') ? decodeFoods(sp.get('foods')) : sp.get('food') ? [[sp.get('food')!, 1]] : [])
  const [point, setPoint] = useState(0)
  const [drinks, setDrinks] = useState<Record<string, number>>({})
  const [req, setReq] = useState<string[]>([])
  const [tab, setTab] = useState<OrderTab>('food')
  const [show, setShow] = useState(false)
  const count = (z: string) => picked.find(([x]) => x === z)?.[1] ?? 0
  const setFood = (z: string, n: number) => setPicked((l) => {
    const v = Math.max(0, Math.min(9, n))
    if (!v) return l.filter(([x]) => x !== z)
    return l.some(([x]) => x === z) ? l.map(([x, c]) => [x, x === z ? v : c] as [string, number]) : [...l, [z, v]]
  })
  const setDrink = (z: string, n: number) => setDrinks((m) => ({ ...m, [z]: Math.max(0, Math.min(9, n)) }))
  const dsel = DRINKS.map((d) => [d, drinks[d.zh] ?? 0] as [Drink, number]).filter(([, n]) => n > 0)
  const sentence = composeTabOrder(picked, point, dsel, req)
  const koName = (z: string) => foods.find((f) => f.nameZh === z)?.nameKo.split('(')[0] ?? z
  const ko = [...picked.map(([z, n]) => `${koName(z)} ${n}`), ...(point ? [`이거(가리키며) ${point}`] : []), ...dsel.map(([d, n]) => `${d.ko.split('(')[0]} ${n}`)].join(' · ')
  const extra = picked.filter(([z]) => !foods.some((f) => f.nameZh === z))
  const tabCount = (t: OrderTab) => t === 'food' ? picked.reduce((a, [, n]) => a + n, 0) + point : t === 'req' ? req.length : dsel.filter(([d]) => tabOf(d) === t).reduce((a, [, n]) => a + n, 0)
  const any = picked.length > 0 || point > 0 || dsel.length > 0
  if (show) return (
    <ShowCard header="종업원에게 보여주세요" onClose={() => setShow(false)} actions={<button className="btn" onClick={() => copyText(sentence)}><Copy size={16} />복사</button>}>
      <div className="phrase zh">{sentence}</div>
      <div className="sub">{ko}</div>
    </ShowCard>
  )
  const Row = ({ zh, koText, note, n, onSet }: { zh: string; koText: string; note?: string; n: number; onSet: (n: number) => void }) => (
    <div className={`li order-row ${n ? 'on' : ''}`} data-testid="item" data-zh={zh} onClick={() => onSet(n + 1)} style={{ cursor: 'pointer', background: n ? 'var(--primary-c)' : undefined }}>
      <div className="grow" style={{ minWidth: 0 }}>
        <div className="zh bold" style={{ fontSize: '1.15em' }}>{zh}</div>
        <div className="small">{koText}{note ? <span className="tiny muted"> · {note}</span> : null}</div>
      </div>
      <div className="row" style={{ gap: 6, flex: 'none' }} onClick={(e) => e.stopPropagation()}>
        {n > 0 && <><button className="iconbtn stepper" aria-label="빼기" data-testid="minus" onClick={() => onSet(n - 1)}><Minus size={20} /></button>
          <span className="bold" data-testid="qty" style={{ minWidth: 22, textAlign: 'center', fontSize: '1.2em' }}>{n}</span></>}
        <button className="iconbtn stepper" aria-label="더하기" data-testid="plus" onClick={() => onSet(n + 1)} style={{ background: 'var(--primary)', color: 'var(--on-primary)' }}><Plus size={20} /></button>
      </div>
    </div>
  )
  const groups = [...new Set(DRINKS.filter((d) => tabOf(d) === tab).map((d) => d.group))]
  return (
    <div className="page">
      <TopBar title="주문 문장 만들기" />
      {/* 문장은 늘 위에 보이게 */}
      <div style={{ position: 'sticky', top: 0, zIndex: 6, background: 'var(--bg)' }}>
        <div className="pad" style={{ paddingBottom: 6 }}><div className="card mint">
          <div className="zh bold" data-testid="order-sentence" style={{ fontSize: '1.45em', lineHeight: 1.35 }}>{sentence}</div>
          {ko && <div className="small" style={{ marginTop: 4 }}>{ko}</div>}
          <div className="wrap" style={{ marginTop: 8 }}>
            <button className="btn primary" onClick={() => setShow(true)}><Maximize2 size={16} />크게 보여주기</button>
            <button className="btn" onClick={() => copyText(sentence)}><Copy size={16} />복사</button>
            {any && <button className="btn ghost" onClick={() => { setPicked([]); setPoint(0); setDrinks({}); setReq([]) }}>비우기</button>}
          </div>
        </div></div>
        {/* 다섯 탭이 한 줄에 다 보이게(가로 스크롤 없음) */}
        <div className="row" data-testid="order-tabs" style={{ gap: 6, padding: '4px 16px 8px', flexWrap: 'nowrap' }}>
          {ORDER_TABS.map(([k, t]) => <button key={k} className={`chip ${tab === k ? 'on' : ''}`} onClick={() => setTab(k)} style={{ flex: '1 1 auto', fontSize: '.98em', padding: '9px 6px', whiteSpace: 'nowrap' }}>{t}{tabCount(k) ? ` ${tabCount(k)}` : ''}</button>)}
        </div>
      </div>
      <div className="pad"><div className="card flat" style={{ padding: 0 }}>
        {tab === 'req' ? ORDER_REQUESTS.map(([z, k]) => {
          const on = req.includes(z)
          return <div key={z} className="li" data-testid="req" data-zh={z} onClick={() => setReq((r) => (on ? r.filter((x) => x !== z) : [...r, z]))}
            style={{ cursor: 'pointer', background: on ? 'var(--primary-c)' : undefined }}>
            <div className="grow"><div className="zh bold" style={{ fontSize: '1.15em' }}>{z}</div><div className="small">{k}</div></div>
            <span className="iconbtn stepper" style={{ background: on ? 'var(--primary)' : 'var(--card-2)', color: on ? 'var(--on-primary)' : 'var(--ink-3)' }}>{on ? <Check size={20} /> : <Plus size={20} />}</span>
          </div>
        }) : tab === 'food' ? <>
          <Row zh="这个" koText="이거 (메뉴판을 손으로 가리키며)" n={point} onSet={setPoint} />
          {extra.map(([z, n]) => <Row key={z} zh={z} koText="메뉴판에서 고른 음식" n={n} onSet={(v) => setFood(z, v)} />)}
          {foods.map((f) => <Row key={f.id} zh={f.nameZh} koText={f.nameKo.split('(')[0]} note={f.region} n={count(f.nameZh)} onSet={(v) => setFood(f.nameZh, v)} />)}
        </> : groups.map((g) => <div key={g}>
          {groups.length > 1 && <div className="tiny muted" style={{ padding: '8px 14px 2px' }}>{g}</div>}
          {DRINKS.filter((d) => d.group === g).map((d) => <Row key={d.zh} zh={d.zh} koText={d.ko} note={d.note} n={drinks[d.zh] ?? 0} onSet={(v) => setDrink(d.zh, v)} />)}
        </div>)}
      </div></div>
      {tab === 'liquor' && <p className="pad tiny faint" style={{ margin: 0 }}>고량주(백주)는 40도 이상이라 작은 병(小瓶)으로 주문한다.</p>}
      <p className="pad tiny faint">항목을 누르거나 + 로 담고 − 로 줄인다. 요청사항(안 맵게·포장 등)은 눌러서 넣고 뺀다. 문장은 위에서 바로 바뀐다.</p>
    </div>
  )
}
