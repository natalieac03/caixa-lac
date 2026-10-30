-- Pix pago no Mercado Pago cuja venda nunca foi confirmada (tela fechada) aparece
-- num aviso do histórico até alguém resolver à mão; aqui fica o registro disso.
ALTER TABLE cobranca_pix ADD COLUMN resolvida_em TIMESTAMP(6);
ALTER TABLE cobranca_pix ADD COLUMN resolucao VARCHAR(255);
ALTER TABLE cobranca_pix ADD CONSTRAINT ck_cobranca_pix_resolucao CHECK (
    (resolvida_em IS NULL AND resolucao IS NULL) OR (resolvida_em IS NOT NULL AND LENGTH(TRIM(resolucao)) > 0));
