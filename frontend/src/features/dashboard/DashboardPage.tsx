import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { loadDashboard, type Portfolio, type Position } from './dashboardApi'

type DashboardData = { portfolios: Portfolio[]; totalPortfolios: number; selectedPortfolio: Portfolio | null; positions: Position[] }
const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const decimal = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 8 })

export function DashboardPage() {
  const { session } = useAuth()
  const [data, setData] = useState<DashboardData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!session) return
    const controller = new AbortController()
    setError(null)
    loadDashboard(session.accessToken, controller.signal)
      .then(({ portfolios, positions, selectedPortfolio }) => setData({
        portfolios: portfolios.items, totalPortfolios: portfolios.totalElements, selectedPortfolio, positions,
      }))
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Não foi possível carregar o painel.')
      })
    return () => controller.abort()
  }, [session])

  if (error) return <div className="dashboard"><section className="panel status-panel" role="alert"><h1>Não foi possível carregar o painel</h1><p>{error}</p></section></div>
  if (!data) return <div className="dashboard"><section className="panel status-panel" aria-live="polite"><h1>Carregando seu painel…</h1><p>Consultando carteiras e posições com segurança.</p></section></div>

  return (
    <div className="dashboard">
      <section className="hero-row">
        <div><span className="eyebrow">VISÃO GERAL</span><h1>Seu patrimônio, com clareza.</h1><p>Posições calculadas diretamente do histórico de compras e vendas.</p></div>
        <button className="primary-button" type="button">+ Nova operação</button>
      </section>
      <section className="summary-grid" aria-label="Resumo da carteira">
        <article className="summary-card"><span>Carteira selecionada</span><strong>{data.selectedPortfolio?.name ?? '—'}</strong><small>Primeira carteira disponível</small></article>
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
            </div>
          ) : (
            <div className="position-list" aria-label="Posições abertas">
              {data.positions.map((position) => <article className="position-row" key={position.assetId}>
                <div><strong>Ativo {position.assetId.slice(0, 8)}</strong><small>{decimal.format(position.quantity)} unidades</small></div>
                <div><span>Preço médio</span><strong>{money.format(position.averagePrice)}</strong></div>
                <div><span>Custo</span><strong>{money.format(position.totalCost)}</strong></div>
              </article>)}
            </div>
          )}
        </article>
        <aside className="panel activity-panel"><div className="panel-heading"><div><span className="eyebrow">DADOS REAIS</span><h2>Limites atuais</h2></div></div><div className="activity-line" /><p className="muted">Rentabilidade e valor de mercado aparecerão somente após a integração de cotações. O painel mostra agora o custo contábil confirmado pela API.</p></aside>
      </section>
    </div>
  )
}
