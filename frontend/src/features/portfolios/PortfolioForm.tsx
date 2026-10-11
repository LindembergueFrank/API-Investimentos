import { useState, type FormEvent } from 'react'
import type { Portfolio } from '../dashboard/dashboardApi'
import { createPortfolio } from './portfolioApi'

type Props = {
  accessToken: string
  onCancel: () => void
  onCreated: (portfolio: Portfolio) => void
}

export function PortfolioForm({ accessToken, onCancel, onCreated }: Props) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      onCreated(await createPortfolio(name.trim(), accessToken))
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível criar a carteira.')
    } finally {
      setSubmitting(false)
    }
  }

  return <section className="panel compact-form-panel" aria-labelledby="portfolio-form-title">
    <div className="panel-heading"><div><span className="eyebrow">NOVA CARTEIRA</span><h2 id="portfolio-form-title">Organize seus investimentos</h2></div></div>
    <form className="compact-form" onSubmit={(event) => void submit(event)}>
      <label>Nome da carteira<input autoFocus value={name} required maxLength={80} placeholder="Ex.: Longo prazo" onChange={(event) => { setName(event.target.value); setError(null) }} /></label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancelar</button><button className="primary-button" type="submit" disabled={submitting}>{submitting ? 'Criando…' : 'Criar carteira'}</button></div>
    </form>
  </section>
}
