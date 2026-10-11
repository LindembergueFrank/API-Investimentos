export type ProblemDetail = {
  title?: string
  detail?: string
  status?: number
}

export type RenewAccessToken = (rejectedAccessToken: string) => Promise<string | null>

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

async function readProblem(response: Response, fallback: string) {
  const problem = await response.json().catch(() => null) as ProblemDetail | null
  return problem?.detail || problem?.title || fallback
}

function authenticatedGet(path: string, accessToken: string, signal?: AbortSignal) {
  return fetch(`${apiBaseUrl}${path}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    signal,
  })
}

export async function getJson<T>(path: string, accessToken: string, signal?: AbortSignal): Promise<T> {
  const response = await authenticatedGet(path, accessToken, signal)
  if (!response.ok) throw new Error(await readProblem(response, 'Não foi possível carregar os dados.'))
  return response.json() as Promise<T>
}

export async function getJsonWithRefresh<T>(path: string, accessToken: string, renew: RenewAccessToken, signal?: AbortSignal): Promise<T> {
  let response = await authenticatedGet(path, accessToken, signal)
  if (response.status === 401) {
    const renewedAccessToken = await renew(accessToken)
    if (renewedAccessToken) response = await authenticatedGet(path, renewedAccessToken, signal)
  }
  if (!response.ok) throw new Error(await readProblem(response, 'Não foi possível carregar os dados.'))
  return response.json() as Promise<T>
}

export async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    const fallback = 'Não foi possível concluir a solicitação.'
    throw new Error(await readProblem(response, fallback))
  }

  return response.json() as Promise<T>
}

export async function postAuthenticatedJson<T>(path: string, body: unknown, accessToken: string): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
    body: JSON.stringify(body),
  })

  if (!response.ok) throw new Error(await readProblem(response, 'Não foi possível concluir a solicitação.'))
  return response.json() as Promise<T>
}

export async function postNoContent(path: string, body: unknown): Promise<void> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error('Não foi possível encerrar a sessão no servidor.')
}
