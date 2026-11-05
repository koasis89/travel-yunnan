import { useEffect, useMemo, useRef } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Bus, Car, CarTaxiFront, CheckCircle2, Clock, Footprints, MapPin, MessageSquareText, Mountain, PenLine, TrainFront } from 'lucide-react'
import { DayChips, TopBar } from '../components/ui'
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
  const sel = sp.get('day') ?? todayId ?? data.days[0].id
  const day = data.day(sel) ?? data.days[0]
  const rows = data.scheduleOf(day.id)
  const current = day.id === todayId ? findNow(rows, now.slice(11)).current : null
  const [done, setDone] = useStored<string[]>('done', [])
  const curRef = useRef<HTMLDivElement>(null)
  useEffect(() => { curRef.current?.scrollIntoView({ block: 'center' }) }, [sel])

  return (
    <div className="page">
      <TopBar title="일정" back={false} />
      <DayChips days={data.days} selected={day.id} todayId={todayId} onSelect={(id) => id && setSp({ day: id }, { replace: true })} />
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
    </div>
  )
}

function RowCard({ r, isCurrent, isDone, toggle }: { r: Row; isCurrent: boolean; isDone: boolean; toggle: () => void }) {
  const data = useData()
  const links = useMemo(() => scheduleLinks(data, r), [data, r])
  const msg = useMemo(() => messagePhrase(data, r), [data, r])
  const tpls = data.templatesFor(r)
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
        {r.via && r.via !== '-' && <div className="small muted">경유: {r.via}</div>}
        {r.transport && r.transport !== '-' && <div className="row small" style={{ marginTop: 2 }}>{transportIcon(r.transport)}{[r.transport, r.duration].filter((x) => x && x !== '-').join(' · ')}</div>}
        {r.tip && r.tip !== '-' && <div className="small" style={{ marginTop: 4 }}>{r.tip}</div>}
        {(links.length > 0 || msg || tpls.length > 0 || r.amapUrl) && (
          <div className="wrap" style={{ marginTop: 10 }}>
            {links.map((l) => <Link key={l.type + l.id} className="linkchip" to={`/taxi/${l.type}/${l.id}`}><CarTaxiFront size={15} />{l.label}</Link>)}
            {tpls.map((t) => <Link key={t.id} className="linkchip tpl" to={`/templates?link=${t.id}`}><PenLine size={15} />위챗: {t.label}</Link>)}
            {msg && <Link className="linkchip" to={`/show/${msg.id}`}><MessageSquareText size={15} />매니저에게 보여주기</Link>}
            {r.amapUrl && <a className="linkchip" href={r.amapUrl} target="_blank" rel="noreferrer"><MapPin size={15} />고덕지도</a>}
          </div>
        )}
      </div>
    </div>
  )
}
