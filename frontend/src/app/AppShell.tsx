import type { PropsWithChildren } from 'react'
import { useAuth } from '../features/auth/AuthContext'

const navigation = ['Visão geral', 'Carteiras', 'Ativos', 'Operações']

export function AppShell({ children }: PropsWithChildren) {
  const { logout } = useAuth()

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#main" aria-label="Investimentos — início">
          <span className="brand-mark" aria-hidden="true">I</span>
          <span>Investimentos</span>
        </a>
        <nav aria-label="Navegação principal">
          <ul className="nav-list">
            {navigation.map((item, index) => (
              <li key={item}>
                <a className={index === 0 ? 'nav-link active' : 'nav-link'} href={index === 0 ? '#main' : `#${item.toLowerCase()}`}>
                  <span className="nav-dot" aria-hidden="true" />
                  {item}
                </a>
              </li>
            ))}
          </ul>
        </nav>
        <div className="sidebar-footer">
          <span className="avatar" aria-hidden="true">LF</span>
          <span><strong>Minha conta</strong><button className="logout-button" type="button" onClick={logout}>Sair com segurança</button></span>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div>
            <small>PAINEL</small>
            <strong>Visão geral</strong>
          </div>
          <button className="icon-button" type="button" aria-label="Notificações">●</button>
        </header>
        <main id="main">{children}</main>
      </div>
    </div>
  )
}
