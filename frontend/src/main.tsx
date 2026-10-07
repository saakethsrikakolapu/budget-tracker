import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth/AuthProvider'
import { ServerWakeGate } from './components/ServerWakeGate'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* BrowserRouter: keeps the URL in sync with the page. ServerWakeGate: waits for a sleeping
        backend to wake up. AuthProvider: shares who's logged in. */}
    <BrowserRouter>
      <ServerWakeGate>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ServerWakeGate>
    </BrowserRouter>
  </StrictMode>,
)
