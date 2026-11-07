import { useMemo, useState } from 'react'
import { Copy, MapPin, Search } from 'lucide-react'
import { Banner, TopBar, copyText } from '../components/ui'
import { useData } from '../store/data'
import { amapRows, amapText, type AmapRow } from '../domain/cards'

const GROUPS: AmapRow['group'][] = ['숙소', '관광지·장소', '식당']

export function AmapLinks() {
  const data = useData()
  const rows = useMemo(() => amapRows(data), [data])
  const [q, setQ] = useState('')
  const [group, setGroup] = useState<AmapRow['group'] | null>(null)
  const matched = rows.filter((r) => !q.trim() || `${r.nameKo} ${r.nameZh} ${r.addrZh}`.includes(q.trim()))
  const shown = matched.filter((r) => !group || r.group === group)
  return (
    <div className="page">
      <TopBar title="지도 링크 모음" right={<button className="iconbtn" aria-label="전체 복사" onClick={() => copyText(shown.map(amapText).join('\n\n'), `${shown.length}곳을 복사했다`)}><Copy size={20} /></button>} />
      <div className="pad stack">
        <Banner icon={<MapPin size={18} />}>고덕지도(Amap) 앱을 깔고 링크를 누른다. <b>정확</b> 표시는 고덕지도에서 공유받은 위치이고, 나머지는 이름·주소 검색 링크라 검색 결과에서 맞는 곳을 고른다. 오른쪽 위 버튼은 보이는 목록 전체를 복사한다.</Banner>
        <div className="row" style={{ gap: 8 }}><Search size={18} /><input className="input grow" placeholder="이름·주소 검색" value={q} onChange={(e) => setQ(e.target.value)} /></div>
      </div>
      <div className="chips" data-testid="amap-groups">
        <button className={`chip${group === null ? ' on' : ''}`} onClick={() => setGroup(null)}>전체 {matched.length}</button>
        {GROUPS.map((g) => <button key={g} className={`chip${group === g ? ' on' : ''}`} onClick={() => setGroup(g)}>{g} {matched.filter((r) => r.group === g).length}</button>)}
      </div>
      {GROUPS.map((g) => {
        const list = shown.filter((r) => r.group === g)
        if (!list.length) return null
        return (
          <div key={g}>
            <div className="section">{g} ({list.length})</div>
            <div className="pad"><div className="card flat" style={{ padding: 0 }}>
              {list.map((r) => (
                <div key={r.url} className="li" data-testid="amap-row">
                  <a className="grow" href={r.url} target="_blank" rel="noreferrer" style={{ color: 'inherit', textDecoration: 'none' }}>
                    <div className="bold">{r.nameKo} {r.exact && <span className="tiny" style={{ background: 'var(--primary)', color: '#fff', borderRadius: 6, padding: '1px 6px' }}>정확</span>}</div>
                    {r.nameZh && <div className="small zh">{r.nameZh}</div>}
                    {r.addrZh && <div className="tiny muted">{r.addrZh}</div>}
                  </a>
                  <button className="iconbtn" aria-label="복사" onClick={() => copyText(amapText(r), '이름·주소·링크를 복사했다')}><Copy size={18} /></button>
                </div>
              ))}
            </div></div>
          </div>
        )
      })}
    </div>
  )
}
