# Fundação de produto e arquitetura

## 1. Objetivo

Evoluir a API de um CRUD de usuários para uma plataforma confiável de consolidação e acompanhamento de carteiras de investimentos.

A primeira versão comercial deve permitir que investidores registrem ou importem operações e obtenham posição, preço médio, alocação e resultados de forma reproduzível e auditável.

O sistema não executará ordens nem fornecerá recomendação personalizada de investimentos no MVP.

## 2. Problema e público inicial

### Problema

Investidores que usam mais de uma instituição frequentemente controlam operações em planilhas e têm dificuldade para consolidar:

- quantidade e custo por ativo;
- preço médio e taxas;
- lucro ou prejuízo realizado;
- posição e alocação atuais;
- proventos;
- histórico para conferência e exportação.

### Público inicial

O MVP será validado com investidores pessoa física que já controlam operações manualmente. O produto poderá evoluir depois para profissionais e integrações B2B, sem antecipar requisitos de múltiplos clientes antes da validação.

### Hipótese de valor

> Registrar ou importar operações uma única vez e obter uma carteira consolidada, confiável e explicável.

## 3. Escopo do MVP

### Incluído

- cadastro e autenticação;
- carteira individual;
- ações, ETFs e FIIs brasileiros;
- operações de compra e venda;
- custos e taxas da operação;
- posição, custo e preço médio;
- lucro ou prejuízo realizado;
- proventos;
- alocação da carteira;
- importação CSV idempotente;
- exportação CSV;
- trilha de auditoria das alterações.

### Fora do escopo inicial

- recomendação de compra ou venda;
- execução de ordens;
- imposto de renda completo;
- criptomoedas;
- renda fixa;
- Open Finance e integração direta com a B3;
- microsserviços;
- inteligência artificial;
- cotações em tempo real sem fonte licenciada.

Itens fora do escopo só entram após validação com usuários e uma decisão arquitetural registrada.

## 4. Princípios de engenharia

1. **Corretude antes de velocidade:** cálculos financeiros devem ser determinísticos, testados e explicáveis.
2. **Simplicidade antes de abstração:** novos padrões são adotados quando resolvem variação ou acoplamento reais.
3. **Domínio isolado de infraestrutura:** regras financeiras não dependem de HTTP, JPA ou fornecedor externo.
4. **Mudanças pequenas:** cada commit representa uma alteração coerente, testável e reversível.
5. **Contrato explícito:** endpoints, erros e versões são documentados com OpenAPI.
6. **Segurança por padrão:** menor privilégio, segredos fora do repositório e isolamento de dados por usuário.
7. **Observabilidade desde o MVP:** erros, latência e operações críticas devem ser rastreáveis.
8. **Evolução orientada por evidência:** funcionalidades dependem de problema validado, não apenas de interesse técnico.

## 5. Arquitetura

### Estilo

Será utilizado um **monólito modular organizado por funcionalidade**. Essa arquitetura oferece baixo custo operacional e fronteiras claras sem introduzir a complexidade distribuída de microsserviços.

Estrutura-alvo:

```text
api_tech.api_investimentos
├── identity
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── portfolio
├── asset
├── transaction
├── position
├── income
├── importjob
├── report
├── audit
└── shared
```

Cada módulo pode conter:

- `api`: controllers, DTOs e mapeamento HTTP;
- `application`: casos de uso e limites transacionais;
- `domain`: entidades, objetos de valor, regras e eventos;
- `infrastructure`: JPA, integrações e detalhes técnicos.

O pacote `shared` deve permanecer pequeno. Conceitos de negócio pertencem ao módulo que os controla.

### Dependências permitidas

```text
api -> application -> domain
infrastructure -> application/domain
domain -> nenhuma camada externa
```

Controllers não acessam repositories diretamente. Entidades JPA não são retornadas como contrato HTTP.

### Padrões adotados

- **Application Service / Use Case:** coordena cada operação da aplicação;
- **Repository:** abstrai persistência;
- **DTO + Mapper:** separa contrato HTTP do modelo interno;
- **Value Object:** representa dinheiro, quantidade, ticker e identificadores quando houver invariantes;
- **Strategy:** apenas para regras que realmente variam, como cálculo por tipo de ativo;
- **Adapter:** integra provedores de cotação, importadores e serviços externos;
- **Domain Event:** para efeitos desacoplados relevantes, como auditoria após importação.

CQRS, event sourcing e microsserviços não fazem parte da arquitetura inicial.

## 6. Modelo de domínio inicial

### Agregados

- **User:** identidade e credenciais;
- **Portfolio:** carteira pertencente a um usuário;
- **Transaction:** compra ou venda registrada na carteira;
- **ImportBatch:** lote importado e sua chave de idempotência.

### Entidades e objetos de valor

- `Asset`: ativo identificado por mercado e ticker;
- `TransactionType`: `BUY` ou `SELL`;
- `Money`: valor e moeda;
- `Quantity`: quantidade com precisão definida;
- `Position`: projeção calculada a partir das operações;
- `Income`: provento associado ao ativo e à carteira.

### Invariantes mínimas

- valores, quantidades e taxas não podem ser negativos;
- uma venda não pode exceder a posição, salvo decisão futura explícita;
- dinheiro e quantidade usam `BigDecimal`, nunca `double`;
- a mesma importação não pode gerar operações duplicadas;
- alterar ou cancelar uma operação deve deixar trilha auditável;
- o reprocessamento do mesmo histórico deve gerar o mesmo resultado.

## 7. Persistência e consistência

- Flyway será a única fonte de evolução do schema;
- `ddl-auto` será `validate` fora dos testes;
- índices e restrições de unicidade existirão também no banco;
- datas de negócio e instantes de auditoria serão conceitos distintos;
- operações críticas terão limite transacional explícito;
- concorrência será tratada com versionamento otimista onde necessário;
- o MVP continuará em MySQL para evitar uma migração sem benefício validado.

H2 poderá permanecer para testes rápidos, mas os testes de integração usarão Testcontainers com MySQL.

## 8. API e compatibilidade

- base path versionado em `/v1`;
- UUID tipado nos limites HTTP;
- Bean Validation para entrada;
- erros no formato Problem Details (`application/problem+json`);
- paginação em endpoints de coleção;
- idempotency key em importações e comandos sujeitos a repetição;
- OpenAPI versionada junto ao código;
- alterações incompatíveis exigem nova versão ou estratégia de migração.

## 9. Segurança e privacidade

Antes do piloto:

- Spring Security;
- senha com algoritmo adaptativo;
- access token de curta duração e refresh token rotativo;
- recuperação de senha com token único e expirável;
- autorização por propriedade do recurso;
- proteção contra enumeração de contas;
- rate limiting nos endpoints sensíveis;
- logs sem senha, token ou dados financeiros completos;
- auditoria de autenticação e alteração de operações;
- segredos fornecidos pelo ambiente;
- backup e teste de restauração;
- política de retenção e exclusão de dados.

Toda consulta de carteira deve comprovar que o recurso pertence ao usuário autenticado. Testes automatizados devem tentar acesso horizontal entre usuários.

## 10. Estratégia de testes

### Pirâmide

1. testes unitários do domínio;
2. testes de casos de uso;
3. testes de contrato HTTP com MockMvc;
4. testes de persistência e integração com Testcontainers;
5. poucos testes ponta a ponta dos fluxos críticos.

Casos obrigatórios do motor:

- compra inicial;
- várias compras e preço médio ponderado;
- venda parcial e total;
- taxas;
- tentativa de venda acima da posição;
- operações fora de ordem cronológica;
- importação repetida;
- isolamento entre usuários;
- concorrência em comandos críticos.

Cobertura será um indicador auxiliar. O critério principal é cobrir riscos e regras, não atingir um percentual isolado.

## 11. Qualidade operacional

A aplicação deverá oferecer:

- health, readiness e liveness checks;
- logs estruturados com correlation ID;
- métricas de erro, latência e importação;
- perfis separados de desenvolvimento, teste e produção;
- imagem Docker reproduzível;
- pipeline com testes, análise estática e verificação de dependências;
- rollback documentado;
- backups automatizados em produção.

## 12. Git e fluxo de entrega

### Branches

- `master` permanece estável e protegida;
- branches curtas seguem `feat/`, `fix/`, `docs/`, `test/`, `refactor/` e `chore/`;
- não haverá commits diretos em `master`;
- cada branch resolve uma issue ou objetivo claramente descrito.

### Commits

Conventional Commits, em português ou inglês de forma consistente:

```text
feat(portfolio): adiciona criação de carteira
fix(transaction): impede venda acima da posição
test(position): cobre preço médio ponderado
docs(architecture): registra decisão sobre monólito modular
```

Cada commit deve:

- ser pequeno e coerente;
- manter build e testes verdes;
- não misturar formatação com mudança funcional;
- não conter segredo, arquivo local ou dado real;
- explicar intenção, não apenas arquivos alterados.

### Pull requests

Toda PR deve conter:

- problema e objetivo;
- solução e decisões;
- como validar;
- riscos e rollback;
- evidência de testes;
- documentação afetada;
- vínculo com a issue.

Merge permanece manual após CI verde e revisão do diff.

## 13. Definition of Done

Uma entrega só está concluída quando:

- critérios de aceitação foram atendidos;
- testes relevantes foram adicionados e executados;
- contrato e documentação foram atualizados;
- logs não expõem dados sensíveis;
- migrations são reversíveis operacionalmente ou têm rollback descrito;
- pipeline está verde;
- PR está pequena o suficiente para revisão;
- não há TODO crítico escondido no código.

## 14. Roadmap por marcos

### M0 — Fundação

- consolidar branches existentes;
- validação e erros HTTP;
- testes de controller;
- OpenAPI;
- Flyway;
- perfis de configuração;
- atualizar README e decisões arquiteturais.

### M1 — Identidade segura

- autenticação;
- refresh token;
- recuperação de senha;
- autorização por recurso;
- testes de segurança.

### M2 — Domínio de carteira

- carteira;
- ativos;
- compra e venda;
- posição e preço médio;
- testes do motor financeiro.

### M3 — Uso real

- importação CSV idempotente;
- proventos;
- dashboard mínimo;
- exportação;
- observabilidade;
- deploy de homologação.

### M4 — Validação comercial

- cinco usuários-piloto;
- conferência com carteiras reais anonimizadas;
- métricas de ativação e retenção;
- teste de disposição a pagar;
- decisão sobre planos e integrações.

## 15. Métricas do MVP

- tempo até a primeira carteira calculada;
- percentual de importações concluídas;
- divergências de cálculo reportadas;
- usuários ativos após 7 e 30 dias;
- carteiras reprocessadas sem inconsistência;
- usuários-piloto dispostos a pagar.

O sucesso do MVP não será medido pela quantidade de funcionalidades, mas pela confiança nos cálculos e pelo uso recorrente.
