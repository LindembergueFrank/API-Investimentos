import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type PropsWithChildren } from 'react'
import { getJsonWithRefresh } from '../../shared/api/http'
import { authenticate, refresh, revoke, type Session } from './authApi'

type AuthContextValue = {
  session: Session | null
  isRestoring: boolean
  login(email: string, password: string): Promise<void>
  logout(): Promise<void>
  getJson<T>(path: string, signal?: AbortSignal): Promise<T>
}

const AuthContext = createContext<AuthContextValue | null>(null)

type AuthProviderProps = PropsWithChildren<{ restoreSession?: boolean }>

export function AuthProvider({ children, restoreSession = true }: AuthProviderProps) {
  const [session, setSession] = useState<Session | null>(null)
  const [isRestoring, setIsRestoring] = useState(restoreSession)
  const sessionRef = useRef<Session | null>(null)
  const refreshFlight = useRef<Promise<string | null> | null>(null)
  const restoreFlight = useRef<Promise<Session> | null>(null)
  const sessionVersion = useRef(0)

  const replaceSession = useCallback((nextSession: Session | null) => {
    sessionVersion.current += 1
    sessionRef.current = nextSession
    setSession(nextSession)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    replaceSession(await authenticate(email, password))
  }, [replaceSession])

  const logout = useCallback(async () => {
    replaceSession(null)
    await revoke().catch(() => undefined)
  }, [replaceSession])

  const renewAccessToken = useCallback((rejectedAccessToken: string) => {
    const current = sessionRef.current
    if (!current) return Promise.resolve(null)
    if (current.accessToken !== rejectedAccessToken) return Promise.resolve(current.accessToken)
    if (refreshFlight.current) return refreshFlight.current

    const expectedVersion = sessionVersion.current
    const flight = refresh()
      .then((nextSession) => {
        if (sessionVersion.current !== expectedVersion) return sessionRef.current?.accessToken ?? null
        replaceSession(nextSession)
        return nextSession.accessToken
      })
      .catch(() => {
        if (sessionVersion.current === expectedVersion) replaceSession(null)
        return null
      })
      .finally(() => {
        if (refreshFlight.current === flight) refreshFlight.current = null
      })
    refreshFlight.current = flight
    return flight
  }, [replaceSession])

  useEffect(() => {
    if (!restoreSession) return
    const expectedVersion = sessionVersion.current
    let active = true
    if (!restoreFlight.current) restoreFlight.current = refresh()
    restoreFlight.current
      .then((restoredSession) => {
        if (active && sessionVersion.current === expectedVersion) replaceSession(restoredSession)
      })
      .catch(() => undefined)
      .finally(() => { if (active) setIsRestoring(false) })
    return () => { active = false }
  }, [replaceSession, restoreSession])

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

  const value = useMemo(() => ({ session, isRestoring, login, logout, getJson: authenticatedGet }), [session, isRestoring, login, logout, authenticatedGet])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
