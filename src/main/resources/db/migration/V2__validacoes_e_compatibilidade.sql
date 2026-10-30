-- Reconcilia também bancos locais que não foram abertos desde a primeira rodada.
-- As colunas de valores permanecem NULL nas vendas antigas; não há recálculo retroativo.
ALTER TABLE produto ADD COLUMN IF NOT EXISTS versao BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE venda ADD COLUMN IF NOT EXISTS valor_calculado NUMERIC(38,2);
ALTER TABLE venda ADD COLUMN IF NOT EXISTS desconto NUMERIC(38,2);
ALTER TABLE venda ADD COLUMN IF NOT EXISTS valor_cobrado NUMERIC(38,2);
ALTER TABLE venda ADD COLUMN IF NOT EXISTS motivo_ajuste VARCHAR(255);
ALTER TABLE venda ADD COLUMN IF NOT EXISTS token VARCHAR(255);
SELECT token, COUNT(*) FROM venda WHERE token IS NOT NULL GROUP BY token HAVING COUNT(*) > 1;
CREATE UNIQUE INDEX IF NOT EXISTS uk_venda_token ON venda(token);

-- Converte o ENUM nativo do H2; no Postgres essas colunas já são VARCHAR.
ALTER TABLE venda ALTER COLUMN forma_pagamento SET DATA TYPE VARCHAR(255);
ALTER TABLE promocao ALTER COLUMN tipo SET DATA TYPE VARCHAR(255);

-- Os SELECTs denunciam os IDs para saneamento. A constraint seguinte aborta
-- se houver violações. Não se fabricam valores financeiros nem referências.
SELECT id, quantidade FROM produto WHERE quantidade < 0;
ALTER TABLE produto ADD CONSTRAINT ck_produto_quantidade CHECK (quantidade >= 0);
SELECT id, preco FROM produto WHERE preco <= 0;
ALTER TABLE produto ADD CONSTRAINT ck_produto_preco CHECK (preco > 0);
SELECT id, valor_cobrado FROM venda WHERE valor_cobrado < 0;
ALTER TABLE venda ADD CONSTRAINT ck_venda_valor_cobrado CHECK (valor_cobrado >= 0);
SELECT id, preco FROM promocao WHERE preco <= 0;
ALTER TABLE promocao ADD CONSTRAINT ck_promocao_preco CHECK (preco > 0);
SELECT id, quantidade FROM promocao_item WHERE quantidade <= 0;
ALTER TABLE promocao_item ADD CONSTRAINT ck_promocao_item_quantidade CHECK (quantidade > 0);
SELECT id FROM produto WHERE nome IS NULL;
ALTER TABLE produto ALTER COLUMN nome SET NOT NULL;
SELECT id FROM venda WHERE data_hora IS NULL;
ALTER TABLE venda ALTER COLUMN data_hora SET NOT NULL;
SELECT id FROM venda WHERE forma_pagamento IS NULL;
ALTER TABLE venda ALTER COLUMN forma_pagamento SET NOT NULL;
SELECT id FROM item_venda WHERE produto_id IS NULL;
ALTER TABLE item_venda ALTER COLUMN produto_id SET NOT NULL;
