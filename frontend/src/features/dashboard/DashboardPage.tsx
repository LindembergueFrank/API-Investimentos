const summaries = [
  { label: 'Patrimônio total', value: 'R$ 0,00', helper: 'Sem posições abertas' },
  { label: 'Custo investido', value: 'R$ 0,00', helper: 'Derivado das operações' },
  { label: 'Carteiras', value: '0', helper: 'Nenhuma carteira criada' },
]

export function DashboardPage() {
  return (
    <div className="dashboard">
      <section className="hero-row">
        <div>
          <span className="eyebrow">Bom dia</span>
          <h1>Seu patrimônio, com clareza.</h1>
          <p>Acompanhe posições calculadas diretamente do histórico de compras e vendas.</p>
        </div>
        <button className="primary-button" type="button">+ Nova operação</button>
      </section>

      <section className="summary-grid" aria-label="Resumo financeiro">
        {summaries.map((summary) => (
          <article className="summary-card" key={summary.label}>
            <span>{summary.label}</span>
            <strong>{summary.value}</strong>
            <small>{summary.helper}</small>
          </article>
        ))}
      </section>

      <section className="content-grid">
        <article className="panel positions-panel">
          <div className="panel-heading">
            <div><span className="eyebrow">CARTEIRA</span><h2>Posições</h2></div>
            <button className="text-button" type="button">Ver todas</button>
          </div>
          <div className="empty-state">
            <span className="empty-icon" aria-hidden="true">↗</span>
            <h3>Comece pela primeira operação</h3>
            <p>Registre uma compra para visualizar quantidade, custo e preço médio da posição.</p>
            <button className="secondary-button" type="button">Registrar compra</button>
          </div>
        </article>

        <aside className="panel activity-panel">
          <div className="panel-heading"><div><span className="eyebrow">HISTÓRICO</span><h2>Atividade recente</h2></div></div>
          <div className="activity-line" />
          <p className="muted">Suas operações aparecerão aqui em ordem cronológica.</p>
        </aside>
      </section>
    </div>
  )
}
