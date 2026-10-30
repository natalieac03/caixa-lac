-- Limite de estoque baixo opcional por produto: com ele, o alerta da tela
-- inicial avisa quando a quantidade chega ao limite; sem ele, usa a média de
-- vendas por dia do último evento.
ALTER TABLE produto ADD COLUMN estoque_minimo INTEGER;
ALTER TABLE produto ADD CONSTRAINT ck_produto_estoque_minimo CHECK (estoque_minimo IS NULL OR estoque_minimo >= 0);
