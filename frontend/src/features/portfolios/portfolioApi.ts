import { postAuthenticatedJson } from '../../shared/api/http'
import type { Portfolio } from '../dashboard/dashboardApi'

export function createPortfolio(name: string, accessToken: string) {
  return postAuthenticatedJson<Portfolio>('/v1/portfolios', { name }, accessToken)
}
