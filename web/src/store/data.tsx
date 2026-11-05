import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { TripData } from '../domain/types'
import { tripNow } from '../domain/tripTime'
import { DEFAULT_SETTINGS, useStored, type Settings } from './local'

const DataCtx = createContext<TripData | null>(null)
export const DataProvider = ({ data, children }: { data: TripData; children: ReactNode }) => <DataCtx.Provider value={data}>{children}</DataCtx.Provider>
export function useData(): TripData { const d = useContext(DataCtx); if (!d) throw new Error('no data'); return d }

export const useSettings = () => useStored<Settings>('settings', DEFAULT_SETTINGS)

/** 현지 시각(날짜 지정 모드 반영). 30초마다 갱신 */
export function useNow(): string {
  const [s] = useSettings()
  const [tick, setTick] = useState(0)
  useEffect(() => { const t = window.setInterval(() => setTick((x) => x + 1), 30000); return () => window.clearInterval(t) }, [])
  void tick
  return tripNow(s.override)
}
