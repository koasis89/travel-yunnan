import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Copy, MapPin } from 'lucide-react'
import { PhoneLinks, ShowCard, TopBar, copyText } from '../components/ui'
import { useData } from '../store/data'
import { buildTaxiCard, type TargetType } from '../domain/cards'

export function Taxi() {
  const data = useData(); const nav = useNavigate()
  const { type, id } = useParams()
  const [cur, setCur] = useState(id ?? '')
  const card = buildTaxiCard(data, type as TargetType, cur)
  if (!card) return <div className="page"><TopBar title="택시 카드" /><p className="pad muted">대상을 찾지 못했다</p></div>
  const head = data.phrase('px-001')
  const target = card.addrZh || card.nameZh
  return (
    <ShowCard header="기사님께 보여주세요" onClose={() => nav(-1)} actions={<>
      <button className="btn" onClick={() => copyText(target, '주소를 복사했다')}><Copy size={16} />주소 복사</button>
      {card.amapUrl && <a className="btn" href={card.amapUrl} target="_blank" rel="noreferrer"><MapPin size={16} />고덕지도</a>}
      <PhoneLinks phones={card.phones} />
      {card.siblings.length > 0 && <div style={{ width: '100%' }} className="small bold">다른 선택지</div>}
      {card.siblings.map((o) => <button key={o.id} className="chip zh" onClick={() => setCur(o.id)}>{o.name.split('(')[0]}</button>)}
    </>}>
      {head && <><div className="head zh">{head.zh.replace(/。$/, '')}</div><div className="note">{head.pinyin}</div></>}
      <div className="name zh">{card.nameZh}</div>
      {card.addrZh && <div className="addr zh">{card.addrZh}</div>}
      {card.helpers.map((h) => <div key={h.id}><div className="sub zh">{h.zh}</div><div className="note sub helperko">{h.ko}</div></div>)}
      {card.phones.length > 0 && <div className="sub">☎ {card.phones.join('  ')}</div>}
      <div className="note">{card.nameKo}{card.bookingInfo ? `\n예약 ${card.bookingInfo}` : ''}</div>
      {card.warning && <div className="note">⚠ {card.warning}</div>}
    </ShowCard>
  )
}

export function ShowPhrase() {
  const data = useData(); const nav = useNavigate()
  const { id } = useParams()
  const p = data.phrase(id)
  if (!p) return <div className="page"><TopBar title="보여주기" /><p className="pad muted">문장을 찾지 못했다</p></div>
  const long = p.copyOnly || p.zh.length > 30
  return (
    <ShowCard header={p.copyOnly ? '매니저에게 보여주세요' : '보여주기'} onClose={() => nav(-1)} actions={
      <button className="btn" onClick={() => copyText(p.zh, '문장을 복사했다. 위챗에 붙여넣는다')}><Copy size={16} />복사(위챗용)</button>
    }>
      <div className={`${long ? 'long' : 'phrase'} zh`}>{p.zh}</div>
      {!long && <div className="note">{p.pinyin}</div>}
      <div className={long ? 'note' : 'sub'}>{p.ko}</div>
    </ShowCard>
  )
}
