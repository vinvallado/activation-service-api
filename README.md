# Activation Service API

Serviço para registrar solicitações de ativação de produtos, acompanhar seu estado e executar a integração com um provedor externo de forma resiliente.

## Arquitetura

O serviço é um monólito Spring Boot organizado em camadas horizontais: `controller`, `dto`, `service`, `model`, `entity`, `repository`, `client`, `scheduler`, `security`, `exception`, `config` e `observability`.

Essa organização foi escolhida porque há um único domínio e um único processo de implantação. Ela separa HTTP, regras de negócio e persistência sem introduzir abstrações adicionais que não agregariam valor ao serviço.

## Execução local

1. Copie `.env.example` para `.env` e defina uma senha local para `DB_PASSWORD`.
2. Execute `docker compose up -d --build`.
3. Consulte `docker compose ps` e aguarde os serviços saudáveis.

O Compose inicia PostgreSQL 17, a API e um simulador de provedor. O simulador fornece JWKS e token local de desenvolvimento; chaves privadas e tokens não são versionados.

## Segurança

A API é um OAuth2 Resource Server. Ela valida assinatura RSA por JWKS, emissor, audiência e expiração do JWT. A identidade confiável é o claim `client_id`; ela determina o proprietário do recurso. Os escopos são:

- `activation:write` para criar solicitações;
- `activation:read` para consultar uma solicitação ou listar recursos;
- `activation:metrics` para consultar métricas Prometheus.

Em produção, o emissor local deve ser substituído por um provedor OIDC, como Keycloak, com credenciais Client Credentials armazenadas em secret manager.

## Processamento do provedor

O POST confirma somente após persistir a solicitação e uma tarefa durável. O worker reivindica tarefas com `FOR UPDATE SKIP LOCKED`, lease e token de posse; executa HTTP fora da transação de banco e finaliza a tarefa somente se ainda possuir o token.

Respostas 2xx concluem a ativação; 400 falha definitivamente; 429, 5xx e falhas de comunicação são reagendadas com backoff exponencial, jitter e `Retry-After` quando fornecido. A mesma `Idempotency-Key` é reutilizada. O Circuit Breaker impede chamadas adicionais enquanto o provedor está indisponível.

## Observabilidade

Health checks liveness/readiness e métricas Prometheus são expostos pelo Actuator. Os logs são JSON estruturado e carregam `correlationId`; tokens, senhas e dados de segredo não devem ser registrados. Traces OpenTelemetry são configuráveis por variável de ambiente e não exigem collector local.

## Postman

Importe `postman/environments/local.postman_environment.json` e as collections em `postman/collections`. Execute primeiro **Obter token local**. As collections não armazenam tokens ou credenciais.

Para validar a integração assíncrona, use a collection **Activation Provider - Scenarios** nesta ordem: **Reset mock state**, escolha um cenário em **Set ...**, crie uma solicitação na collection JWT, consulte a solicitação até o estado terminal e use **Statistics** para conferir as chamadas e as chaves de idempotência recebidas pelo provedor local. O controle de cenário existe apenas no simulador de desenvolvimento e não faz parte do contrato da API.

## Operação em produção

Em aumento simultâneo de latência da API, banco e provedor, investigue traces e percentis antes de concluir uma causa: pool de conexões, locks, queries lentas, saturação HTTP e chamadas remotas dentro de transações são hipóteses distintas. Use logs estruturados, métricas de latência/erros do provedor, `EXPLAIN ANALYZE` e locks do PostgreSQL para confirmar a origem. Uma correção deve ser validada contra baseline, carga controlada, rollout gradual e plano de rollback.

O rate limit local é adequado somente para desenvolvimento. Em múltiplas instâncias, use gateway ou armazenamento distribuído. Para publicar conclusão a outros serviços, use outbox transacional e broker como evolução; o serviço não exige broker para sua operação atual.

## Uso de IA

Ferramentas de IA foram usadas como apoio de implementação, revisão e documentação. A execução, os resultados de build e as limitações devem ser verificados no ambiente local antes de uma publicação.
