export type Portfolio = { id: string; name: string }
export type Position = { assetId: string; quantity: number; averagePrice: number; totalCost: number }
export type Asset = { id: string; market: string; ticker: string; type: string; name: string }
export type Transaction = { id: string; assetId: string; type: 'BUY' | 'SELL'; quantity: number; unitPrice: number; fees: number; occurredAt: string }
type ItemPage<T> = { items: T[]; totalElements: number; totalPages?: number }
type TransactionPage = { content: Transaction[]; totalElements: number }

export type AuthenticatedGet = <T>(path: string, signal?: AbortSignal) => Promise<T>

export async function loadPortfolioDetails(portfolioId: string, getJson: AuthenticatedGet, signal?: AbortSignal) {
  const [positions, transactions] = await Promise.all([
    getJson<Position[]>(`/v1/portfolios/${portfolioId}/positions`, signal),
    getJson<TransactionPage>(`/v1/portfolios/${portfolioId}/transactions?page=0&size=5`, signal),
  ])
  return { positions, transactions: transactions.content }
}

async function loadAllItems<T>(path: string, getJson: AuthenticatedGet, signal?: AbortSignal): Promise<ItemPage<T>> {
  const firstPage = await getJson<ItemPage<T>>(`${path}?page=0&size=100`, signal)
  const totalPages = firstPage.totalPages ?? Math.ceil(firstPage.totalElements / 100)
  const items = [...firstPage.items]

  for (let page = 1; page < totalPages; page += 1) {
    const nextPage = await getJson<ItemPage<T>>(`${path}?page=${page}&size=100`, signal)
    items.push(...nextPage.items)
  }

  return { ...firstPage, items }
}

export async function loadDashboard(getJson: AuthenticatedGet, signal?: AbortSignal) {
  const [portfolios, assets] = await Promise.all([
    loadAllItems<Portfolio>('/v1/portfolios', getJson, signal),
    loadAllItems<Asset>('/v1/assets', getJson, signal),
  ])
  const selectedPortfolio = portfolios.items[0] ?? null
  if (!selectedPortfolio) return { portfolios, assets, positions: [], transactions: [], selectedPortfolio }

  const { positions, transactions } = await loadPortfolioDetails(selectedPortfolio.id, getJson, signal)
  return { portfolios, assets, positions, transactions, selectedPortfolio }
}
