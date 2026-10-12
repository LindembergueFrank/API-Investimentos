# ADR 0001: manter o cálculo de posições sob demanda

- Status: aceito
- Data: 2026-10-12

## Contexto

As posições são derivadas do livro completo de operações, em ordem determinística. Snapshot ou projeção persistida reduziria o trabalho de leitura, mas acrescentaria estado derivado, reconstrução, versionamento da regra financeira e tratamento de concorrência. A mudança só se justifica com evidência de que o caminho atual não atende à carga real.

Dois benchmarks separados e reproduzíveis já fazem parte da CI:

- `PositionCalculatorBenchmark` mede somente a regra de domínio em memória;
- `PositionReadPathBenchmark` mede a materialização do livro via JPA e o cálculo subsequente, usando H2 em modo MySQL.

Na execução de CI nº 160, em Java 21.0.12, runner Linux com 4 processadores e heap máximo de 3.998 MiB, foram observados:

| Operações | Cálculo em memória (mediana) | Leitura via JPA | Cálculo após leitura | Variação de heap na leitura |
| ---: | ---: | ---: | ---: | ---: |
| 1.000 | 0,737 ms | 123,379 ms | 5,309 ms | 10 MiB |
| 10.000 | 1,687 ms | 84,808 ms | 8,705 ms | 8 MiB |
| 100.000 | 9,071 ms | 311,295 ms | 19,775 ms | 74 MiB |

Esses números são diagnóstico de CI, não SLO: há aquecimento de JVM, o ambiente é compartilhado e H2 não representa o plano de execução do MySQL de produção. Ainda assim, mostram que, até 100 mil operações em uma carteira, a materialização domina o custo e o cálculo puro permanece pequeno.

## Decisão

Manter o cálculo sob demanda como fonte única da posição e não introduzir snapshot ou projeção persistida neste momento.

Continuar executando os dois benchmarks na CI como série diagnóstica, sem limite rígido de tempo para evitar falhas por ruído do runner. A decisão deverá ser reavaliada com telemetria de produção e um benchmark em MySQL quando qualquer um destes gatilhos ocorrer:

- percentil 95 do endpoint de posições exceder o SLO que vier a ser definido;
- consumo de memória ou pressão de GC limitar a instância;
- carteiras reais se aproximarem de 100 mil operações;
- reprocessamentos passarem a competir com comandos no banco;
- requisitos de leitura histórica ou relatórios exigirem projeções próprias.

## Consequências

- a regra financeira permanece simples, determinística e sem risco de divergência entre livro e estado derivado;
- leituras continuam com custo linear no tamanho do livro e devem ser monitoradas;
- otimizações devem atacar primeiro a consulta e a materialização, antes de duplicar estado;
- uma futura projeção deverá ser reconstruível a partir do livro, versionar a regra de cálculo e preservar isolamento por proprietário, idempotência e ordem monotônica.
