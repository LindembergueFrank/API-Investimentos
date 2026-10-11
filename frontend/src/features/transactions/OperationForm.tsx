import { useEffect, useState, type FormEvent } from 'react'
import { searchAssets, type AssetPage } from '../assets/assetApi'
import type { Asset, AuthenticatedGet, Portfolio } from '../dashboard/dashboardApi'
import { createTransaction, type TransactionType } from './transactionApi'

type Props = {
  accessToken: string
  portfolio: Portfolio
  assets: Asset[]
  initialAssetPage: AssetPage
  getJson: AuthenticatedGet
  onCancel: () => void
  onCreated: (asset: Asset) => Promise<void>
}

const localNow = () => {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

export function OperationForm({ accessToken, portfolio, assets, initialAssetPage, getJson, onCancel, onCreated }: Props) {
  const [requestId, setRequestId] = useState(() => crypto.randomUUID())
  const [type, setType] = useState<TransactionType>('BUY')
  const [assetId, setAssetId] = useState(assets[0]?.id ?? '')
  const [assetQuery, setAssetQuery] = useState('')
  const [assetPage, setAssetPage] = useState<AssetPage>(initialAssetPage)
  const [loadingAssets, setLoadingAssets] = useState(false)
  const [assetError, setAssetError] = useState<string | null>(null)
  const [quantity, setQuantity] = useState('')
  const [unitPrice, setUnitPrice] = useState('')
  const [fees, setFees] = useState('0')
  const [occurredAt, setOccurredAt] = useState(localNow)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setLoadingAssets(true)
      setAssetError(null)
      searchAssets(assetQuery, assetPage.page, getJson, controller.signal)
        .then((result) => {
          setAssetPage(result)
          const nextAssetId = result.items[0]?.id ?? ''
          if (!result.items.some((asset) => asset.id === assetId)) {
            setAssetId(nextAssetId)
            setRequestId(crypto.randomUUID())
          }
        })
        .catch((cause: unknown) => {
          if (!controller.signal.aborted) setAssetError(cause instanceof Error ? cause.message : 'Não foi possível buscar os ativos.')
        })
        .finally(() => { if (!controller.signal.aborted) setLoadingAssets(false) })
    }, assetQuery ? 300 : 0)
    return () => { window.clearTimeout(timer); controller.abort() }
  }, [assetQuery, assetPage.page, getJson])

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
      const selectedAsset = assetPage.items.find((asset) => asset.id === assetId)
      if (!selectedAsset) throw new Error('Selecione um ativo válido.')
      await onCreated(selectedAsset)
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
      <label>Buscar ativo<input value={assetQuery} maxLength={80} placeholder="Ticker ou nome" onChange={(event) => { setAssetQuery(event.target.value); setAssetPage((current) => ({ ...current, page: 0 })) }} /></label>
      <label>Ativo<select value={assetId} required disabled={loadingAssets || assetPage.items.length === 0} onChange={(event) => changed(() => setAssetId(event.target.value))}>{assetPage.items.map((asset) => <option key={asset.id} value={asset.id}>{asset.ticker} — {asset.name}</option>)}</select></label>
      <label>Quantidade<input value={quantity} required inputMode="decimal" pattern="[0-9]+([.,][0-9]{1,8})?" placeholder="0,00000000" onChange={(event) => changed(() => setQuantity(event.target.value.replace(',', '.')))} /></label>
      <label>Preço unitário<input value={unitPrice} required inputMode="decimal" pattern="[0-9]+([.,][0-9]{1,8})?" placeholder="0,00" onChange={(event) => changed(() => setUnitPrice(event.target.value.replace(',', '.')))} /></label>
      <label>Taxas<input value={fees} required inputMode="decimal" pattern="[0-9]+([.,][0-9]{1,2})?" onChange={(event) => changed(() => setFees(event.target.value.replace(',', '.')))} /></label>
      <label>Data e hora<input type="datetime-local" value={occurredAt} max={localNow()} required onChange={(event) => changed(() => setOccurredAt(event.target.value))} /></label>
      {assetPage.totalPages > 1 && <nav className="asset-pagination" aria-label="Paginação dos ativos"><button type="button" disabled={loadingAssets || assetPage.page === 0} onClick={() => setAssetPage((current) => ({ ...current, page: current.page - 1 }))}>Ativos anteriores</button><span aria-live="polite">Página {assetPage.page + 1} de {assetPage.totalPages}</span><button type="button" disabled={loadingAssets || assetPage.page + 1 >= assetPage.totalPages} onClick={() => setAssetPage((current) => ({ ...current, page: current.page + 1 }))}>Próximos ativos</button></nav>}
      {assetError && <p className="form-error operation-error" role="alert">{assetError}</p>}
      {error && <p className="form-error operation-error" role="alert">{error}</p>}
      <div className="form-actions"><button className="secondary-button" type="button" onClick={onCancel}>Cancelar</button><button className="primary-button" type="submit" disabled={submitting}>{submitting ? 'Registrando…' : 'Registrar operação'}</button></div>
    </form>
  </section>
}
