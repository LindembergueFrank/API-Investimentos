import { AppShell } from './AppShell'
import { DashboardPage } from '../features/dashboard/DashboardPage'
import { AuthProvider, useAuth } from '../features/auth/AuthContext'
import { LoginPage } from '../features/auth/LoginPage'

function AuthenticatedApp() {
  const { session, isRestoring } = useAuth()

  if (isRestoring) {
    return <main className="login-page" aria-live="polite">Restaurando sessão…</main>
  }

  if (!session) {
    return <LoginPage />
  }

  return (
    <AppShell>
      <DashboardPage />
    </AppShell>
  )
}

export function App({ restoreSession = true }: { restoreSession?: boolean }) {
  return <AuthProvider restoreSession={restoreSession}><AuthenticatedApp /></AuthProvider>
}
