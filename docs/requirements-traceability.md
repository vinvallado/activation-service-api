# Rastreabilidade

| Área | Implementação | Evidência |
|---|---|---|
| Criação e consulta | Controller, DTOs e serviços | Collections Postman e validação automatizada |
| Idempotência | `external_id` único e serviço transacional | Repositório, testes de serviço e PostgreSQL |
| Worker | Claim SQL, lease e token | Repositório de claim e worker agendado |
| Provedor | Cliente HTTP, timeout, retry e circuit breaker | Simulador local e métricas |
| Segurança | JWT/JWKS, audience, issuer e scopes | Configuração Resource Server e collection JWT |
| Operação | Compose, health, Prometheus, logs e tracing | Actuator e configuração de ambiente |
