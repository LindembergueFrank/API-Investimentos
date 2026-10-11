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
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/v1/auth/token') return Promise.resolve(new Response(JSON.stringify({
        accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900,
        refreshToken: 'refresh-token', refreshExpiresIn: 2_592_000,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      if (url.startsWith('/v1/portfolios?')) return Promise.resolve(new Response(JSON.stringify({
        items: [{ id: 'portfolio-1', name: 'Longo prazo' }], page: 0, size: 100, totalElements: 1, totalPages: 1,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      if (url.startsWith('/v1/assets?')) return Promise.resolve(new Response(JSON.stringify({
        items: [{ id: 'asset-12345678', ticker: 'ACME3', name: 'Acme S.A.', market: 'B3', type: 'STOCK' }], totalElements: 1,
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      if (url.includes('/positions')) return Promise.resolve(new Response(JSON.stringify([{
        assetId: 'asset-12345678', quantity: 2, averagePrice: 25, totalCost: 50,
      }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      return Promise.resolve(new Response(JSON.stringify({ content: [{
        id: 'transaction-1', assetId: 'asset-12345678', type: 'BUY', quantity: 2, unitPrice: 25, fees: 0, occurredAt: '2026-10-09T12:00:00Z',
      }], totalElements: 1 }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'investidor@example.com' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'segredo-forte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() => expect(screen.getByText('R$ 25,00')).toBeInTheDocument())
    expect(screen.getAllByText('ACME3')).toHaveLength(2)
    expect(screen.getByText('Compra')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/v1/auth/token', expect.objectContaining({ method: 'POST' }))
    expect(fetchMock).toHaveBeenCalledWith('/v1/portfolios?page=0&size=100', expect.objectContaining({
      headers: { Authorization: 'Bearer access-token' },
    }))
    expect(localStorage).toHaveLength(0)
  })

  it('shows an honest empty state when the user has no portfolios', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/v1/auth/token') return Promise.resolve(new Response(JSON.stringify({ accessToken: 'access-token', expiresIn: 900, refreshToken: 'refresh-token' }), { status: 200 }))
      return Promise.resolve(new Response(JSON.stringify({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }), { status: 200 }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'investidor@example.com' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'segredo-forte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    await waitFor(() => expect(screen.getByText('Crie sua primeira carteira')).toBeInTheDocument())
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('creates the first portfolio with authentication and updates the empty dashboard', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString()
      if (url === '/v1/auth/token') return Promise.resolve(new Response(JSON.stringify({ accessToken: 'access-token', expiresIn: 900, refreshToken: 'refresh-token' }), { status: 200 }))
      if (url === '/v1/portfolios' && init?.method === 'POST') return Promise.resolve(new Response(JSON.stringify({ id: 'portfolio-1', name: 'Reserva segura' }), { status: 201 }))
      return Promise.resolve(new Response(JSON.stringify({ items: [], totalElements: 0 }), { status: 200 }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'investidor@example.com' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'segredo-forte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Criar carteira' }))
    fireEvent.change(screen.getByLabelText('Nome da carteira'), { target: { value: '  Reserva segura  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Criar carteira' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/v1/portfolios', expect.objectContaining({
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer access-token' },
      body: JSON.stringify({ name: 'Reserva segura' }),
    })))
    expect((await screen.findAllByText('Reserva segura')).length).toBeGreaterThan(0)
    expect(screen.getByText('1')).toBeInTheDocument()
  })

  it('switches between owned portfolios and reloads their details', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/v1/auth/token') return Promise.resolve(new Response(JSON.stringify({ accessToken: 'access-token', expiresIn: 900, refreshToken: 'refresh-token' }), { status: 200 }))
      if (url.startsWith('/v1/portfolios?')) return Promise.resolve(new Response(JSON.stringify({
        items: [{ id: 'portfolio-1', name: 'Longo prazo' }, { id: 'portfolio-2', name: 'Reserva' }], totalElements: 2,
      }), { status: 200 }))
      if (url.startsWith('/v1/assets?')) return Promise.resolve(new Response(JSON.stringify({ items: [], totalElements: 0 }), { status: 200 }))
      if (url.includes('/positions')) return Promise.resolve(new Response(JSON.stringify(url.includes('portfolio-2') ? [{
        assetId: 'asset-2', quantity: 5, averagePrice: 10, totalCost: 50,
      }] : []), { status: 200 }))
      return Promise.resolve(new Response(JSON.stringify({ content: [], totalElements: 0 }), { status: 200 }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'investidor@example.com' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'segredo-forte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    const selector = await screen.findByLabelText('Carteira')
    fireEvent.change(selector, { target: { value: 'portfolio-2' } })

    await waitFor(() => expect(screen.getByText('Reserva')).toBeInTheDocument())
    expect(screen.getByText('5 unidades')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/v1/portfolios/portfolio-2/positions', expect.objectContaining({
      headers: { Authorization: 'Bearer access-token' },
    }))
  })

  it('registers an authenticated and idempotent operation without converting decimals', async () => {
    let createAttempts = 0
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString()
      if (url === '/v1/auth/token') return Promise.resolve(new Response(JSON.stringify({ accessToken: 'access-token', expiresIn: 900, refreshToken: 'refresh-token' }), { status: 200 }))
      if (url === '/v1/transactions' && init?.method === 'POST') {
        createAttempts += 1
        return Promise.resolve(createAttempts === 1
          ? new Response(JSON.stringify({ detail: 'Falha temporária.' }), { status: 503 })
          : new Response(JSON.stringify({ id: 'transaction-1' }), { status: 201 }))
      }
      if (url.startsWith('/v1/portfolios?')) return Promise.resolve(new Response(JSON.stringify({ items: [{ id: 'portfolio-1', name: 'Longo prazo' }], totalElements: 1 }), { status: 200 }))
      if (url.startsWith('/v1/assets?')) return Promise.resolve(new Response(JSON.stringify({ items: [{ id: 'asset-1', ticker: 'ACME3', name: 'Acme S.A.', market: 'B3', type: 'STOCK' }], totalElements: 1 }), { status: 200 }))
      if (url.includes('/positions')) return Promise.resolve(new Response(JSON.stringify([]), { status: 200 }))
      return Promise.resolve(new Response(JSON.stringify({ content: [], totalElements: 0 }), { status: 200 }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'investidor@example.com' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'segredo-forte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    fireEvent.click(await screen.findByRole('button', { name: '+ Nova operação' }))
    fireEvent.change(screen.getByLabelText('Quantidade'), { target: { value: '1,12345678' } })
    fireEvent.change(screen.getByLabelText('Preço unitário'), { target: { value: '25,99' } })
    fireEvent.change(screen.getByLabelText('Taxas'), { target: { value: '0,10' } })
    fireEvent.click(screen.getByRole('button', { name: 'Registrar operação' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Falha temporária.')
    fireEvent.click(screen.getByRole('button', { name: 'Registrar operação' }))
    await waitFor(() => expect(createAttempts).toBe(2))
    expect(fetchMock).toHaveBeenCalledWith('/v1/transactions', expect.objectContaining({
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer access-token' },
    }))
    const transactionCalls = fetchMock.mock.calls.filter(([url]) => url === '/v1/transactions')
    const body = JSON.parse(transactionCalls[0][1]?.body as string)
    const retryBody = JSON.parse(transactionCalls[1][1]?.body as string)
    expect(body).toMatchObject({ portfolioId: 'portfolio-1', assetId: 'asset-1', type: 'BUY', quantity: '1.12345678', unitPrice: '25.99', fees: '0.10' })
    expect(body.requestId).toMatch(/^[0-9a-f-]{36}$/)
    expect(retryBody.requestId).toBe(body.requestId)
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Registrar compra ou venda' })).not.toBeInTheDocument())
  })

  it('keeps the current portfolio visible when switching fails', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/v1/auth/token') return Promise.resolve(new Response(JSON.stringify({ accessToken: 'access-token', expiresIn: 900, refreshToken: 'refresh-token' }), { status: 200 }))
      if (url.startsWith('/v1/portfolios?')) return Promise.resolve(new Response(JSON.stringify({
        items: [{ id: 'portfolio-1', name: 'Longo prazo' }, { id: 'portfolio-2', name: 'Reserva' }], totalElements: 2,
      }), { status: 200 }))
      if (url.startsWith('/v1/assets?')) return Promise.resolve(new Response(JSON.stringify({ items: [], totalElements: 0 }), { status: 200 }))
      if (url.includes('portfolio-2')) return Promise.resolve(new Response(JSON.stringify({ detail: 'Carteira indisponível.' }), { status: 503 }))
      if (url.includes('/positions')) return Promise.resolve(new Response(JSON.stringify([]), { status: 200 }))
      return Promise.resolve(new Response(JSON.stringify({ content: [], totalElements: 0 }), { status: 200 }))
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'investidor@example.com' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'segredo-forte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    fireEvent.change(await screen.findByLabelText('Carteira'), { target: { value: 'portfolio-2' } })

    expect(await screen.findByRole('alert')).toHaveTextContent('Carteira indisponível.')
    expect(screen.getAllByText('Longo prazo').length).toBeGreaterThan(0)
  })

  it('shows the API problem when dashboard loading fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/v1/auth/token') return Promise.resolve(new Response(JSON.stringify({ accessToken: 'access-token', expiresIn: 900, refreshToken: 'refresh-token' }), { status: 200 }))
      if (url.startsWith('/v1/assets?')) return Promise.resolve(new Response(JSON.stringify({ items: [], totalElements: 0 }), { status: 200 }))
      return Promise.resolve(new Response(JSON.stringify({ title: 'Acesso negado', detail: 'A sessão não permite consultar esta carteira.' }), { status: 403 }))
    }))
    render(<App />)

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'investidor@example.com' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'segredo-forte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('A sessão não permite consultar esta carteira.')
  })
})
