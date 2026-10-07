import { useEffect, useState, type ReactNode } from 'react'
import * as api from '../api'
import { AuthContext } from './authContext'

/** Holds who is logged in and shares it with every component inside it. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<api.User | null>(null)
  const [loading, setLoading] = useState(true)

  // On page load, ask the backend if our session cookie is still valid (e.g. after a refresh).
  useEffect(() => {
    api.fetchCurrentUser()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
  }, [])

  async function login(email: string, password: string) {
    setUser(await api.login(email, password))
  }

  async function register(email: string, password: string) {
    await api.register(email, password)
    // Log in right away so the user doesn't have to type their password twice.
    setUser(await api.login(email, password))
  }

  async function logout() {
    await api.logout()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
