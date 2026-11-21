# ADR 0002: serializar operações de sessão pelo proprietário

- Status: aceito
- Data: 2026-11-20

## Contexto

Refresh e revogação podem receber tokens diferentes da mesma família. Bloquear diretamente o índice não único de família em transações concorrentes produz next-key locks no InnoDB. A CI reproduziu deadlock tanto no cenário de duas renovações do mesmo token quanto no teste de renovação versus revogação.

## Decisão

Todas as operações que renovam ou revogam sessões seguem a mesma ordem:

1. resolver o usuário proprietário pelo hash do token, sem bloquear a linha do refresh token;
2. adquirir PESSIMISTIC_WRITE na linha do usuário, usando sua chave primária única como mutex estável;
3. resolver a família e executar uma leitura bloqueante das linhas da família, ordenadas por ID;
4. revalidar o token apresentado usando as entidades obtidas na leitura atual;
5. somente então revogar tokens ou emitir um descendente.

O bloqueio por usuário é deliberadamente mais amplo que o bloqueio por família. Ele evita que duas transações do mesmo usuário adquiram simultaneamente next-key locks no índice não único de família. Usuários diferentes continuam independentes. Uma tabela de mutex por família só será introduzida se métricas demonstrarem que a serialização por usuário é um gargalo relevante.

## Consequências

- refresh e revogação concorrentes do mesmo usuário são serializados antes da leitura bloqueante da família;
- uma revogação que vence a disputa impede que um descendente posterior permaneça utilizável;
- sessões de famílias diferentes do mesmo usuário também aguardam a mesma linha de usuário;
- a consulta de família permanece uma leitura atual bloqueante no MySQL 8.4, necessária porque a consulta inicial do proprietário pode estabelecer um snapshot anterior à espera pelo mutex;
- os testes MySQL devem continuar cobrindo renovação concorrente e as duas ordens de refresh versus revogação.
