// 기기별 저장(완료 표시·체크·경비·설정). localStorage 를 쓸 수 없으면 메모리에만 둔다
import { useCallback, useSyncExternalStore } from 'react'

const mem = new Map<string, string>()
// localStorage 쓰기가 실패한(저장 공간 부족 등) 키는 메모리 값을 우선한다.
// 예전에는 실패 후에도 localStorage 의 옛 값을 읽어 삭제·수정이 화면에 반영되지 않았다(2026-11-05 경비 삭제 미반영)
const memOnly = new Set<string>()
const listeners = new Set<() => void>()
const notify = () => listeners.forEach((l) => l())
const read = (k: string) => {
  if (memOnly.has(k)) return mem.get(k) ?? null
  try { return localStorage.getItem(k) } catch { return mem.get(k) ?? null }
}
const write = (k: string, v: string) => {
  try { localStorage.setItem(k, v); memOnly.delete(k) } catch {
    mem.set(k, v); memOnly.add(k)
    window.dispatchEvent(new CustomEvent('yn-storage-fail'))
  }
  notify()
}
const subscribe = (l: () => void) => { listeners.add(l); return () => listeners.delete(l) }
// 같은 기기에서 창·탭을 둘 이상 열었을 때 다른 창의 변경도 바로 반영한다
try { window.addEventListener('storage', (e) => { if (!e.key || e.key.startsWith('yn.')) notify() }) } catch { /* 테스트 환경 */ }

const cache = new Map<string, { raw: string | null; val: unknown }>()
function get<T>(key: string, def: T): T {
  const raw = read(key); const hit = cache.get(key)
  if (hit && hit.raw === raw) return hit.val as T
  let val: T = def
  if (raw) { try { val = JSON.parse(raw) as T } catch { val = def } }
  cache.set(key, { raw, val }); return val
}

export function useStored<T>(key: string, def: T): [T, (v: T | ((p: T) => T)) => void] {
  const value = useSyncExternalStore(subscribe, () => get(`yn.${key}`, def), () => def)
  const set = useCallback((v: T | ((p: T) => T)) => {
    const next = typeof v === 'function' ? (v as (p: T) => T)(get(`yn.${key}`, def)) : v
    write(`yn.${key}`, JSON.stringify(next))
  }, [key]) // eslint-disable-line react-hooks/exhaustive-deps
  return [value, set]
}

export interface Settings { override: string | null; bigFont: boolean; cnyRate: number | null; cash: number }
export const DEFAULT_SETTINGS: Settings = { override: null, bigFont: false, cnyRate: null, cash: 0 }
