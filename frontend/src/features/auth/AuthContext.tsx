import { createContext, useCallback, useContext, useEffect, useMemo, useState, type PropsWithChildren } from 'react'
import { authenticate, refresh, revoke, type Session } from './authApi'

type AuthContextValue = {
  session: Session | null
  login(email: string, password: string): Promise<void>
  logout(): Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<Session | null>(null)

  const login = useCallback(async (email: string, password: string) => {
    setSession(await authenticate(email, password))
  }, [])

  const logout = useCallback(async () => {
    const token = session?.refreshToken
    setSession(null)
    if (token) await revoke(token).catch(() => undefined)
  }, [session])

  useEffect(() => {
    if (!session) return
    const refreshInMilliseconds = Math.max((session.expiresIn - 30) * 1000, 1_000)
    const timer = window.setTimeout(() => {
      refresh(session.refreshToken).then(setSession).catch(() => setSession(null))
    }, refreshInMilliseconds)
    return () => window.clearTimeout(timer)
  }, [session])

  const value = useMemo(() => ({ session, login, logout }), [session, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
