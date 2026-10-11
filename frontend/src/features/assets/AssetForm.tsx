import { useState, type FormEvent } from 'react'
import type { Asset } from '../dashboard/dashboardApi'
import { createAsset, type AssetType } from './assetApi'

type Props = {
  accessToken: string
  onCancel: () => void
  onCreated: (asset: Asset) => void
}

export function AssetForm({ accessToken, onCancel, onCreated }: Props) {
  const [ticker, setTicker] = useState('')
  const [type, setType] = useState<AssetType>('STOCK')
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      onCreated(await createAsset(ticker.trim().toUpperCase(), type, name.trim(), accessToken))
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível cadastrar o ativo.')
    } finally {
      setSubmitting(false)
    }
  }

  return <section className="panel compact-form-panel" aria-labelledby="asset-form-title">
    <div className="panel-heading"><div><span className="eyebrow">ADMINISTRAÇÃO</span><h2 id="asset-form-title">Cadastrar ativo</h2></div></div>
    <form className="asset-form" onSubmit={(event) => void submit(event)}>
      <label>Mercado<input value="B3" disabled aria-describedby="market-description" /></label>
      <label>Ticker<input autoFocus value={ticker} required minLength={4} maxLength={12} pattern="[A-Za-z0-9]{4,12}" placeholder="Ex.: PETR4" onChange={(event) => { setTicker(event.target.value); setError(null) }} /></label>
      <label>Tipo<select value={type} onChange={(event) => setType(event.target.value as AssetType)}><option value="STOCK">Ação</option><option value="ETF">ETF</option><option value="FII">Fundo imobiliário</option></select></label>
      <label className="asset-name-field">Nome<input value={name} required maxLength={120} placeholder="Ex.: Petrobras PN" onChange={(event) => { setName(event.target.value); setError(null) }} /></label>
      <small id="market-description" className="form-hint">O catálogo atual aceita ativos negociados na B3.</small>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="form-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancelar</button><button className="primary-button" type="submit" disabled={submitting}>{submitting ? 'Cadastrando…' : 'Cadastrar ativo'}</button></div>
    </form>
  </section>
}
