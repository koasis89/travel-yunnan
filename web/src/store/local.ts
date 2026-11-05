// 기기별 저장(완료 표시·체크·경비·설정). localStorage 를 쓸 수 없으면 메모리에만 둔다
import { useCallback, useSyncExternalStore } from 'react'

const mem = new Map<string, string>()
const listeners = new Set<() => void>()
const read = (k: string) => { try { return localStorage.getItem(k) } catch { return mem.get(k) ?? null } }
const write = (k: string, v: string) => { try { localStorage.setItem(k, v) } catch { mem.set(k, v) }; listeners.forEach((l) => l()) }
const subscribe = (l: () => void) => { listeners.add(l); return () => listeners.delete(l) }

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
