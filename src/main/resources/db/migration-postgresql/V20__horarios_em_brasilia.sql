-- V20 (SÓ NO POSTGRESQL): horários gravados em UTC passam para Brasília.
--
-- Em produção o app rodou com a JVM em UTC (consulta de horas: pico de
-- madrugada), então o relógio gravou a hora UTC em colunas TIMESTAMP sem fuso.
-- Desde esta versão o relógio é o de Brasília (RelogioConfig); esta migração
-- converte o que já estava gravado, para o banco não ficar misturado. Precisa
-- subir no MESMO deploy da troca do relógio. Em banco novo não faz nada.
--
-- Só no Postgres (pasta db/migration-postgresql, via spring.flyway.locations com
-- {vendor}): o H2 local roda na máquina em Brasília e já está certo.
--
-- Conversão: (hora AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo'. Para
-- datas desde 2019 dá exatamente -3 h; antes disso respeita o horário de verão.
--
-- Muda (gravado pelo relógio do app ou pelo CURRENT_TIMESTAMP do Postgres em UTC):
--   venda.data_hora, venda.data_cancelamento, movimento_estoque.data_hora,
--   compra_estoque.registrada_em, cobranca_pix.criada_em/paga_em/resolvida_em,
--   despesa.registrada_em, taxa_pagamento.registrada_em.
-- Não muda (digitado pela pessoa): evento.inicio/fim (formulário; os botões
--   Iniciar/Encerrar nascem nesta mesma versão), despesa.data,
--   compra_estoque.data_compra, taxa_pagamento.vigente_desde (DATE).
-- Decisão da LAC: as vendas corrigidas à mão no histórico (já digitadas em
--   Brasília) também são convertidas e ficam 3 h adiantadas; corrija-as na tela se preciso.

UPDATE venda
SET data_hora = (data_hora AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo';

UPDATE venda
SET data_cancelamento = (data_cancelamento AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo'
WHERE data_cancelamento IS NOT NULL;

UPDATE movimento_estoque
SET data_hora = (data_hora AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo';

UPDATE compra_estoque
SET registrada_em = (registrada_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo';

UPDATE cobranca_pix
SET criada_em = (criada_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo',
    paga_em = (paga_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo',
    resolvida_em = (resolvida_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo';

UPDATE despesa
SET registrada_em = (registrada_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo';

UPDATE taxa_pagamento
SET registrada_em = (registrada_em AT TIME ZONE 'UTC') AT TIME ZONE 'America/Sao_Paulo';
