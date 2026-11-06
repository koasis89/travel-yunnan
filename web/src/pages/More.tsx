import { Link } from 'react-router-dom'
import { BookOpen, ChevronRight, FolderOpen, Landmark, MapPinned, UtensilsCrossed, Hash, Info, ListChecks, LogOut, MessageSquareText, Receipt, Settings as SettingsIcon, Siren, TrainFront, TriangleAlert, Download } from 'lucide-react'
import type { ReactNode } from 'react'
import { DayChips, TopBar } from '../components/ui'
import { useData, useNow, useSettings } from '../store/data'
import { shiftHours, tripNow } from '../domain/tripTime'

const items: [string, string, string, ReactNode][] = [
  ['/transport', '교통·열차·알림', 'C446·C644, 비용, 캘린더 파일', <TrainFront size={20} />],
  ['/sights', '관광지 안내', '리장고성·옥룡설산·호도협·샹그릴라·쿤밍 사진과 설명', <Landmark size={20} />],
  ['/eats', '맛집 안내', '식당 24곳 대표 메뉴판·사진·주문 문장', <UtensilsCrossed size={20} />],
  ['/amap', '지도 링크 모음', '숙소·관광지·식당 고덕지도 링크, 복사해서 공유', <MapPinned size={20} />],
  ['/docs', '문서함', '바우처·기차표·숙소 예약·영수증 보관', <FolderOpen size={20} />],
  ['/expense', '경비', '예산 대비 지출, CSV', <Receipt size={20} />],
  ['/packing', '준비물·날씨', '체크리스트, 지역별 옷차림', <ListChecks size={20} />],
  ['/notes', '유의사항', '고산병·열차·빵차·배달', <TriangleAlert size={20} />],
  ['/emergency', '긴급 연락처', '영사콜센터·대사관·110·120', <Siren size={20} />],
  ['/templates', '위챗 메시지 템플릿', '빵차·픽업·배달 요청문', <MessageSquareText size={20} />],
  ['/menu', '주문 문장 만들기', '음식·수량·요청 조합', <BookOpen size={20} />],
  ['/number', '숫자 카드', '금액을 크게, 중국어 읽기', <Hash size={20} />],
  ['/settings', '설정', '큰 글씨, 날짜 지정 모드', <SettingsIcon size={20} />],
]

export function More({ onLogout }: { onLogout: () => void }) {
  const data = useData()
  return (
    <div className="page"><TopBar title="더보기" back={false} />
      <div className="card" style={{ margin: '0 16px', padding: 0 }}>
        {items.map(([to, t, s, ic]) => <Link key={to} to={to} className="li"><span className="ic">{ic}</span><span className="grow"><div className="bold">{t}</div><div className="tiny muted">{s}</div></span><ChevronRight size={18} color="var(--ink-3)" /></Link>)}
      </div>
      <div className="card" style={{ margin: '12px 16px 0', padding: 0 }}>
        <div className="li"><span className="ic"><Download size={20} /></span><span className="grow"><div className="bold">홈 화면에 추가</div>
          <div className="tiny muted">안드로이드 크롬: 메뉴 → 홈 화면에 추가 · 아이폰 사파리: 공유 → 홈 화면에 추가. 한 번 열면 오프라인에서도 열린다</div></span></div>
        <button className="li" style={{ width: '100%', border: 0, background: 'none', textAlign: 'left' }} onClick={() => confirm('로그아웃하면 다시 이메일을 입력해야 한다. 로그아웃할까?') && onLogout()}>
          <span className="ic"><LogOut size={20} /></span><span className="grow bold">로그아웃</span></button>
      </div>
      <p className="pad tiny faint center" style={{ marginTop: 16 }}><Info size={12} /> 데이터 버전 {data.version} · 웹 {__APP_VERSION__}<br />음식 사진: Wikimedia Commons (CC)</p>
    </div>
  )
}

export function Settings() {
  const data = useData(); const [s, setS] = useSettings(); const now = useNow()
  const cur = tripNow(s.override)
  const sel = data.days.find((d) => d.date === cur.slice(0, 10))?.id ?? null
  return (
    <div className="page"><TopBar title="설정" />
      <div className="card" style={{ margin: '0 16px' }}>
        <label className="check" style={{ padding: 0 }}><input type="checkbox" checked={s.bigFont} onChange={() => setS({ ...s, bigFont: !s.bigFont })} />
          <span className="grow"><div className="bold">큰 글씨</div><div className="tiny muted">모든 화면 글자를 크게</div></span></label>
      </div>
      <div className="section">날짜 지정 모드</div>
      <div className="card stack" style={{ margin: '0 16px' }}>
        <div className="small muted">여행 전에 특정 날짜·시각 화면을 미리 본다. 현지 시각 기준이다.</div>
        <label className="check" style={{ padding: 0 }}><input type="checkbox" checked={!!s.override} onChange={() => setS({ ...s, override: s.override ? null : `${data.days[0].date}T09:00` })} />
          <span className="grow bold">사용 · 현재 {now.replace('T', ' ')}</span></label>
        {s.override && <>
          <DayChips days={data.days} selected={sel} onSelect={(id) => { const d = data.day(id); if (d) setS({ ...s, override: `${d.date}T${cur.slice(11)}` }) }} />
          <div className="row" style={{ justifyContent: 'center' }}>
            <button className="btn ghost" onClick={() => setS({ ...s, override: shiftHours(cur, -1) })}>-1시간</button>
            <span className="bold" style={{ fontSize: '1.4em', padding: '0 12px' }}>{cur.slice(11)}</span>
            <button className="btn ghost" onClick={() => setS({ ...s, override: shiftHours(cur, 1) })}>+1시간</button>
          </div>
          <div className="wrap" style={{ justifyContent: 'center' }}>{['00', '15', '30', '45'].map((m) => <button key={m} className={`chip ${cur.slice(14) === m ? 'on' : ''}`} onClick={() => setS({ ...s, override: cur.slice(0, 14) + m })}>:{m}</button>)}</div>
        </>}
      </div>
    </div>
  )
}
