import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'

describe('App', () => {
  it('renders an honest empty portfolio state', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: 'Seu patrimônio, com clareza.' })).toBeInTheDocument()
    expect(screen.getByText('Sem posições abertas')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Registrar compra' })).toBeInTheDocument()
  })

  it('provides the primary navigation landmark', () => {
    render(<App />)

    expect(screen.getByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument()
  })
})
