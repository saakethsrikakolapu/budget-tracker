import { createContext, useContext } from 'react'
import type { User } from '../api'

export type AuthContextValue = {
  /** The logged-in user, or null when logged out. */
  user: User | null
  /** True until we've asked the backend whether a session already exists. */
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

/** Read the logged-in user and auth actions from any component under <AuthProvider>. */
export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return value
}
