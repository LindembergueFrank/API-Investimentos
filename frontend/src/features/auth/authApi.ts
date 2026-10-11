import { postJsonWithCredentials, postNoContentWithCredentials } from '../../shared/api/http'

export type Session = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  roles: string[]
}

type SessionResponse = Omit<Session, 'roles'>

function readRoles(accessToken: string) {
  try {
    const payload = accessToken.split('.')[1]
    if (!payload) return []
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const normalized = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const claims = JSON.parse(atob(normalized)) as { roles?: unknown }
    return Array.isArray(claims.roles) ? claims.roles.filter((role): role is string => typeof role === 'string') : []
  } catch {
    return []
  }
}

function withRoles(session: SessionResponse): Session {
  return { ...session, roles: readRoles(session.accessToken) }
}

export async function authenticate(email: string, password: string) {
  return withRoles(await postJsonWithCredentials<SessionResponse>('/v1/auth/browser/token', { email, password }))
}

export async function refresh() {
  return withRoles(await postJsonWithCredentials<SessionResponse>('/v1/auth/browser/refresh'))
}

export function revoke() {
  return postNoContentWithCredentials('/v1/auth/browser/revoke')
}
