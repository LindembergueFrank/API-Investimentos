import { useState, type FormEvent } from 'react'
import type { Asset, Portfolio } from '../dashboard/dashboardApi'
import { createTransaction, type TransactionType } from './transactionApi'

type Props = {
  accessToken: string
  portfolio: Portfolio
  assets: Asset[]
  onCancel: () => void
  onCreated: () => Promise<void>
}

const localNow = () => {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

export function OperationForm({ accessToken, portfolio, assets, onCancel, onCreated }: Props) {
  const [requestId, setRequestId] = useState(() => crypto.randomUUID())
  const [type, setType] = useState<TransactionType>('BUY')
  const [assetId, setAssetId] = useState(assets[0]?.id ?? '')
  const [quantity, setQuantity] = useState('')
  const [unitPrice, setUnitPrice] = useState('')
  const [fees, setFees] = useState('0')
  const [occurredAt, setOccurredAt] = useState(localNow)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function changed(update: () => void) {
    update()
    setRequestId(crypto.randomUUID())
    setError(null)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await createTransaction({
        requestId, portfolioId: portfolio.id, assetId, type, quantity, unitPrice, fees,
        occurredAt: new Date(occurredAt).toISOString(),
      }, accessToken)
      await onCreated()
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : 'Não foi possível registrar a operação.')
    } finally {
      setSubmitting(false)
    }
  }

  return <section className="panel operation-panel" aria-labelledby="operation-title">
    <div className="panel-heading"><div><span className="eyebrow">NOVA OPERAÇÃO</span><h2 id="operation-title">Registrar compra ou venda</h2></div></div>
    <p className="muted">Carteira: <strong>{portfolio.name}</strong>. Os valores serão enviados sem conversão por ponto flutuante.</p>
    <form className="operation-form" onSubmit={(event) => void submit(event)}>
      <label>Tipo<select value={type} onChange={(event) => changed(() => setType(event.target.value as TransactionType))}><option value="BUY">Compra</option><option value="SELL">Venda</option></select></label>
      <label>Ativo<select value={assetId} required onChange={(event) => changed(() => setAssetId(event.target.value))}>{assets.map((asset) => <option key={asset.id} value={asset.id}>{asset.ticker} — {asset.name}</option>)}</select></label>
      <label>Quantidade<input value={quantity} required inputMode="decimal" pattern="[0-9]+([.,][0-9]{1,8})?" placeholder="0,00000000" onChange={(event) => changed(() => setQuantity(event.target.value.replace(',', '.')))} /></label>
      <label>Preço unitário<input value={unitPrice} required inputMode="decimal" pattern="[0-9]+([.,][0-9]{1,8})?" placeholder="0,00" onChange={(event) => changed(() => setUnitPrice(event.target.value.replace(',', '.')))} /></label>
      <label>Taxas<input value={fees} required inputMode="decimal" pattern="[0-9]+([.,][0-9]{1,2})?" onChange={(event) => changed(() => setFees(event.target.value.replace(',', '.')))} /></label>
      <label>Data e hora<input type="datetime-local" value={occurredAt} max={localNow()} required onChange={(event) => changed(() => setOccurredAt(event.target.value))} /></label>
      {error && <p className="form-error operation-error" role="alert">{error}</p>}
      <div className="form-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancelar</button><button className="primary-button" type="submit" disabled={submitting}>{submitting ? 'Registrando…' : 'Registrar operação'}</button></div>
    </form>
  </section>
}
