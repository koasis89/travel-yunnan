import { useState } from 'react'
import { Download, Plus, Trash2 } from 'lucide-react'
import { Section, TopBar, download } from '../components/ui'
import { useData, useNow, useSettings } from '../store/data'
import { useStored } from '../store/local'
import { resolvePhase } from '../domain/tripTime'
import { CATEGORIES, METHODS, PAYERS, budgetKey, expensesCsv, krw, type Expense as Ex } from '../domain/expense'

const Bar = ({ v, max }: { v: number; max: number }) => <div className={`bar ${max > 0 && v > max ? 'over' : ''}`}><i style={{ width: `${max > 0 ? Math.min(100, (v / max) * 100) : 0}%` }} /></div>

export function Expense() {
  const data = useData(); const now = useNow(); const [s, setS] = useSettings()
  const [list, setList] = useStored<Ex[]>('expenses', [])
  const [adding, setAdding] = useState(false)
  const rate = s.cnyRate ?? data.rates.CNY
  const total = list.reduce((a, e) => a + krw(e, rate), 0)
  const budgetTotal = data.budget.find((b) => b.isTotal)?.amountKrw ?? 0
  const cashSpent = list.filter((e) => e.method === '현금' && e.currency === 'CNY').reduce((a, e) => a + e.amount, 0)
  const p = resolvePhase(now, data.days)
  return (
    <div className="page">
      <TopBar title="경비" right={<button className="iconbtn" aria-label="CSV" onClick={() => download('yunnan-expenses.csv', '﻿' + expensesCsv(list, rate), 'text/csv')}><Download size={20} /></button>} />
      <div className="pad"><div className="card mint">
        <div className="bold" style={{ fontSize: '1.3em' }}>지출 {Math.round(total).toLocaleString()}원</div>
        <div className="small">예산 {Math.round(budgetTotal).toLocaleString()}원(항공 제외, 3인) · ¥1 = {rate}원</div>
        <div style={{ margin: '8px 0' }}><Bar v={total} max={budgetTotal} /></div>
        {s.cash > 0 && <div className="small">현금 잔액 약 ¥{Math.round(s.cash - cashSpent).toLocaleString()} / ¥{s.cash.toLocaleString()}</div>}
        <div className="row" style={{ marginTop: 8, gap: 8 }}>
          <input className="input" inputMode="numeric" placeholder="환율 ¥1=원" defaultValue={s.cnyRate ?? ''} onBlur={(e) => setS({ ...s, cnyRate: Number(e.target.value) || null })} />
          <input className="input" inputMode="numeric" placeholder="가져온 현금 ¥" defaultValue={s.cash || ''} onBlur={(e) => setS({ ...s, cash: Number(e.target.value) || 0 })} />
        </div>
      </div></div>
      <Section>구분별 예산 대비</Section>
      <div className="stack pad">{data.budget.filter((b) => !b.isTotal).map((b) => {
        const spent = list.filter((e) => e.category === budgetKey(b.category)).reduce((a, e) => a + krw(e, rate), 0)
        return <div key={b.category}><div className="row small"><span className="grow">{b.category}</span><span>{Math.round(spent).toLocaleString()} / {Math.round(b.amountKrw).toLocaleString()}원</span></div><Bar v={spent} max={b.amountKrw} /></div>
      })}</div>
      <Section>지출 내역 ({list.length}건)</Section>
      <div className="pad">
        {adding ? <AddForm dayDefault={p.kind === 'on' ? p.day.id : 'D1'} days={data.days.map((d) => d.id)} onCancel={() => setAdding(false)} onSave={(e) => { setList((l) => [e, ...l]); setAdding(false) }} />
          : <button className="btn primary block" onClick={() => setAdding(true)}><Plus size={18} />지출 추가</button>}
        <div className="card flat" style={{ padding: 0, marginTop: 12 }}>
          {list.length === 0 && <div className="li muted small">아직 기록이 없다</div>}
          {list.map((e) => (
            <div key={e.id} className="li">
              <div className="grow">
                <div className="bold">{e.currency === 'CNY' ? `¥${e.amount.toLocaleString()}` : `${e.amount.toLocaleString()}원`} &nbsp;{e.memo}</div>
                <div className="tiny muted">{e.dayId} · {e.category} · {e.method} · {e.payer}{e.currency === 'CNY' ? ` · 약 ${Math.round(krw(e, rate)).toLocaleString()}원` : ''}</div>
              </div>
              <button className="iconbtn" aria-label="삭제" onClick={() => confirm('삭제할까?') && setList((l) => l.filter((x) => x.id !== e.id))}><Trash2 size={18} /></button>
            </div>
          ))}
        </div>
        <p className="tiny faint">기록은 이 기기에만 저장된다. CSV(오른쪽 위)로 내보내 가족과 공유한다.</p>
      </div>
    </div>
  )
}

function AddForm({ dayDefault, days, onSave, onCancel }: { dayDefault: string; days: string[]; onSave: (e: Ex) => void; onCancel: () => void }) {
  const [amount, setAmount] = useState(''); const [currency, setCurrency] = useState<'CNY' | 'KRW'>('CNY')
  const [category, setCategory] = useState(CATEGORIES[2]); const [method, setMethod] = useState(METHODS[0]); const [payer, setPayer] = useState(PAYERS[0])
  const [day, setDay] = useState(dayDefault); const [memo, setMemo] = useState('')
  const pick = (opts: string[], v: string, set: (x: string) => void) => <div className="wrap">{opts.map((o) => <button key={o} type="button" className={`chip ${v === o ? 'on' : ''}`} onClick={() => set(o)}>{o}</button>)}</div>
  return (
    <div className="card stack">
      <div className="row"><input className="input grow" inputMode="decimal" placeholder="금액" value={amount} onChange={(e) => setAmount(e.target.value.replace(/[^\d.]/g, ''))} autoFocus />
        {pick(['CNY', 'KRW'], currency, (x) => setCurrency(x as 'CNY' | 'KRW'))}</div>
      <input className="input" placeholder="메모(가게·항목)" value={memo} onChange={(e) => setMemo(e.target.value)} />
      <div className="small bold">구분</div>{pick(CATEGORIES, category, setCategory)}
      <div className="small bold">결제 · 지불자</div>{pick(METHODS, method, setMethod)}{pick(PAYERS, payer, setPayer)}
      <div className="small bold">일차</div>{pick(days, day, setDay)}
      <div className="row"><button className="btn ghost grow" onClick={onCancel}>취소</button>
        <button className="btn primary grow" disabled={!Number(amount)} onClick={() => onSave({ id: Date.now(), dayId: day, amount: Number(amount), currency, category, memo: memo.trim(), method, payer, at: Date.now() })}>저장</button></div>
    </div>
  )
}
