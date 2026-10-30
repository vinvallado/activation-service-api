# ADR-003 — Segurança e observabilidade

## Decisão

A API é OAuth2 Resource Server, valida JWT assinado por JWKS e usa `client_id` como proprietário. Logs são estruturados e carregam correlation ID; métricas e tracing são configuráveis.

## Motivo

Scopes distinguem leitura e escrita, e o proprietário impede acesso horizontal entre consumidores. Logs e métricas permitem investigar falhas sem expor tokens, senhas ou consultas internas.

## Consequências

O emissor local é exclusivo do ambiente de desenvolvimento. Em produção, um provedor OIDC e secret manager devem substituir a configuração local.
