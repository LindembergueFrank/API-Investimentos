import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { AssetForm } from '../assets/AssetForm'
import type { AssetPage } from '../assets/assetApi'
import { OperationForm } from '../transactions/OperationForm'
import { PortfolioForm } from '../portfolios/PortfolioForm'
import { loadDashboard, loadPortfolioDetails, loadTransactionPage, type Asset, type Portfolio, type Position, type Transaction, type TransactionPage } from './dashboardApi'

type DashboardData = { portfolios: Portfolio[]; totalPortfolios: number; selectedPortfolio: Portfolio | null; positions: Position[]; transactions: Transaction[]; transactionPage: TransactionPage | null; assets: Map<string, Asset>; assetPage: AssetPage }
const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const decimal = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 8 })
const date = new Intl.DateTimeFormat('pt-BR')

export function DashboardPage() {
  const { session, getJson } = useAuth()
  const [data, setData] = useState<DashboardData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [portfolioError, setPortfolioError] = useState<string | null>(null)
  const [isSwitchingPortfolio, setIsSwitchingPortfolio] = useState(false)
  const [showOperationForm, setShowOperationForm] = useState(false)
  const [showPortfolioForm, setShowPortfolioForm] = useState(false)
  const [showAssetForm, setShowAssetForm] = useState(false)
  const [isLoadingHistory, setIsLoadingHistory] = useState(false)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const portfolioRequest = useRef<AbortController | null>(null)

  useEffect(() => {
    if (!session) return
    const controller = new AbortController()
    setError(null)
    loadDashboard(getJson, controller.signal)
      .then(({ portfolios, positions, transactions, transactionPage, assets, selectedPortfolio }) => setData({
        portfolios: portfolios.items, totalPortfolios: portfolios.totalElements, selectedPortfolio, positions, transactions, transactionPage,
        assets: new Map(assets.items.map((asset) => [asset.id, asset])),
        assetPage: { items: assets.items, page: assets.page ?? 0, size: assets.size ?? 20, totalElements: assets.totalElements, totalPages: assets.totalPages ?? Math.ceil(assets.totalElements / 20) },
      }))
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Não foi possível carregar o painel.')
      })
    return () => {
      controller.abort()
      portfolioRequest.current?.abort()
    }
  }, [session, getJson])

  async function selectPortfolio(portfolioId: string) {
    if (!session || !data || portfolioId === data.selectedPortfolio?.id) return
    const selectedPortfolio = data.portfolios.find((portfolio) => portfolio.id === portfolioId)
    if (!selectedPortfolio) return

    portfolioRequest.current?.abort()
    const controller = new AbortController()
    portfolioRequest.current = controller
    setIsSwitchingPortfolio(true)
    setPortfolioError(null)
    try {
      const details = await loadPortfolioDetails(portfolioId, getJson, controller.signal)
      setData((current) => current ? { ...current, ...details, selectedPortfolio } : current)
    } catch (cause: unknown) {
      if (!controller.signal.aborted) setPortfolioError(cause instanceof Error ? cause.message : 'Não foi possível carregar a carteira selecionada.')
    } finally {
      if (portfolioRequest.current === controller) setIsSwitchingPortfolio(false)
    }
  }

  async function refreshSelectedPortfolio(asset?: Asset) {
    if (!session || !data?.selectedPortfolio) return
    const details = await loadPortfolioDetails(data.selectedPortfolio.id, getJson)
    setData((current) => {
      if (!current) return current
      const assets = new Map(current.assets)
      if (asset) assets.set(asset.id, asset)
      return { ...current, ...details, assets }
    })
    setShowOperationForm(false)
  }

  function portfolioCreated(portfolio: Portfolio) {
    setData((current) => current ? {
      ...current,
      portfolios: [...current.portfolios, portfolio],
      totalPortfolios: current.totalPortfolios + 1,
      selectedPortfolio: portfolio,
      positions: [],
      transactions: [],
      transactionPage: null,
    } : current)
    setShowPortfolioForm(false)
  }

  function assetCreated(asset: Asset) {
    setData((current) => {
      if (!current) return current
      const assets = new Map(current.assets)
      assets.set(asset.id, asset)
      return { ...current, assets }
    })
    setShowAssetForm(false)
  }

  async function selectHistoryPage(page: number) {
    if (!data?.selectedPortfolio || !data.transactionPage || page < 0 || page >= data.transactionPage.totalPages) return
    setIsLoadingHistory(true)
    setHistoryError(null)
    try {
      const transactionPage = await loadTransactionPage(data.selectedPortfolio.id, page, data.transactionPage.size, getJson)
      setData((current) => current ? { ...current, transactions: transactionPage.content, transactionPage } : current)
    } catch (cause: unknown) {
      setHistoryError(cause instanceof Error ? cause.message : 'Não foi possível carregar o histórico.')
    } finally {
      setIsLoadingHistory(false)
    }
  }

  if (error) return <div className="dashboard"><section className="panel status-panel" role="alert"><h1>Não foi possível carregar o painel</h1><p>{error}</p></section></div>
  if (!data) return <div className="dashboard"><section className="panel status-panel" aria-live="polite"><h1>Carregando seu painel…</h1><p>Consultando carteiras e posições com segurança.</p></section></div>

  return (
    <div className="dashboard">
      <section className="hero-row">
        <div><span className="eyebrow">VISÃO GERAL</span><h1>Seu patrimônio, com clareza.</h1><p>Posições calculadas diretamente do histórico de compras e vendas.</p></div>
        <div className="hero-actions">{session?.roles.includes('ADMIN') && <button className="secondary-button" type="button" onClick={() => setShowAssetForm(true)}>+ Novo ativo</button>}<button className="secondary-button" type="button" onClick={() => setShowPortfolioForm(true)}>+ Nova carteira</button>{data.selectedPortfolio && data.assets.size > 0 && <button className="primary-button" type="button" onClick={() => setShowOperationForm(true)}>+ Nova operação</button>}{data.portfolios.length > 1 && <label className="portfolio-selector">Carteira
          <select value={data.selectedPortfolio?.id ?? ''} disabled={isSwitchingPortfolio} onChange={(event) => void selectPortfolio(event.target.value)}>
            {data.portfolios.map((portfolio) => <option key={portfolio.id} value={portfolio.id}>{portfolio.name}</option>)}
          </select>
        </label>}</div>
      </section>
      {portfolioError && <div className="inline-error" role="alert">{portfolioError}</div>}
      {showPortfolioForm && session && <PortfolioForm accessToken={session.accessToken} onCancel={() => setShowPortfolioForm(false)} onCreated={portfolioCreated} />}
      {showAssetForm && session?.roles.includes('ADMIN') && <AssetForm accessToken={session.accessToken} onCancel={() => setShowAssetForm(false)} onCreated={assetCreated} />}
      {showOperationForm && session && data.selectedPortfolio && <OperationForm accessToken={session.accessToken} portfolio={data.selectedPortfolio} assets={[...data.assets.values()]} initialAssetPage={data.assetPage} getJson={getJson} onCancel={() => setShowOperationForm(false)} onCreated={refreshSelectedPortfolio} />}
      <section className="summary-grid" aria-label="Resumo da carteira">
        <article className="summary-card"><span>Carteira selecionada</span><strong>{data.selectedPortfolio?.name ?? '—'}</strong><small>{isSwitchingPortfolio ? 'Atualizando posições…' : 'Dados reais da carteira'}</small></article>
        <article className="summary-card"><span>Posições abertas</span><strong>{data.positions.length}</strong><small>{data.selectedPortfolio?.name ?? 'Nenhuma carteira'}</small></article>
        <article className="summary-card"><span>Carteiras</span><strong>{data.totalPortfolios}</strong><small>Vinculadas à sua conta</small></article>
      </section>
      <section className="content-grid">
        <article className="panel positions-panel">
          <div className="panel-heading"><div><span className="eyebrow">CARTEIRA</span><h2>Posições</h2></div></div>
          {data.positions.length === 0 ? (
            <div className="empty-state"><span className="empty-icon" aria-hidden="true">↗</span>
              <h3>{data.portfolios.length === 0 ? 'Crie sua primeira carteira' : 'Comece pela primeira operação'}</h3>
              <p>{data.portfolios.length === 0 ? 'Uma carteira organiza suas operações e posições.' : 'Registre uma compra para visualizar quantidade, custo e preço médio.'}</p>
              {data.portfolios.length === 0 && !showPortfolioForm && <button className="secondary-button" type="button" onClick={() => setShowPortfolioForm(true)}>Criar carteira</button>}
            </div>
          ) : (
            <div className="position-list" aria-label="Posições abertas">
              {data.positions.map((position) => <article className="position-row" key={position.assetId}>
                <div><strong>{data.assets.get(position.assetId)?.ticker ?? `Ativo ${position.assetId.slice(0, 8)}`}</strong><small>{data.assets.get(position.assetId)?.name ? `${data.assets.get(position.assetId)?.name} · ` : ''}{decimal.format(position.quantity)} unidades</small></div>
                <div><span>Preço médio</span><strong>{money.format(position.averagePrice)}</strong></div>
                <div><span>Custo</span><strong>{money.format(position.totalCost)}</strong></div>
              </article>)}
            </div>
          )}
        </article>
        <aside className="panel activity-panel">
          <div className="panel-heading"><div><span className="eyebrow">HISTÓRICO</span><h2>Todas as operações</h2></div></div>
          <div className="activity-line" />
          {historyError && <p className="inline-error" role="alert">{historyError}</p>}
          {data.transactions.length === 0 ? <p className="muted">Nenhuma operação registrada nesta carteira.</p> : <ol className="activity-list">
            {data.transactions.map((transaction) => <li key={transaction.id}>
              <span className={`transaction-kind ${transaction.type.toLowerCase()}`}>{transaction.type === 'BUY' ? 'Compra' : 'Venda'}</span>
              <div><strong>{data.assets.get(transaction.assetId)?.ticker ?? `Ativo ${transaction.assetId.slice(0, 8)}`}</strong><small>{decimal.format(transaction.quantity)} unidades · {money.format(transaction.unitPrice)}</small></div>
              <time dateTime={transaction.occurredAt}>{date.format(new Date(transaction.occurredAt))}</time>
            </li>)}
          </ol>}
          {data.transactionPage && data.transactionPage.totalPages > 1 && <nav className="history-pagination" aria-label="Paginação do histórico">
            <button type="button" disabled={isLoadingHistory || data.transactionPage.page === 0} onClick={() => void selectHistoryPage(data.transactionPage!.page - 1)}>Anterior</button>
            <span aria-live="polite">Página {data.transactionPage.page + 1} de {data.transactionPage.totalPages}</span>
            <button type="button" disabled={isLoadingHistory || data.transactionPage.page + 1 >= data.transactionPage.totalPages} onClick={() => void selectHistoryPage(data.transactionPage!.page + 1)}>Próxima</button>
          </nav>}
        </aside>
      </section>
    </div>
  )
}
