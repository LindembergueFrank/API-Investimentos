import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type PropsWithChildren } from 'react'
import { getJsonWithRefresh } from '../../shared/api/http'
import { authenticate, refresh, revoke, type Session } from './authApi'

type AuthContextValue = {
  session: Session | null
  login(email: string, password: string): Promise<void>
  logout(): Promise<void>
  getJson<T>(path: string, signal?: AbortSignal): Promise<T>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<Session | null>(null)
  const sessionRef = useRef<Session | null>(null)
  const refreshFlight = useRef<Promise<string | null> | null>(null)

  const replaceSession = useCallback((nextSession: Session | null) => {
    sessionRef.current = nextSession
    setSession(nextSession)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    replaceSession(await authenticate(email, password))
  }, [replaceSession])

  const logout = useCallback(async () => {
    const token = sessionRef.current?.refreshToken
    replaceSession(null)
    if (token) await revoke(token).catch(() => undefined)
  }, [replaceSession])

  const renewAccessToken = useCallback((rejectedAccessToken: string) => {
    const current = sessionRef.current
    if (!current) return Promise.resolve(null)
    if (current.accessToken !== rejectedAccessToken) return Promise.resolve(current.accessToken)
    if (refreshFlight.current) return refreshFlight.current

    const expectedRefreshToken = current.refreshToken
    const flight = refresh(expectedRefreshToken)
      .then((nextSession) => {
        if (sessionRef.current?.refreshToken !== expectedRefreshToken) return sessionRef.current?.accessToken ?? null
        replaceSession(nextSession)
        return nextSession.accessToken
      })
      .catch(() => {
        if (sessionRef.current?.refreshToken === expectedRefreshToken) replaceSession(null)
        return null
      })
      .finally(() => {
        if (refreshFlight.current === flight) refreshFlight.current = null
      })
    refreshFlight.current = flight
    return flight
  }, [replaceSession])

  const authenticatedGet = useCallback(<T,>(path: string, signal?: AbortSignal) => {
    const current = sessionRef.current
    if (!current) return Promise.reject(new Error('Sessão não autenticada.'))
    return getJsonWithRefresh<T>(path, current.accessToken, renewAccessToken, signal)
  }, [renewAccessToken])

  useEffect(() => {
    if (!session) return
    const refreshInMilliseconds = Math.max((session.expiresIn - 30) * 1000, 1_000)
    const timer = window.setTimeout(() => { void renewAccessToken(session.accessToken) }, refreshInMilliseconds)
    return () => window.clearTimeout(timer)
  }, [session, renewAccessToken])

  const value = useMemo(() => ({ session, login, logout, getJson: authenticatedGet }), [session, login, logout, authenticatedGet])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
