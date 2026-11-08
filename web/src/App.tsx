import { useEffect, useState } from 'react'
import { HashRouter, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import { BedDouble, CalendarDays, House, Menu as MenuIcon, Search as SearchIcon } from 'lucide-react'
import { TripData } from './domain/types'
import { DataProvider, useSettings } from './store/data'
import { logout, savedEmail, unlock } from './store/vault'
import { BrandHeader, ToastHost, toast } from './components/ui'
import { Login } from './pages/Login'
import { Home } from './pages/Home'
import { Schedule } from './pages/Schedule'
import { ShowPhrase, Taxi } from './pages/Taxi'
import { Menu, Stay } from './pages/Stay'
import { Transport } from './pages/Transport'
import { Expense } from './pages/Expense'
import { Emergency, Notes, Packing } from './pages/Info'
import { NumberCard, Templates } from './pages/Tools'
import { More, Settings } from './pages/More'
import { MilkTea } from './pages/MilkTea'
import { ArrivalCard } from './pages/ArrivalCard'
import { Search } from './pages/Search'
import { DocView, Docs } from './pages/Docs'
import { SightView, Sights } from './pages/Sights'
import { AmapLinks } from './pages/AmapLinks'
import { EatView, Eats } from './pages/Eats'

const TABS = [
  { to: '/', label: '오늘', icon: House, end: true },
  { to: '/schedule', label: '일정', icon: CalendarDays },
  { to: '/stay', label: '숙소·식사', icon: BedDouble },
  { to: '/search', label: '검색', icon: SearchIcon },
  { to: '/more', label: '더보기', icon: MenuIcon },
]

function TabBar() {
  const loc = useLocation()
  if (loc.pathname.startsWith('/taxi') || loc.pathname.startsWith('/show')) return null
  return (
    <div className="tabbar"><nav>
      {TABS.map(({ to, label, icon: Icon, end }) => (
        <NavLink key={to} to={to} end={end} className={({ isActive }) => (isActive ? 'active' : '')}>
          <span className="pill"><Icon size={22} /></span>{label}
        </NavLink>
      ))}
    </nav></div>
  )
}

function ScrollTop() {
  const { pathname } = useLocation()
  useEffect(() => { window.scrollTo(0, 0) }, [pathname])
  return null
}

function BigFont() {
  const [s] = useSettings()
  useEffect(() => { document.documentElement.classList.toggle('big', s.bigFont) }, [s.bigFont])
  // 저장 공간이 꽉 차 기기에 저장하지 못하면 알린다(이번 실행 동안은 메모리에 남는다)
  useEffect(() => {
    const f = () => toast('기기 저장 공간이 부족해 변경을 저장하지 못했다. 문서함의 큰 파일을 지우고 다시 시도한다')
    window.addEventListener('yn-storage-fail', f); return () => window.removeEventListener('yn-storage-fail', f)
  }, [])
  return null
}

export default function App() {
  const [data, setData] = useState<TripData | null>(null)
  const [booting, setBooting] = useState(!!savedEmail())

  useEffect(() => {
    const email = savedEmail()
    if (!email) return
    unlock(email).then((b) => { if (b) setData(new TripData(b)); else logout() }).catch(() => {}).finally(() => setBooting(false))
  }, [])

  if (booting) return <div className="app"><BrandHeader status="여는 중…" /></div>
  if (!data) return <><Login onOpen={(b) => setData(new TripData(b))} /><ToastHost /></>

  return (
    <DataProvider data={data}>
      <HashRouter>
        <ScrollTop /><BigFont />
        <div className="app">
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/schedule" element={<Schedule />} />
            <Route path="/stay" element={<Stay />} />
            <Route path="/lodgings" element={<Stay />} />
            <Route path="/meals" element={<StayMeals />} />
            <Route path="/menu" element={<Menu />} />
            <Route path="/milktea" element={<MilkTea />} />
            <Route path="/arrival-card" element={<ArrivalCard />} />
            <Route path="/search" element={<Search />} />
            <Route path="/taxi/:type/:id" element={<Taxi />} />
            <Route path="/show/:id" element={<ShowPhrase />} />
            <Route path="/transport" element={<Transport />} />
            <Route path="/expense" element={<Expense />} />
            <Route path="/docs" element={<Docs />} />
            <Route path="/sights" element={<Sights />} />
            <Route path="/amap" element={<AmapLinks />} />
            <Route path="/eats" element={<Eats />} />
            <Route path="/eats/:id" element={<EatView />} />
            <Route path="/sights/:id" element={<SightView />} />
            <Route path="/docs/:id" element={<DocView />} />
            <Route path="/packing" element={<Packing />} />
            <Route path="/notes" element={<Notes />} />
            <Route path="/emergency" element={<Emergency />} />
            <Route path="/templates" element={<Templates />} />
            <Route path="/number" element={<NumberCard />} />
            <Route path="/more" element={<More onLogout={() => { logout(); setData(null) }} />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="*" element={<Home />} />
          </Routes>
          <TabBar />
        </div>
        <ToastHost />
      </HashRouter>
    </DataProvider>
  )
}

/** /meals?day=D2 → 숙소·식사 화면의 식사 탭으로 */
function StayMeals() {
  const loc = useLocation()
  const day = new URLSearchParams(loc.search).get('day')
  useEffect(() => { window.location.hash = `#/stay?tab=meals${day ? `&day=${day}` : ''}` }, [day])
  return null
}
