-- Remove as colunas legadas da venda. Desde a V6 a forma de pagamento mora em
-- pagamento_venda e o valor em valor_cobrado; forma_pagamento, forma_pagamento_outro
-- e valor_total só existiam para as vendas antigas. Roda igual no PostgreSQL e no H2.

-- 1. Vendas antigas passam a ter valor_cobrado e valor_calculado. Com calculado igual
--    ao cobrado, o ajuste manual delas continua zero no fechamento (como antes).
UPDATE venda SET valor_cobrado = valor_total WHERE valor_cobrado IS NULL;
UPDATE venda SET valor_calculado = valor_total WHERE valor_calculado IS NULL;

-- 2. Para a migração com mensagem clara (o CAST do texto para número falha e a
--    mensagem aparece no erro). Não se inventa valor: corrija com dados confirmados
--    pela LAC e rode de novo.
SELECT CAST('V7 parada: a venda ' || CAST(id AS VARCHAR(20))
            || ' continua sem valor_cobrado (nem valor_total). Informe o valor e rode de novo.' AS INTEGER)
FROM venda WHERE valor_cobrado IS NULL;
-- A forma de pagamento antiga vai ser apagada: toda venda com valor precisa já ter
-- as suas linhas em pagamento_venda (a V6 as criou), senão a informação se perde.
SELECT CAST('V7 parada: a venda ' || CAST(v.id AS VARCHAR(20))
            || ' tem valor mas não tem linha em pagamento_venda. Confira a V6 antes de apagar forma_pagamento.' AS INTEGER)
FROM venda v
WHERE v.valor_cobrado > 0
  AND NOT EXISTS (SELECT 1 FROM pagamento_venda p WHERE p.venda_id = v.id);

-- valor_cobrado nunca mais fica vazio (a aplicação não tem mais para onde cair).
ALTER TABLE venda ALTER COLUMN valor_cobrado SET NOT NULL;

-- 3. Apaga a regra e as colunas antigas.
ALTER TABLE venda DROP CONSTRAINT IF EXISTS ck_venda_forma_pagamento;
ALTER TABLE venda DROP COLUMN forma_pagamento;
ALTER TABLE venda DROP COLUMN forma_pagamento_outro;
ALTER TABLE venda DROP COLUMN valor_total;
