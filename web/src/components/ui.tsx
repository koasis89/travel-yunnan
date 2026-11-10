import { useData } from '../store/data'
import { useStored } from '../store/local'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Phone, X } from 'lucide-react'
import type { Day } from '../domain/types'

export const TAGLINE = '딸들과 함께 여행을'

export function Logo({ className = 'logo' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 48 48" aria-hidden>
      <circle cx="24" cy="24" r="22" fill="#1F6F6B" />
      <circle cx="33" cy="15" r="4.5" fill="#F2B544" />
      <path d="M8 36 L19 19 L24 26 L28 21 L40 36 Z" fill="#fff" />
      <path d="M19 19 L22 23.5 L20 23 L18 25 L16.5 23 Z" fill="#9FD3CF" />
      <path d="M28 21 L30.5 24.5 L29 24 L27.5 25.5 L26 24 Z" fill="#9FD3CF" />
      <path d="M11 40 q3 -2 6 0 t6 0 t6 0 t6 0" stroke="#9FD3CF" strokeWidth="1.8" fill="none" />
    </svg>
  )
}

export function BrandHeader({ status, sub, children }: { status?: string; sub?: string; children?: ReactNode }) {
  return (
    <header className="brand">
      <div className="row" style={{ gap: 12 }}>
        <Logo />
        <div><div className="name">윈난 7박 8일</div><div className="tagline">{TAGLINE}</div></div>
      </div>
      {status && <div className="status">{status}</div>}
      {sub && <div className="sub">{sub}</div>}
      {children}
    </header>
  )
}

export function TopBar({ title, back = true, right }: { title: string; back?: boolean; right?: ReactNode }) {
  const nav = useNavigate()
  return (
    <div className="topbar">
      {back && <button className="iconbtn" aria-label="뒤로" onClick={() => (history.length > 1 ? nav(-1) : nav('/'))}><ArrowLeft size={22} /></button>}
      <h1 className="grow">{title}</h1>
      {right}
    </div>
  )
}

export const Section = ({ children }: { children: ReactNode }) => <div className="section">{children}</div>

export function Banner({ icon, children, tone }: { icon?: ReactNode; children: ReactNode; tone?: 'rose' }) {
  return <div className={`banner ${tone ?? ''}`}>{icon}<div>{children}</div></div>
}

export function DayChips({ days, selected, onSelect, todayId, allLabel, preLabel }: { days: Day[]; selected: string | null; onSelect: (id: string | null) => void; todayId?: string | null; allLabel?: string; preLabel?: string }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => { ref.current?.querySelector('.on')?.scrollIntoView({ inline: 'center', block: 'nearest' }) }, [selected])
  return (
    <div className="chips" ref={ref}>
      {allLabel && <button className={`chip ${selected === null ? 'on' : ''}`} onClick={() => onSelect(null)}>{allLabel}</button>}
      {preLabel && <button className={`chip ${selected === 'pre' ? 'on' : ''}`} data-testid="pre-chip" onClick={() => onSelect('pre')}>{preLabel}</button>}
      {days.map((d) => (
        <button key={d.id} className={`chip ${selected === d.id ? 'on' : ''}`} onClick={() => onSelect(d.id)}>
          {d.id} {d.id === todayId ? '오늘' : d.dateLabel.split('(')[0]}
        </button>
      ))}
    </div>
  )
}

export function PhoneLinks({ phones }: { phones: string[] }) {
  return <>{phones.map((p) => <a key={p} className="btn" href={`tel:${p.replace(/[^\d+]/g, '')}`}><Phone size={16} />{p}</a>)}</>
}

// 토스트
let pushToast: ((m: string) => void) | null = null
export const toast = (m: string) => pushToast?.(m)
export function ToastHost() {
  const [msg, setMsg] = useState<string | null>(null)
  useEffect(() => {
    let t: number | undefined
    pushToast = (m) => { setMsg(m); window.clearTimeout(t); t = window.setTimeout(() => setMsg(null), 2200) }
    return () => { pushToast = null }
  }, [])
  return msg ? <div className="toast" role="status">{msg}</div> : null
}

export async function copyText(text: string, label = '복사 완료') {
  try { await navigator.clipboard.writeText(text) } catch {
    const ta = document.createElement('textarea'); ta.value = text; document.body.appendChild(ta); ta.select()
    try { document.execCommand('copy') } finally { ta.remove() }
  }
  toast(label)
}

export function download(name: string, text: string, type: string) {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const a = document.createElement('a'); a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}

/** 보여주기 화면: 전체 화면 + 화면 켜짐 유지(Wake Lock). 화면을 누르면 버튼을 숨긴다 */
export function ShowCard({ header, onClose, children, actions }: { header: string; onClose: () => void; children: ReactNode; actions?: ReactNode }) {
  const [chrome, setChrome] = useState(true)
  useEffect(() => {
    let lock: WakeLockSentinel | null = null
    const req = async () => { try { lock = await navigator.wakeLock?.request('screen') ?? null } catch { /* 미지원 */ } }
    req()
    const vis = () => { if (document.visibilityState === 'visible') req() }
    document.addEventListener('visibilitychange', vis)
    return () => { document.removeEventListener('visibilitychange', vis); lock?.release().catch(() => {}) }
  }, [])
  return (
    <div className="show" onClick={() => setChrome((c) => !c)}>
      {chrome && (
        <div className="chrome" onClick={(e) => e.stopPropagation()}>
          <button className="iconbtn" aria-label="닫기" onClick={onClose}><X size={24} /></button>
          <div className="grow muted small">{header}</div>
          <div className="faint tiny" style={{ paddingRight: 8 }}>화면을 누르면 버튼 숨김 · 밝기를 최대로</div>
        </div>
      )}
      <div className="body">{children}</div>
      {chrome && actions && <div className="actions" onClick={(e) => e.stopPropagation()}><div className="wrap">{actions}</div></div>}
    </div>
  )
}

/** 중국어 아래 작은 한국어 번역(번역 사전에 있을 때만). 이미 한국어가 함께 보이는 곳에는 쓰지 않는다 */
export function Ko({ zh, className = 'tiny muted' }: { zh?: string | null; className?: string }) {
  const k = useData().ko(zh)
  return k ? <div className={`${className} ko-gloss`} data-testid="ko-gloss">{k}</div> : null
}

/** 체크리스트 카드: 항목을 누르면 체크(기기 저장, 일정 완료 표시와 같은 'done' 저장소에 'chk:<id>') */
export function CheckList({ title, sub, items, testId = 'checklist' }: { title?: string; sub?: string; items: { id: string; text: string }[]; testId?: string }) {
  const [done, setDone] = useStored<string[]>('done', [])
  if (!items.length) return null
  const n = items.filter((x) => done.includes(`chk:${x.id}`)).length
  return (
    <div className="card" data-testid={testId} style={{ padding: '12px 14px' }}>
      {title && <div className="row"><span className="bold">{title}</span><span className="grow" /><span className="tiny muted">{n}/{items.length}</span></div>}
      {sub && <div className="tiny muted">{sub}</div>}
      <div className="stack" style={{ gap: 2, marginTop: title ? 6 : 0 }}>
        {items.map((x) => {
          const k = `chk:${x.id}`; const on = done.includes(k)
          return (
            <label key={x.id} className="row" data-testid="check-item" style={{ alignItems: 'flex-start', gap: 10, padding: '6px 0', cursor: 'pointer' }}>
              <input type="checkbox" checked={on} onChange={() => setDone((d) => (d.includes(k) ? d.filter((y) => y !== k) : [...d, k]))}
                style={{ width: 20, height: 20, marginTop: 1, flex: 'none', accentColor: 'var(--primary)' }} />
              <span className="small" style={{ textDecoration: on ? 'line-through' : 'none', opacity: on ? 0.55 : 1 }}>{x.text}</span>
            </label>
          )
        })}
      </div>
    </div>
  )
}
