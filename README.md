# API de Investimentos

API REST em evolução para servir como base de um agregador de investimentos, desenvolvida com **Java 21, Spring Boot, JPA/Hibernate e MySQL**.

O projeto começou pelo domínio de usuários e está sendo gradualmente elevado de exercício de backend para um repositório de portfólio com foco em segurança, testes, configuração por ambiente e práticas de engenharia de software.

## Estado atual

A API implementa CRUD básico de usuários em `/v1/users`.

| Método | Endpoint | Descrição |
| --- | --- | --- |
| `POST` | `/v1/users` | cria um usuário |
| `GET` | `/v1/users/{id}` | consulta um usuário |
| `GET` | `/v1/users` | lista usuários |
| `PATCH` | `/v1/users/{id}` | atualiza nome e/ou senha |
| `DELETE` | `/v1/users/{id}` | remove um usuário |

## Documentação da API

Com a aplicação em execução, o contrato OpenAPI e a interface Swagger UI ficam disponíveis em:

- `http://localhost:8080/v3/api-docs` — especificação OpenAPI em JSON;
- `http://localhost:8080/swagger-ui.html` — documentação interativa.

Os endpoints documentam payloads, validações, códigos de resposta e erros no formato Problem Details (RFC 9457). Exemplos usam somente dados fictícios.

## Segurança aplicada

- senhas são armazenadas com **BCrypt**;
- autenticação usa access tokens JWT assinados com HS256 e expiração curta;
- refresh tokens são opacos, rotativos e persistidos somente como hash SHA-256;
- a reutilização de um refresh token revogado invalida toda a família da sessão;
- os endpoints de autenticação possuem limite configurável por endereço de origem;
- a API é stateless e exige token nos endpoints protegidos;
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
├── common/api/               # contrato comum de erros HTTP
├── config/                   # configurações técnicas compartilhadas
└── identity/
    ├── api/                  # controllers e DTOs HTTP
    ├── application/          # casos de uso, comandos e portas
    ├── domain/               # modelo de identidade
    └── infrastructure/       # adaptadores de persistência
```

Cada funcionalidade mantém suas fronteiras de API, aplicação, domínio e infraestrutura no mesmo módulo. DTOs HTTP são convertidos em comandos antes de entrar na aplicação, e o serviço depende da porta `UserRepository`, não do Spring Data diretamente.

## Roadmap de engenharia

Próximas evoluções priorizadas:

1. limpeza segura e observável de sessões expiradas;
2. perfis e autorização por recurso;
3. modelagem do domínio de investimentos;
4. verificação de e-mail e recuperação de senha;
5. observabilidade, rate limiting distribuído e configuração de produção.

## Princípios de contribuição

- commits seguem **Conventional Commits**;
- mudanças devem ser pequenas, coerentes e testáveis;
- segurança, testes e documentação têm prioridade sobre quantidade de commits;
- secrets e arquivos locais nunca devem ser versionados.

## Autor

**Lindembergue Frank**

[LinkedIn](https://www.linkedin.com/in/lindembergue-frank-b991202b7/)
