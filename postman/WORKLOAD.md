# Carga assíncrona de cinco minutos

Importe `activation-async-workload.postman_collection.json` e selecione o environment local. A collection representa o comportamento de um consumidor externo que cria solicitações com produtos distintos e observa o processamento assíncrono.

## Preparação

Execute a pasta **Prepare workload** uma única vez. Ela emite um JWT com os escopos necessários, limpa o estado do mock e define os cenários por produto:

- `FIBER-100`: sucesso;
- `MOBILE-50`: indisponibilidade transitória uma vez;
- `TV-PLUS`: limite de taxa uma vez;
- `LEGACY-PLAN`: falha permanente;
- `SATELLITE-20`: timeout.

## Execução de cinco minutos

No Collection Runner, selecione apenas a pasta **Workload**. Configure `300` iterações e `1000 ms` de atraso entre requisições. Essa configuração gera uma solicitação por segundo durante aproximadamente cinco minutos, alternando os cinco produtos.

Após o Runner terminar, execute a pasta **Results**. Ela apresenta a listagem das solicitações, as métricas Prometheus e as estatísticas do mock. As métricas verificadas são contagem e duração de chamadas ao provedor, além das métricas padrão da JVM e HTTP expostas pelo Actuator.

O mock é exclusivo do ambiente local. O endpoint de controle de produtos não compõe o contrato público da API.
