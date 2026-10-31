# Activation Service API

Serviço para registrar solicitações de ativação de produtos, acompanhar seu estado e executar a integração 
com um provedor externo de forma resiliente.

## Arquitetura

O serviço é um monólito Spring Boot organizado em camadas horizontais: 
`controller`, `dto`, `service`, `model`, `entity`, `repository`, `client`, `scheduler`, `security`, `exception`,
`config` e `observability`.

Essa organização foi escolhida porque há um único domínio e um único processo de implantação. 
Ela separa HTTP, regras de negócio e persistência sem introduzir abstrações adicionais que não agregariam valor
ao serviço.

## Execução local

1. Copie `.env.example` para `.env` e defina uma senha local para `DB_PASSWORD`.
2. Execute `docker compose up -d --build`.
3. Consulte `docker compose ps` e aguarde os serviços saudáveis.

O Compose inicia PostgreSQL 17, a API e um simulador de provedor. O simulador fornece JWKS e token local de 
desenvolvimento; chaves privadas e tokens não são versionados.

## Segurança

A API é um OAuth2 Resource Server. Ela valida assinatura RSA por JWKS, emissor, audiência e expiração do JWT. 
A identidade confiável é o claim `client_id`; ela determina o proprietário do recurso. Os escopos são:

- `activation:write` para criar solicitações;
- `activation:read` para consultar uma solicitação ou listar recursos;
- `activation:metrics` para consultar métricas Prometheus.

Em produção, o emissor local deve ser substituído por um provedor OIDC, como Keycloak, com credenciais Client 
Credentials armazenadas em secret manager.

## Processamento do provedor

O POST confirma somente após persistir a solicitação e uma tarefa durável. O worker reivindica tarefas com 
`FOR UPDATE SKIP LOCKED`, lease e token de posse; 
executa HTTP fora da transação de banco e finaliza a tarefa somente se ainda possuir o token.

Respostas 2xx concluem a ativação; 
400 falha definitivamente; 
429, 5xx e falhas de comunicação são reagendadas com backoff exponencial, 
jitter e `Retry-After` quando fornecido. A mesma `Idempotency-Key` é reutilizada. 
O Circuit Breaker impede chamadas adicionais enquanto o provedor está indisponível.

## Observabilidade

Health checks liveness/readiness e métricas Prometheus são expostos pelo Actuator. 
Os logs são JSON estruturado e carregam `correlationId`; 
tokens, senhas e dados de segredo não devem ser registrados. Traces OpenTelemetry são configuráveis 
por variável de ambiente e não exigem collector local.

## Postman

Importe `postman/environments/local.postman_environment.json` e as collections em `postman/collections`. 
Execute primeiro **Obter token local**. As collections não armazenam tokens ou credenciais.

Para validar a integração assíncrona, use a collection **Activation Provider - Scenarios** nesta ordem: 
**Reset mock state**, 
escolha um cenário em **Set ...**, 
crie uma solicitação na collection JWT, 
consulte a solicitação até o estado terminal e 
use **Statistics** para conferir as chamadas e as chaves de idempotência recebidas pelo provedor local. 
O controle de cenário existe apenas no simulador de desenvolvimento e não faz parte do contrato da API.

## Uso de IA

Ferramentas de IA foram usadas como apoio de implementação, revisão e documentação. 
A execução, os resultados de build e as limitações devem ser verificados no ambiente local antes de uma publicação.

## Perguntas técnicas e respostas

### Por que há índices para a consulta paginada com filtros?

As consultas sempre restringem pelo proprietário autenticado e ordenam por `createdAt` e `id`. Os índices combinados por proprietário, cliente e status evitam varreduras amplas quando a base cresce e preservam paginação determinística. A necessidade e a ordem final dos índices devem ser confirmadas em produção com `EXPLAIN ANALYZE` e dados representativos.

### Como a solução evita duplicidade, inclusive sob concorrência?

`externalId` possui restrição única no banco, que é a garantia definitiva contra duas inserções concorrentes. Antes da inserção a aplicação consulta o registro existente para dar uma resposta idempotente; caso duas transações passem por essa consulta ao mesmo tempo, a segunda violação de unicidade é convertida em conflito controlado. Assim, a aplicação não depende apenas da verificação prévia em memória.

### O que a API responde para repetições de criação?

| Pergunta | Resposta |
|---|---|
| Mesmo `externalId` e mesmo conteúdo | `200 OK` com o recurso existente; é uma repetição idempotente legítima. |
| Mesmo `externalId` e conteúdo diferente | `409 Conflict`; a chave externa já representa outra solicitação e não pode mudar de significado. |
| Duas requisições idênticas em paralelo | Uma cria o registro; a outra recebe o recurso existente ou um conflito controlado, sem criar um segundo registro. |

### Como são controladas as transições e falhas temporárias?

O fluxo é `PENDING → PROCESSING → COMPLETED` ou `FAILED`. Falhas transitórias devolvem a tarefa a `PENDING`, incrementam a tentativa, guardam o diagnóstico e definem `nextAttemptAt`. Estados terminais não retornam para processamento. A reivindicação usa lease e token de posse, portanto um worker recuperado não conclui uma tarefa que já foi retomada por outro worker.

### Como a integração reage a cada resposta do provedor?

| Retorno | Estado resultante | Nova tentativa | Decisão |
|---|---|---|---|
| `2xx` | `COMPLETED` | Não | O provedor confirmou a ativação. |
| `400` | `FAILED` | Não | É uma falha de negócio; repetir não corrige uma requisição inválida. |
| `500` | `PENDING` | Sim, até o limite | Indica indisponibilidade temporária do provedor. |
| Timeout | `PENDING` | Sim, até o limite | O resultado é ambíguo; a mesma chave de idempotência é reutilizada. |
| `429` ou `503` com `Retry-After` | `PENDING` | Sim, respeitando `Retry-After` | O provedor informa quando é seguro tentar novamente. |

### Por que o processamento é assíncrono?

O `POST` persiste a solicitação e uma tarefa durável na mesma transação e retorna sem aguardar a chamada remota. Isso mantém a latência da API previsível quando o provedor está lento. O worker executa HTTP fora da transação de banco, faz retry com backoff e jitter e usa Circuit Breaker para reduzir chamadas durante indisponibilidade.

### Como é garantido o envio após uma queda entre persistência e chamada externa?

A solicitação e a tarefa são gravadas atomicamente. Depois do commit, a tarefa permanece pendente e pode ser reivindicada por qualquer worker. Leases expirados são recuperados. A entrega para o provedor é ao menos uma vez; a `Idempotency-Key` estável torna a repetição segura.

### Como os endpoints são protegidos?

O serviço é um OAuth2 Resource Server para comunicação sistema a sistema, apropriada ao fluxo Client Credentials. O JWT é validado por assinatura RSA via JWKS, emissor, audiência e expiração. Os escopos `activation:write`, `activation:read` e `activation:metrics` separam criação, consulta e métricas. O claim `client_id` define o proprietário, impedindo que um consumidor consulte dados de outro.

Em produção, credenciais para o provedor e parâmetros de segurança ficam em secret manager, com rotação e injeção por variáveis de ambiente; nunca no repositório, logs ou imagens.

### Qual é a estratégia de testes?

Testes unitários cobrem domínio, retentativas, serviços, cliente HTTP, filtros e controller. O teste de integração usa PostgreSQL real por Testcontainers para validar unicidade, claim, token de posse, reagendamento e conclusão. ArchUnit verifica dependências entre camadas. Collections Postman oferecem validação caixa-preta de autenticação, contratos REST, provedor simulado e carga assíncrona. A cobertura de instruções é superior a 80%.

### O que investigar primeiro em um cenário de lentidão?

Começaria pela correlação temporal entre aumento de latência da API, consultas e provedor. CPU e memória estáveis reduzem a hipótese de saturação de processo, mas não eliminam locks, pool de conexões, saturação de threads ou chamadas remotas bloqueantes.

| Pergunta | Resposta |
|---|---|
| Como separar aplicação, banco e integração? | Comparar percentis HTTP e traces por rota, duração das queries, locks, pool de conexões e duração/erros das chamadas ao provedor usando o mesmo `correlationId`. |
| Quais logs e métricas ajudam? | Logs estruturados sem segredos, `correlationId`, taxa e duração de chamadas ao provedor, status HTTP, métricas de JVM, conexões, locks, queries lentas e percentis de latência. |
| Que melhorias considerar? | Índices comprovados por plano de execução, correção de queries/locks, ajuste de pools e timeouts, isolamento de chamadas remotas, cache somente quando houver consistência adequada e scaling após encontrar o gargalo. |
| Os sintomas podem ter uma causa comum? | Sim. Um provedor lento pode reter workers e conexões, aumentar fila e pressionar banco. A hipótese é confirmada correlacionando traces, fila, pool, locks e latência externa no mesmo intervalo. |
| Como validar a correção publicada? | Comparar baseline e percentis antes/depois sob carga controlada, acompanhar taxa de erro e saturação durante rollout gradual e manter rollback disponível. |

## Produção e evolução

O contêiner recebe configuração por variáveis de ambiente, possui health check e desligamento gracioso. A imagem é construída em múltiplos estágios e o Compose local reúne API, PostgreSQL e simulador de provedor. Em produção, o simulador é removido, PostgreSQL é serviço gerenciado e logs/traces/métricas são enviados a uma plataforma central.

Em múltiplas instâncias, a restrição única do banco e `FOR UPDATE SKIP LOCKED` preservam duplicidade e distribuição do trabalho. Rate limit local deve ser movido para gateway ou armazenamento distribuído. Para notificar outros serviços sobre conclusão, a evolução recomendada é outbox transacional com fila ou tópico. O pipeline executa testes, gera imagem e pode promover artefato imutável entre ambientes.

### Pontos pendentes

- O emissor JWT local existe apenas para desenvolvimento; produção requer provedor OIDC e secret manager.
- O limite de taxa em memória não é compartilhado entre instâncias.
- Não há publicação de evento de conclusão; consumidores consultam o recurso. Uma outbox transacional é a extensão apropriada.
- As métricas atuais cobrem a JVM, HTTP e cliente do provedor; métricas agregadas por estado de solicitação podem ser adicionadas quando houver requisito operacional definido.

### Melhorias futuras

- Dashboard e alertas por SLO de latência, erro e fila.
- Métricas de idade da fila e solicitações por estado.
- Reconciliação periódica com o provedor quando o contrato externo suportar consulta de status.
- Limite de taxa distribuído, outbox, broker e consumidores idempotentes.
- Testes de concorrência e carga contínuos no pipeline.
