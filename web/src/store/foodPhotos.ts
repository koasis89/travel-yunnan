// 음식 사진: 사용자가 가진 사진 묶음(zip·사진 파일)을 이 기기의 IndexedDB 에만 넣어 본다(서버·저장소에 올리지 않는다).
// 사진마다 목록용 작은 그림(thumb)과 크게 볼 그림(full)을 JPEG 로 줄여 둔다. id 는 파일 이름(확장자 뺀 것, 예: photo_002)
import { useEffect, useState } from 'react'

export interface FoodPhoto { id: string; thumb: Blob; full: Blob; added: number }
const DB = 'yn-foodphotos'
let dbP: Promise<IDBDatabase> | null = null
const listeners = new Set<() => void>()
const notify = () => listeners.forEach((l) => l())

function db(): Promise<IDBDatabase> {
  if (!dbP) {
    dbP = new Promise((res, rej) => {
      const r = indexedDB.open(DB, 1)
      r.onupgradeneeded = () => { r.result.createObjectStore('p', { keyPath: 'id' }) }
      r.onsuccess = () => res(r.result)
      r.onerror = () => { dbP = null; rej(r.error) }
    })
  }
  return dbP
}

function tx<T>(mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T> | void): Promise<T> {
  return db().then((d) => new Promise<T>((res, rej) => {
    const t = d.transaction('p', mode); const req = fn(t.objectStore('p'))
    t.oncomplete = () => res(req ? req.result : (undefined as T))
    t.onerror = () => rej(t.error); t.onabort = () => rej(t.error ?? new Error('abort'))
  }))
}

export const photoIds = () => tx<IDBValidKey[]>('readonly', (s) => s.getAllKeys()).then((k) => (k as string[]).sort())
export const getPhoto = (id: string) => tx<FoodPhoto | undefined>('readonly', (s) => s.get(id))
export async function clearPhotos() { await tx('readwrite', (s) => { s.clear() }); notify() }

/** 사진 id 목록(바뀌면 다시 읽는다) */
export function usePhotoIds(): string[] | null {
  const [ids, setIds] = useState<string[] | null>(null)
  useEffect(() => {
    let on = true
    const load = () => photoIds().then((v) => on && setIds(v)).catch(() => on && setIds([]))
    load(); listeners.add(load)
    return () => { on = false; listeners.delete(load) }
  }, [])
  return ids
}

const IMG = /\.(webp|jpe?g|png|gif|heic)$/i
const MIME: Record<string, string> = { webp: 'image/webp', jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif', heic: 'image/heic' }
export const photoId = (name: string) => name.split('/').pop()!.replace(/\.[a-z0-9]{2,5}$/i, '')

async function shrink(src: Blob, max: number, q: number): Promise<Blob> {
  const bm = await createImageBitmap(src)
  const s = Math.min(1, max / Math.max(bm.width, bm.height))
  const c = document.createElement('canvas'); c.width = Math.max(1, Math.round(bm.width * s)); c.height = Math.max(1, Math.round(bm.height * s))
  const g = c.getContext('2d')!; g.fillStyle = '#fff'; g.fillRect(0, 0, c.width, c.height); g.drawImage(bm, 0, 0, c.width, c.height); bm.close?.()
  return new Promise((res, rej) => c.toBlob((b) => (b ? res(b) : rej(new Error('jpeg'))), 'image/jpeg', q))
}

/** zip 또는 사진 파일들을 넣는다. 같은 이름은 덮어쓴다. 반환: 넣은 장 수 */
export async function importPhotos(files: File[], onProgress?: (done: number, total: number) => void): Promise<number> {
  const items: { name: string; blob: Blob }[] = []
  for (const f of files) {
    if (/\.zip$/i.test(f.name) || f.type.includes('zip')) {
      const { unzipSync } = await import('fflate')
      const all = unzipSync(new Uint8Array(await f.arrayBuffer()), { filter: (e) => IMG.test(e.name) && !e.name.includes('__MACOSX') })
      for (const [name, bytes] of Object.entries(all)) {
        const ext = name.split('.').pop()!.toLowerCase()
        items.push({ name, blob: new Blob([bytes as BlobPart], { type: MIME[ext] ?? 'image/jpeg' }) })
      }
    } else if (f.type.startsWith('image/') || IMG.test(f.name)) items.push({ name: f.name, blob: f })
  }
  let n = 0
  for (const it of items) {
    try {
      const [thumb, full] = await Promise.all([shrink(it.blob, 360, 0.72), shrink(it.blob, 1280, 0.82)])
      const p: FoodPhoto = { id: photoId(it.name), thumb, full, added: Date.now() }
      await tx('readwrite', (s) => { s.put(p) }); n++
    } catch { /* 열 수 없는 사진은 건너뛴다 */ }
    onProgress?.(n, items.length)
  }
  try { await navigator.storage?.persist?.() } catch { /* 지원 안 함 */ }
  notify()
  return n
}
