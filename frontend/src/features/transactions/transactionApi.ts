import { postAuthenticatedJson } from '../../shared/api/http'

export type TransactionType = 'BUY' | 'SELL'

export type CreateTransaction = {
  requestId: string
  portfolioId: string
  assetId: string
  type: TransactionType
  quantity: string
  unitPrice: string
  fees: string
  occurredAt: string
}

export function createTransaction(transaction: CreateTransaction, accessToken: string) {
  return postAuthenticatedJson('/v1/transactions', transaction, accessToken)
}
