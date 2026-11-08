import { useState } from 'react'
import { Copy, CupSoda, Maximize2 } from 'lucide-react'
import { ShowCard, TopBar, copyText } from '../components/ui'
import { useData } from '../store/data'
import type { MilkTeaDrink } from '../domain/types'

/** 주문 문장: "你好，我要一杯伯牙绝弦，少糖，去冰。" */
export const milkTeaOrder = (zh: string, sugar: string, ice: string) => `你好，我要一杯${zh}${sugar ? `，${sugar}` : ''}${ice ? `，${ice}` : ''}。`

/** 더보기 → 밀크티 종류: 브랜드별 추천 BEST 5(이름·병음·한글 읽기·설명) + 당도·얼음 골라 주문 문장 */
export function MilkTea() {
  const data = useData(); const mt = data.milktea
  const [brand, setBrand] = useState(mt.items[0]?.id ?? '')
  const [sugar, setSugar] = useState('')
  const [ice, setIce] = useState('')
  const [show, setShow] = useState<MilkTeaDrink | null>(null)
  const b = mt.items.find((x) => x.id === brand) ?? mt.items[0]
  const ko = (pairs: [string, string][], z: string) => pairs.find(([x]) => x === z)?.[1] ?? ''
  if (show) {
    const s = milkTeaOrder(show.zh, sugar, ice)
    return (
      <ShowCard header="점원에게 보여주세요" onClose={() => setShow(null)} actions={<button className="btn" onClick={() => copyText(s)}><Copy size={16} />복사</button>}>
        <div className="phrase zh">{s}</div>
        <div className="sub">{show.koRead} 한 잔{sugar ? ` · ${ko(mt.sugar, sugar)}` : ''}{ice ? ` · ${ko(mt.ice, ice)}` : ''}</div>
      </ShowCard>
    )
  }
  if (!b) return <div className="page"><TopBar title="밀크티 종류" /><div className="pad muted">자료가 없다</div></div>
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
          <div className="small muted">{b.pinyin} · 추천 BEST 5</div>
        </div>
      </div>
      <div className="pad"><div className="card flat" style={{ padding: 0 }}>
        {b.drinks.map((d, i) => (
          <div key={d.id} className="li" data-testid="milktea-drink" style={{ alignItems: 'flex-start', gap: 10 }}>
            <span className="bold" style={{ background: b.color, color: '#fff', borderRadius: 999, minWidth: 26, height: 26, display: 'grid', placeItems: 'center', flex: 'none', marginTop: 2 }}>{i + 1}</span>
            <div className="grow" style={{ minWidth: 0 }}>
              <div className="zh bold" style={{ fontSize: '1.35em' }}>{d.zh}</div>
              <div className="tiny muted">{d.pinyin} · {d.koRead}</div>
              <div className="small" style={{ marginTop: 4 }}>{d.desc}</div>
            </div>
            <button className="btn" style={{ flex: 'none' }} onClick={() => setShow(d)}><Maximize2 size={16} />주문</button>
          </div>
        ))}
      </div></div>
      <div className="section row"><CupSoda size={18} /><span>주문할 때 당도·얼음 (고르면 '주문' 문장에 붙는다)</span></div>
      <div className="pad stack" style={{ gap: 8 }}>
        <div className="wrap" data-testid="milktea-sugar">{mt.sugar.map(([z, k]) => <button key={z} className={`chip ${sugar === z ? 'on' : ''}`} onClick={() => setSugar(sugar === z ? '' : z)}><span className="zh">{z}</span> {k}</button>)}</div>
        <div className="wrap" data-testid="milktea-ice">{mt.ice.map(([z, k]) => <button key={z} className={`chip ${ice === z ? 'on' : ''}`} onClick={() => setIce(ice === z ? '' : z)}><span className="zh">{z}</span> {k}</button>)}</div>
      </div>
      <p className="pad tiny faint">브랜드별 추천 메뉴는 인스타그램 '중국 나이차 브랜드별 추천 BEST 5'(chinese_shenjie)를 정리한 것이다. 메뉴·가격은 매장·계절마다 다르다. 12월엔 따뜻하게(热的)가 좋다.</p>
    </div>
  )
}
