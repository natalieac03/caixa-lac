ALTER TABLE venda ADD COLUMN cancelada BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE venda ADD COLUMN data_cancelamento TIMESTAMP(6);
ALTER TABLE venda ADD COLUMN motivo_cancelamento VARCHAR(255);
ALTER TABLE venda ADD COLUMN versao BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE venda ADD CONSTRAINT ck_venda_cancelamento CHECK (
    (cancelada = FALSE AND data_cancelamento IS NULL AND motivo_cancelamento IS NULL)
    OR (cancelada = TRUE AND data_cancelamento IS NOT NULL
        AND motivo_cancelamento IS NOT NULL AND LENGTH(TRIM(motivo_cancelamento)) > 0)
);

-- Índices dos novos vínculos, usados na carga dos itens e nas agregações.
CREATE INDEX idx_item_venda_venda ON item_venda(venda_id);
CREATE INDEX idx_promocao_item_promocao ON promocao_item(promocao_id);

-- Reinstala a validação de enums nas bases H2 legadas após a conversão da V2.
-- No banco novo essas regras já existem; os nomes explícitos são estáveis.
SELECT id, forma_pagamento FROM venda WHERE forma_pagamento NOT IN ('CREDITO','DEBITO','DINHEIRO','OUTRO','PIX');
ALTER TABLE venda ADD CONSTRAINT ck_venda_forma_pagamento CHECK (forma_pagamento IN ('CREDITO','DEBITO','DINHEIRO','OUTRO','PIX'));
SELECT id, tipo FROM promocao WHERE tipo NOT IN ('A_PARTIR_DE','PACOTE');
ALTER TABLE promocao ADD CONSTRAINT ck_promocao_tipo CHECK (tipo IN ('A_PARTIR_DE','PACOTE'));
