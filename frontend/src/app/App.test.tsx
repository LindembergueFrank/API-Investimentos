import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'

describe('App', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('starts with the authentication boundary', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: 'Acesse sua carteira' })).toBeInTheDocument()
    expect(screen.queryByText('Sem posições abertas')).not.toBeInTheDocument()
  })

  it('opens the dashboard after a successful login without persisting tokens', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900,
      refreshToken: 'refresh-token', refreshExpiresIn: 2_592_000,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'investidor@example.com' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'segredo-forte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() => expect(screen.getByText('Sem posições abertas')).toBeInTheDocument())
    expect(screen.getByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/v1/auth/token', expect.objectContaining({ method: 'POST' }))
    expect(localStorage).toHaveLength(0)
  })
})
