-- Conferência da produção (PostgreSQL), SÓ LEITURA. Rode ANTES e DEPOIS do deploy
-- e compare as duas saídas (veja docs/DEPLOY.md):
--
--   psql "$DATABASE_URL" -f scripts/conferir-producao.sql > conferencia-antes.txt
--
-- Tudo roda numa transação READ ONLY (o Postgres recusa qualquer escrita) que é
-- desfeita no fim. Funciona em qualquer versão do esquema: antes do deploy o banco
-- pode ainda não ter pagamento_venda, a coluna cancelada ou o flyway_schema_history;
-- o script confere o que existe (psql \gset / \if) antes de consultar.

\set ON_ERROR_STOP on
\pset footer off
BEGIN TRANSACTION READ ONLY;

-- O que existe neste banco
SELECT to_regclass('public.pagamento_venda') IS NOT NULL       AS tem_pagamento_venda,
       to_regclass('public.flyway_schema_history') IS NOT NULL AS tem_flyway,
       to_regclass('public.venda_itens') IS NOT NULL           AS tem_venda_itens,
       to_regclass('public.evento') IS NOT NULL                AS tem_evento,
       EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'venda' AND column_name = 'cancelada')     AS tem_cancelada,
       EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'venda' AND column_name = 'valor_total')   AS tem_valor_total,
       EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'venda' AND column_name = 'valor_cobrado') AS tem_valor_cobrado,
       EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'item_venda' AND column_name = 'venda_id') AS tem_item_venda_id
\gset

\echo
\echo '== 1. Contagens'
SELECT 'produto' AS tabela, count(*) AS linhas FROM produto
UNION ALL SELECT 'venda', count(*) FROM venda
UNION ALL SELECT 'item_venda', count(*) FROM item_venda;

\if :tem_pagamento_venda
SELECT 'pagamento_venda' AS tabela, count(*) AS linhas FROM pagamento_venda;
\else
\echo 'pagamento_venda: ainda não existe (é criada pela V6).'
\endif

\if :tem_cancelada
SELECT 'vendas canceladas' AS tabela, count(*) AS linhas FROM venda WHERE cancelada;
\else
\echo 'vendas canceladas: a coluna ainda não existe (é criada pela V5); conta como 0.'
\endif

\echo
\echo '== 2. Soma do valor cobrado das vendas não canceladas'
\echo '   (antes da V7, vendas antigas têm só valor_total: a soma usa valor_cobrado ou, se vazio, valor_total;'
\echo '    a V7 copia valor_total para valor_cobrado, então o número tem de ser o mesmo antes e depois)'
-- O valor de cada venda conforme a versão do esquema: só valor_total (original),
-- valor_cobrado ou valor_total (V2 a V6) ou só valor_cobrado (V7 em diante).
\if :tem_valor_cobrado
  \if :tem_valor_total
\set valor 'COALESCE(valor_cobrado, valor_total)'
  \else
\set valor 'valor_cobrado'
  \endif
\else
\set valor 'valor_total'
\endif
\if :tem_cancelada
\set validas 'WHERE NOT cancelada'
\else
\set validas ''
\endif
SELECT count(*) AS vendas_validas, COALESCE(SUM(:valor), 0) AS soma_cobrado FROM venda :validas;

\echo
\echo '== 3. Flyway: últimas migrações aplicadas'
\if :tem_flyway
SELECT installed_rank, version, description, type, installed_on, execution_time AS ms, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 12;
\else
\echo 'flyway_schema_history não existe: o primeiro deploy faz o baseline (V1) e aplica V2 em diante.'
\endif

\echo
\echo '== 4. Pré-voo: o que faria uma migração parar (tudo deve dar 0; só faz sentido ANTES do deploy)'
SELECT 'produto com quantidade < 0 (V2)' AS conferencia, count(*) AS linhas FROM produto WHERE quantidade < 0
UNION ALL SELECT 'produto com preço <= 0 ou vazio (V2)', count(*) FROM produto WHERE preco IS NULL OR preco <= 0
UNION ALL SELECT 'produto sem nome (V2)', count(*) FROM produto WHERE nome IS NULL
UNION ALL SELECT 'venda sem data_hora (V2)', count(*) FROM venda WHERE data_hora IS NULL
UNION ALL SELECT 'item sem produto (V2)', count(*) FROM item_venda WHERE produto_id IS NULL
UNION ALL SELECT 'item sem preço unitário (V12)', count(*) FROM item_venda WHERE preco_unitario IS NULL;

\if :tem_valor_total
SELECT 'venda sem valor (V6/V7)' AS conferencia, count(*) AS linhas FROM venda WHERE :valor IS NULL
UNION ALL SELECT 'venda com valor negativo (V2/V6)', count(*) FROM venda WHERE :valor < 0;
\endif

\if :tem_item_venda_id
\else
  \if :tem_venda_itens
SELECT 'item sem venda na tabela venda_itens (V3)' AS conferencia, count(*) AS linhas
FROM item_venda i WHERE NOT EXISTS (SELECT 1 FROM venda_itens vi WHERE vi.itens_id = i.id);
  \endif
\endif

\echo
\echo '== 5. Vendas por hora do dia (pico de madrugada = horas gravadas em UTC; pico à noite = Brasília)'
SELECT EXTRACT(HOUR FROM data_hora) h, COUNT(*) FROM venda GROUP BY 1 ORDER BY 1;

\echo
\echo '== 6. Eventos dos botões Iniciar/Encerrar entre o deploy de 30/10 21h27 e a V20'
\echo 'Nesse intervalo o relógio gravava a hora UTC, e a V20 não converte evento.inicio nem evento.fim.'
\echo 'Cruze os ids abaixo com as linhas de auditoria evento_iniciado e evento_encerrado dos logs do Railway.'
\if :tem_flyway
SELECT version, installed_on AS aplicada_em FROM flyway_schema_history WHERE version = '20';
\endif
\if :tem_evento
SELECT id, nome, inicio, fim FROM evento
WHERE inicio >= TIMESTAMP '2026-10-30 21:27' OR fim >= TIMESTAMP '2026-10-30 21:27'
ORDER BY id;
\endif

-- Correção (NÃO roda aqui: este script é só leitura). Use só os ids que aparecem nos logs
-- do Railway, e só no campo que o botão gravou, numa sessão própria:
--
--   Linha "evento=evento_iniciado id=N": o início foi gravado pelo botão Iniciar.
--     UPDATE evento SET inicio = inicio - INTERVAL '3 hours' WHERE id = N;
--
--   Linha "evento=evento_encerrado id=N fim=...": o fim foi gravado pelo botão Encerrar,
--   a não ser que esse fim seja o início do evento seguinte (aí ele foi digitado e está certo).
--     UPDATE evento SET fim = fim - INTERVAL '3 hours' WHERE id = N;
--
-- Depois de corrigir, rode este script de novo e confira que os eventos não se sobrepõem.

ROLLBACK;
