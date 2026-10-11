import { useState, type FormEvent } from 'react'
import { useAuth } from './AuthContext'

export function LoginPage() {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await login(email.trim(), password)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível entrar.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <div className="login-brand"><span className="brand-mark" aria-hidden="true">I</span><strong>Investimentos</strong></div>
        <span className="eyebrow">ÁREA SEGURA</span>
        <h1 id="login-title">Acesse sua carteira</h1>
        <p>Entre para acompanhar posições calculadas a partir das suas operações.</p>
        <form onSubmit={submit}>
          <label htmlFor="email">E-mail</label>
          <input id="email" name="email" type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} />
          <label htmlFor="password">Senha</label>
          <input id="password" name="password" type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} />
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button login-button" type="submit" disabled={submitting}>{submitting ? 'Entrando…' : 'Entrar'}</button>
        </form>
        <small className="security-note">Sua sessão permanece somente nesta aba e não é salva no navegador.</small>
      </section>
    </main>
  )
}
