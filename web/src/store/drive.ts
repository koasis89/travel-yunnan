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
export async function uploadToDrive(key: string, f: { folder: string; sub?: string | null; name: string; blob: Blob; memo?: string }, force = false): Promise<'uploaded' | 'skipped'> {
  if (!force && isUploaded(key)) return 'skipped'
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

// ---- 드라이브에서 가져오기(웹 문서함): 앱·다른 기기가 올린 문서를 이 기기 문서함으로 ----
export interface DriveFile { id: string; name: string; mime: string; size: number; sub: string; memo: string; created: number }
const IMPORTED = 'yn.drive.imported'

/** 백업 폴더(문서함 등)의 파일 목록 */
export async function listDrive(folder = '문서함'): Promise<DriveFile[]> {
  const c = driveConfig()
  const r = await fetch(`${c.url}?token=${encodeURIComponent(c.token)}&action=list&folder=${encodeURIComponent(folder)}`)
  const j = await r.json() as { ok: boolean; files?: DriveFile[]; error?: string }
  if (!j.ok) throw new Error(j.error === 'token' ? '백업 키가 맞지 않는다' : j.error ?? '목록을 못 받았다')
  if (!j.files) throw new Error('드라이브 스크립트가 예전 버전이다. 새 스크립트로 다시 배포한다')
  return j.files
}

/** 파일 내려받기 */
export async function getDriveFile(id: string): Promise<{ name: string; blob: Blob; memo: string }> {
  const c = driveConfig()
  const r = await fetch(`${c.url}?token=${encodeURIComponent(c.token)}&action=get&id=${encodeURIComponent(id)}`)
  const j = await r.json() as { ok: boolean; name?: string; mime?: string; memo?: string; data?: string; error?: string }
  if (!j.ok || !j.data) throw new Error(j.error === 'token' ? '백업 키가 맞지 않는다' : j.error ?? '내려받지 못했다')
  const bin = atob(j.data); const buf = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i)
  return { name: j.name ?? id, blob: new Blob([buf], { type: j.mime || 'application/octet-stream' }), memo: j.memo ?? '' }
}

/** 드라이브 이름 'D3_제목_id.pdf' → 일차·제목·원래 id(앱·웹이 올린 이름 규칙). 규칙이 아니면 제목만 */
export function parseDriveName(name: string): { dayId: string | null; title: string; id: string | null } {
  const base = name.replace(/\.[A-Za-z0-9]{1,5}$/, '')
  const m = base.match(/^(D\d+|공통)_(.+)_([A-Za-z0-9-]+)$/)
  if (!m) return { dayId: null, title: base, id: null }
  return { dayId: m[1] === '공통' ? null : m[1], title: m[2], id: m[3] }
}

export function importedMap(): Record<string, string> {
  try { return JSON.parse(localStorage.getItem(IMPORTED) ?? '{}') as Record<string, string> } catch { return {} }
}
/** 드라이브 파일을 이 기기 문서로 가져왔다고 표시(다시 가져오지 않고, 다시 올리지도 않는다) */
export function markImported(driveId: string, localId: string) {
  const m = importedMap(); m[driveId] = localId
  try { localStorage.setItem(IMPORTED, JSON.stringify(m)) } catch { /* 무시 */ }
  markUploaded(`doc:${localId}`)
}

// 이 기기에서 지운 문서: '드라이브에서 가져오기'가 다시 들여오지 않게 기억한다(드라이브 이름·드라이브 파일 id)
const DELETED = 'yn.drive.deleted'
export function deletedSet(): Set<string> {
  try { return new Set(JSON.parse(localStorage.getItem(DELETED) ?? '[]') as string[]) } catch { return new Set() }
}
export function rememberDeleted(keys: string[]) {
  const s = deletedSet(); keys.filter(Boolean).forEach((k) => s.add(k))
  try { localStorage.setItem(DELETED, JSON.stringify([...s])) } catch { /* 무시 */ }
}
