ALTER TABLE item_venda ADD COLUMN venda_id BIGINT;
UPDATE item_venda SET venda_id = (SELECT vi.venda_id FROM venda_itens vi WHERE vi.itens_id = item_venda.id);
-- Órfãos não são descartados: a migração para para exigir correção do vínculo.
SELECT id FROM item_venda WHERE venda_id IS NULL;
ALTER TABLE item_venda ALTER COLUMN venda_id SET NOT NULL;
ALTER TABLE item_venda ADD CONSTRAINT fk_item_venda_venda FOREIGN KEY (venda_id) REFERENCES venda(id);
DROP TABLE venda_itens;

ALTER TABLE promocao_item ADD COLUMN promocao_id BIGINT;
UPDATE promocao_item SET promocao_id = (SELECT pi.promocao_id FROM promocao_itens pi WHERE pi.itens_id = promocao_item.id);
SELECT id FROM promocao_item WHERE promocao_id IS NULL;
ALTER TABLE promocao_item ALTER COLUMN promocao_id SET NOT NULL;
ALTER TABLE promocao_item ADD CONSTRAINT fk_promocao_item_promocao FOREIGN KEY (promocao_id) REFERENCES promocao(id);
DROP TABLE promocao_itens;
