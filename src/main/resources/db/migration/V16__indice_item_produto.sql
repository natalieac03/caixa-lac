-- Finanças: "produtos parados" procura, para cada produto, se há alguma venda no
-- período (NOT EXISTS por item_venda.produto_id). Sem este índice o Postgres lê
-- todos os itens do período; com ele para no primeiro item do produto
-- (EXPLAIN no teste FinancasExplainTest: custo de 5502 para 853 num ano de vendas).
-- As demais consultas já usam idx_venda_data_hora, idx_item_venda_venda,
-- idx_pagamento_venda_venda e ix_venda_desconto_venda.
CREATE INDEX ix_item_venda_produto ON item_venda(produto_id);
