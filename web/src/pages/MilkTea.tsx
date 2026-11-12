import { useState } from 'react'
import { Copy, CupSoda, MapPin, Maximize2 } from 'lucide-react'
import { ShowCard, TopBar, copyText } from '../components/ui'
import { useData } from '../store/data'
import type { MilkTeaDrink } from '../domain/types'

/** 주문 문장: "你好，我要一杯伯牙绝弦，少糖，去冰。". 맞춤 옵션(opt)이 있으면 옵션을 그대로 붙이고 당도·얼음 선택은 쓰지 않는다 */
export const milkTeaOrder = (zh: string, sugar: string, ice: string, opt?: string) =>
  opt ? `你好，我要一杯${zh}，${opt}。` : `你好，我要一杯${zh}${sugar ? `，${sugar}` : ''}${ice ? `，${ice}` : ''}。`

/** 더보기 → 밀크티 종류: 브랜드별 추천(이름·병음·한글 읽기·설명·맞춤 옵션) + 당도·얼음 골라 주문 문장 */
export function MilkTea() {
  const data = useData(); const mt = data.milktea
  const [brand, setBrand] = useState(mt.items[0]?.id ?? '')
  const [sugar, setSugar] = useState('')
  const [ice, setIce] = useState('')
  const [show, setShow] = useState<MilkTeaDrink | null>(null)
  const b = mt.items.find((x) => x.id === brand) ?? mt.items[0]
  const ko = (pairs: [string, string][], z: string) => pairs.find(([x]) => x === z)?.[1] ?? ''
  if (show) {
    const s = milkTeaOrder(show.zh, sugar, ice, show.opt)
    return (
      <ShowCard header="점원에게 보여주세요" onClose={() => setShow(null)} actions={<button className="btn" onClick={() => copyText(s)}><Copy size={16} />복사</button>}>
        <div className="phrase zh">{s}</div>
        <div className="sub">{show.koRead} 한 잔{show.opt ? ' · 맞춤 옵션 그대로' : `${sugar ? ` · ${ko(mt.sugar, sugar)}` : ''}${ice ? ` · ${ko(mt.ice, ice)}` : ''}`}</div>
      </ShowCard>
    )
  }
  if (!b) return <div className="page"><TopBar title="밀크티 종류" /><div className="pad muted">자료 없음</div></div>
  return (
    <div className="page">
      <TopBar title="밀크티 종류" />
      <div className="chips" data-testid="milktea-brands">
        {mt.items.map((x) => <button key={x.id} className={`chip ${brand === x.id ? 'on' : ''}`} onClick={() => setBrand(x.id)}><span className="zh">{x.nameZh}</span> <span className="tiny">{x.nameKo}</span></button>)}
      </div>
      <div className="pad" style={{ paddingTop: 4 }}>
        <div className="card" style={{ borderLeft: `6px solid ${b.color}` }}>
          <div className="row" style={{ alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
            <span className="zh bold" style={{ fontSize: '1.5em' }}>{b.nameZh}</span>
            <span className="bold" style={{ fontSize: '1.15em' }}>{b.nameKo}</span>
            <span className="tag" style={{ background: b.color, color: '#fff' }}>{b.nameEn}</span>
          </div>
          <div className="small muted">{b.pinyin} · 추천 {b.drinks.length}가지</div>
          {b.note && <div className="tiny muted" style={{ marginTop: 4 }}>{b.note}</div>}
          {!!b.maps?.length && (
            <div style={{ marginTop: 10 }}>
              <div className="small bold row" style={{ gap: 4 }}><MapPin size={15} />매장 찾기 (고덕지도)</div>
              <div className="wrap" data-testid="milktea-maps" style={{ marginTop: 6 }}>
                {b.maps.map((x) => <a key={x.ko} className="chip" href={x.url} target="_blank" rel="noreferrer" style={{ textDecoration: 'none', color: 'inherit' }}>{x.ko} <span className="tiny muted">{x.days}</span></a>)}
              </div>
              <div className="tiny faint" style={{ marginTop: 4 }}>브랜드 이름으로 그 동네를 검색한다. 결과에서 가까운 매장을 고르고, 없으면 다른 브랜드를 찾는다.</div>
            </div>
          )}
        </div>
      </div>
      <div className="pad"><div className="card flat" style={{ padding: 0 }}>
        {b.drinks.map((d, i) => (
          <div key={d.id} className="li" data-testid="milktea-drink" style={{ alignItems: 'flex-start', gap: 10 }}>
            <span className="bold" style={{ background: b.color, color: '#fff', borderRadius: 999, minWidth: 26, height: 26, display: 'grid', placeItems: 'center', flex: 'none', marginTop: 2 }}>{i + 1}</span>
            <div className="grow" style={{ minWidth: 0 }}>
              <div className="zh bold" style={{ fontSize: '1.35em' }}>{d.zh}</div>
              <div className="tiny muted">{d.pinyin} · {d.koRead}</div>
              {d.tag && <span className="tag" style={{ marginTop: 4, display: 'inline-block' }}>{d.tag}</span>}
              {d.opt && <div className="small zh" data-testid="milktea-opt" style={{ marginTop: 4 }}>{d.opt}</div>}
              <div className="small" style={{ marginTop: 4 }}>{d.desc}</div>
            </div>
            <button className="btn" style={{ flex: 'none' }} onClick={() => setShow(d)}><Maximize2 size={16} />주문</button>
          </div>
        ))}
      </div></div>
      <div className="section row"><CupSoda size={18} /><span>주문할 때 당도·얼음 (고르면 주문 문장에 붙음 · 맞춤 주문 메뉴는 옵션 그대로)</span></div>
      <div className="pad stack" style={{ gap: 8 }}>
        <div className="wrap" data-testid="milktea-sugar">{mt.sugar.map(([z, k]) => <button key={z} className={`chip ${sugar === z ? 'on' : ''}`} onClick={() => setSugar(sugar === z ? '' : z)}><span className="zh">{z}</span> {k}</button>)}</div>
        <div className="wrap" data-testid="milktea-ice">{mt.ice.map(([z, k]) => <button key={z} className={`chip ${ice === z ? 'on' : ''}`} onClick={() => setIce(ice === z ? '' : z)}><span className="zh">{z}</span> {k}</button>)}</div>
      </div>
      <p className="pad tiny faint">브랜드별 추천 메뉴는 인스타그램 chinese_shenjie·hyorin_c·raraclass_chinese 게시물을 정리한 것이다. 메뉴·가격·한정 메뉴는 매장·계절마다 다르다. 12월엔 따뜻하게(热的)가 좋다.</p>
    </div>
  )
}
