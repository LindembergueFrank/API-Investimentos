import { getJson } from '../../shared/api/http'

export type Portfolio = { id: string; name: string }
export type Position = { assetId: string; quantity: number; averagePrice: number; totalCost: number }
export type Asset = { id: string; market: string; ticker: string; type: string; name: string }
export type Transaction = { id: string; assetId: string; type: 'BUY' | 'SELL'; quantity: number; unitPrice: number; fees: number; occurredAt: string }
type PortfolioPage = { items: Portfolio[]; totalElements: number }
type AssetPage = { items: Asset[]; totalElements: number }
type TransactionPage = { content: Transaction[]; totalElements: number }

export async function loadPortfolioDetails(portfolioId: string, accessToken: string, signal?: AbortSignal) {
  const [positions, transactions] = await Promise.all([
    getJson<Position[]>(`/v1/portfolios/${portfolioId}/positions`, accessToken, signal),
    getJson<TransactionPage>(`/v1/portfolios/${portfolioId}/transactions?page=0&size=5`, accessToken, signal),
  ])
  return { positions, transactions: transactions.content }
}

export async function loadDashboard(accessToken: string, signal?: AbortSignal) {
  const [portfolios, assets] = await Promise.all([
    getJson<PortfolioPage>('/v1/portfolios?page=0&size=100', accessToken, signal),
    getJson<AssetPage>('/v1/assets?page=0&size=100', accessToken, signal),
  ])
  const selectedPortfolio = portfolios.items[0] ?? null
  if (!selectedPortfolio) return { portfolios, assets, positions: [], transactions: [], selectedPortfolio }

  const { positions, transactions } = await loadPortfolioDetails(selectedPortfolio.id, accessToken, signal)
  return { portfolios, assets, positions, transactions, selectedPortfolio }
}
