import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Copy, Delete, Maximize2, MessageSquareText } from 'lucide-react'
import { Banner, ShowCard, TopBar, copyText } from '../components/ui'
import { useData } from '../store/data'
import { TEMPLATES, templateById } from '../domain/templates'
import { readNumber } from '../domain/chineseNumber'

export function Templates() {
  const data = useData(); const [sp] = useSearchParams()
  const link = data.templateLink(sp.get('link'))
  const [sel, setSel] = useState(link?.templateId ?? sp.get('id') ?? TEMPLATES[0].id)
  const t = templateById(sel) ?? TEMPLATES[0]
  const [values, setValues] = useState<Record<string, string>>(link && link.templateId === sel ? link.values : {})
  const [show, setShow] = useState(false)
  const zh = t.render(values)
  const choose = (id: string) => { setSel(id); setValues(link && link.templateId === id ? link.values : {}) }
  if (show) return (
    <ShowCard header="매니저에게 보여주세요" onClose={() => setShow(false)} actions={<button className="btn" onClick={() => copyText(zh, '복사했다. 위챗에 붙여넣는다')}><Copy size={16} />복사</button>}>
      <div className="long zh">{zh}</div><div className="note">{t.renderKo(values)}</div>
    </ShowCard>
  )
  return (
    <div className="page"><TopBar title="위챗 메시지 템플릿" />
      {link && <div className="pad" style={{ marginBottom: 8 }}><Banner icon={<MessageSquareText size={18} />}>{link.dayId} {data.day(link.dayId)?.dateLabel} {link.timeStart} 일정에 맞춰 값을 채웠다: {link.label}</Banner></div>}
      <div className="chips">{TEMPLATES.map((x) => <button key={x.id} className={`chip ${sel === x.id ? 'on' : ''}`} onClick={() => choose(x.id)}>{x.title}</button>)}</div>
      <div className="pad stack">
        <div className="card mint">
          <div className="zh" style={{ fontSize: '1.12em', whiteSpace: 'pre-line', lineHeight: 1.6 }}>{zh}</div>
          <div className="small" style={{ opacity: .8, marginTop: 8 }}>{t.renderKo(values)}</div>
          <div className="wrap" style={{ marginTop: 10 }}>
            <button className="btn" onClick={() => copyText(zh, '복사했다. 위챗에 붙여넣는다')}><Copy size={16} />복사</button>
            <button className="btn" onClick={() => setShow(true)}><Maximize2 size={16} />크게</button>
          </div>
        </div>
        <div className="section" style={{ padding: '8px 0' }}>바꿀 값</div>
        {t.fields.map((f) => (
          <div key={f.key} className="field"><label>{f.label}{f.hint ? ` · ${f.hint}` : ''}</label>
            <input className="input zh" placeholder={f.def} value={values[f.key] ?? ''} onChange={(e) => setValues({ ...values, [f.key]: e.target.value })} /></div>
        ))}
        <p className="tiny faint">비워 두면 기본값이 들어간다.</p>
      </div>
    </div>
  )
}

export function NumberCard() {
  const [d, setD] = useState('150')
  const n = Number(d) || 0
  const press = (k: string) => setD((p) => (k === '⌫' ? p.slice(0, -1) : (p === '0' ? '' : p) + k).slice(0, 5) || '0')
  return (
    <div className="page"><TopBar title="숫자 카드" />
      <div className="center" style={{ padding: '24px 16px' }}>
        <div className="bignum">¥ {n.toLocaleString()}</div>
        <div className="zh bold" style={{ fontSize: '2em', color: 'var(--primary)' }}>{readNumber(n)}块</div>
        <div className="small muted">bǎi(百)=백, shí(十)=십, qiān(千)=천, wàn(万)=만</div>
        <div className="wrap" style={{ justifyContent: 'center', marginTop: 12 }}>
          <button className="btn" onClick={() => copyText('太贵了，便宜一点吧。')}><span className="zh">太贵了，便宜一点吧</span> (비싸요, 깎아 주세요)</button>
          <button className="btn" onClick={() => copyText(`${readNumber(n)}块，可以吗？`)}><span className="zh">{readNumber(n)}块，可以吗？</span></button>
        </div>
      </div>
      <div className="keypad">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9', '00', '0', '⌫'].map((k) => <button key={k} onClick={() => press(k)} aria-label={k === '⌫' ? '지우기' : k}>{k === '⌫' ? <Delete size={22} /> : k}</button>)}
      </div>
      <div className="wrap pad" style={{ marginTop: 10 }}>{[10, 50, 100, 500].map((p) => <button key={p} className="chip" onClick={() => setD(String(p))}>¥{p}</button>)}<button className="chip" onClick={() => setD('0')}>지움</button></div>
    </div>
  )
}
