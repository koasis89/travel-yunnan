import { Copy, QrCode } from 'lucide-react'
import { Banner, TopBar, copyText } from '../components/ui'
import { useData } from '../store/data'
import { useStored } from '../store/local'

/** 알리페이로 중국 입국신고서(Arrival Card) QR 만들기: 순서 + 입국·호텔·출국 값 복사 */
export function ArrivalCard() {
  const data = useData(); const ac = data.checklists.arrivalCard
  // 항공편 편명은 이 기기에만 저장한다(저장소에 넣지 않는다)
  const [inputs, setInputs] = useStored<Record<string, string>>('arrival-inputs', {})
  const groups = [...new Set(ac.fields.map((f) => f.group))]
  return (
    <div className="page">
      <TopBar title="입국신고 QR (알리페이)" />
      <div className="pad stack">
        <Banner icon={<QrCode size={18} />}>{ac.summary}</Banner>
        <div className="section" style={{ padding: '4px 0' }}>만드는 순서</div>
        <div className="card flat" style={{ padding: 0 }}>
          {ac.steps.map((s, i) => (
            <div key={s.title} className="li" data-testid="arrival-step" style={{ alignItems: 'flex-start', gap: 10 }}>
              <span className="bold" style={{ background: 'var(--primary)', color: '#fff', borderRadius: 999, minWidth: 26, height: 26, display: 'grid', placeItems: 'center', flex: 'none', marginTop: 2 }}>{i + 1}</span>
              <div className="grow"><div className="bold">{s.title}</div><div className="small" style={{ marginTop: 2 }}>{s.desc}</div></div>
            </div>
          ))}
        </div>
        {groups.map((g) => (
          <div key={g} className="stack" style={{ gap: 6 }}>
            <div className="section" style={{ padding: '8px 0 0' }}>{g}</div>
            <div className="card flat" style={{ padding: 0 }} data-testid={`arrival-group-${g}`}>
              {ac.fields.filter((f) => f.group === g).map((f) => {
                const v = f.input ? (inputs[f.input] ?? '') : f.value
                const canCopy = f.copy !== false
                return (
                  <div key={f.label} className="li" data-testid="arrival-field" style={{ alignItems: 'center', gap: 10 }}>
                    <div className="grow" style={{ minWidth: 0 }}>
                      <div className="tiny muted">{f.label}</div>
                      {f.input
                        ? <input className="input" placeholder="편명(영문+숫자)" value={v} onChange={(e) => setInputs({ ...inputs, [f.input!]: e.target.value.toUpperCase().replace(/\s/g, '') })} style={{ marginTop: 4, maxWidth: 220 }} />
                        : <div className={canCopy ? 'bold zh' : 'bold'} style={{ wordBreak: 'break-all' }}>{v}</div>}
                      {f.note && <div className="tiny faint">{f.note}</div>}
                    </div>
                    {canCopy && <button className="btn" style={{ flex: 'none' }} disabled={!v} onClick={() => copyText(v, `${f.label}을(를) 복사했다`)}><Copy size={16} />복사</button>}
                  </div>
                )
              })}
            </div>
          </div>
        ))}
        <div className="section" style={{ padding: '8px 0 0' }}>알아 둘 것</div>
        {ac.notes.map((n) => <div key={n} className="small">· {n}</div>)}
        <p className="tiny faint">영문 이름·여권 번호·생년월일은 여권 사진으로 자동 입력되니 확인만 한다. 개인 정보라 이 앱에는 넣지 않는다.</p>
      </div>
    </div>
  )
}
