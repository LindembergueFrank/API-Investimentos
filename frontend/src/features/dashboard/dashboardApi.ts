import { getJson } from '../../shared/api/http'

export type Portfolio = { id: string; name: string }
export type Position = { assetId: string; quantity: number; averagePrice: number; totalCost: number }
export type Asset = { id: string; market: string; ticker: string; type: string; name: string }
export type Transaction = { id: string; assetId: string; type: 'BUY' | 'SELL'; quantity: number; unitPrice: number; fees: number; occurredAt: string }
type ItemPage<T> = { items: T[]; totalElements: number; totalPages?: number }
type PortfolioPage = ItemPage<Portfolio>
type AssetPage = ItemPage<Asset>
type TransactionPage = { content: Transaction[]; totalElements: number }

export async function loadPortfolioDetails(portfolioId: string, accessToken: string, signal?: AbortSignal) {
  const [positions, transactions] = await Promise.all([
    getJson<Position[]>(`/v1/portfolios/${portfolioId}/positions`, accessToken, signal),
    getJson<TransactionPage>(`/v1/portfolios/${portfolioId}/transactions?page=0&size=5`, accessToken, signal),
  ])
  return { positions, transactions: transactions.content }
}

async function loadAllItems<T>(path: string, accessToken: string, signal?: AbortSignal): Promise<ItemPage<T>> {
  const firstPage = await getJson<ItemPage<T>>(`${path}?page=0&size=100`, accessToken, signal)
  const totalPages = firstPage.totalPages ?? Math.ceil(firstPage.totalElements / 100)
  const items = [...firstPage.items]

  for (let page = 1; page < totalPages; page += 1) {
    const nextPage = await getJson<ItemPage<T>>(`${path}?page=${page}&size=100`, accessToken, signal)
    items.push(...nextPage.items)
  }

  return { ...firstPage, items }
}

export async function loadDashboard(accessToken: string, signal?: AbortSignal) {
  const [portfolios, assets] = await Promise.all([
    loadAllItems<Portfolio>('/v1/portfolios', accessToken, signal),
    loadAllItems<Asset>('/v1/assets', accessToken, signal),
  ])
  const selectedPortfolio = portfolios.items[0] ?? null
  if (!selectedPortfolio) return { portfolios, assets, positions: [], transactions: [], selectedPortfolio }

  const { positions, transactions } = await loadPortfolioDetails(selectedPortfolio.id, accessToken, signal)
  return { portfolios, assets, positions, transactions, selectedPortfolio }
}
