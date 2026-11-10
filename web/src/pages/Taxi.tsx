import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Copy, MapPin, Search } from 'lucide-react'
import { ShowCard, TopBar, copyText } from '../components/ui'
import { useData } from '../store/data'
import { amapSearchUrl, buildTaxiCard, optKo, type TargetType } from '../domain/cards'

export function Taxi() {
  const data = useData(); const nav = useNavigate()
  const { type, id } = useParams()
  const [cur, setCur] = useState(id ?? '')
  // 택시 카드에서 다른 카드로 바로 이동하면(주소만 바뀜) 새 대상으로 바꾼다
  useEffect(() => { setCur(id ?? '') }, [type, id])
  const card = buildTaxiCard(data, type as TargetType, cur)
  if (!card) return <div className="page"><TopBar title="택시 카드" /><p className="pad muted">대상 없음</p></div>
  const head = data.phrase('px-001')
  const target = card.addrZh || card.nameZh
  return (
    <ShowCard header="기사님께 보여주세요" onClose={() => nav(-1)} actions={<>
      <button className="btn" onClick={() => copyText(target, '주소 복사 완료')}><Copy size={16} />주소 복사</button>
      {card.amapUrl && <a className="btn" href={card.amapUrl} target="_blank" rel="noreferrer"><MapPin size={16} />지도</a>}
      <a className="btn" href={amapSearchUrl(target)} target="_blank" rel="noreferrer"><Search size={16} />지도 검색</a>
      {card.siblings.length > 0 && <div style={{ width: '100%' }} className="small bold">다른 선택지</div>}
      {card.siblings.map((o) => <button key={o.id} className="chip" onClick={() => setCur(o.id)}>{optKo(o).split('(')[0]}</button>)}
    </>}>
      {head && <><div className="head zh">{head.zh.replace(/。$/, '')}</div><div className="note">{head.pinyin}{head.ko ? ` · ${head.ko}` : ''}</div></>}
      <div className="name zh">{card.nameZh}</div>
      {card.addrZh && <div className="addr zh">{card.addrZh}</div>}
      {data.ko(card.addrZh) && <div className="note" data-testid="ko-gloss">{data.ko(card.addrZh)}</div>}
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
  if (!p) return <div className="page"><TopBar title="보여주기" /><p className="pad muted">문장 없음</p></div>
  const long = p.copyOnly || p.zh.length > 30
  return (
    <ShowCard header={p.copyOnly ? '매니저에게 보여주세요' : '보여주기'} onClose={() => nav(-1)} actions={
      <button className="btn" onClick={() => copyText(p.zh, '문장 복사 완료 · 위챗에 붙여넣으세요')}><Copy size={16} />복사(위챗용)</button>
    }>
      <div className={`${long ? 'long' : 'phrase'} zh`}>{p.zh}</div>
      {!long && <div className="note">{p.pinyin}</div>}
      <div className={long ? 'note' : 'sub'}>{p.ko}</div>
    </ShowCard>
  )
}
