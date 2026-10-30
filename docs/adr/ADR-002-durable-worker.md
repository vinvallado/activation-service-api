# ADR-002 — Worker persistente e idempotência

## Decisão

Cada solicitação cria uma tarefa persistente na mesma transação. O worker usa `FOR UPDATE SKIP LOCKED`, lease e token de posse.

## Motivo

A chamada remota não deve manter uma transação de banco aberta. A tarefa sobrevive a reinícios e múltiplas instâncias podem trabalhar sem executar a mesma posse em paralelo.

## Consequências

A entrega é ao menos uma vez. A chave de idempotência estável enviada ao provedor torna reenvios seguros. Um token vencido não pode concluir uma tarefa recuperada por outro worker.
