# ADR-001 — Arquitetura em camadas

## Decisão

O serviço usa camadas horizontais para HTTP, contratos, serviços, domínio, persistência, integração e agendamento.

## Motivo

O domínio é único e o serviço é implantado como uma JVM. Separar responsabilidades técnicas torna as dependências explícitas e reduz acoplamento sem exigir portas, adaptadores ou módulos adicionais.

## Consequências

Controllers não usam entidades ou repositórios; regras de transição permanecem no modelo; serviços coordenam transações e integrações. ArchUnit protege essas regras.
