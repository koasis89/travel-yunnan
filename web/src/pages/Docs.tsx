import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { CheckSquare, CloudDownload, CloudUpload, FileText, FolderOpen, Pencil, Plus, Search, Share2, Trash2 } from 'lucide-react'
import { Banner, DayChips, TopBar, toast } from '../components/ui'
import { useData, useNow } from '../store/data'
import { resolvePhase } from '../domain/tripTime'
import { DOC_KINDS, filterDocs, fmtSize, sortDocs, type DocKind, type DocMeta } from '../domain/docs'
import { addDoc, deleteDoc, getFile, storageUse, updateDoc, useDocs } from '../store/docs'
import { deletedSet, driveName, driveReady, getDriveFile, importedMap, listDrive, markImported, parseDriveName, rememberDeleted, uploadToDrive, useDrive } from '../store/drive'

const KIND_TONE: Record<DocKind, string> = {
  '바우처': '#6f42c1', '기차표': '#1f4e79', '항공권': '#4b5563', '숙소 예약': '#b4442e', '영수증': '#e09a2b', '보험·신분': '#1f6f6b', '블로그': '#03a94f', '기타': '#6e6a64',
}
export const KindBadge = ({ k }: { k: DocKind }) => <span className="tiny bold" style={{ background: KIND_TONE[k], color: '#fff', borderRadius: 8, padding: '2px 8px', whiteSpace: 'nowrap' }}>{k}</span>

function useTodayId(): string | null {
  const data = useData(); const now = useNow()
  const p = resolvePhase(now, data.days)
  return p.kind === 'on' ? p.day.id : null
}

export function Docs() {
  const data = useData(); const todayId = useTodayId()
  const [sp, setSp] = useSearchParams()
  const { docs, error } = useDocs()
  const [kind, setKind] = useState<DocKind | null>(null)
  const day = sp.get('day')
  const [q, setQ] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [picking, setPicking] = useState(false)
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const [use, setUse] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const nav = useNavigate()
  useEffect(() => { storageUse().then(setUse) }, [docs])
  const shown = useMemo(() => sortDocs(filterDocs(docs ?? [], kind, day, q), todayId), [docs, kind, day, q, todayId])

  async function onFiles(files: FileList | null) {
    if (!files?.length) return
    const list = Array.from(files); let last: DocMeta | null = null
    try {
      for (const [i, f] of list.entries()) {
        setBusy(`${list.length > 1 ? `${i + 1}/${list.length} ` : ''}${f.name} 저장 중…${/pdf/i.test(f.type + f.name) ? ' (PDF 쪽을 그림으로 펼친다)' : ''}`)
        last = await addDoc(f, data.days, { dayId: day ?? todayId })
        if (driveReady()) backupDoc(last).catch(() => { /* 나중에 '드라이브로 백업'으로 다시 */ })
      }
      toast(`${list.length}건을 문서함에 넣었다`)
      if (list.length === 1 && last) nav(`/docs/${last.id}?edit=1`)
    } catch { toast('저장하지 못했다. 저장 공간을 확인한다') } finally { setBusy(null); if (fileRef.current) fileRef.current.value = '' }
  }

  return (
    <div className="page">
      <TopBar title="문서함" />
      <div className="pad stack">
        <button className="btn primary block" disabled={!!busy} onClick={() => fileRef.current?.click()}><Plus size={18} />문서 추가 (사진·캡처·PDF)</button>
        <input ref={fileRef} type="file" accept="image/*,application/pdf,.pdf" multiple hidden data-testid="doc-input" onChange={(e) => onFiles(e.target.files)} />
        {busy && <Banner icon={<FileText size={18} />}>{busy}</Banner>}
        <DriveBackupBar docs={docs ?? []} />
        {error && <Banner tone="rose">이 브라우저는 문서 저장(IndexedDB)을 막고 있다. 시크릿 창이 아닌 일반 창에서 연다.</Banner>}
      </div>
      <DayChips days={data.days} selected={day} todayId={todayId} allLabel="전체 일차" onSelect={(id) => setSp(id ? { day: id } : {}, { replace: true })} />
      <div className="chips">
        <button className={`chip ${kind === null ? 'on' : ''}`} onClick={() => setKind(null)}>전체</button>
        {DOC_KINDS.map((k) => <button key={k} className={`chip ${kind === k ? 'on' : ''}`} onClick={() => setKind(kind === k ? null : k)}>{k}</button>)}
      </div>
      {(docs?.length ?? 0) > 5 && <div className="pad"><div className="row" style={{ gap: 8 }}><Search size={18} /><input className="input grow" placeholder="제목·메모 검색" value={q} onChange={(e) => setQ(e.target.value)} /></div></div>}
      <div className="pad">
        {(docs?.length ?? 0) > 0 && (
          <div className="row" data-testid="doc-select-bar" style={{ gap: 8, marginBottom: 8, flexWrap: 'wrap' }}>
            {!picking ? <button className="btn ghost" onClick={() => { setPicking(true); setPicked(new Set()) }}><CheckSquare size={16} />선택</button> : <>
              <button className="btn ghost" onClick={() => setPicked(picked.size === shown.length ? new Set() : new Set(shown.map((d) => d.id)))}>{picked.size === shown.length ? '선택 해제' : `전체 선택(${shown.length})`}</button>
              <button className="btn" style={{ color: 'var(--danger, #c62828)' }} disabled={picked.size === 0 || !!busy} onClick={async () => {
                if (!confirm(`선택한 ${picked.size}건을 이 기기 문서함에서 지울까? 되돌릴 수 없다(구글 드라이브·다른 기기에는 남는다)`)) return
                await deleteDocs(docs!.filter((d) => picked.has(d.id)))
                toast(`${picked.size}건을 지웠다`); setPicked(new Set()); setPicking(false)
              }}><Trash2 size={16} />{picked.size}건 삭제</button>
              <button className="btn ghost" onClick={() => { setPicking(false); setPicked(new Set()) }}>취소</button>
            </>}
          </div>
        )}
        <div className="card flat" style={{ padding: 0 }}>
          {docs === null && <div className="li muted small">불러오는 중…</div>}
          {docs !== null && shown.length === 0 && (
            <div className="li muted small" style={{ display: 'block' }}>
              {docs.length === 0 ? <>아직 문서가 없다. 바우처·기차표 예매 확인·숙소 예약 확인·영수증 캡처를 넣어 두면 인터넷이 없어도 바로 꺼내 보여 줄 수 있다.</> : '조건에 맞는 문서가 없다'}
            </div>
          )}
          {shown.map((d) => (
            <Link key={d.id} to={`/docs/${d.id}`} className="li" data-testid="doc-row" onClick={(e) => { if (picking) { e.preventDefault(); const n = new Set(picked); if (n.has(d.id)) n.delete(d.id); else n.add(d.id); setPicked(n) } }}>
              {picking && <input type="checkbox" readOnly checked={picked.has(d.id)} data-testid="doc-pick" style={{ width: 22, height: 22, flex: 'none', accentColor: 'var(--primary)' }} />}
              {d.thumb ? <img src={d.thumb} alt="" style={{ width: 52, height: 52, objectFit: 'cover', borderRadius: 10, flex: 'none', border: '1px solid var(--line)' }} />
                : <span className="ic" style={{ width: 52, height: 52 }}><FileText size={24} /></span>}
              <span className="grow">
                <div className="bold">{d.title}</div>
                <div className="tiny muted row" style={{ gap: 6, flexWrap: 'wrap' }}>
                  <KindBadge k={d.kind} />{d.dayId ? `${d.dayId}${d.dayId === todayId ? ' 오늘' : ''}` : '일차 없음'}{d.pages ? ` · ${d.pages}쪽` : ''}{d.note ? ` · ${d.note}` : ''}
                </div>
              </span>
            </Link>
          ))}
        </div>
        <p className="tiny faint">문서는 이 휴대폰 안에만 저장되고 서버로 보내지 않는다. 다른 가족 휴대폰에는 각자 넣거나, 문서를 열어 공유 버튼으로 보낸다. 브라우저 데이터를 지우면 함께 지워진다.{use ? ` · ${use}` : ''}</p>
      </div>
    </div>
  )
}

export function DocView() {
  const { id = '' } = useParams(); const [sp] = useSearchParams(); const nav = useNavigate()
  const data = useData()
  const { docs } = useDocs()
  const meta = docs?.find((d) => d.id === id) ?? null
  const [urls, setUrls] = useState<string[]>([])
  const [orig, setOrig] = useState<Blob | null>(null)
  const [edit, setEdit] = useState(sp.get('edit') === '1')
  useEffect(() => {
    let made: string[] = []
    getFile(id).then((f) => {
      if (!f) return
      setOrig(f.blob)
      const imgs = f.pages.length ? f.pages : f.blob.type.startsWith('image/') ? [f.blob] : []
      made = imgs.map((b) => URL.createObjectURL(b)); setUrls(made)
    })
    return () => made.forEach((u) => URL.revokeObjectURL(u))
  }, [id])

  if (docs && !meta) return <div className="page"><TopBar title="문서" /><div className="pad muted">문서를 찾을 수 없다(삭제됐거나 다른 기기에서 넣은 문서).</div></div>
  if (!meta) return <div className="page"><TopBar title="문서" /><div className="pad muted">불러오는 중…</div></div>

  const file = orig ? new File([orig], meta.fileName, { type: meta.mime }) : null
  async function share() {
    if (!file) return
    try {
      if (navigator.canShare?.({ files: [file] })) { await navigator.share({ files: [file], title: meta!.title }); return }
    } catch (e) { if ((e as Error).name === 'AbortError') return }
    const a = document.createElement('a'); a.href = URL.createObjectURL(file); a.download = meta!.fileName; a.click()
    setTimeout(() => URL.revokeObjectURL(a.href), 4000)
  }
  function openOriginal() {
    if (!file) return
    const u = URL.createObjectURL(file); window.open(u, '_blank'); setTimeout(() => URL.revokeObjectURL(u), 60000)
  }
  return (
    <div className="page">
      <TopBar title={meta.title} right={<>
        <button className="iconbtn" aria-label="정보 고치기" onClick={() => setEdit(!edit)}><Pencil size={20} /></button>
        <button className="iconbtn" aria-label="공유" onClick={share}><Share2 size={20} /></button>
      </>} />
      {edit && <DocEdit meta={meta} days={data.days.map((d) => d.id)} onDone={() => setEdit(false)} onDelete={async () => {
        if (!confirm('이 문서를 삭제할까? 되돌릴 수 없다')) return
        await deleteDocs([meta]); toast('삭제했다'); nav('/docs', { replace: true })
      }} />}
      <div className="pad tiny muted row" style={{ gap: 6, flexWrap: 'wrap' }}>
        <KindBadge k={meta.kind} />{meta.dayId ?? '일차 없음'} · {fmtSize(meta.size)}{meta.pages ? ` · ${meta.pages}쪽` : ''}{meta.note ? ` · ${meta.note}` : ''}
      </div>
      <div style={{ background: '#fff', padding: '0 4px' }} data-testid="doc-pages">
        {urls.map((u, i) => <img key={u} src={u} alt={`${meta.title} ${i + 1}쪽`} style={{ width: '100%', display: 'block', marginBottom: 6, borderRadius: 6, border: '1px solid var(--line)' }} />)}
      </div>
      {urls.length === 0 && orig && (
        <div className="pad"><Banner icon={<FolderOpen size={18} />}>이 문서는 화면 안에서 미리 볼 수 없다. 아래 버튼으로 휴대폰의 기본 앱에서 연다.</Banner></div>
      )}
      <div className="pad stack">
        <div className="row">
          <button className="btn block grow" onClick={openOriginal} disabled={!file}><FolderOpen size={18} />원본 열기</button>
          <button className="btn block grow" onClick={share} disabled={!file}><Share2 size={18} />공유·저장</button>
        </div>
        <DriveDocButton meta={meta} />
        <p className="tiny faint">사진은 두 손가락으로 확대한다. 검표원·가이드에게 보여 줄 때는 화면 밝기를 올린다.</p>
      </div>
    </div>
  )
}

function DocEdit({ meta, days, onDone, onDelete }: { meta: DocMeta; days: string[]; onDone: () => void; onDelete: () => void }) {
  const [title, setTitle] = useState(meta.title); const [kind, setKind] = useState<DocKind>(meta.kind)
  const [day, setDay] = useState<string | null>(meta.dayId); const [note, setNote] = useState(meta.note)
  return (
    <div className="pad"><div className="card stack">
      <div className="small bold">제목</div>
      <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} data-testid="doc-title" />
      <div className="small bold">분류</div>
      <div className="wrap">{DOC_KINDS.map((k) => <button key={k} type="button" className={`chip ${kind === k ? 'on' : ''}`} onClick={() => setKind(k)}>{k}</button>)}</div>
      <div className="small bold">일차</div>
      <div className="wrap">
        <button type="button" className={`chip ${day === null ? 'on' : ''}`} onClick={() => setDay(null)}>없음</button>
        {days.map((d) => <button key={d} type="button" className={`chip ${day === d ? 'on' : ''}`} onClick={() => setDay(d)}>{d}</button>)}
      </div>
      <input className="input" placeholder="메모(예약번호·좌석·주의사항)" value={note} onChange={(e) => setNote(e.target.value)} />
      <div className="row">
        <button className="btn ghost" onClick={onDelete} aria-label="삭제"><Trash2 size={18} />삭제</button>
        <span className="grow" />
        <button className="btn primary" disabled={!title.trim()} onClick={async () => {
          await updateDoc({ ...meta, title: title.trim(), kind, dayId: day, note: note.trim() }); toast('저장했다'); onDone()
        }}>저장</button>
      </div>
    </div></div>
  )
}

/** 홈·일정 화면용: 그 일차 문서 바로가기 */
export function DayDocsLink({ dayId }: { dayId: string }) {
  const { docs } = useDocs()
  const n = docs?.filter((d) => d.dayId === dayId).length ?? 0
  if (!n) return null
  const first = docs!.filter((d) => d.dayId === dayId).sort((a, b) => b.created - a.created)
  return (
    <Link to={n === 1 ? `/docs/${first[0].id}` : `/docs?day=${dayId}`} className="card row" style={{ textDecoration: 'none', gap: 12 }} data-testid="day-docs">
      <FileText size={26} />
      <div className="grow"><div className="bold">{dayId} 문서 {n}건</div><div className="small muted">{first.slice(0, 3).map((d) => d.title).join(' · ')}</div></div>
    </Link>
  )
}

/** 문서 1건을 드라이브(문서함/D3/…)로 올린다 */
export async function backupDoc(m: DocMeta, force = false) {
  const f = await getFile(m.id)
  if (!f) throw new Error('파일 없음')
  return uploadToDrive(`doc:${m.id}`, { folder: '문서함', sub: m.dayId ?? '공통', name: driveName(m.dayId, m.title, m.id, m.fileName), blob: f.blob, memo: [m.kind, m.note].filter(Boolean).join(' · ') }, force)
}

/** 문서함 위: 드라이브 백업 상태·한꺼번에 올리기(설정 안 했으면 설정 안내) */
function DriveBackupBar({ docs }: { docs: DocMeta[] }) {
  const data = useData()
  const { ready, isUploaded: up } = useDrive()
  const [run, setRun] = useState<string | null>(null)
  const left = docs.filter((d) => !up(`doc:${d.id}`))
  if (!ready) return <Link className="tiny muted" to="/settings" data-testid="drive-setup-link" style={{ textAlign: 'center' }}>구글 드라이브 백업 설정하기 →</Link>
  /** force: 이미 올린 것도 다시 보낸다(드라이브에서 지웠을 때). 드라이브에 같은 이름이 있으면 그쪽에서 건너뛴다 */
  async function all(force = false) {
    const list = force ? docs : left
    if (force && !confirm(`이 기기 문서 ${list.length}건을 드라이브로 다시 보낼까? 드라이브에 이미 있는 것은 건너뛰고, 지워진 것만 다시 만든다`)) return
    let ok = 0, fail = 0
    for (const [i, d] of list.entries()) {
      setRun(`드라이브로 올리는 중 ${i + 1}/${list.length}`)
      try { await backupDoc(d, force); ok++ } catch { fail++ }
    }
    setRun(null); toast(fail ? `${ok}건 보냄 · ${fail}건 실패(인터넷 확인 후 다시)` : `${ok}건을 드라이브로 보냈다`)
  }
  /** 드라이브(문서함)에 있는데 이 기기에 없는 문서를 가져온다 */
  async function pull() {
    setRun('드라이브 목록을 읽는 중…')
    try {
      const files = await listDrive('문서함')
      const imported = importedMap()
      const localIds = new Set(docs.map((d) => d.id))
      const localNames = new Set(docs.map((d) => driveName(d.dayId, d.title, d.id, d.fileName)))
      const gone = deletedSet()
      const todo = files.filter((f) => !imported[f.id] && !gone.has(f.id) && !gone.has(f.name) && !gone.has(parseDriveName(f.name).id ?? '-') && !localNames.has(f.name) && !localIds.has(parseDriveName(f.name).id ?? ''))
      if (!todo.length) { toast('가져올 새 문서가 없다'); return }
      if (!confirm(`드라이브에서 ${todo.length}건을 이 기기 문서함으로 가져올까?`)) return
      let ok = 0, fail = 0
      for (const [i, f] of todo.entries()) {
        setRun(`가져오는 중 ${i + 1}/${todo.length}`)
        try { await importOne(f.id, f.sub, f.name, data); ok++ } catch { fail++ }
      }
      toast(fail ? `${ok}건 가져옴 · ${fail}건 실패` : `${ok}건을 가져왔다`)
    } catch (e) { toast(`가져오지 못했다: ${(e as Error).message}`) } finally { setRun(null) }
  }
  return (
    <div className="stack" data-testid="drive-bar" style={{ gap: 6 }}>
      <div className="row" style={{ gap: 8, justifyContent: 'space-between' }}>
        <span className="tiny muted"><CloudUpload size={14} style={{ verticalAlign: -2 }} /> 드라이브 백업 {docs.length - left.length}/{docs.length}</span>
        {left.length > 0
          ? <button className="btn" disabled={!!run} onClick={() => all()}><CloudUpload size={16} />드라이브로 백업 ({left.length}건)</button>
          : <button className="btn ghost" data-testid="drive-reupload" disabled={!!run || docs.length === 0} onClick={() => all(true)}><CloudUpload size={16} />모두 올렸다 · 다시 올리기</button>}
      </div>
      <button className="btn ghost" data-testid="drive-pull" disabled={!!run} onClick={pull}><CloudDownload size={16} />드라이브에서 가져오기(앱·다른 기기 문서)</button>
      {run && <div className="tiny muted">{run}</div>}
    </div>
  )
}

function DriveDocButton({ meta }: { meta: DocMeta }) {
  const { ready, isUploaded: up } = useDrive()
  const [busy, setBusy] = useState(false)
  if (!ready) return null
  const done = up(`doc:${meta.id}`)
  return (
    <button className="btn block" data-testid="drive-doc" disabled={busy || done} onClick={async () => {
      setBusy(true)
      try { await backupDoc(meta); toast('드라이브에 올렸다') } catch (e) { toast(`올리지 못했다: ${(e as Error).message}`) } finally { setBusy(false) }
    }}><CloudUpload size={18} />{done ? '드라이브에 올렸다' : busy ? '올리는 중…' : '드라이브로 올리기'}</button>
  )
}

/** 드라이브 파일 1건 → 이 기기 문서함(제목·일차·분류·메모를 이름·설명에서 되살린다) */
async function importOne(driveId: string, sub: string, name: string, data: ReturnType<typeof useData>) {
  const { blob, memo } = await getDriveFile(driveId)
  const p = parseDriveName(name)
  const meta = await addDoc(new File([blob], name, { type: blob.type }), data.days, { dayId: null })
  const [k, ...rest] = memo.split(' · ')
  const kind = (DOC_KINDS as readonly string[]).includes(k) ? (k as DocKind) : meta.kind
  const dayId = /^D\d+$/.test(sub) ? sub : p.dayId ?? meta.dayId
  await updateDoc({ ...meta, title: p.title || meta.title, dayId, kind, note: (DOC_KINDS as readonly string[]).includes(k) ? rest.join(' · ') : memo })
  markImported(driveId, meta.id)
}

/** 여러 건 지우기(이 기기만). 드라이브에서 다시 가져오지 않게 이름·드라이브 id 를 기억한다 */
export async function deleteDocs(list: DocMeta[]) {
  const imp = importedMap()
  for (const m of list) {
    const driveIds = Object.entries(imp).filter(([, local]) => local === m.id).map(([driveId]) => driveId)
    rememberDeleted([m.id, driveName(m.dayId, m.title, m.id, m.fileName), ...driveIds])
    await deleteDoc(m.id)
  }
}
