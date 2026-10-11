import { postJson, postNoContent } from '../../shared/api/http'

export type Session = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  refreshToken: string
  refreshExpiresIn: number
}

export function authenticate(email: string, password: string) {
  return postJson<Session>('/v1/auth/token', { email, password })
}

export function refresh(refreshToken: string) {
  return postJson<Session>('/v1/auth/refresh', { refreshToken })
}

export function revoke(refreshToken: string) {
  return postNoContent('/v1/auth/revoke', { refreshToken })
}
