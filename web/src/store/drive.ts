// 구글 드라이브 백업: 사용자가 배포한 Apps Script 웹 앱(tools/drive_backup.gs)으로 파일을 보낸다.
// 주소·키는 기기 설정(localStorage) 또는 데이터 묶음의 private.json(driveUrl·driveToken) 값을 쓴다. 저장소(공개)에는 넣지 않는다.
import { useEffect, useState } from 'react'

export interface DriveConfig { url: string; token: string }
const KEY = 'yn.drive'
const DONE = 'yn.drive.done'
let fallback: DriveConfig = { url: '', token: '' }
const listeners = new Set<() => void>()
const notify = () => listeners.forEach((l) => l())

/** 데이터 묶음(private.json)의 기본값. TripData 가 부른다 */
export function setDriveDefault(url?: string, token?: string) { fallback = { url: (url ?? '').trim(), token: (token ?? '').trim() } }

export function driveConfig(): DriveConfig {
  try {
    const s = JSON.parse(localStorage.getItem(KEY) ?? 'null') as DriveConfig | null
    if (s?.url && s?.token) return s
  } catch { /* 저장소 못 씀 */ }
  return fallback
}
export function saveDriveConfig(c: DriveConfig | null) {
  try { if (c) localStorage.setItem(KEY, JSON.stringify({ url: c.url.trim(), token: c.token.trim() })); else localStorage.removeItem(KEY) } catch { /* 무시 */ }
  notify()
}
export const driveReady = () => { const c = driveConfig(); return /^https:\/\/script\.google(usercontent)?\.com\//.test(c.url) && !!c.token }

function doneSet(): Set<string> {
  try { return new Set(JSON.parse(localStorage.getItem(DONE) ?? '[]') as string[]) } catch { return new Set() }
}
export const isUploaded = (key: string) => doneSet().has(key)
function markUploaded(key: string) {
  const s = doneSet(); s.add(key)
  try { localStorage.setItem(DONE, JSON.stringify([...s])) } catch { /* 무시 */ }
  notify()
}

/** 설정·업로드 표시가 바뀌면 다시 그린다 */
export function useDrive() {
  const [, set] = useState(0)
  useEffect(() => { const l = () => set((n) => n + 1); listeners.add(l); return () => { listeners.delete(l) } }, [])
  return { ready: driveReady(), config: driveConfig(), isUploaded }
}

async function toBase64(blob: Blob): Promise<string> {
  const buf = new Uint8Array(await blob.arrayBuffer())
  let s = ''
  for (let i = 0; i < buf.length; i += 0x8000) s += String.fromCharCode(...buf.subarray(i, i + 0x8000))
  return btoa(s)
}

/** 연결 확인: 폴더 이름을 돌려준다 */
export async function pingDrive(c: DriveConfig = driveConfig()): Promise<string> {
  const r = await fetch(`${c.url}?token=${encodeURIComponent(c.token)}`)
  const j = await r.json() as { ok: boolean; folder?: string; error?: string }
  if (!j.ok) throw new Error(j.error === 'token' ? '백업 키가 맞지 않는다' : j.error ?? '연결 실패')
  return j.folder ?? ''
}

/** 한 파일 올리기. key 로 이미 올린 것은 건너뛴다(드라이브 쪽도 같은 이름이면 건너뛴다) */
export async function uploadToDrive(key: string, f: { folder: string; sub?: string | null; name: string; blob: Blob; memo?: string }): Promise<'uploaded' | 'skipped'> {
  if (isUploaded(key)) return 'skipped'
  const c = driveConfig()
  if (!driveReady()) throw new Error('드라이브 백업이 설정되지 않았다')
  const body = JSON.stringify({ token: c.token, folder: f.folder, sub: f.sub ?? '', name: f.name, mime: f.blob.type || 'application/octet-stream', data: await toBase64(f.blob), memo: f.memo ?? '' })
  // text/plain 이면 사전 확인(preflight) 없이 보낼 수 있다
  const r = await fetch(c.url, { method: 'POST', headers: { 'Content-Type': 'text/plain;charset=utf-8' }, body })
  const j = await r.json() as { ok: boolean; skipped?: boolean; error?: string }
  if (!j.ok) throw new Error(j.error === 'token' ? '백업 키가 맞지 않는다' : j.error ?? '업로드 실패')
  markUploaded(key)
  return j.skipped ? 'skipped' : 'uploaded'
}

/** 공유(휴대폰 공유 화면 → Google Drive 등). 지원하지 않으면 false */
export async function shareFile(blob: Blob, name: string): Promise<boolean> {
  const file = new File([blob], name, { type: blob.type || 'application/octet-stream' })
  const nav = navigator as Navigator & { canShare?: (d: { files: File[] }) => boolean }
  if (!nav.share || !nav.canShare?.({ files: [file] })) return false
  try { await nav.share({ files: [file], title: name }) } catch { /* 사용자가 닫음 */ }
  return true
}

/** 드라이브에 올릴 파일 이름: D3_제목_id.확장자 */
export function driveName(dayId: string | null, title: string, id: string, fileName: string) {
  const ext = (fileName.match(/\.[A-Za-z0-9]{1,5}$/)?.[0] ?? '').toLowerCase()
  return `${dayId ?? '공통'}_${title.replace(/\s+/g, ' ').trim().slice(0, 60)}_${id}${ext}`
}
