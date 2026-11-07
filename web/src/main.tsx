import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { initUpdates } from './store/update'
import App from './App'
import './theme.css'

initUpdates()
createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)
