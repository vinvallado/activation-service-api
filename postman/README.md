# Collections Postman

Selecione o environment `Local activation service` e execute primeiro `Obter token local`.

Use a collection **Activation API - JWT**
- para criar, consultar e listar solicitações autenticadas.
A collection **Activation Provider - Scenarios**
- demonstra as respostas determinísticas do provedor local.
- alNenhum token é versionado: ele é obtido em tempo de execução e permanece somente no environment local do Postman.

Para observar processamento consecutivo, execute **Reset mock state**,
selecione um cenário com **Set ...**, envie uma criação pela collection JWT e
consulte o recurso até `COMPLETED` ou `FAILED`. Em cenários transitórios,
aguarde o `nextAttemptAt` e consulte novamente.

Por fim, **Statistics** informa o total de chamadas e a quantidade por chave de idempotência.
