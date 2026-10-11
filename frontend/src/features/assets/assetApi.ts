import { postAuthenticatedJson } from '../../shared/api/http'
import type { Asset, AuthenticatedGet } from '../dashboard/dashboardApi'

export type AssetType = 'STOCK' | 'ETF' | 'FII'
export type AssetPage = { items: Asset[]; page: number; size: number; totalElements: number; totalPages: number }

export function searchAssets(query: string, page: number, getJson: AuthenticatedGet, signal?: AbortSignal) {
  const encodedQuery = encodeURIComponent(query.trim())
  return getJson<AssetPage>(`/v1/assets?query=${encodedQuery}&page=${page}&size=20`, signal)
}

export function createAsset(ticker: string, type: AssetType, name: string, accessToken: string) {
  return postAuthenticatedJson<Asset>('/v1/assets', { market: 'B3', ticker, type, name }, accessToken)
}
