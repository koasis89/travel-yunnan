import { useEffect, useState } from 'react'
import { HashRouter, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import { BedDouble, CalendarDays, House, Menu as MenuIcon } from 'lucide-react'
import { TripData } from './domain/types'
import { DataProvider, useSettings } from './store/data'
import { logout, savedEmail, unlock } from './store/vault'
import { BrandHeader, ToastHost } from './components/ui'
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

const TABS = [
  { to: '/', label: '오늘', icon: House, end: true },
  { to: '/schedule', label: '일정', icon: CalendarDays },
  { to: '/stay', label: '숙소·식사', icon: BedDouble },
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
            <Route path="/taxi/:type/:id" element={<Taxi />} />
            <Route path="/show/:id" element={<ShowPhrase />} />
            <Route path="/transport" element={<Transport />} />
            <Route path="/expense" element={<Expense />} />
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
