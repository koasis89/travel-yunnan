import { registerSW } from 'virtual:pwa-register'

/**
 * 앱(PWA) 업데이트. 새 버전은 서비스 워커가 받아 두고 자동으로 새로고침한다(registerType: autoUpdate).
 * 홈 화면 앱은 껐다 켜도 뒤에 살아 있던 화면이 그대로 열려 새 버전 확인을 건너뛸 수 있으므로,
 * 앱이 다시 앞으로 올 때와 30분마다 서버에 새 버전이 있는지 묻는다.
 */
let reg: ServiceWorkerRegistration | undefined

export function initUpdates() {
  registerSW({
    immediate: true,
    onRegisteredSW(_url, r) {
      reg = r
      if (!r) return
      const check = () => { if (navigator.onLine) r.update().catch(() => {}) }
      setInterval(check, 30 * 60 * 1000)
      document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') check() })
    },
  })
}

/** 지금 새 버전이 있는지 확인한다. 'new' 이면 받는 중이고, 다 받으면 화면이 저절로 새로고침된다 */
export async function checkUpdate(): Promise<'new' | 'latest' | 'offline' | 'unsupported'> {
  if (!navigator.onLine) return 'offline'
  if (!reg) return 'unsupported'
  await reg.update()
  return reg.installing || reg.waiting ? 'new' : 'latest'
}

/**
 * 앱 파일만 지우고 서버에서 다시 받는다. 문서함(IndexedDB)·체크·경비·설정(localStorage)과
 * 영수증 글자 인식 파일 캐시(ocr)는 그대로 둔다. 인터넷이 될 때만 쓴다.
 */
export async function reinstallApp() {
  const regs = (await navigator.serviceWorker?.getRegistrations()) ?? []
  await Promise.all(regs.map((r) => r.unregister()))
  if ('caches' in window) {
    const keys = await caches.keys()
    await Promise.all(keys.filter((k) => k !== 'ocr').map((k) => caches.delete(k)))
  }
  location.reload()
}
