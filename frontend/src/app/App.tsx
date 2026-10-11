import { AppShell } from './AppShell'
import { DashboardPage } from '../features/dashboard/DashboardPage'
import { AuthProvider, useAuth } from '../features/auth/AuthContext'
import { LoginPage } from '../features/auth/LoginPage'

function AuthenticatedApp() {
  const { session } = useAuth()

  if (!session) {
    return <LoginPage />
  }

  return (
    <AppShell>
      <DashboardPage />
    </AppShell>
  )
}

export function App() {
  return <AuthProvider><AuthenticatedApp /></AuthProvider>
}
