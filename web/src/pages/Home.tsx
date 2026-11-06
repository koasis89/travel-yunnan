import { Link } from 'react-router-dom'
import { BedDouble, CalendarDays, CarTaxiFront, Clock, Shirt, Thermometer, TrainFront, Car, TriangleAlert, UtensilsCrossed } from 'lucide-react'
import { Banner, BrandHeader, Section } from '../components/ui'
import { useData, useNow, useSettings } from '../store/data'
import { findNow, focusDay, formatLeft, nextMove, resolvePhase } from '../domain/tripTime'
import { mealFor, slotFor } from '../domain/cards'
import { DayDocsLink } from './Docs'
import { DaySights } from './Sights'

export function Home() {
  const data = useData(); const now = useNow(); const [settings] = useSettings()
  const phase = resolvePhase(now, data.days)
  const day = focusDay(phase, data.days)
  const rows = data.scheduleOf(day.id)
  const st = phase.kind === 'on' ? findNow(rows, now.slice(11)) : { current: null, next: rows[0] ?? null }
  const move = nextMove(now, data.days, (id) => data.scheduleOf(id))
  const lodging = data.lodging(day.lodgingId)
  const hour = Number(now.slice(11, 13))
  const meal = mealFor(data, day.id, hour)
  const cautions = [...rows.filter((r) => r.tip.includes('★')).map((r) => `${r.timeLabel}  ${r.tip}`), ...data.notes.filter((n) => n.dayIds.includes(day.id)).map((n) => `${n.title}: ${n.body}`)]
  const status = phase.kind === 'before' ? `출발까지 D-${phase.daysLeft} · 12/16(수) 쿤밍 도착` : phase.kind === 'on' ? `${day.id} · ${day.dateLabel} · ${day.route}` : '여행이 끝났다. 수고했다!'
  const [, m, d] = now.slice(0, 10).split('-')

  return (
    <div className="page">
      <BrandHeader status={status} sub={`현지 시각 ${Number(m)}/${Number(d)} ${now.slice(11)} · 한국보다 1시간 늦다`} />
      <div className="stack pad" style={{ marginTop: 12 }}>
        {settings.override && <Banner icon={<Clock size={18} />}>날짜 지정 모드다. 설정에서 해제하면 실제 시각으로 돌아간다.</Banner>}
        <Link to={`/schedule?day=${day.id}`} className="card mint" style={{ textDecoration: 'none' }}>
          <h3>{phase.kind === 'on' ? '오늘 ' : ''}{day.id} {day.dateLabel} · {day.route}</h3>
          <div className="small">{day.summary}</div>
          <div className="divider" />
          <div className="stack small" style={{ gap: 4 }}>
            <div className="row"><Thermometer size={16} />{day.region} {day.altitude} · 낮 {day.tempDay?.replace('약 ', '')} / 밤 {day.tempNight?.replace('약 ', '')}</div>
            {day.outfit && <div className="row"><Shirt size={16} />{day.outfit}</div>}
            <div className="row"><BedDouble size={16} />{day.lodgingLabel || '숙박 없음(귀국)'}</div>
          </div>
        </Link>
      </div>

      <Section>{phase.kind === 'on' ? '지금 일정' : `${day.id} 첫 일정`}</Section>
      <div className="pad">
        <Link to={`/schedule?day=${day.id}`} className="card" style={{ display: 'block', textDecoration: 'none', color: 'inherit' }}>
          {st.current ? (<>
            <div className="small bold" style={{ color: 'var(--primary)' }}>{st.current.timeLabel}</div>
            <div className="bold" style={{ fontSize: '1.15em' }}>{[st.current.from, st.current.to].filter((x) => x && x !== '-').join(' → ')}</div>
            {st.current.transport && st.current.transport !== '-' && <div className="small">{st.current.transport} · {st.current.duration}</div>}
            {st.current.tip && <div className="small muted">{st.current.tip}</div>}
          </>) : <div className="muted small">지금 진행 중인 일정이 없다</div>}
          {st.next && <><div className="divider" /><div className="small">다음 &nbsp;{st.next.timeLabel} &nbsp;{[st.next.to, st.next.via, st.next.from].find((x) => x && x !== '-') ?? st.next.tip}</div></>}
        </Link>
      </div>

      <div className="pad" style={{ marginTop: 12 }}><DayDocsLink dayId={day.id} /></div>
      <DaySights dayId={day.id} />

      {move && (<>
        <Section>다음 이동</Section>
        <div className="pad">
          <Link to={`/schedule?day=${move.day.id}`} className="card sand row" style={{ textDecoration: 'none', gap: 12 }}>
            {move.row.transport.includes('열차') ? <TrainFront size={30} /> : <Car size={30} />}
            <div className="grow">
              <div className="bold">{move.day.id} {move.row.timeLabel} · {move.row.transport}</div>
              <div className="small">{move.row.from} → {move.row.to}</div>
            </div>
            <div className="bold" style={{ color: 'var(--tertiary)', whiteSpace: 'nowrap' }}>{formatLeft(move.minutesLeft)}</div>
          </Link>
        </div>
      </>)}

      <Section>바로가기</Section>
      <div className="tiles">
        <Link className="tile" style={{ background: 'var(--primary-c)', color: 'var(--on-primary-c)' }} to={lodging ? `/taxi/lodging/${lodging.id}` : '/taxi/place/kmg_airport'}>
          <span className="ic"><CarTaxiFront size={20} /></span>
          <div><div className="t">{lodging ? '숙소 택시 카드' : '공항 택시 카드'}</div><div className="s">{lodging ? lodging.nameKo.split(' (')[0] : '쿤밍 창수이 공항'}</div></div>
        </Link>
        <Link className="tile" style={{ background: 'var(--tertiary-c)' }} to={meal ? `/taxi/meal/${meal.id}` : `/meals?day=${day.id}`}>
          <span className="ic"><UtensilsCrossed size={20} /></span>
          <div><div className="t">{slotFor(hour)} 식당 카드</div><div className="s">{meal?.name.split('(')[0] ?? '선택지 없음'}</div></div>
        </Link>
        <Link className="tile" style={{ background: 'var(--secondary-c)', color: 'var(--on-secondary-c)' }} to={`/schedule?day=${day.id}`}>
          <span className="ic"><CalendarDays size={20} /></span>
          <div><div className="t">{day.id} 일정</div><div className="s">{rows.length}개 일정</div></div>
        </Link>
        <Link className="tile" style={{ background: 'var(--card-2)' }} to="/transport">
          <span className="ic"><TrainFront size={20} /></span>
          <div><div className="t">교통·알림</div><div className="s">C446 · C644 · 캘린더</div></div>
        </Link>
      </div>

      {cautions.length > 0 && (<>
        <Section>{day.id} 주의</Section>
        <div className="stack pad">{cautions.slice(0, 5).map((c, i) => <Banner key={i} tone="rose" icon={<TriangleAlert size={18} />}>{c}</Banner>)}</div>
      </>)}

      {phase.kind !== 'on' && (<>
        <Section>일정 한눈에</Section>
        <div className="card pad" style={{ margin: '0 16px', padding: 0 }}>
          <div className="list">
            {data.days.map((d) => (
              <Link key={d.id} to={`/schedule?day=${d.id}`} className="li">
                <span className="bold" style={{ color: 'var(--primary)', width: 32 }}>{d.id}</span>
                <span className="small muted" style={{ width: 80 }}>{d.dateLabel}</span>
                <span className="grow small">{d.route}</span>
              </Link>
            ))}
          </div>
        </div>
      </>)}
    </div>
  )
}
