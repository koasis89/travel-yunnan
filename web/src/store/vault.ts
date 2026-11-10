// 이메일 입력 로그인(웹 절차서 W6-2). 데이터는 허용 이메일로만 풀린다.
const u8 = (s: string) => Uint8Array.from(atob(s), (ch) => ch.charCodeAt(0))
export const normalizeEmail = (e: string) => e.trim().toLowerCase()
const KEY = 'yn.login_email'
const FAIL = 'yn.login_fail'

interface KeysFile { v: number; iterations: number; keys: { salt: string; iv: string; wrapped: string }[] }
interface Box { v: number; iv: string; data: string }

async function load(): Promise<[KeysFile, Box]> {
  const base = import.meta.env.BASE_URL
  const [k, b] = await Promise.all([fetch(`${base}keys.json`, { cache: 'no-cache' }), fetch(`${base}data.enc`, { cache: 'no-cache' })])
  if (!k.ok || !b.ok) throw new Error('데이터 파일 받기 실패')
  return [await k.json(), await b.json()]
}

export async function unlock(email: string): Promise<Record<string, unknown> | null> {
  const [keysFile, box] = await load()
  const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(normalizeEmail(email)), 'PBKDF2', false, ['deriveKey'])
  for (const k of keysFile.keys) {
    try {
      const kek = await crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt: u8(k.salt), iterations: keysFile.iterations },
        base, { name: 'AES-GCM', length: 256 }, false, ['decrypt'])
      const raw = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: u8(k.iv) }, kek, u8(k.wrapped))
      const dek = await crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['decrypt'])
      const plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: u8(box.iv) }, dek, u8(box.data))
      try { localStorage.setItem(KEY, normalizeEmail(email)); localStorage.removeItem(FAIL) } catch { /* 저장 불가 브라우저 */ }
      return JSON.parse(new TextDecoder().decode(plain))
    } catch { /* 다음 키 */ }
  }
  return null
}

export const savedEmail = () => { try { return localStorage.getItem(KEY) } catch { return null } }
export const logout = () => { try { localStorage.removeItem(KEY) } catch { /* noop */ } }

/** 5회 연속 실패하면 30초 잠금. 남은 초를 돌려준다 */
export function lockSeconds(): number {
  try {
    const f = JSON.parse(localStorage.getItem(FAIL) ?? '{"n":0,"t":0}') as { n: number; t: number }
    if (f.n < 5) return 0
    return Math.max(0, Math.ceil((f.t + 30000 - Date.now()) / 1000))
  } catch { return 0 }
}
export function recordFail() {
  try {
    const f = JSON.parse(localStorage.getItem(FAIL) ?? '{"n":0,"t":0}') as { n: number; t: number }
    const n = f.n >= 5 && Date.now() - f.t > 30000 ? 1 : f.n + 1
    localStorage.setItem(FAIL, JSON.stringify({ n, t: Date.now() }))
  } catch { /* noop */ }
}
