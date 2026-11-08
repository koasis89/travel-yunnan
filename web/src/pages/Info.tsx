import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { CarTaxiFront, Copy, Hospital, MapPin, Maximize2, MessageSquareText, Mountain, Phone, ShieldAlert, Siren } from 'lucide-react'
import { Banner, Ko, Section, ShowCard, TopBar, copyText } from '../components/ui'
import type { Hospital as HospitalInfo } from '../domain/types'
import { useData } from '../store/data'
import { useStored } from '../store/local'

export function Packing() {
  const data = useData()
  const [checked, setChecked] = useStored<string[]>('pack', [])
  const [closed, setClosed] = useState<string[]>([])
  const groups = useMemo(() => data.packing.reduce<Record<string, typeof data.packing>>((a, p) => ((a[p.group] ??= []).push(p), a), {}), [data])
  const n = data.packing.filter((p) => checked.includes(p.id)).length
  return (
    <div className="page">
      <TopBar title="준비물·날씨" />
      <Section>12월 기온·옷차림</Section>
      <div className="stack pad">{data.weather.map((w) => (
        <div key={w.region} className="card"><div className="bold">{w.region} · {w.dates} · {w.altitude}</div>
          <div className="small">낮 {w.tempDay} / 밤 {w.tempNight} · {w.feature}</div><div className="small" style={{ color: 'var(--primary)' }}>{w.outfit}</div></div>
      ))}</div>
      <Section>준비물 {n} / {data.packing.length}</Section>
      <div className="pad"><div className="bar"><i style={{ width: `${(n / Math.max(1, data.packing.length)) * 100}%` }} /></div></div>
      {Object.entries(groups).map(([g, items]) => (
        <div key={g} className="card" style={{ margin: '12px 16px 0', padding: '4px 0' }}>
          <button className="li" style={{ width: '100%', border: 0, background: 'none' }} onClick={() => setClosed((c) => (c.includes(g) ? c.filter((x) => x !== g) : [...c, g]))}>
            <span className="bold grow" style={{ textAlign: 'left', color: 'var(--primary)' }}>{g}</span><span className="small">{items.filter((i) => checked.includes(i.id)).length}/{items.length}</span>
          </button>
          {!closed.includes(g) && items.map((p) => (
            <label key={p.id} className="check">
              <input type="checkbox" checked={checked.includes(p.id)} onChange={() => setChecked((c) => (c.includes(p.id) ? c.filter((x) => x !== p.id) : [...c, p.id]))} />
              <span className="grow"><span className={checked.includes(p.id) ? 'faint' : ''}>{p.name}</span><div className="tiny muted">{[p.qty, p.note].filter(Boolean).join(' · ')}</div></span>
            </label>
          ))}
        </div>
      ))}
    </div>
  )
}

export function Notes() {
  const data = useData()
  const byZh = (zh: string) => { const k = zh.replace(/[\s，,。？?！!]/g, ''); return data.phrases.find((p) => p.zh.replace(/[\s，,。？?！!]/g, '') === k) ?? data.phrases.find((p) => k.length >= 6 && p.zh.replace(/[\s，,。？?！!]/g, '').includes(k.slice(0, 10))) }
  const linked = data.notes.filter((n) => n.dayIds.length).sort((a, b) => a.dayIds[0].localeCompare(b.dayIds[0]))
  const common = data.notes.filter((n) => !n.dayIds.length)
  const card = (n: (typeof data.notes)[number]) => (
    <div key={n.id} className="card">
      <div className="row"><span className="bold" style={{ color: 'var(--primary)' }}>{n.title}</span>{n.dayIds.map((d) => <span key={d} className="tag">{d}</span>)}</div>
      <div style={{ marginTop: 4 }}>{n.body}</div>
      <div className="wrap" style={{ marginTop: 8 }}>{n.phrasesInline.map((z) => byZh(z)).filter(Boolean).map((p) => <Link key={p!.id} className="linkchip" to={`/show/${p!.id}`}><MessageSquareText size={15} />{p!.ko.slice(0, 16)}</Link>)}</div>
    </div>
  )
  return (
    <div className="page"><TopBar title="유의사항" />
      {linked.length > 0 && <><Section>일차와 연결된 항목</Section><div className="stack pad">{linked.map(card)}</div></>}
      <Section>공통</Section><div className="stack pad">{common.map(card)}</div>
    </div>
  )
}

export function Emergency() {
  const data = useData()
  const groups = data.emergency.contacts.reduce<Record<string, typeof data.emergency.contacts>>((a, c) => ((a[c.group] ??= []).push(c), a), {})
  return (
    <div className="page"><TopBar title="긴급 연락처" />
      <div className="wrap pad">
        <a className="btn" href="tel:120"><Hospital size={16} />구급차 120</a>
        <a className="btn" href="tel:110"><Siren size={16} />경찰 110</a>
        <a className="btn" href="tel:+82232100404"><Phone size={16} />영사콜센터</a>
      </div>
      {Object.entries(groups).map(([g, list]) => (<div key={g}>
        <Section>{g}</Section>
        <div className="card" style={{ margin: '0 16px', padding: 0 }}>
          {list.map((c) => <a key={c.id} className="li" href={`tel:${c.phone.replace(/[^\d+]/g, '')}`}>
            <span className="ic"><Phone size={18} /></span>
            <span className="grow"><div className="bold small">{c.name}</div><div className="tiny muted">{c.phone}{c.note ? ` · ${c.note}` : ''}</div></span></a>)}
        </div>
      </div>))}
      <div className="pad tiny faint" style={{ marginTop: 8 }}>{data.emergency.note}</div>
      {data.emergency.hospitals.length > 0 && <><Section>고산병 병원(산소 치료)</Section>
        <div className="stack pad">{data.emergency.hospitals.map((h) => <HospitalCard key={h.id} h={h} />)}</div></>}
      <Section>고산병</Section>
      <div className="stack pad">{data.emergency.altitude.map((a, i) => <Banner key={i} icon={<Mountain size={18} />}>{a}</Banner>)}</div>
      <div className="pad" style={{ marginTop: 12 }}><Banner tone="rose" icon={<ShieldAlert size={18} />}>여권 정보는 웹에 저장하지 않는다. 안드로이드 앱의 긴급·여권 화면이나 종이 사본을 쓴다.</Banner></div>
    </div>
  )
}

function HospitalCard({ h }: { h: HospitalInfo }) {
  const [show, setShow] = useState(false)
  if (show) return (
    <ShowCard header="병원 접수처에 보여주세요" onClose={() => setShow(false)} actions={<button className="btn" onClick={() => copyText(h.phrases.map((p) => p.zh).join('\n'))}><Copy size={16} />복사</button>}>
      {h.phrases.map((p) => <div key={p.zh} style={{ marginBottom: 14 }}><div className="sub zh">{p.zh}</div><div className="note">{p.ko}</div></div>)}
    </ShowCard>
  )
  return (
    <div className="card stack" data-testid="hospital" style={{ gap: 6 }}>
      <div className="bold">{h.nameKo} <span className="small muted">{h.region}</span></div>
      <div className="zh bold" style={{ fontSize: '1.2em' }}>{h.nameZh}</div>
      <div className="small zh">{h.addrZh}</div>
      <Ko zh={h.addrZh} />
      <div className="small">{h.note}</div>
      <div className="wrap">
        <Link className="btn primary" to={`/taxi/place/${h.placeId}`}><CarTaxiFront size={16} />택시 카드</Link>
        <a className="btn" href={h.amapUrl} target="_blank" rel="noreferrer"><MapPin size={16} />지도</a>
        <button className="btn" onClick={() => setShow(true)}><Maximize2 size={16} />병원에서 보여줄 문장</button>
      </div>
    </div>
  )
}
