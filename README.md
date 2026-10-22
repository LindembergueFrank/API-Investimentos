# API de Investimentos

API REST em evolução para servir como base de um agregador de investimentos, desenvolvida com **Java 21, Spring Boot, JPA/Hibernate e MySQL**.

O escopo implementado inclui identidade, carteiras isoladas por proprietário, catálogo controlado de ativos, operações idempotentes, posições derivadas do livro e um cliente React/TypeScript integrado. Cotações, gráficos e rentabilidade não são expostos porque ainda não existe um contrato backend real para esses dados.

A base utiliza Spring Boot 3.5.16 e é desenvolvida com foco em segurança, testes, configuração por ambiente e práticas de engenharia de software.

## Estado atual

A aplicação implementa o fluxo autenticado de identidade, carteiras, ativos, operações e posições, com isolamento por proprietário e precisão financeira.

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `POST` | `/v1/users` | cria um usuário |
| `GET` | `/v1/users/{id}` | consulta o próprio usuário |
| `GET` | `/v1/users` | lista usuários (reservado a administradores) |
| `PATCH` | `/v1/users/{id}` | atualiza nome e/ou senha do próprio usuário |
| `DELETE` | `/v1/users/{id}` | remove o próprio usuário |
| `POST` | `/v1/portfolios` | cria uma carteira para o usuário autenticado |
| `GET` | `/v1/portfolios?page=0&size=20` | lista uma página das carteiras do usuário autenticado |
| `GET` | `/v1/portfolios/{id}` | consulta uma carteira do próprio usuário |
| `POST` | `/v1/assets` | cadastra ativo no catálogo (somente `ADMIN`) |
| `GET` | `/v1/assets?page=0&size=20` | lista o catálogo para usuários autenticados |
| `POST` | `/v1/transactions` | registra compra ou venda na carteira do usuário |
| `GET` | `/v1/portfolios/{id}/transactions?page=0&size=20` | lista as operações da própria carteira |
| `GET` | `/v1/portfolios/{id}/positions` | calcula posições e preço médio da própria carteira |

## Documentação da API

Por segurança, o contrato OpenAPI e a interface Swagger UI ficam desabilitados por padrão. Para habilitá-los explicitamente em um ambiente local ou controlado, defina:

```bash
SPRINGDOC_API_DOCS_ENABLED=true
SPRINGDOC_SWAGGER_UI_ENABLED=true
```

Com a aplicação em execução, ficam disponíveis em:

- `http://localhost:8080/v3/api-docs` — especificação OpenAPI em JSON;
- `http://localhost:8080/swagger-ui.html` — documentação interativa.

Não habilite a interface em ambiente público sem uma decisão operacional explícita e controles de acesso adequados. Os testes de contrato habilitam a documentação isoladamente. Os endpoints documentam payloads, validações, códigos de resposta e erros no formato Problem Details (RFC 9457). Exemplos usam somente dados fictícios.

## Segurança aplicada

- senhas são armazenadas com **BCrypt**;
- autenticação usa access tokens JWT assinados com HS256 e expiração curta;
- refresh tokens são opacos, rotativos e persistidos somente como hash SHA-256;
- a reutilização de um refresh token revogado invalida toda a família da sessão;
- famílias de sessões totalmente expiradas são removidas em lotes após uma retenção de segurança;
- os endpoints de autenticação possuem limite configurável por endereço de origem;
- a API é stateless e exige token nos endpoints protegidos;
- o identificador do proprietário vem do `sub` assinado do JWT; um usuário não pode consultar, alterar ou excluir outro cadastro;
- os papéis `USER`, `PROFESSIONAL` e `ADMIN` seguem privilégio mínimo; todo cadastro público recebe exclusivamente `USER`;
- o papel é carregado do banco no login e incluído no token como autoridade; somente `ADMIN` pode listar usuários;
- não existe endpoint público para promover papéis: a atribuição de `PROFESSIONAL` ou `ADMIN` deve ocorrer por processo administrativo controlado;
- a carteira recebe o proprietário exclusivamente do `sub` assinado do JWT; identificadores enviados pelo cliente não definem propriedade;
- consultas de carteira filtram simultaneamente pelo identificador do recurso e pelo proprietário, impedindo acesso horizontal;
- a listagem de carteiras é paginada, ordenada de forma estável e limitada a 100 itens por página;
- ativos são normalizados e únicos por mercado + ticker; apenas administradores alteram o catálogo compartilhado;
- operações usam precisão decimal, chave idempotente por carteira e bloqueio pessimista para impedir vendas concorrentes acima da posição;
- a linha do tempo de operações é paginada, limitada a 100 itens e ordenada de forma determinística;
- posições são derivadas na ordem monotônica do livro; taxas de compra compõem o custo e vendas não alteram o preço médio remanescente;
- o contrato de posições documenta explicitamente schemas de sucesso, autenticação e recurso não encontrado no OpenAPI;
- CORS permanece fechado por padrão e aceita somente origens HTTP(S) exatas configuradas em `API_CORS_ALLOWED_ORIGINS`;
- o frontend React/TypeScript consome carteiras, ativos, operações e posições reais, com renovação HTTP coordenada, estados explícitos e módulos organizados por funcionalidade;
- cada requisição recebe um UUID gerado pelo servidor em `X-Correlation-Id`; o mesmo valor permanece no contexto de log somente durante a requisição e é exposto ao frontend para suporte sem registrar payloads, tokens ou dados financeiros;
- OpenAPI e Swagger UI permanecem desabilitados por padrão e só são publicados quando o ambiente os habilita explicitamente;
- a chave de assinatura é obrigatória e fornecida por variável de ambiente;
- respostas HTTP utilizam um DTO específico e **nunca retornam o campo de senha**;
- credenciais de banco não ficam versionadas;
- configuração local utiliza variáveis de ambiente;
- `.env` é ignorado pelo Git e `.env.example` contém apenas valores de referência;
- testes usam banco H2 em memória e não dependem de credenciais externas.

## Exemplo de criação

```http
POST /v1/users
Content-Type: application/json
```

```json
{
  "username": "lindembergue",
  "email": "dev@example.com",
  "password": "uma-senha-forte"
}
```

Resposta `201 Created`:

```json
{
  "id": "9cc32af0-6bd3-47aa-9f03-2bf93825b5be",
  "username": "lindembergue",
  "email": "dev@example.com",
  "creationTimestamp": "2026-08-29T16:00:00Z",
  "updateTimestamp": "2026-08-29T16:00:00Z"
}
```

A senha não faz parte do contrato de resposta.

## Autenticação

O cadastro e os endpoints de autenticação são públicos. Os demais endpoints exigem um access token JWT no header `Authorization: Bearer <token>`. O access token expira em 15 minutos por padrão; o refresh token expira em 30 dias e é substituído a cada renovação.

Gere uma chave exclusiva para o ambiente antes de iniciar a aplicação:

```bash
openssl rand -base64 32
```

Defina o resultado em `AUTH_JWT_SECRET_BASE64`. A aplicação interrompe a inicialização quando a chave está ausente, não é Base64 válida ou possui menos de 256 bits.

Para autenticar:

```http
POST /v1/auth/token
Content-Type: application/json

{
  "email": "investidor@example.com",
  "password": "uma-senha-forte"
}
```

Credenciais inválidas retornam a mesma resposta genérica, sem indicar se o e-mail está cadastrado.

A autenticação retorna `accessToken`, `expiresIn`, `refreshToken` e `refreshExpiresIn`. Para renovar a sessão, envie o refresh token uma única vez:

```http
POST /v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "token-opaco"
}
```

Cada renovação revoga o token anterior e devolve um novo. Se um token anterior for reutilizado, toda a família da sessão é revogada como medida contra roubo de credenciais. O cliente deve então solicitar novo login.

Para encerrar a sessão, use `POST /v1/auth/revoke` com o mesmo corpo. A resposta é sempre `204 No Content`, inclusive para valores desconhecidos, evitando revelar quais tokens são válidos. Respostas que contêm credenciais usam `Cache-Control: no-store`.

Os três endpoints de autenticação compartilham, por endereço de origem, um limite padrão de 20 requisições por minuto. Ao excedê-lo, a API responde `429` em Problem Details, informa `Retry-After` e não processa a credencial. Ajuste `AUTH_RATE_LIMIT_MAX_REQUESTS` e `AUTH_RATE_LIMIT_WINDOW` conforme o ambiente. O limitador é local à instância; múltiplas réplicas exigirão armazenamento compartilhado em uma evolução posterior. A aplicação usa apenas o endereço remoto fornecido pelo servidor e não confia diretamente em headers encaminhados pelo cliente.

A limpeza de sessões roda, por padrão, a cada hora e remove no máximo 500 famílias por execução. Uma família só é elegível quando todos os seus tokens expiraram há pelo menos sete dias; assim, um token ancestral não é apagado enquanto ainda pode existir um descendente válido cuja reutilização precise ser detectada. Configure esse comportamento com `AUTH_REFRESH_TOKEN_CLEANUP_ENABLED`, `AUTH_REFRESH_TOKEN_CLEANUP_INTERVAL`, `AUTH_REFRESH_TOKEN_CLEANUP_RETENTION` e `AUTH_REFRESH_TOKEN_CLEANUP_BATCH_SIZE`.

## Executando localmente

### Pré-requisitos

- Java 21;
- Docker + Docker Compose.

Crie seu arquivo local de ambiente a partir do exemplo:

```bash
cp .env.example .env
```

Defina uma senha de desenvolvimento em `DB_PASSWORD` e suba o MySQL:

```bash
docker compose up -d
```

Exporte as variáveis do `.env` para o processo da aplicação conforme o seu shell/IDE e execute:

```bash
./mvnw spring-boot:run
```

Por padrão, a aplicação espera MySQL em `localhost:3307` e banco `mydatabase`.

No Windows, utilize `mvnw.cmd spring-boot:run` e `mvnw.cmd test`, configurando as variáveis no terminal ou na IDE.

## Migrações de banco

O Flyway é responsável pela evolução do schema. Na inicialização, migrations pendentes em `src/main/resources/db/migration` são aplicadas antes de o Hibernate validar o mapeamento JPA. O Hibernate usa `ddl-auto=validate` e não cria nem altera tabelas.

Para um banco local descartável criado antes da adoção do Flyway, recrie o volume:

```bash
docker compose down -v
docker compose up -d
```

Esse comando remove os dados locais. Para preservar um banco existente, faça backup, confira se o schema corresponde às migrations publicadas e execute a aplicação uma única vez com `FLYWAY_BASELINE_ON_MIGRATE=true`. Depois remova essa variável para que divergências futuras voltem a interromper a inicialização.

## Testes

A suíte unitária e os testes de integração usam H2 em memória. O teste de contexto executa as migrations versionadas e valida o schema com Hibernate:

```bash
./mvnw test
```

O mesmo comando é executado automaticamente pelo GitHub Actions em pushes e pull requests para `master`.

## Organização

```text
src/main/java/api_tech/api_investimentos/
├── common/                   # contrato comum de erros HTTP
├── config/                   # configurações técnicas compartilhadas
├── identity/                 # usuários, autenticação e sessão
├── portfolio/                # carteiras isoladas por proprietário
├── asset/                    # catálogo controlado de ativos
├── transaction/              # compras, vendas e histórico paginado
└── position/                 # cálculo derivado de quantidade e preço médio
```

O frontend fica em `frontend/`, com infraestrutura compartilhada e funcionalidades separadas em `auth`, `dashboard`, `portfolios`, `assets` e `transactions`. Para executá-lo:

```bash
cd frontend
npm ci
npm run dev
```

O frontend mantém somente o access token em memória. O refresh token fica em um cookie `__Host-` com `HttpOnly`, `Secure`, `SameSite=Strict` e `Path=/`; ele não é exposto ao JavaScript, é rotacionado a cada renovação e permite restaurar a sessão após recarregar a página. O modelo de CSRF combina `SameSite=Strict`, o prefixo `__Host-` e CORS fechado por padrão; os endpoints de navegador recebem o cookie apenas com `credentials: include`. A URL da API pode ser definida por `VITE_API_BASE_URL`; sem ela, o frontend usa a mesma origem.

Cada funcionalidade mantém suas fronteiras de API, aplicação, domínio e infraestrutura no mesmo módulo. DTOs HTTP são convertidos em comandos antes de entrar na aplicação, e o serviço depende da porta `UserRepository`, não do Spring Data diretamente.

## Roadmap de engenharia

Próximas evoluções priorizadas:

1. implementar verificação de e-mail, recuperação de senha e observabilidade;
2. definir fonte e contrato backend de cotações antes de iniciar rentabilidade, gráficos ou qualquer dado financeiro de mercado.

A busca e a paginação server-side já substituem cargas integrais nos catálogos e na seleção de carteiras. A posição continua calculada sob demanda: a [decisão arquitetural](docs/decisions/0001-position-calculation.md) registra as medições atuais, os limites da evidência e os gatilhos para reavaliar snapshot ou projeção.

## Princípios de contribuição

- commits seguem **Conventional Commits**;
- mudanças devem ser pequenas, coerentes e testáveis;
- segurança, testes e documentação têm prioridade sobre quantidade de commits;
- secrets e arquivos locais nunca devem ser versionados.

## Autor

**Lindembergue Frank**

[LinkedIn](https://www.linkedin.com/in/lindembergue-frank-b991202b7/)
