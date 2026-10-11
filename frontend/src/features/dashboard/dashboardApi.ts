export type Portfolio = { id: string; name: string }
export type Position = { assetId: string; quantity: number; averagePrice: number; totalCost: number }
export type Asset = { id: string; market: string; ticker: string; type: string; name: string }
export type Transaction = { id: string; assetId: string; type: 'BUY' | 'SELL'; quantity: number; unitPrice: number; fees: number; occurredAt: string }
type ItemPage<T> = { items: T[]; page?: number; size?: number; totalElements: number; totalPages?: number }
export type TransactionPage = { content: Transaction[]; page: number; size: number; totalElements: number; totalPages: number }

export type AuthenticatedGet = <T>(path: string, signal?: AbortSignal) => Promise<T>

export async function loadTransactionPage(portfolioId: string, page: number, size: number, getJson: AuthenticatedGet, signal?: AbortSignal) {
  const response = await getJson<Partial<TransactionPage> & Pick<TransactionPage, 'content' | 'totalElements'>>(
    `/v1/portfolios/${portfolioId}/transactions?page=${page}&size=${size}`,
    signal,
  )
  return {
    ...response,
    page: response.page ?? page,
    size: response.size ?? size,
    totalPages: response.totalPages ?? Math.ceil(response.totalElements / size),
  }
}

export async function loadPortfolioDetails(portfolioId: string, getJson: AuthenticatedGet, signal?: AbortSignal) {
  const [positions, transactionPage] = await Promise.all([
    getJson<Position[]>(`/v1/portfolios/${portfolioId}/positions`, signal),
    loadTransactionPage(portfolioId, 0, 10, getJson, signal),
  ])
  return { positions, transactions: transactionPage.content, transactionPage }
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
    getJson<ItemPage<Asset>>('/v1/assets?query=&page=0&size=20', signal),
  ])
  const selectedPortfolio = portfolios.items[0] ?? null
  if (!selectedPortfolio) return { portfolios, assets, positions: [], transactions: [], transactionPage: null, selectedPortfolio }

  const { positions, transactions, transactionPage } = await loadPortfolioDetails(selectedPortfolio.id, getJson, signal)
  return { portfolios, assets, positions, transactions, transactionPage, selectedPortfolio }
}
