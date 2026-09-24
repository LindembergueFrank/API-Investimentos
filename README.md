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
- respostas HTTP utilizam um DTO específico e **nunca retornam o campo de senha**;
- credenciais de banco não ficam versionadas;
- configuração local utiliza variáveis de ambiente;
- `.env` é ignorado pelo Git e `.env.example` contém apenas valores de referência;
- testes usam banco H2 em memória e não dependem de credenciais externas.

> O projeto ainda não implementa autenticação/autorização. BCrypt protege o armazenamento das credenciais, mas autenticação com Spring Security permanece no roadmap.

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

Esse comando remove os dados locais. Para preservar um banco existente, faça backup, confira se o schema corresponde à migration `V1` e execute a aplicação uma única vez com `FLYWAY_BASELINE_ON_MIGRATE=true`. Depois remova essa variável para que divergências futuras voltem a interromper a inicialização.

## Testes

A suíte unitária e os testes de integração usam H2 em memória. O teste de contexto executa a migration inicial e valida o schema com Hibernate:

```bash
./mvnw test
```

O mesmo comando é executado automaticamente pelo GitHub Actions em pushes e pull requests para `master`.

## Organização

```text
src/main/java/api_tech/api_investimentos/
├── config/       # configuração técnica, como PasswordEncoder
├── controller/   # endpoints e DTOs HTTP
├── entity/       # entidades JPA
├── repository/   # persistência
└── service/      # regras de aplicação
```

## Roadmap de engenharia

Próximas evoluções priorizadas:

1. migrations com Flyway;
2. organização por funcionalidade;
3. autenticação e autorização com Spring Security;
4. modelagem do domínio de investimentos;
5. observabilidade e configuração de produção.

## Princípios de contribuição

- commits seguem **Conventional Commits**;
- mudanças devem ser pequenas, coerentes e testáveis;
- segurança, testes e documentação têm prioridade sobre quantidade de commits;
- secrets e arquivos locais nunca devem ser versionados.

## Autor

**Lindembergue Frank**

[LinkedIn](https://www.linkedin.com/in/lindembergue-frank-b991202b7/)
