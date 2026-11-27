# ADR 0002: serializar operações de sessão pelo proprietário

- Status: aceito
- Data: 2026-11-20
- Atualização: 2026-11-27

## Contexto

Refresh e revogação podem receber tokens diferentes da mesma família. Bloquear diretamente o índice não único de família em transações concorrentes produz next-key locks no InnoDB. A CI reproduziu deadlock tanto no cenário de duas renovações do mesmo token quanto no teste de renovação versus revogação.

A primeira estratégia passou a adquirir um bloqueio pessimista na linha do usuário antes de ler a família. Isso serializa operações de sessões do mesmo proprietário e evita disputas entre famílias do mesmo usuário.

O benchmark MySQL 8.4 da issue #165, porém, reproduziu um deadlock ao inserir o token descendente durante renovações simultâneas de proprietários distintos. A falha ocorreu no índice secundário não único de `family_id`: em `REPEATABLE READ`, a leitura bloqueante por faixa pode adquirir next-key/gap locks. O mutex por proprietário não serializa proprietários diferentes, e a inserção em uma família pode conflitar com o gap protegido pela leitura de outra família.

## Decisão

Todas as operações que renovam ou revogam sessões seguem a mesma ordem:

1. resolver o usuário proprietário pelo hash do token, sem bloquear a linha do refresh token;
2. adquirir `PESSIMISTIC_WRITE` na linha do usuário, usando sua chave primária única como mutex estável;
3. resolver a família e executar uma leitura bloqueante das linhas da família, ordenadas por ID;
4. revalidar o token apresentado usando as entidades obtidas na leitura atual;
5. somente então revogar tokens ou emitir um descendente.

Os métodos de aplicação `refresh` e `revoke` executam suas transações em `READ COMMITTED`. Nesse nível, o InnoDB não adquire gap locks para as leituras de busca/bloqueio comuns; as exceções de integridade referencial e verificação de chave duplicada continuam aplicáveis. A leitura bloqueante da família mantém os bloqueios de registro necessários.

O isolamento `READ COMMITTED` também faz com que a consulta da família, executada depois de aguardar o mutex do usuário, veja o estado confirmado mais recente. Isso é necessário porque a consulta inicial do proprietário pode ocorrer antes da espera pelo bloqueio.

O bloqueio por usuário permanece deliberadamente mais amplo que o bloqueio por família. Ele garante que duas operações do mesmo proprietário não alterem a mesma família simultaneamente. Usuários diferentes continuam independentes, sem depender de gap locks em um índice secundário não único.

Uma tabela de mutex por família só será introduzida se medições futuras demonstrarem que a serialização por usuário é um gargalo relevante.

## Consequências

- refresh e revogação concorrentes do mesmo usuário são serializados antes da leitura bloqueante da família;
- uma revogação que vence a disputa impede que um descendente posterior permaneça utilizável;
- sessões de famílias diferentes do mesmo usuário também aguardam a mesma linha de usuário;
- as leituras bloqueantes por família operam em `READ COMMITTED`, reduzindo a contenção de gap no índice secundário entre proprietários distintos;
- o schema e as migrations permanecem inalterados;
- os testes MySQL devem continuar cobrindo reutilização concorrente, refresh versus revogação nas duas ordens, isolamento entre proprietários e rotação concorrente de famílias independentes.
