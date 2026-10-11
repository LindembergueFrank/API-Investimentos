import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useState } from 'react'
import { postAuthenticatedJson } from '../../shared/api/http'
import { AuthProvider, useAuth } from './AuthContext'

function Harness() {
  const { session, login, logout } = useAuth()
  return <div>
    <span>{session?.accessToken ?? 'signed-out'}</span>
    <button type="button" onClick={() => login('user@example.com', 'safe-password')}>login</button>
    <button type="button" onClick={logout}>logout</button>
  </div>
}

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function SafeRequestHarness() {
  const { login, getJson } = useAuth()
  const [result, setResult] = useState('idle')
  async function loadConcurrently() {
    try {
      const values = await Promise.all([
        getJson<{ value: string }>('/v1/protected/a'),
        getJson<{ value: string }>('/v1/protected/b'),
      ])
      setResult(values.map((item) => item.value).join(','))
    } catch (cause) {
      setResult(cause instanceof Error ? cause.message : 'failed')
    }
  }

  return <div>
    <span>{result}</span>
    <button type="button" onClick={() => login('user@example.com', 'safe-password')}>login-safe</button>
    <button type="button" onClick={() => void loadConcurrently()}>load-safe</button>
  </div>
}

describe('AuthProvider session lifecycle', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('rotates the in-memory session before the access token expires', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ accessToken: 'access-1', expiresIn: 31, refreshToken: 'refresh-1' }))
      .mockResolvedValueOnce(response({ accessToken: 'access-2', expiresIn: 900, refreshToken: 'refresh-2' }))
    vi.stubGlobal('fetch', fetchMock)
    render(<AuthProvider><Harness /></AuthProvider>)

    fireEvent.click(screen.getByRole('button', { name: 'login' }))
    await act(async () => { await Promise.resolve() })
    expect(screen.getByText('access-1')).toBeInTheDocument()

    await act(async () => { await vi.advanceTimersByTimeAsync(1_000) })
    expect(screen.getByText('access-2')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenLastCalledWith('/v1/auth/refresh', expect.objectContaining({
      body: JSON.stringify({ refreshToken: 'refresh-1' }),
    }))
  })

  it('clears local state before a best-effort remote revocation finishes', async () => {
    let finishRevocation!: () => void
    const revocation = new Promise<Response>((resolve) => { finishRevocation = () => resolve(new Response(null, { status: 204 })) })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ accessToken: 'access-1', expiresIn: 900, refreshToken: 'refresh-1' }))
      .mockReturnValueOnce(revocation)
    vi.stubGlobal('fetch', fetchMock)
    render(<AuthProvider><Harness /></AuthProvider>)

    fireEvent.click(screen.getByRole('button', { name: 'login' }))
    expect(await screen.findByText('access-1')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'logout' }))

    expect(screen.getByText('signed-out')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenLastCalledWith('/v1/auth/revoke', expect.objectContaining({
      body: JSON.stringify({ refreshToken: 'refresh-1' }),
    }))
    finishRevocation()
  })

  it('does not restore a session when an in-flight refresh finishes after logout', async () => {
    vi.useFakeTimers()
    let finishRefresh!: () => void
    const pendingRefresh = new Promise<Response>((resolve) => {
      finishRefresh = () => resolve(response({ accessToken: 'stale-access', expiresIn: 900, refreshToken: 'stale-refresh' }))
    })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ accessToken: 'access-1', expiresIn: 31, refreshToken: 'refresh-1' }))
      .mockReturnValueOnce(pendingRefresh)
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    render(<AuthProvider><Harness /></AuthProvider>)

    fireEvent.click(screen.getByRole('button', { name: 'login' }))
    await act(async () => { await Promise.resolve() })
    await act(async () => { await vi.advanceTimersByTimeAsync(1_000) })
    fireEvent.click(screen.getByRole('button', { name: 'logout' }))
    finishRefresh()
    await act(async () => { await Promise.resolve() })

    expect(screen.getByText('signed-out')).toBeInTheDocument()
    expect(screen.queryByText('stale-access')).not.toBeInTheDocument()
  })

  it('clears the session when refresh is rejected', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ accessToken: 'access-1', expiresIn: 31, refreshToken: 'refresh-1' }))
      .mockResolvedValueOnce(response({ detail: 'Sessão expirada.' }, 401))
    vi.stubGlobal('fetch', fetchMock)
    render(<AuthProvider><Harness /></AuthProvider>)

    fireEvent.click(screen.getByRole('button', { name: 'login' }))
    await act(async () => { await Promise.resolve() })
    expect(screen.getByText('access-1')).toBeInTheDocument()

    await act(async () => { await vi.advanceTimersByTimeAsync(1_000) })

    expect(screen.getByText('signed-out')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenLastCalledWith('/v1/auth/refresh', expect.objectContaining({
      body: JSON.stringify({ refreshToken: 'refresh-1' }),
    }))
  })

  it('does not overwrite a newer login when an older refresh finishes later', async () => {
    vi.useFakeTimers()
    let finishRefresh!: () => void
    const pendingRefresh = new Promise<Response>((resolve) => {
      finishRefresh = () => resolve(response({ accessToken: 'stale-access', expiresIn: 900, refreshToken: 'stale-refresh' }))
    })
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response({ accessToken: 'access-1', expiresIn: 31, refreshToken: 'refresh-1' }))
      .mockReturnValueOnce(pendingRefresh)
      .mockResolvedValueOnce(response({ accessToken: 'access-2', expiresIn: 900, refreshToken: 'refresh-2' }))
    vi.stubGlobal('fetch', fetchMock)
    render(<AuthProvider><Harness /></AuthProvider>)

    fireEvent.click(screen.getByRole('button', { name: 'login' }))
    await act(async () => { await Promise.resolve() })
    await act(async () => { await vi.advanceTimersByTimeAsync(1_000) })
    fireEvent.click(screen.getByRole('button', { name: 'login' }))
    await act(async () => { await Promise.resolve() })
    expect(screen.getByText('access-2')).toBeInTheDocument()

    finishRefresh()
    await act(async () => { await Promise.resolve() })

    expect(screen.getByText('access-2')).toBeInTheDocument()
    expect(screen.queryByText('stale-access')).not.toBeInTheDocument()
  })

  it('shares one refresh across concurrent safe requests and retries each once', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString()
      if (url === '/v1/auth/token') return Promise.resolve(response({ accessToken: 'access-1', expiresIn: 900, refreshToken: 'refresh-1' }))
      if (url === '/v1/auth/refresh') return Promise.resolve(response({ accessToken: 'access-2', expiresIn: 900, refreshToken: 'refresh-2' }))
      const authorization = (init?.headers as Record<string, string>)?.Authorization
      if (authorization === 'Bearer access-1') return Promise.resolve(response({ detail: 'Sessão expirada.' }, 401))
      return Promise.resolve(response({ value: url.endsWith('/a') ? 'a' : 'b' }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<AuthProvider><SafeRequestHarness /></AuthProvider>)

    fireEvent.click(screen.getByRole('button', { name: 'login-safe' }))
    await act(async () => { await Promise.resolve() })
    fireEvent.click(screen.getByRole('button', { name: 'load-safe' }))

    expect(await screen.findByText('a,b')).toBeInTheDocument()
    expect(fetchMock.mock.calls.filter(([url]) => url === '/v1/auth/refresh')).toHaveLength(1)
    expect(fetchMock.mock.calls.filter(([, init]) => (init?.headers as Record<string, string>)?.Authorization === 'Bearer access-2')).toHaveLength(2)
  })

  it('does not retry an unsafe authenticated POST after a 401', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({ detail: 'Sessão expirada.' }, 401))
    vi.stubGlobal('fetch', fetchMock)

    await expect(postAuthenticatedJson('/v1/transactions', { requestId: 'stable-id' }, 'expired-access'))
      .rejects.toThrow('Sessão expirada.')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

})
