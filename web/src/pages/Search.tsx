import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Search as SearchIcon, X } from 'lucide-react'
import { TopBar } from '../components/ui'
import { useData } from '../store/data'
import { KIND_ORDER, buildSearchIndex, searchIndex } from '../domain/search'

const EXAMPLES = ['여권', '산소캔', '기차', '과교미선', '빵차', '사방가', '택시', '딤섬', '알리페이', '고산병']

/** 통합 검색(아래 탭 '검색'): 앱 안의 일정·숙소·식당·관광지·회화·템플릿 등을 한 번에 찾는다. 중국어로 찾아도 된다 */
export function Search() {
  const data = useData()
  const [sp, setSp] = useSearchParams()
  const q = sp.get('q') ?? ''
  const kind = sp.get('k') ?? ''
  const index = useMemo(() => buildSearchIndex(data), [data])
  const all = useMemo(() => searchIndex(index, q), [index, q])
  const kinds = KIND_ORDER.filter((k) => all.some((h) => h.kind === k))
  const shown = kind ? all.filter((h) => h.kind === kind) : all
  const set = (nq: string, nk = '') => setSp(nq ? (nk ? { q: nq, k: nk } : { q: nq }) : {}, { replace: true })
  return (
    <div className="page">
      <TopBar title="검색" back={false} />
      <div className="pad" style={{ paddingTop: 4 }}>
        <div className="row" style={{ gap: 8, position: 'relative' }}>
          <SearchIcon size={18} style={{ position: 'absolute', left: 12, opacity: 0.6 }} />
          <input className="input" type="text" enterKeyHint="search" autoFocus data-testid="search-input" placeholder="일정·숙소·식당·관광지·회화·체크리스트…"
            value={q} onChange={(e) => set(e.target.value)} style={{ paddingLeft: 38, paddingRight: 38 }} />
          {q && <button className="btn ghost" aria-label="지우기" onClick={() => set('')} style={{ position: 'absolute', right: 2, padding: 6 }}><X size={18} /></button>}
        </div>
      </div>
      {!q && (
        <div className="pad stack" style={{ gap: 8 }}>
          <div className="small muted">한국어·중국어 모두 된다. 띄어 쓰면 낱말이 모두 들어간 것만 찾는다.</div>
          <div className="wrap">{EXAMPLES.map((x) => <button key={x} className="chip" onClick={() => set(x)}>{x}</button>)}</div>
        </div>
      )}
      {q && <>
        <div className="chips" data-testid="search-kinds">
          <button className={`chip ${!kind ? 'on' : ''}`} onClick={() => set(q)}>전체 {all.length}</button>
          {kinds.map((k) => <button key={k} className={`chip ${kind === k ? 'on' : ''}`} onClick={() => set(q, k)}>{k} {all.filter((h) => h.kind === k).length}</button>)}
        </div>
        {shown.length === 0
          ? <div className="pad muted">찾는 내용이 없다. 낱말을 줄이거나 다르게 써 본다.</div>
          : <div className="pad" style={{ paddingTop: 4 }}><div className="card flat" style={{ padding: 0 }}>
              {shown.map((h, i) => (
                <Link key={`${h.to}-${h.title}-${i}`} to={h.to} className="li" data-testid="search-hit" style={{ textDecoration: 'none', color: 'inherit', alignItems: 'flex-start', gap: 10 }}>
                  <span className="tag" style={{ flex: 'none', marginTop: 2 }}>{h.kind}</span>
                  <div className="grow" style={{ minWidth: 0 }}>
                    <div className="bold" style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{h.title}</div>
                    {h.sub && <div className="tiny muted">{h.sub}</div>}
                  </div>
                </Link>
              ))}
            </div></div>}
      </>}
    </div>
  )
}
