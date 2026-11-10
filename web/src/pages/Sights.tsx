import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { BookOpenText, CarTaxiFront, Clock, ExternalLink, FilePlus, FileText, Landmark, Lightbulb, MapPin, Sparkles, Ticket, Timer } from 'lucide-react'
import { DayChips, TopBar, toast } from '../components/ui'
import { addDoc, updateDoc, useDocs } from '../store/docs'
import { docForBlog, type Blog } from '../domain/docs'
import { useData, useNow } from '../store/data'
import { resolvePhase } from '../domain/tripTime'
import type { Sight } from '../domain/types'

const img = (s: Sight) => (s.image ? `${import.meta.env.BASE_URL}images/${s.image}` : null)

export function SightCard({ s, compact }: { s: Sight; compact?: boolean }) {
  const src = img(s)
  return (
    <Link to={`/sights/${s.id}`} className="card" data-testid="sight-card" style={{ display: 'block', padding: 0, overflow: 'hidden', textDecoration: 'none', color: 'inherit', ...(compact ? { width: 220, flex: 'none' } : {}) }}>
      {src ? <img src={src} alt={s.nameKo} loading="lazy" style={{ width: '100%', height: compact ? 120 : 170, objectFit: 'cover', display: 'block' }} />
        : <div className="center muted" style={{ height: compact ? 120 : 110, display: 'grid', placeItems: 'center', background: 'var(--sand, #f1ece1)' }}><Landmark size={34} /></div>}
      <div style={{ padding: '10px 12px' }}>
        <div className="bold">{s.nameKo} <span className="small muted">{s.nameZh}</span></div>
        {!compact && <div className="small muted" style={{ marginTop: 2, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{s.summary}</div>}
        <div className="tiny faint" style={{ marginTop: 4 }}>{s.dayIds.join('·')} · {s.duration}</div>
      </div>
    </Link>
  )
}

export function Sights() {
  const data = useData(); const now = useNow(); const [sp, setSp] = useSearchParams()
  const p = resolvePhase(now, data.days); const todayId = p.kind === 'on' ? p.day.id : null
  const day = sp.get('day')
  const list = day ? data.sightsOf(day) : data.sights
  const regions = useMemo(() => [...new Set(list.map((s) => s.region))], [list])
  return (
    <div className="page">
      <TopBar title="관광지 안내" />
      <DayChips days={data.days} selected={day} todayId={todayId} allLabel="전체" onSelect={(id) => setSp(id ? { day: id } : {}, { replace: true })} />
      {regions.map((r) => (
        <div key={r}>
          <div className="section">{r}</div>
          <div className="stack pad">{list.filter((s) => s.region === r).map((s) => <SightCard key={s.id} s={s} />)}</div>
        </div>
      ))}
      <p className="pad tiny faint">요금·운영 시간은 참고용이며 바뀔 수 있다. 사진은 대부분 Flickr 의 CC 라이선스 사진이고 일부는 여행자가 직접 찍은 사진이다. 각 관광지 화면 아래에 촬영자와 라이선스를 적었다.</p>
    </div>
  )
}

export function SightView() {
  const { id = '' } = useParams(); const data = useData()
  const s = data.sight(id)
  if (!s) return <div className="page"><TopBar title="관광지" /><div className="pad muted">찾을 수 없습니다</div></div>
  const src = img(s); const place = data.place(s.placeId)
  const rows = data.schedule.filter((r) => s.dayIds.includes(r.dayId) && s.keywords.some((k) => `${r.from} ${r.via} ${r.to} ${r.tip}`.includes(k)))
  return (
    <div className="page">
      <TopBar title={s.nameKo} />
      {src && <figure style={{ margin: 0 }}>
        <img src={src} alt={s.nameKo} style={{ width: '100%', maxHeight: 340, objectFit: 'cover', display: 'block' }} />
        {s.credit && <figcaption className="tiny faint" style={{ padding: '4px 16px' }}>
          사진: {s.credit.url ? <a href={s.credit.url} target="_blank" rel="noreferrer">{s.credit.title}</a> : s.credit.title} · {s.credit.author} · {s.credit.license}
        </figcaption>}
      </figure>}
      <div className="pad stack" style={{ marginTop: 8 }}>
        <div><div className="bold" style={{ fontSize: '1.3em' }}>{s.nameKo}</div><div style={{ fontSize: '1.4em', fontWeight: 700 }}>{s.nameZh}</div></div>
        <p style={{ margin: 0 }}>{s.summary}</p>
        <div className="card flat stack" style={{ gap: 6 }}>
          <div className="row small"><Clock size={16} />{s.hours}</div>
          <div className="row small"><Ticket size={16} />{s.fee}</div>
          <div className="row small"><Timer size={16} />머무는 시간 {s.duration}</div>
        </div>
        {place && <Link className="btn primary block" to={`/taxi/place/${place.id}`}><CarTaxiFront size={18} />택시 카드 · {place.nameZh}</Link>}
        {(s.amapUrl ?? place?.amapUrl) && <a className="btn block" href={(s.amapUrl ?? place?.amapUrl)!} target="_blank" rel="noreferrer"><MapPin size={18} />지도에서 보기(고덕지도)</a>}
      </div>
      {s.highlights.length > 0 && <><div className="section">볼거리</div>
        <div className="pad stack" style={{ gap: 6 }}>{s.highlights.map((h) => <div key={h} className="row small" style={{ alignItems: 'flex-start' }}><Sparkles size={16} style={{ flex: 'none', marginTop: 2 }} />{h}</div>)}</div></>}
      {s.tips.length > 0 && <><div className="section">팁·주의</div>
        <div className="pad stack" style={{ gap: 6 }}>{s.tips.map((h) => <div key={h} className="row small" style={{ alignItems: 'flex-start' }}><Lightbulb size={16} style={{ flex: 'none', marginTop: 2 }} />{h}</div>)}</div></>}
      {rows.length > 0 && <><div className="section">관련 일정</div>
        <div className="pad"><div className="card flat" style={{ padding: 0 }}>
          {rows.map((r) => <Link key={r.id} className="li" to={`/schedule?day=${r.dayId}`}><span className="small bold" style={{ color: 'var(--primary)', width: 110, flex: 'none' }}>{r.dayId} {r.timeLabel}</span><span className="small grow">{[r.from, r.to].filter((x) => x && x !== '-').join(' → ')}</span></Link>)}
        </div></div></>}
      <BlogSection blogs={data.blogsFor(s.id)} />
      <p className="pad tiny faint">요금·운영 시간은 참고용이며 바뀔 수 있다. 현장 안내를 따른다.</p>
    </div>
  )
}

/** 블로그 후기: 원문 열기 + 문서함에 넣은 PDF 보기(없으면 PDF 넣기). PDF 는 이 기기 문서함에만 저장된다 */
function BlogSection({ blogs }: { blogs: Blog[] }) {
  const data = useData(); const { docs } = useDocs(); const nav = useNavigate()
  const [busy, setBusy] = useState<string | null>(null)
  if (!blogs.length) return null
  const put = async (b: Blog, f?: File) => {
    if (!f) return
    setBusy(b.id)
    try {
      const m = await addDoc(f, data.days, { dayId: null })
      await updateDoc({ ...m, title: b.title, kind: '블로그', note: `${b.blog} · ${b.date}\n${b.url}` })
      toast('문서함 저장 완료'); nav(`/docs/${m.id}`)
    } catch { toast('PDF 저장 실패') } finally { setBusy(null) }
  }
  return (
    <>
      <div className="section row"><BookOpenText size={18} /><span>블로그 후기 ({blogs.length})</span></div>
      <div className="pad stack">
        {blogs.map((b) => {
          const d = docs ? docForBlog(b, docs) : undefined
          return (
            <div key={b.id} className="card stack" style={{ gap: 6 }} data-testid="blog-card">
              <div className="bold">{b.title}</div>
              <div className="tiny muted">{b.blog} · {b.date}</div>
              <div className="small">{b.summary}</div>
              <div className="wrap" style={{ marginTop: 4 }}>
                {d ? <Link className="btn primary" to={`/docs/${d.id}`} data-testid="blog-pdf"><FileText size={16} />PDF 보기</Link>
                  : <label className="btn" data-testid="blog-add"><FilePlus size={16} />{busy === b.id ? '넣는 중…' : 'PDF 넣기'}
                    <input type="file" accept="application/pdf,image/*" hidden disabled={busy != null} onChange={(e) => { put(b, e.target.files?.[0]); e.target.value = '' }} /></label>}
                <a className="btn" href={b.url} target="_blank" rel="noreferrer"><ExternalLink size={16} />{b.search ? '블로그 검색' : '원문 열기'}</a>
              </div>
            </div>
          )
        })}
        <p className="tiny faint" style={{ margin: 0 }}>PDF 는 'PDF 넣기'로 한 번 고르면 이 휴대폰의 문서함(분류: 블로그)에 저장되어 인터넷 없이 볼 수 있다. 문서함에 이미 넣은 PDF 는 제목으로 찾아 바로 연다. 글의 저작권은 블로그 글쓴이에게 있다.</p>
      </div>
    </>
  )
}

/** 홈용: 그날 볼 관광지 가로 목록 */
export function DaySights({ dayId }: { dayId: string }) {
  const data = useData(); const list = data.sightsOf(dayId)
  if (!list.length) return null
  return (
    <>
      <div className="section row"><span className="grow">{dayId} 관광지</span><Link className="small" to={`/sights?day=${dayId}`}>모두 보기</Link></div>
      <div className="row" style={{ overflowX: 'auto', gap: 10, padding: '0 16px 4px', alignItems: 'stretch' }}>{list.map((s) => <SightCard key={s.id} s={s} compact />)}</div>
    </>
  )
}
