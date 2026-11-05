// 영수증 사진 → 글자(OCR). 기기 안에서만 처리하며 사진은 밖으로 보내지 않는다.
import type { Worker } from 'tesseract.js'

let workerP: Promise<Worker> | null = null
export type OcrProgress = (p: { status: string; progress: number }) => void
let progressCb: OcrProgress | null = null

async function getWorker(): Promise<Worker> {
  if (!workerP) {
    workerP = (async () => {
      const { createWorker } = await import('tesseract.js')
      const base = new URL(import.meta.env.BASE_URL, location.href).href
      return createWorker('chi_sim', 1, {
        workerPath: `${base}ocr/worker.min.js`,
        corePath: `${base}ocr/core`,
        langPath: `${base}ocr/lang`,
        gzip: true,
        workerBlobURL: false,
        logger: (m: { status: string; progress: number }) => progressCb?.(m),
      })
    })().catch((e) => { workerP = null; throw e })
  }
  return workerP
}

/** 큰 사진은 긴 변 1600px 로 줄여 인식 속도를 높인다 */
async function downscale(file: Blob, max: number, quality = 0.85): Promise<{ blob: Blob; dataUrl: string }> {
  const bmp = await createImageBitmap(file)
  const s = Math.min(1, max / Math.max(bmp.width, bmp.height))
  const c = document.createElement('canvas'); c.width = Math.round(bmp.width * s); c.height = Math.round(bmp.height * s)
  c.getContext('2d')!.drawImage(bmp, 0, 0, c.width, c.height)
  const dataUrl = c.toDataURL('image/jpeg', quality)
  const blob = await (await fetch(dataUrl)).blob()
  return { blob, dataUrl }
}

export async function readReceipt(file: File, onProgress?: OcrProgress): Promise<{ text: string; thumb: string }> {
  progressCb = onProgress ?? null
  const [img, thumb] = await Promise.all([downscale(file, 1600), downscale(file, 240, 0.6)])
  const w = await getWorker()
  const { data } = await w.recognize(img.blob)
  progressCb = null
  return { text: data.text, thumb: thumb.dataUrl }
}
