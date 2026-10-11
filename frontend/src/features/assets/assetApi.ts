import { postAuthenticatedJson } from '../../shared/api/http'
import type { Asset } from '../dashboard/dashboardApi'

export type AssetType = 'STOCK' | 'ETF' | 'FII'

export function createAsset(ticker: string, type: AssetType, name: string, accessToken: string) {
  return postAuthenticatedJson<Asset>('/v1/assets', { market: 'B3', ticker, type, name }, accessToken)
}
