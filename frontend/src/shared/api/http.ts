export type ProblemDetail = {
  title?: string
  detail?: string
  status?: number
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

async function readProblem(response: Response, fallback: string) {
  const problem = await response.json().catch(() => null) as ProblemDetail | null
  return problem?.detail || problem?.title || fallback
}

export async function getJson<T>(path: string, accessToken: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    signal,
  })
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

export async function postNoContent(path: string, body: unknown): Promise<void> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error('Não foi possível encerrar a sessão no servidor.')
}
