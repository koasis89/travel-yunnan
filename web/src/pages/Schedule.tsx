import { useEffect, useMemo, useRef } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Copy, Bus, Car, CarTaxiFront, CheckCircle2, Clock, Footprints, Landmark, MapPin, UtensilsCrossed, MessageSquareText, Mountain, PenLine, QrCode, TrainFront } from 'lucide-react'
import { CheckList, DayChips, Ko, TopBar, copyText } from '../components/ui'
import { DayDocsLink } from './Docs'
import { useData, useNow } from '../store/data'
import { useStored } from '../store/local'
import { findNow, resolvePhase } from '../domain/tripTime'
import { messagePhrase, scheduleLinks } from '../domain/cards'
import type { Schedule as Row } from '../domain/types'

export function transportIcon(t: string, size = 16) {
  if (t.includes('열차')) return <TrainFront size={size} />
  if (t.includes('셔틀') || t.includes('버스')) return <Bus size={size} />
  if (t.includes('케이블카')) return <Mountain size={size} />
  if (t.includes('도보')) return <Footprints size={size} />
  if (t.includes('택시') || /didi/i.test(t)) return <CarTaxiFront size={size} />
  if (t.includes('차') || t.includes('픽업')) return <Car size={size} />
  return <Clock size={size} />
}

export function Schedule() {
  const data = useData(); const now = useNow()
  const [sp, setSp] = useSearchParams()
  const phase = resolvePhase(now, data.days)
  const todayId = phase.kind === 'on' ? phase.day.id : null
  // 출발 전에 아직 할 사전 일정이 있으면 '사전'부터 연다
  const preOpen = phase.kind === 'before' && data.pretrip.some((x) => x.date >= now.slice(0, 10))
  const sel = sp.get('day') ?? todayId ?? (preOpen ? 'pre' : data.days[0].id)
  const day = data.day(sel) ?? data.days[0]
  const rows = data.scheduleOf(day.id)
  const current = day.id === todayId ? findNow(rows, now.slice(11)).current : null
  const [done, setDone] = useStored<string[]>('done', [])
  const curRef = useRef<HTMLDivElement>(null)
  useEffect(() => { curRef.current?.scrollIntoView({ block: 'center' }) }, [sel])

  return (
    <div className="page">
      <TopBar title="일정" back={false} />
      <DayChips days={data.days} selected={sel === 'pre' ? 'pre' : day.id} todayId={todayId} preLabel={data.pretrip.length ? '사전' : undefined} onSelect={(id) => id && setSp({ day: id }, { replace: true })} />
      {sel === 'pre' ? <PreTripList /> : <>
      <div className="pad" style={{ padding: '4px 20px 10px' }}>
        <div className="bold" style={{ fontSize: '1.15em' }}>{day.id} {day.dateLabel} · {day.route}</div>
        <div className="small muted">숙박: {day.lodgingLabel || '-'}</div>
        <div className="tiny faint">길게 누르면 완료 표시</div>
      </div>
      <div style={{ padding: '0 16px 10px' }}><DayDocsLink dayId={day.id} /></div>
      <div className="stack" style={{ padding: '0 16px 0 10px', gap: 8 }}>
        {rows.map((r) => (
          <div key={r.id} ref={r.id === current?.id ? curRef : undefined}>
            <RowCard r={r} isCurrent={r.id === current?.id} isDone={done.includes(r.id)}
              toggle={() => setDone((d) => (d.includes(r.id) ? d.filter((x) => x !== r.id) : [...d, r.id]))} />
          </div>
        ))}
      </div>
      <NextDayPrep dayId={day.id} />
      </>}
    </div>
  )
}

/** 사전 일정(여행 전 할 일): 날짜·시각·할 일·위챗 문장 바로가기. 완료 표시는 일정과 같은 'done' 저장소 */
export function PreTripList() {
  const data = useData(); const [done, setDone] = useStored<string[]>('done', [])
  return (
    <>
      <div className="pad" style={{ padding: '4px 20px 10px' }}>
        <div className="bold" style={{ fontSize: '1.15em' }}>사전 일정 · 출발 전에 할 일</div>
        <div className="tiny faint">마친 일은 '완료'를 누르세요</div>
      </div>
      <div className="stack" style={{ padding: '0 16px', gap: 8 }}>
        {data.pretrip.map((x) => {
          const tl = data.templateLink(x.templateLinkId); const ok = done.includes(x.id)
          return (
            <div key={x.id} className="card" data-testid="pretrip" style={{ opacity: ok ? 0.6 : 1 }}>
              <div className="row"><span className="bold" style={{ color: 'var(--primary)' }}>{x.dateLabel} {x.time}</span><span className="grow" />{ok && <span className="tag">완료</span>}</div>
              <h3 style={{ margin: '4px 0' }}>{x.title}</h3>
              <div className="small">{x.detail}</div>
              {x.tips.map((t) => <div key={t} className="tiny muted" style={{ marginTop: 4 }}>· {t}</div>)}
              {x.copy && x.copy.length > 0 && <div className="card flat" data-testid="pretrip-copy" style={{ padding: 0, marginTop: 8 }}>
                {x.copy.map((c) => (
                  <div key={c.label} className="li" style={{ gap: 10 }}>
                    <div className="grow" style={{ minWidth: 0 }}><div className="tiny muted">{c.label}</div><div className="bold zh">{c.value}</div>{c.note && <div className="tiny faint">{c.note}</div>}</div>
                    <button className="btn" style={{ flex: 'none' }} onClick={() => copyText(c.value, `${c.label} 복사 완료`)}><Copy size={16} />복사</button>
                  </div>
                ))}
              </div>}
              {x.checklist && x.checklist.length > 0 && <div style={{ marginTop: 8 }}><CheckList items={x.checklist} testId="pretrip-check" /></div>}
              <div className="wrap" style={{ marginTop: 10 }}>
                {x.link === '/arrival-card' && <Link className="btn primary" to="/arrival-card"><QrCode size={16} />방법·붙여 넣을 값</Link>}
                {tl && <Link className="btn primary" to={`/templates?link=${tl.id}`}><PenLine size={16} />위챗: 가이드 첫 연락 문장</Link>}
                <button className="btn" onClick={() => setDone((d) => (d.includes(x.id) ? d.filter((y) => y !== x.id) : [...d, x.id]))}>{ok ? '완료 취소' : '완료'}</button>
              </div>
              {x.notificationId && <div className="tiny faint" style={{ marginTop: 6 }}>캘린더 알림 파일(교통·열차·알림)에 {x.dateLabel} {x.time} 알림이 들어 있다.</div>}
            </div>
          )
        })}
        {data.checklists.departure.items.length > 0 && <CheckList title={data.checklists.departure.title} items={data.checklists.departure.items} testId="departure-check" />}
      </div>
    </>
  )
}

/** 일정 맨 아래: 다음날 소지품·주의사항 체크리스트 */
function NextDayPrep({ dayId }: { dayId: string }) {
  const data = useData(); const nd = data.nextDayOf(dayId)
  if (!nd) return null
  const next = nd.nextId ? data.day(nd.nextId) : undefined
  return (
    <div style={{ padding: '14px 16px 0' }}>
      <CheckList title={`🧳 ${nd.title}`} sub={next ? `${next.id} ${next.dateLabel} · ${next.route} 준비 — 다음날 소지품·주의사항` : '오늘 소지품·주의사항'} items={nd.items} testId="nextday-check" />
    </div>
  )
}

function RowCard({ r, isCurrent, isDone, toggle }: { r: Row; isCurrent: boolean; isDone: boolean; toggle: () => void }) {
  const data = useData()
  const links = useMemo(() => scheduleLinks(data, r), [data, r])
  const msg = useMemo(() => messagePhrase(data, r), [data, r])
  const tpls = data.templatesFor(r)
  const sights = data.sightsFor(r)
  const eats = data.restaurantsFor(r)
  const press = useRef<number | undefined>(undefined)
  const start = () => { press.current = window.setTimeout(() => { toggle(); navigator.vibrate?.(20) }, 550) }
  const cancel = () => window.clearTimeout(press.current)
  const title = [r.from, r.to].filter((x) => x && x !== '-').join(' → ')
  return (
    <div className="tl">
      <div className="axis"><div className={`dot ${isDone ? 'done' : isCurrent ? 'now' : ''}`} /><div className="line" /></div>
      <div className={`card ${isCurrent ? 'current' : ''} ${isDone ? 'done-card' : ''}`} onPointerDown={start} onPointerUp={cancel} onPointerLeave={cancel} onContextMenu={(e) => e.preventDefault()}>
        <div className="row">
          <span className="time">{r.timeLabel}</span>
          {isCurrent && <span className="small bold" style={{ color: 'var(--tertiary)' }}>지금</span>}
          <span className="grow" />
          {isDone && <CheckCircle2 size={20} color="var(--primary)" />}
        </div>
        {title && <div className="bold" style={{ marginTop: 2 }}>{title}</div>}
        {[r.from, r.to].some((x) => data.ko(x)) && <div className="tiny muted ko-gloss" data-testid="ko-gloss">{[r.from, r.to].filter((x) => x && x !== '-').map((x) => data.ko(x) || x).join(' → ')}</div>}
        {r.via && r.via !== '-' && <div className="small muted">경유: {r.via}</div>}
        <Ko zh={r.via} />
        {r.transport && r.transport !== '-' && <div className="row small" style={{ marginTop: 2 }}>{transportIcon(r.transport)}{[r.transport, r.duration].filter((x) => x && x !== '-').join(' · ')}</div>}
        {r.tip && r.tip !== '-' && <div className="small" style={{ marginTop: 4 }}>{r.tip}</div>}
        {(links.length > 0 || msg || tpls.length > 0 || r.amapUrl || sights.length > 0 || eats.length > 0) && (
          <div className="wrap" style={{ marginTop: 10 }}>
            {links.map((l) => <Link key={l.type + l.id} className="linkchip" to={`/taxi/${l.type}/${l.id}`}><CarTaxiFront size={15} />{l.label}</Link>)}
            {eats.map((x) => <Link key={x.id} className="linkchip" to={`/eats/${x.id}`}><UtensilsCrossed size={15} />{x.nameKo.split('(')[0]}</Link>)}
            {sights.map((x) => <Link key={x.id} className="linkchip" to={`/sights/${x.id}`}><Landmark size={15} />{x.nameKo}</Link>)}
            {tpls.map((t) => <Link key={t.id} className="linkchip tpl" to={`/templates?link=${t.id}`}><PenLine size={15} />위챗: {t.label}</Link>)}
            {msg && <Link className="linkchip" to={`/show/${msg.id}`}><MessageSquareText size={15} />매니저에게 보여주기</Link>}
            {r.amapUrl && <a className="linkchip" href={r.amapUrl} target="_blank" rel="noreferrer"><MapPin size={15} />지도</a>}
          </div>
        )}
      </div>
    </div>
  )
}
