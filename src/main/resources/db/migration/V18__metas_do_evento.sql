-- Metas opcionais por evento: receita líquida e lucro líquido. A tela de
-- Finanças mostra o progresso quando o filtro é um evento com meta.
ALTER TABLE evento ADD COLUMN meta_receita NUMERIC(38,2);
ALTER TABLE evento ADD COLUMN meta_lucro NUMERIC(38,2);
ALTER TABLE evento ADD CONSTRAINT ck_evento_meta_receita CHECK (meta_receita IS NULL OR meta_receita > 0);
ALTER TABLE evento ADD CONSTRAINT ck_evento_meta_lucro CHECK (meta_lucro IS NULL OR meta_lucro > 0);
