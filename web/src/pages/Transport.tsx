import { Link } from 'react-router-dom'
import { Bus, CalendarDays, CalendarPlus, CarTaxiFront, TrainFront } from 'lucide-react'
import { Banner, Section, TopBar, download } from '../components/ui'
import { useData, useNow } from '../store/data'
import { buildIcs } from '../domain/ics'

export function Transport() {
  const data = useData(); const now = useNow()
  const trains = data.schedule.filter((r) => r.transport.includes('열차') && /C\d+/.test(r.transport))
  const groups = data.transport.reduce<Record<string, typeof data.transport>>((a, t) => ((a[t.group] ??= []).push(t), a), {})
  const alt = data.notes.find((n) => n.title === '빵차 대안')
  return (
    <div className="page">
      <TopBar title="교통·열차·알림" />
      <Section>열차</Section>
      <div className="stack pad">
        {trains.map((t) => {
          const day = data.day(t.dayId)
          const code = t.transport.match(/C\d+/)?.[0]
          const station = t.from.includes('쿤밍') ? 'kunming_station' : 'shangrila_station'
          return (
            <div key={t.id} className="card mint">
              <div className="row bold" style={{ fontSize: '1.15em' }}><TrainFront size={22} />{code} · {t.dayId} {day?.dateLabel}</div>
              <div style={{ marginTop: 4 }}>{t.timeLabel} &nbsp;{t.from} → {t.to}</div>
              <div className="small">{t.duration} · {t.tip}</div>
              <div className="tiny" style={{ opacity: .8, marginTop: 4 }}>출발 30분 전 역 도착, 20분 전 개찰 마감, 여권으로 유인 게이트</div>
              <div className="wrap" style={{ marginTop: 10 }}>
                <Link className="btn" to={`/taxi/place/${station}`}><CarTaxiFront size={16} />역 택시 카드</Link>
                <Link className="btn" to={`/schedule?day=${t.dayId}`}><CalendarDays size={16} />일정 보기</Link>
              </div>
            </div>
          )
        })}
      </div>
      <Section>일정 알림 → 휴대폰 캘린더</Section>
      <div className="stack pad">
        <Banner icon={<CalendarPlus size={18} />}>웹에서는 알림을 직접 울릴 수 없다. 아래 파일을 받아 열면 휴대폰 캘린더에 {data.notifications.length}건이 현지 시각 알림과 함께 등록된다.</Banner>
        <button className="btn primary block" onClick={() => download('yunnan-alerts.ics', buildIcs(data.notifications), 'text/calendar')}><CalendarPlus size={18} />캘린더 파일 받기 (.ics)</button>
        <div className="card flat" style={{ padding: 0 }}>
          {data.notifications.map((n) => (
            <div key={n.id} className="li" style={{ opacity: n.at < now ? 0.45 : 1 }}>
              <div className="grow">
                <div className="tiny faint">{n.dayId} · {Number(n.at.slice(5, 7))}/{Number(n.at.slice(8, 10))} {n.at.slice(11)}{n.at < now ? ' · 지남' : ''}</div>
                <div className="bold small">{n.title}</div>
                <div className="tiny muted">{n.body}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
      {Object.entries(groups).map(([g, list]) => (<div key={g}>
        <Section>{g}</Section>
        <div className="card pad" style={{ margin: '0 16px' }}>
          <table className="tbl"><tbody>
            {list.map((t) => (
              <tr key={t.id}><td>{t.title}<div className="tiny faint">{t.unitPrice != null ? `${t.unitPrice.toLocaleString()} ${t.currency}/${t.unit} × ${t.qty}` : ''}{t.note ? ` · ${t.note}` : ''}</div></td>
                <td className="num bold">{Math.round(t.totalKrw).toLocaleString()}원</td></tr>
            ))}
          </tbody></table>
        </div>
      </div>))}
      {alt && <><Section>빵차 대안</Section><div className="pad"><Banner icon={<Bus size={18} />}>{alt.body}</Banner></div></>}
      <div className="pad tiny faint" style={{ marginTop: 12 }}>
        {data.transportNotes.map((n, i) => <div key={i}>※ {n}</div>)}
        <div>환율 ¥1 = {data.rates.CNY}원 · $1 = {data.rates.USD}원 · {data.rates.persons}인</div>
      </div>
    </div>
  )
}
