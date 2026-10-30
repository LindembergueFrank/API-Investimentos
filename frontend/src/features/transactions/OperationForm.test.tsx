import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AuthenticatedGet } from '../dashboard/dashboardApi'
import { OperationForm } from './OperationForm'

describe('OperationForm', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('does not send a transaction when the fictitious catalog is empty', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const emptyPage = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
    const getJson = vi.fn().mockResolvedValue(emptyPage) as AuthenticatedGet
    const onCreated = vi.fn()
    render(<OperationForm accessToken="fictitious-token" portfolio={{ id: 'demo-portfolio', name: 'Carteira fictícia' }}
      assets={[]} initialAssetPage={emptyPage} getJson={getJson} onCancel={vi.fn()} onCreated={onCreated} />)

    await waitFor(() => expect(getJson).toHaveBeenCalled())
    const submit = screen.getByRole('button', { name: 'Registrar operação' })
    expect(submit).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Quantidade'), { target: { value: '2' } })
    fireEvent.change(screen.getByLabelText('Preço unitário'), { target: { value: '25,00' } })
    fireEvent.submit(submit.closest('form')!)

    expect(await screen.findByRole('alert')).toHaveTextContent('Selecione um ativo válido.')
    expect(fetchMock).not.toHaveBeenCalled()
    expect(onCreated).not.toHaveBeenCalled()
  })
})
