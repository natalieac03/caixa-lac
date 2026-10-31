-- Pix: situação ESTORNADA (o Mercado Pago devolveu o dinheiro: refunded ou
-- charged_back; nunca conta como pago) e a hora da última consulta ao Mercado
-- Pago, para a tela não perguntar de novo em menos de 5 s.
ALTER TABLE cobranca_pix DROP CONSTRAINT ck_cobranca_pix_status;
ALTER TABLE cobranca_pix ADD CONSTRAINT ck_cobranca_pix_status
    CHECK (status IN ('PENDENTE','PAGA','EXPIRADA','CANCELADA','ESTORNADA'));
ALTER TABLE cobranca_pix ADD COLUMN consultada_em TIMESTAMP(6);
