import { getJson } from '../../shared/api/http'

export type Portfolio = { id: string; name: string }
export type Position = { assetId: string; quantity: number; averagePrice: number; totalCost: number }
type PortfolioPage = { items: Portfolio[]; totalElements: number }

export async function loadDashboard(accessToken: string, signal?: AbortSignal) {
  const portfolios = await getJson<PortfolioPage>('/v1/portfolios?page=0&size=100', accessToken, signal)
  const positions = portfolios.items.length === 0
    ? []
    : await getJson<Position[]>(`/v1/portfolios/${portfolios.items[0].id}/positions`, accessToken, signal)
  return { portfolios, positions, selectedPortfolio: portfolios.items[0] ?? null }
}
