import { useEffect, useState, type FormEvent } from 'react'
import { LogIn } from 'lucide-react'
import { BrandHeader } from '../components/ui'
import { lockSeconds, recordFail, unlock } from '../store/vault'

export function Login({ onOpen }: { onOpen: (b: Record<string, unknown>) => void }) {
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [wait, setWait] = useState(lockSeconds())
  useEffect(() => { if (wait <= 0) return; const t = window.setTimeout(() => setWait(lockSeconds()), 1000); return () => window.clearTimeout(t) }, [wait])

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (busy || wait > 0 || !email.trim()) return
    setBusy(true); setErr(null)
    try {
      const b = await unlock(email)
      if (b) onOpen(b)
      else { recordFail(); setWait(lockSeconds()); setErr('등록된 아이디가 아닙니다') }
    } catch { setErr('데이터 받기 실패 · 인터넷 연결을 확인하세요') } finally { setBusy(false) }
  }
  return (
    <div className="login app">
      <BrandHeader status="접속 아이디로 로그인" sub="처음 한 번만 입력하면 이 기기에서는 자동으로 열립니다" />
      <form onSubmit={submit}>
        <div className="field">
          <label htmlFor="email">접속 아이디</label>
          <input id="email" className="input" type="text" autoComplete="username" autoCapitalize="none" autoCorrect="off" spellCheck={false} placeholder="아이디"
            value={email} onChange={(e) => setEmail(e.target.value)} autoFocus />
        </div>
        {err && <div className="banner rose">{err}</div>}
        <button className="btn primary block" disabled={busy || wait > 0}>
          <LogIn size={18} />{busy ? '확인 중…' : wait > 0 ? `${wait}초 후 다시 시도` : '들어가기'}
        </button>
        <p className="faint small center">윈난 7박 8일 · 2026.12.16 ~ 12.23</p>
      </form>
    </div>
  )
}
