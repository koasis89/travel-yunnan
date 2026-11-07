import { useEffect, useMemo, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, ImagePlus, Trash2, X } from 'lucide-react'
import { Banner, toast } from '../components/ui'
import { useData } from '../store/data'
import { clearPhotos, getPhoto, importPhotos, usePhotoIds } from '../store/foodPhotos'

type Filter = 'all' | 'named' | 'unknown'

/** 숙소·식사 → 음식 사진: 이 기기에 넣은 리장 음식 사진을 1줄 3장 격자로 보고, 누르면 크게 본다 */
export function FoodPhotos() {
  const data = useData(); const ids = usePhotoIds()
  const [filter, setFilter] = useState<Filter>('all')
  const [open, setOpen] = useState<number | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const name = (id: string) => data.foodPhotoNames[id]
  // 음식 사진(같은 음식끼리) → 한 상 차림 → 가게 외관 → 이름 모르는 사진
  const rank = (id: string) => { const n = name(id)?.nameKo; return !n ? 3 : n.startsWith('가게 외관') ? 2 : n === '한 상 차림' ? 1 : 0 }
  const sorted = useMemo(() => [...(ids ?? [])].sort((a, b) =>
    rank(a) - rank(b) || (name(a)?.nameKo ?? '').localeCompare(name(b)?.nameKo ?? '') || a.localeCompare(b)), [ids, data]) // eslint-disable-line react-hooks/exhaustive-deps
  const shown = sorted.filter((id) => filter === 'all' || (filter === 'named') === !!name(id))
  const namedCount = sorted.filter((id) => name(id)).length

  const add = async (files: File[]) => {
    if (!files.length) return
    setBusy('사진을 여는 중…')
    try {
      const n = await importPhotos(files, (d, t) => setBusy(`사진 넣는 중… ${d}/${t}`))
      toast(n ? `사진 ${n}장을 넣었다` : '넣을 사진을 찾지 못했다')
    } catch { toast('사진을 넣지 못했다') } finally { setBusy(null) }
  }
  const picker = (
    <label className="btn primary" data-testid="photo-add"><ImagePlus size={18} />{busy ?? '사진 묶음(zip)·사진 넣기'}
      <input type="file" accept=".zip,application/zip,image/*" multiple hidden disabled={busy != null}
        onChange={(e) => { add([...(e.target.files ?? [])]); e.target.value = '' }} /></label>
  )

  if (ids === null) return <div className="pad muted">불러오는 중…</div>
  if (!ids.length) return (
    <div className="pad stack">
      <Banner icon={<ImagePlus size={18} />}>받아 둔 <b>리장 음식 사진 묶음(zip)</b>을 한 번 넣으면 이 휴대폰에만 저장되어 인터넷 없이 본다. 사진을 보고 음식 이름을 알 수 있는 것은 이름을 붙여 보여 준다.</Banner>
      {picker}
      <p className="tiny faint" style={{ margin: 0 }}>남이 찍은 사진(携程 등)이 섞여 있어 앱·웹 서버에는 올리지 않는다. 사진은 기기마다 한 번씩 넣는다.</p>
    </div>
  )
  return (
    <div>
      <div className="chips" data-testid="photo-filter">
        {([['all', `전체 ${sorted.length}`], ['named', `이름 있음 ${namedCount}`], ['unknown', `이름 모름 ${sorted.length - namedCount}`]] as [Filter, string][]).map(([k, t]) =>
          <button key={k} className={`chip ${filter === k ? 'on' : ''}`} onClick={() => setFilter(k)}>{t}</button>)}
      </div>
      {data.foodPhotoRestaurant && <p className="pad tiny muted" style={{ margin: '0 0 6px' }}>대부분 리장 {data.foodPhotoRestaurant}(구리 솥 소과반 식당) 사진이다. 이름은 사진을 보고 붙인 것이라 틀릴 수 있다.</p>}
      <div data-testid="photo-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 4, padding: '0 8px' }}>
        {shown.map((id, i) => <Thumb key={id} id={id} label={name(id)?.nameKo} onClick={() => setOpen(i)} />)}
      </div>
      <div className="pad wrap" style={{ marginTop: 12 }}>
        {picker}
        <button className="btn" onClick={() => { if (confirm('이 기기에 넣은 음식 사진을 모두 지울까? (zip 을 다시 넣으면 된다)')) clearPhotos() }}><Trash2 size={16} />모두 지우기</button>
      </div>
      {open != null && shown[open] && <Viewer ids={shown} index={open} onIndex={setOpen} onClose={() => setOpen(null)} />}
    </div>
  )
}

function useBlobUrl(id: string, kind: 'thumb' | 'full') {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    let u: string | null = null; let on = true
    getPhoto(id).then((p) => { if (on && p) { u = URL.createObjectURL(p[kind]); setUrl(u) } })
    return () => { on = false; if (u) URL.revokeObjectURL(u); setUrl(null) }
  }, [id, kind])
  return url
}

function Thumb({ id, label, onClick }: { id: string; label?: string; onClick: () => void }) {
  const url = useBlobUrl(id, 'thumb')
  return (
    <button data-testid="photo-cell" onClick={onClick} style={{ border: 0, padding: 0, background: 'none', textAlign: 'left', minWidth: 0 }}>
      <div style={{ aspectRatio: '1', background: 'var(--line, #e7e1d6)', borderRadius: 8, overflow: 'hidden' }}>
        {url && <img src={url} alt={label ?? id} style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />}
      </div>
      {label && <div className="tiny" style={{ marginTop: 2, lineHeight: 1.25, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{label}</div>}
    </button>
  )
}

function Viewer({ ids, index, onIndex, onClose }: { ids: string[]; index: number; onIndex: (i: number) => void; onClose: () => void }) {
  const data = useData(); const id = ids[index]; const url = useBlobUrl(id, 'full'); const n = data.foodPhotoNames[id]
  const startX = useRef<number | null>(null)
  const go = (d: number) => onIndex((index + d + ids.length) % ids.length)
  useEffect(() => {
    const k = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); if (e.key === 'ArrowRight') go(1); if (e.key === 'ArrowLeft') go(-1) }
    window.addEventListener('keydown', k); return () => window.removeEventListener('keydown', k)
  })
  return (
    <div data-testid="photo-viewer" role="dialog" style={{ position: 'fixed', inset: 0, zIndex: 50, background: 'rgba(0,0,0,.94)', color: '#fff', display: 'flex', flexDirection: 'column' }}
      onTouchStart={(e) => { startX.current = e.touches[0].clientX }}
      onTouchEnd={(e) => { const s = startX.current; startX.current = null; if (s == null) return; const dx = e.changedTouches[0].clientX - s; if (Math.abs(dx) > 50) go(dx < 0 ? 1 : -1) }}>
      <div className="row" style={{ padding: '10px 12px' }}>
        <span className="small grow">{index + 1} / {ids.length}</span>
        <button className="iconbtn" aria-label="닫기" onClick={onClose} style={{ color: '#fff' }}><X size={26} /></button>
      </div>
      <div style={{ flex: 1, minHeight: 0, display: 'grid', placeItems: 'center' }} onClick={onClose}>
        {url && <img src={url} alt={n?.nameKo ?? id} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} onClick={(e) => e.stopPropagation()} />}
      </div>
      <div className="row" style={{ padding: '10px 12px 18px', gap: 8 }}>
        <button className="iconbtn" aria-label="이전" onClick={() => go(-1)} style={{ color: '#fff' }}><ChevronLeft size={30} /></button>
        <div className="grow center">
          {n ? <><div className="bold" style={{ fontSize: '1.1em' }}>{n.nameKo}</div>{n.nameZh && <div style={{ fontSize: '1.25em' }}>{n.nameZh}</div>}</>
            : <div className="small" style={{ opacity: .7 }}>이름 모름 · {id}</div>}
        </div>
        <button className="iconbtn" aria-label="다음" onClick={() => go(1)} style={{ color: '#fff' }}><ChevronRight size={30} /></button>
      </div>
    </div>
  )
}
