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

## Roteiro completo de validação

Esta sequência permite demonstrar o serviço de ponta a ponta:
qualidade de código, execução em contêiner, autenticação, contrato REST, idempotência, processamento assíncrono,
resiliência e observabilidade.

### Pré-requisitos

- Java 21;
- Docker Desktop em execução;
- Git e Postman;
- portas locais `8080`, `9099` e `55432` disponíveis.

### 1. Reprodução a partir de um clone limpo

```powershell
git clone https://github.com/vinvallado/activation-service-api.git
cd activation-service-api
git switch master
Copy-Item .env.example .env
```

Defina uma senha local em `DB_PASSWORD` no `.env`.
O arquivo contém somente configuração local e não deve ser versionado.

Valide o código e os testes antes de iniciar contêineres:

```powershell
.\mvnw.cmd clean verify
```

O comando executa testes unitários, testes de arquitetura e integração com PostgreSQL via Testcontainers.
O resultado esperado é `BUILD SUCCESS` e cobertura de instruções superior a 80%.

### 2. Subida do ambiente

```powershell
docker compose up -d --build --force-recreate
docker compose ps
```

O Compose inicia PostgreSQL, API e mock do provedor.
Aguarde PostgreSQL e `mock-provider` ficarem saudáveis e valide a API:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

Resultado esperado:

```json
{ "status": "UP" }
```

### 3. Preparação do Postman

Importe o environment `postman/environments/local.postman_environment.json` e estas collections:

- `activation-api-jwt.postman_collection.json`;
- `activation-api-rest.postman_collection.json`;
- `activation-provider-processing.postman_collection.json`;
- `activation-async-workload.postman_collection.json`.

Selecione o environment **Local activation service**
e confirme `baseUrl` como `http://127.0.0.1:8080` e `providerBaseUrl` como `http://127.0.0.1:9099`.

### 4. Contrato, autenticação e idempotência

Na collection **Activation API - JWT**, execute **Obter token local**.
O token possui os escopos de criação, leitura e métricas.

Em seguida, execute **Criar solicitação**, **Consultar solicitação** e **Listar solicitações**.
A criação deve responder `201`, a consulta e a listagem `200`;
uma solicitação nova pode começar em `PENDING` até o worker processá-la.

Na collection **Activation API - REST**, valide os casos de borda:

| Requisição | Resultado esperado | Evidência |
|---|---|---|
| Criação inválida | `400` | Contrato de validação sem detalhes internos. |
| Sem token | `401` | Autenticação obrigatória. |
| Repetição idempotente | `200` ou `201` | Não há duplicação para a mesma chave externa. |
| Métricas protegidas | `200` | O token contém `activation:metrics`. |

### 5. Integração externa, retry e Circuit Breaker

Na collection **Activation Provider - Scenarios**, execute primeiro **Reset mock state**. Escolha um cenário, crie uma nova solicitação na collection JWT e consulte-a até o estado terminal. Use **Statistics** para confirmar chamadas e chaves de idempotência recebidas pelo mock.

| Cenário do mock | Comportamento esperado |
|---|---|
| `Set transient 503 once` | Uma falha transitória, reagendamento e conclusão posterior. |
| `Set permanent 400` | Falha terminal sem retry. |
| `Set rate limit once` | Reagendamento respeitando `Retry-After`. |
| `Set sustained 500` | Retentativas e abertura do Circuit Breaker. |
| `Set timeout` | Timeout, nova tentativa com a mesma chave de idempotência e limite de tentativas. |

### 6. Carga assíncrona de cinco minutos

Na collection **Activation API - Five Minute Async Workload**,
execute a pasta **Prepare workload** uma vez. Em seguida, no menu de três pontos da pasta **Workload**,
use **Run folder**; não execute a collection inteira. No Collection Runner, confirme que a origem exibida
é a pasta `Workload` e configure `300` iterações com atraso de `1000 ms`. Isso produz aproximadamente uma
solicitação por segundo durante cinco minutos e alterna produtos com sucesso, `503`, `429`, `400` e timeout.

Se o relatório do Runner mostrar `Iterations: 1`, a carga não foi executada.
O resultado correto deve informar aproximadamente `300` iterações e duração próxima de cinco minutos.
A pasta **Results** é executada manualmente somente após o Runner terminar.

Ao terminar, execute a pasta **Results**. Ela apresenta a listagem de solicitações,
as métricas Prometheus e as estatísticas do mock. Alguns itens podem continuar pendentes enquanto o worker
drena a fila e aplica retries; esse é o comportamento esperado de processamento assíncrono durável.

### 7. Observabilidade e encerramento

No endpoint `/actuator/prometheus`, verifique `activation_provider_calls`, `activation_provider_duration`,
`http_server_requests` e métricas de JVM. Para inspecionar logs estruturados:

```powershell
docker compose logs --tail=200 api
```

Para encerrar o ambiente sem perder os dados locais:

```powershell
docker compose down
```

Para reiniciar completamente a base local, use `docker compose down -v`; esse comando remove o volume do PostgreSQL.

## Perguntas técnicas e respostas

### Por que há índices para a consulta paginada com filtros?

As consultas sempre restringem pelo proprietário autenticado e ordenam por `createdAt` e `id`.
Os índices combinados por proprietário, cliente e status evitam varreduras amplas quando a base cresce e
preservam paginação determinística. A necessidade e a ordem final dos índices devem ser confirmadas em
produção com `EXPLAIN ANALYZE` e dados representativos.

### Como a solução evita duplicidade, inclusive sob concorrência?

O `externalId` possui restrição única no banco, que é a garantia definitiva contra duas inserções concorrentes.
Antes da inserção a aplicação consulta o registro existente para dar uma resposta idempotente;
caso duas transações passem por essa consulta ao mesmo tempo, a segunda violação de unicidade é convertida em
conflito controlado. Assim, a aplicação não depende apenas da verificação prévia em memória.

### O que a API responde para repetições de criação?

| Pergunta | Resposta |
|---|---|
| Mesmo `externalId` e mesmo conteúdo | `200 OK` com o recurso existente; é uma repetição idempotente legítima. |
| Mesmo `externalId` e conteúdo diferente | `409 Conflict`; a chave externa já representa outra solicitação e não pode mudar de significado. |
| Duas requisições idênticas em paralelo | Uma cria o registro; a outra recebe o recurso existente ou um conflito controlado, sem criar um segundo registro. |

### Como são controladas as transições e falhas temporárias?

O fluxo é `PENDING → PROCESSING → COMPLETED` ou `FAILED`. Falhas transitórias devolvem a tarefa a `PENDING`,
incrementam a tentativa, guardam o diagnóstico e definem `nextAttemptAt`. Estados terminais não retornam para
processamento. A reivindicação usa lease e token de posse, portanto um worker recuperado não conclui uma tarefa
que já foi retomada por outro worker.

### Como a integração reage a cada resposta do provedor?

| Retorno | Estado resultante | Nova tentativa | Decisão |
|---|---|---|---|
| `2xx` | `COMPLETED` | Não | O provedor confirmou a ativação. |
| `400` | `FAILED` | Não | É uma falha de negócio; repetir não corrige uma requisição inválida. |
| `500` | `PENDING` | Sim, até o limite | Indica indisponibilidade temporária do provedor. |
| Timeout | `PENDING` | Sim, até o limite | O resultado é ambíguo; a mesma chave de idempotência é reutilizada. |
| `429` ou `503` com `Retry-After` | `PENDING` | Sim, respeitando `Retry-After` | O provedor informa quando é seguro tentar novamente. |

### Por que o processamento é assíncrono?

O `POST` persiste a solicitação e uma tarefa durável na mesma transação e retorna sem aguardar a chamada remota.
Isso mantém a latência da API previsível quando o provedor está lento. O worker executa HTTP fora da transação de
banco, faz retry com backoff e jitter e usa Circuit Breaker para reduzir chamadas durante indisponibilidade.

### Como é garantido o envio após uma queda entre persistência e chamada externa?

A solicitação e a tarefa são gravadas atomicamente. Depois do commit, a tarefa permanece pendente e pode ser
reivindicada por qualquer worker. Leases expirados são recuperados. A entrega para o provedor é ao menos uma vez;
a `Idempotency-Key` estável torna a repetição segura.

### Como os endpoints são protegidos?

O serviço é um OAuth2 Resource Server para comunicação sistema a sistema, apropriada ao fluxo Client Credentials.
O JWT é validado por assinatura RSA via JWKS, emissor, audiência e expiração.
Os escopos `activation:write`, `activation:read` e `activation:metrics` separam criação, consulta e métricas.
O claim `client_id` define o proprietário, impedindo que um consumidor consulte dados de outro.

Em produção, credenciais para o provedor e parâmetros de segurança ficam em secret manager,
com rotação e injeção por variáveis de ambiente; nunca no repositório, logs ou imagens.

### Qual é a estratégia de testes?

Testes unitários cobrem domínio, retentativas, serviços, cliente HTTP, filtros e controller.
O teste de integração usa PostgreSQL real por Testcontainers para validar unicidade, claim, token de posse,
reagendamento e conclusão. ArchUnit verifica dependências entre camadas.
Collections Postman oferecem validação caixa-preta de autenticação, contratos REST, provedor simulado
e carga assíncrona. A cobertura de instruções é superior a 80%.

### O que investigar primeiro em um cenário de lentidão?

Começaria pela correlação temporal entre aumento de latência da API, consultas e provedor.
CPU e memória estáveis reduzem a hipótese de saturação de processo, mas não eliminam locks, pool de conexões,
saturação de threads ou chamadas remotas bloqueantes.

| Pergunta | Resposta |
|---|---|
| Como separar aplicação, banco e integração? | Comparar percentis HTTP e traces por rota, duração das queries, locks, pool de conexões e duração/erros das chamadas ao provedor usando o mesmo `correlationId`. |
| Quais logs e métricas ajudam? | Logs estruturados sem segredos, `correlationId`, taxa e duração de chamadas ao provedor, status HTTP, métricas de JVM, conexões, locks, queries lentas e percentis de latência. |
| Que melhorias considerar? | Índices comprovados por plano de execução, correção de queries/locks, ajuste de pools e timeouts, isolamento de chamadas remotas, cache somente quando houver consistência adequada e scaling após encontrar o gargalo. |
| Os sintomas podem ter uma causa comum? | Sim. Um provedor lento pode reter workers e conexões, aumentar fila e pressionar banco. A hipótese é confirmada correlacionando traces, fila, pool, locks e latência externa no mesmo intervalo. |
| Como validar a correção publicada? | Comparar baseline e percentis antes/depois sob carga controlada, acompanhar taxa de erro e saturação durante rollout gradual e manter rollback disponível. |

## Produção e evolução

O contêiner recebe configuração por variáveis de ambiente, possui health check e desligamento gracioso.
A imagem é construída em múltiplos estágios e o Compose local reúne API, PostgreSQL e simulador de provedor.
Em produção, o simulador é removido, PostgreSQL é serviço gerenciado e logs/traces/métricas são enviados a uma plataforma central.

Em múltiplas instâncias, a restrição única do banco e `FOR UPDATE SKIP LOCKED` preservam duplicidade e
distribuição do trabalho. Rate limit local deve ser movido para gateway ou armazenamento distribuído.
Para notificar outros serviços sobre conclusão, a evolução recomendada é outbox transacional com fila ou tópico.
O pipeline executa testes, gera imagem e pode promover artefato imutável entre ambientes.

### Pontos pendentes

- O emissor JWT local existe apenas para desenvolvimento; produção requer provedor OIDC e secret manager.
- O limite de taxa em memória não é compartilhado entre instâncias.
- Não há publicação de evento de conclusão; consumidores consultam o recurso. Uma outbox transacional é a
- extensão apropriada.
- As métricas atuais cobrem a JVM, HTTP e cliente do provedor; métricas agregadas por estado de solicitação podem
- ser adicionadas quando houver requisito operacional definido.

### Melhorias futuras

- Dashboard e alertas por SLO de latência, erro e fila.
- Métricas de idade da fila e solicitações por estado.
- Reconciliação periódica com o provedor quando o contrato externo suportar consulta de status.
- Limite de taxa distribuído, outbox, broker e consumidores idempotentes.
- Testes de concorrência e carga contínuos no pipeline.
