// 문서함 저장소: 이 기기의 IndexedDB 에만 둔다(서버로 보내지 않는다).
// meta(목록용 가벼운 정보)와 files(원본·PDF 쪽 그림)를 나눠 목록을 빨리 연다.
import { useEffect, useState } from 'react'
import { baseTitle, guessDay, guessKind, type DocMeta } from '../domain/docs'
import type { Day } from '../domain/types'

interface DocFile { id: string; blob: Blob; pages: Blob[] }
const DB = 'yn-docs'
let dbP: Promise<IDBDatabase> | null = null
const listeners = new Set<() => void>()
const notify = () => listeners.forEach((l) => l())

function db(): Promise<IDBDatabase> {
  if (!dbP) {
    dbP = new Promise((res, rej) => {
      const r = indexedDB.open(DB, 1)
      r.onupgradeneeded = () => { r.result.createObjectStore('meta', { keyPath: 'id' }); r.result.createObjectStore('files', { keyPath: 'id' }) }
      r.onsuccess = () => res(r.result)
      r.onerror = () => { dbP = null; rej(r.error) }
    })
  }
  return dbP
}

function tx<T>(stores: string[], mode: IDBTransactionMode, fn: (t: IDBTransaction) => IDBRequest<T> | void): Promise<T> {
  return db().then((d) => new Promise<T>((res, rej) => {
    const t = d.transaction(stores, mode)
    const req = fn(t)
    t.oncomplete = () => res(req ? req.result : (undefined as T))
    t.onerror = () => rej(t.error)
    t.onabort = () => rej(t.error ?? new Error('abort'))
  }))
}

export const listDocs = () => tx<DocMeta[]>(['meta'], 'readonly', (t) => t.objectStore('meta').getAll())
export const getFile = (id: string) => tx<DocFile | undefined>(['files'], 'readonly', (t) => t.objectStore('files').get(id))
export async function updateDoc(m: DocMeta) { await tx(['meta'], 'readwrite', (t) => { t.objectStore('meta').put(m) }); notify() }
export async function deleteDoc(id: string) {
  await tx(['meta', 'files'], 'readwrite', (t) => { t.objectStore('meta').delete(id); t.objectStore('files').delete(id) }); notify()
}

export function useDocs(): { docs: DocMeta[] | null; error: boolean } {
  const [docs, setDocs] = useState<DocMeta[] | null>(null); const [error, setError] = useState(false)
  useEffect(() => {
    let alive = true
    const load = () => listDocs().then((l) => alive && setDocs(l)).catch(() => alive && (setError(true), setDocs([])))
    load(); listeners.add(load)
    return () => { alive = false; listeners.delete(load) }
  }, [])
  return { docs, error }
}

async function toJpeg(src: CanvasImageSource & { width: number; height: number }, max: number, q: number): Promise<{ blob: Blob; url: string }> {
  const s = Math.min(1, max / Math.max(src.width, src.height))
  const c = document.createElement('canvas'); c.width = Math.max(1, Math.round(src.width * s)); c.height = Math.max(1, Math.round(src.height * s))
  const g = c.getContext('2d')!; g.fillStyle = '#fff'; g.fillRect(0, 0, c.width, c.height); g.drawImage(src, 0, 0, c.width, c.height)
  const url = c.toDataURL('image/jpeg', q)
  return { blob: await (await fetch(url)).blob(), url }
}

/** PDF 각 쪽을 그림으로 펼친다(처음 한 번). 휴대폰은 PDF 를 화면 안에서 못 여는 경우가 많아서다 */
async function renderPdf(blob: Blob, maxPages = 12): Promise<Blob[]> {
  const pdfjs = await import('pdfjs-dist')
  const workerUrl = (await import('pdfjs-dist/build/pdf.worker.min.mjs?url')).default
  pdfjs.GlobalWorkerOptions.workerSrc = workerUrl
  const doc = await pdfjs.getDocument({ data: new Uint8Array(await blob.arrayBuffer()) }).promise
  const out: Blob[] = []
  for (let i = 1; i <= Math.min(doc.numPages, maxPages); i++) {
    const page = await doc.getPage(i)
    const vp0 = page.getViewport({ scale: 1 })
    const vp = page.getViewport({ scale: Math.min(3, 1600 / vp0.width) })
    const c = document.createElement('canvas'); c.width = Math.round(vp.width); c.height = Math.round(vp.height)
    const g = c.getContext('2d')!; g.fillStyle = '#fff'; g.fillRect(0, 0, c.width, c.height)
    await page.render({ canvasContext: g, viewport: vp }).promise
    out.push((await toJpeg(c, 1600, 0.85)).blob)
  }
  await doc.destroy()
  return out
}

export async function addDoc(file: File, days: Day[], defaults: { dayId: string | null }): Promise<DocMeta> {
  const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name)
  let pages: Blob[] = []; let thumb = ''
  if (isPdf) {
    try { pages = await renderPdf(file) } catch { pages = [] }
    if (pages[0]) thumb = (await toJpeg(await createImageBitmap(pages[0]), 240, 0.6)).url
  } else {
    try { thumb = (await toJpeg(await createImageBitmap(file), 240, 0.6)).url } catch { thumb = '' }
  }
  const meta: DocMeta = {
    id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`,
    title: baseTitle(file.name), kind: guessKind(file.name), dayId: guessDay(file.name, days) ?? defaults.dayId,
    note: '', mime: file.type || (isPdf ? 'application/pdf' : 'application/octet-stream'), fileName: file.name,
    size: file.size, pages: pages.length, created: Date.now(), thumb,
  }
  await tx(['meta', 'files'], 'readwrite', (t) => {
    t.objectStore('meta').put(meta); t.objectStore('files').put({ id: meta.id, blob: file, pages } satisfies DocFile)
  })
  try { await navigator.storage?.persist?.() } catch { /* 지원 안 함 */ }
  notify()
  return meta
}

export async function storageUse(): Promise<string | null> {
  try {
    const e = await navigator.storage?.estimate?.()
    return e?.usage != null ? `${(e.usage / 1024 / 1024).toFixed(1)}MB 사용` : null
  } catch { return null }
}
