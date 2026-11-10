import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Copy, Delete, Maximize2, MessageSquareText, QrCode } from 'lucide-react'
import { Banner, Ko, ShowCard, TopBar, copyText } from '../components/ui'
import { useData } from '../store/data'
import { TEMPLATES, templateById, type TemplateField } from '../domain/templates'
import { isDT, joinDT, koDT, parseDT, zhDT } from '../domain/dateText'
import { readNumber } from '../domain/chineseNumber'

export function Templates() {
  const data = useData(); const [sp] = useSearchParams()
  const link = data.templateLink(sp.get('link'))
  const [sel, setSel] = useState(link?.templateId ?? sp.get('id') ?? TEMPLATES[0].id)
  const t = templateById(sel) ?? TEMPLATES[0]
  const [values, setValues] = useState<Record<string, string>>(link && link.templateId === sel ? link.values : {})
  const [show, setShow] = useState(false)
  const [showLine, setShowLine] = useState<{ zh: string; ko: string } | null>(null)
  const zh = t.render(values)
  // 한국어 설명 줄에는 값도 한국어로(번역 사전에 있을 때)
  const ko = t.renderKo(Object.fromEntries(t.fields.map((f) => { const x = values[f.key] || f.def; return [f.key, f.kind ? x : data.ko(x) || x] })))
  const choose = (id: string) => { setSel(id); setValues(link && link.templateId === id ? link.values : {}) }
  // 같은 화면에서 주소(link·id)만 바뀌어도 그 템플릿을 연다
  const want = link?.templateId ?? sp.get('id')
  useEffect(() => { if (want) choose(want) }, [want, link?.id]) // eslint-disable-line react-hooks/exhaustive-deps
  if (showLine) return (
    <ShowCard header="보여 주세요" onClose={() => setShowLine(null)} actions={<button className="btn" onClick={() => copyText(showLine.zh, '복사 완료')}><Copy size={16} />복사</button>}>
      <div className="long zh">{showLine.zh}</div><div className="note">{showLine.ko}</div>
    </ShowCard>
  )
  if (show) return (
    <ShowCard header="매니저에게 보여주세요" onClose={() => setShow(false)} actions={<button className="btn" onClick={() => copyText(zh, '복사 완료 · 위챗에 붙여넣으세요')}><Copy size={16} />복사</button>}>
      <div className="long zh">{zh}</div><div className="note">{ko}</div>
    </ShowCard>
  )
  return (
    <div className="page"><TopBar title="위챗 메시지 템플릿" />
      {link && <div className="pad" style={{ marginBottom: 8 }}><Banner icon={<MessageSquareText size={18} />}>{link.dayId} {data.day(link.dayId)?.dateLabel} {link.timeStart} 일정에 맞춰 값을 채웠다: {link.label}</Banner></div>}
      <div className="chips">{TEMPLATES.map((x) => <button key={x.id} className={`chip ${sel === x.id ? 'on' : ''}`} onClick={() => choose(x.id)}>{x.title}</button>)}</div>
      <div className="pad stack">
        {t.lines ? <>
          {t.linesNote && <p className="small muted" style={{ margin: 0 }}>{t.linesNote}</p>}
          {t.lines.map((l, i) => (
            <div key={l.zh} className="card mint" data-testid="template-line">
              <div className="small bold" style={{ color: 'var(--primary)' }}>{l.label ?? `${i + 1}`}</div>
              <div className="zh" style={{ fontSize: '1.15em', lineHeight: 1.6, marginTop: 4 }}>{l.zh}</div>
              <div className="small" style={{ opacity: .8, marginTop: 6 }}>{l.ko}</div>
              <div className="wrap" style={{ marginTop: 10 }}>
                <button className="btn" onClick={() => copyText(l.zh, '복사 완료')}><Copy size={16} />복사</button>
                <button className="btn" onClick={() => setShowLine(l)}><Maximize2 size={16} />크게</button>
              </div>
            </div>
          ))}
        </> : <div className="card mint">
          <div className="zh" style={{ fontSize: '1.12em', whiteSpace: 'pre-line', lineHeight: 1.6 }}>{zh}</div>
          <div className="small" style={{ opacity: .8, marginTop: 8 }}>{ko}</div>
          <div className="wrap" style={{ marginTop: 10 }}>
            <button className="btn" onClick={() => copyText(zh, '복사 완료 · 위챗에 붙여넣으세요')}><Copy size={16} />복사</button>
            <button className="btn" onClick={() => setShow(true)}><Maximize2 size={16} />크게</button>
          </div>
        </div>}
        {t.contact && (
          <div className="card stack" data-testid="contact-card" style={{ gap: 6 }}>
            <div className="bold">{t.contact.name}</div>
            <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
              <span>위챗 ID <b>{t.contact.wechat}</b></span>
              <button className="btn" onClick={() => copyText(t.contact!.wechat, '위챗 ID 복사 완료 · 친구 추가 검색창에 붙여넣으세요')}><Copy size={16} />위챗 ID 복사</button>
              <a className="btn" href="weixin://">위챗 열기</a>
            </div>
            {t.contact.bookingRef && <div className="row" data-testid="booking-ref" style={{ gap: 8, flexWrap: 'wrap' }}>
              <span>대표 예약자 <b>{t.contact.booker}</b> · 예약 번호 <b>{t.contact.bookingRef}</b></span>
              <button className="btn ghost" onClick={() => copyText(t.contact!.bookingRef!, '예약 번호 복사 완료')}><Copy size={16} />예약 번호 복사</button>
            </div>}
            <div className="small muted">친구 추가: 위챗 → 오른쪽 위 ⊕ → 친구 추가(添加朋友) → 검색창에 ID 붙여넣기 → 요청 메시지에 아래 문장을 붙인다.</div>
            <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
              <span className="small"><span className="zh">{t.contact.friendNote}</span>{t.contact.friendNoteKo && <span className="tiny muted ko-gloss" data-testid="ko-gloss" style={{ display: 'block' }}>{t.contact.friendNoteKo}</span>}</span>
              <button className="btn ghost" onClick={() => copyText(t.contact!.friendNote, '친구 요청 문장 복사 완료')}><Copy size={16} />요청 문장 복사</button>
            </div>
            <button className="btn ghost" style={{ alignSelf: 'flex-start' }} onClick={() => choose('tpl-wechat-qr')}><QrCode size={16} />QR·아이디 주고받기 회화</button>
            {t.contact.note && <div className="tiny muted">{t.contact.note}</div>}
            {t.contact.email && <div className="tiny faint">이메일(참고): {t.contact.email} · 연락은 위챗으로 한다. 바우처의 '+86 3180884…' 긴 번호는 깨진 값이다.</div>}
          </div>
        )}
        {t.fields.length > 0 && <>
        <div className="section" style={{ padding: '8px 0' }}>바꿀 값</div>
        {t.fields.map((f) => (
          <div key={f.key} className="field"><label>{f.label}{f.hint ? ` · ${f.hint}` : ''}</label>
            {f.kind ? <DateTimeInput f={f} value={values[f.key] ?? ''} onChange={(x) => setValues({ ...values, [f.key]: x })} />
              : <><input className="input zh" placeholder={f.def} value={values[f.key] ?? ''} onChange={(e) => setValues({ ...values, [f.key]: e.target.value })} />
                <Ko zh={values[f.key] || f.def} /></>}</div>
        ))}
        <p className="tiny faint">날짜·시각은 숫자만 넣는다(시각은 24시간, 예: 14시 30분). 비워 두면 기본값이 들어간다.</p>
        </>}
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
        <div className="small" data-testid="ko-gloss">{n.toLocaleString()}위안</div>
        <div className="small muted">bǎi(百)=백, shí(十)=십, qiān(千)=천, wàn(万)=만</div>
        <div className="wrap" style={{ justifyContent: 'center', marginTop: 12 }}>
          <button className="btn" onClick={() => copyText('太贵了，便宜一点吧。')}><span className="zh">太贵了，便宜一点吧</span> (비싸요, 깎아 주세요)</button>
          <button className="btn" onClick={() => copyText(`${readNumber(n)}块，可以吗？`)}><span className="zh">{readNumber(n)}块，可以吗？</span> ({n.toLocaleString()}위안 괜찮아요?)</button>
        </div>
      </div>
      <div className="keypad">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9', '00', '0', '⌫'].map((k) => <button key={k} onClick={() => press(k)} aria-label={k === '⌫' ? '지우기' : k}>{k === '⌫' ? <Delete size={22} /> : k}</button>)}
      </div>
      <div className="wrap pad" style={{ marginTop: 10 }}>{[10, 50, 100, 500].map((p) => <button key={p} className="chip" onClick={() => setD(String(p))}>¥{p}</button>)}<button className="chip" onClick={() => setD('0')}>지움</button></div>
    </div>
  )
}

/** 날짜·시각을 숫자 칸(월·일·시·분)으로 받는다. 아래에 중국어·한국어로 어떻게 들어가는지 보여 준다 */
function DateTimeInput({ f, value, onChange }: { f: TemplateField; value: string; onChange: (v: string) => void }) {
  const kind = f.kind!
  const cur = parseDT(value); const def = parseDT(f.def)
  const p = { mo: cur.mo || (value ? '' : def.mo), d: cur.d || (value ? '' : def.d), h: cur.h || (value ? '' : def.h), mi: cur.mi || (value ? '' : def.mi) }
  const boxes: [keyof typeof p, string, number][] = [
    ...(kind !== 'time' ? [['mo', '월', 12], ['d', '일', 31]] as [keyof typeof p, string, number][] : []),
    ...(kind !== 'date' ? [['h', '시', 23], ['mi', '분', 59]] as [keyof typeof p, string, number][] : []),
  ]
  const set = (k: keyof typeof p, x: string) => onChange(joinDT(kind, { ...p, [k]: x.replace(/\D/g, '').slice(0, 2) }))
  const effective = isDT(value, kind) ? value : f.def
  return (
    <>
      <div className="row" style={{ gap: 6, flexWrap: 'wrap' }} data-testid={`dt-${f.key}`}>
        {boxes.map(([k, label, max]) => (
          <label key={k} className="row" style={{ gap: 4 }}>
            <input className="input" inputMode="numeric" pattern="[0-9]*" aria-label={`${f.label} ${label}`} placeholder={String(def[k] || '')}
              style={{ width: 62, textAlign: 'center', fontSize: '1.1em' }} value={p[k]} onChange={(e) => set(k, e.target.value)}
              onBlur={() => { const n = Number(p[k]); if (p[k] && n > max) set(k, String(max)) }} />
            <span>{label}</span>
          </label>
        ))}
      </div>
      <div className="tiny muted" data-testid="dt-preview"><span className="zh">{zhDT(effective, kind, f.approx)}</span> · {koDT(effective, kind)}{!isDT(value, kind) && value ? ' (칸을 다 채우면 바뀐다)' : ''}</div>
    </>
  )
}
