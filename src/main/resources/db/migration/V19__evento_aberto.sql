-- Evento sem fim = em andamento (iniciado pelo botão "Iniciar evento" e
-- encerrado pelo "Encerrar"). Só pode haver um aberto; ele vale até agora (ou
-- até o início do próximo evento, para nunca sobrepor). A regra fica no serviço.
ALTER TABLE evento ALTER COLUMN fim DROP NOT NULL;

-- "Dia a dia" (vendas fora de qualquer evento) e a venda no evento procuram por
-- evento.inicio <= data_hora < evento.fim.
CREATE INDEX ix_evento_inicio_fim ON evento(inicio, fim);
