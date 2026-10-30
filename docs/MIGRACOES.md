# Migrações do Caixa LAC

## Esquema e comparação com o H2 local

O arquivo `dados/caixa.mv.db` foi inspecionado somente para leitura com H2
2.2.224. O esquema real (sem usuário/senha do banco) está reproduzido em
`src/test/resources/db/esquema-h2-legado.sql` e é migrado em teste de integração.
O arquivo local original não foi migrado durante a implementação.

- V1 representa o modelo da rodada anterior: oito tabelas, IDs `BIGINT GENERATED
  BY DEFAULT AS IDENTITY`, dinheiro `NUMERIC(38,2)`, datas `TIMESTAMP(6)`, FKs e
  unicidade de categoria normalizada, token e filho nas tabelas de junção.
- O H2 local estava atrasado: faltavam `produto.versao` e
  `venda.valor_calculado`, `desconto`, `valor_cobrado`, `motivo_ajuste` e `token`.
  V2 acrescenta essas colunas se faltarem. Versão inicial é zero; valores novos
  de vendas antigas ficam nulos, preservando o fallback para `valor_total` (até a V7, que
  preenche os valores e apaga a coluna).
- H2 usava ENUM nativo. Os mapeamentos e as migrações passam a usar VARCHAR,
  como no PostgreSQL. V1 inclui CHECK dos valores dos enums. V2 converte os
  enums de bases existentes; a conversão não altera os valores gravados.
- V2 acrescenta CHECK de estoque não negativo, preços positivos de produto e
  promoção, valor cobrado não negativo e quantidade positiva de item de promoção.
  Acrescenta NOT NULL em nome do produto, data/forma de pagamento e produto do
  item vendido. CHECK não implica NOT NULL: valores financeiros legados nulos
  continuam aceitos onde não se pediu NOT NULL.
- Cada constraint é precedida por SELECT diagnóstico. Valores inválidos fazem a
  migração falhar, exigindo saneamento com dados confirmados pela LAC. Não há
  correção financeira automática nem descarte de itens órfãos.
- V3 copia os vínculos das junções para `item_venda.venda_id` e
  `promocao_item.promocao_id`, exige NOT NULL, cria FKs e remove `venda_itens` e
  `promocao_itens`. Filhos órfãos interrompem a migração.
- V4 cria `idx_venda_data_hora`.
- V5 acrescenta `venda.cancelada` (falso para vendas existentes),
  `data_cancelamento`, `motivo_cancelamento` e `versao`. Uma constraint exige
  motivo/data quando há cancelamento. A versão evita sobrescrever alterações
  concorrentes da venda; o cancelamento também bloqueia a linha até concluir a
  devolução de estoque. Há índices nas novas FKs dos itens e CHECKs explícitos
  para preservar a validação dos enums também nas bases H2 convertidas pela V2.
- V6 (pagamento dividido) cria `pagamento_venda` (`venda_id` FK NOT NULL,
  `forma` NOT NULL com CHECK dos valores do enum, `valor` NOT NULL > 0,
  `descricao` para Outro, `valor_recebido` só em dinheiro e nunca menor que o
  valor), com índices em `forma` e `venda_id`. **O que acontece com os dados
  antigos:** cada venda existente vira **uma** linha de pagamento, com
  `forma = forma_pagamento`, `valor = coalesce(valor_cobrado, valor_total)` e
  `descricao = forma_pagamento_outro`; canceladas também ganham a linha (e
  continuam fora dos totais pela venda). Vendas antigas de valor zero não ganham
  linha: viram cortesias. `venda.forma_pagamento` perde o NOT NULL e deixa de
  ser preenchida por vendas novas (a coluna é apagada na V7). Antes de copiar, um SELECT para a migração com mensagem clara
  (`V6 parada: a venda N não tem valor_cobrado nem valor_total...`, ou
  `... tem valor negativo`) se houver venda nessas condições; não se inventa
  valor. A mensagem sai de um `CAST` do texto para número, que funciona igual no
  PostgreSQL e no H2.
- O fechamento passa a somar `pagamento_venda` por forma (uma venda dividida
  entra em cada forma, com o valor de cada parte), sem canceladas. A soma das
  formas tem que bater com o total cobrado; se não bater, a tela alerta.
  Cancelar preserva todos os valores originais e só altera a situação/auditoria.
- V7 (remove colunas legadas) mexe só na tabela `venda`:
  - preenche `valor_cobrado = valor_total` e `valor_calculado = valor_total` onde
    estiverem nulos (vendas anteriores à primeira rodada). Com calculado igual ao
    cobrado, o ajuste manual delas continua zero no fechamento;
  - para com mensagem clara (o mesmo `CAST` da V6) se sobrar venda sem
    `valor_cobrado` (`V7 parada: a venda N continua sem valor_cobrado...`) ou se
    uma venda com valor não tiver linha em `pagamento_venda` (`V7 parada: a venda
    N tem valor mas não tem linha em pagamento_venda...`): sem a linha, a forma de
    pagamento se perderia ao apagar a coluna;
  - `valor_cobrado` passa a ser NOT NULL;
  - apaga `ck_venda_forma_pagamento` e as colunas `forma_pagamento`,
    `forma_pagamento_outro` e `valor_total` (no Postgres o CHECK gerado pelo
    Hibernate, `venda_forma_pagamento_check`, cai junto com a coluna).
  - **O que se perde:** a forma de pagamento das vendas antigas de valor zero
    (cortesias), que não têm linha em `pagamento_venda`. Fica só no dump e na
    exportação do passo 7.1 abaixo. As demais vendas já tinham a forma copiada
    pela V6 (inclusive a descrição do Outro).

Nomes de constraints gerados automaticamente pelo Hibernate/H2 diferem dos nomes
explícitos da V1; isso não muda a integridade nem impede `validate`. O H2 usa
identificadores internos em maiúsculas e armazenamento próprio; esses detalhes
não são transportados ao PostgreSQL. Após a reconciliação, não ficou diferença
funcional conhecida no H2. O esquema real de produção não foi acessado nem
comparado: uma restauração de produção é a validação que ainda precisa ser feita.

## Ensaio no PostgreSQL

Num PostgreSQL 16 local e descartável (nunca no Railway), a versão original do
app criou o esquema com `ddl-auto=update` e registrou duas vendas e duas
promoções pelas telas. A versão nova subiu sobre esse banco com o perfil `prod`:
baseline na versão 1, V2 a V5 aplicadas, `validate` aprovado, vínculos e valores
antigos preservados. O ensaio achou um erro que o H2 não mostra: com algum filtro
vazio, o histórico dava 500 (`cannot cast type bytea to timestamp`), porque o
Hibernate envia `null` sem tipo. O histórico, o CSV e o fechamento passaram a
usar `Specification` (`VendaSpecs`): só entra na consulta o predicado do filtro
preenchido, sem `:x is null or ...` e sem limites de data artificiais.

Esse banco virou `src/test/resources/db/postgres-legado.sql`. `PostgresLegadoTest`
e `PostgresBancoNovoTest` sobem um PostgreSQL 16 com Testcontainers e repetem o
caminho da produção e o de um banco vazio a cada `./mvnw verify`. O banco legado
é migrado até a V5, ganha uma venda de valor zero e uma cancelada, e o teste
guarda o fechamento por forma calculado do jeito antigo; depois da V6 confere
que cada venda antiga tem exatamente uma linha de pagamento com o mesmo valor e
que o fechamento por forma é o mesmo. No computador, sem Docker, esses testes
são pulados; no CI (`CI=true`) falta de Docker é erro, e um passo do workflow
falha se os relatórios não mostrarem esses testes rodando. Diferenças do banco legado que ficam, sem efeito no
`validate`: o CHECK de enum gerado pelo Hibernate em `promocao_tipo_check` convive
com o explícito da V5 (o de `venda_forma_pagamento_check` some com a coluna na V7), e as sequences de
identidade mantêm o nome gerado pelo Postgres.

Os testes e os perfis local/prod usam Flyway e `ddl-auto=validate` (configurados
só em `application.properties`). Com o Spring Boot 3.5.16 as versões vêm do Boot
(Flyway 11.7, Hibernate 6.6, H2 2.3.232), sem forçar nada no pom. Uma cópia do
`dados/caixa.mv.db` local (H2 2.2) abriu no H2 2.3.232 e migrou até a V6.

## Aplicação em produção

1. Reserve uma janela sem vendas. Faça backup completo do PostgreSQL (`pg_dump`
   em formato custom, incluindo esquema e dados) e confirme que ele pode ser
   restaurado. Guarde também a versão anterior do aplicativo e suas variáveis.
2. Restaure o backup numa instância isolada. Compare o esquema com V1 e execute
   os SELECTs diagnósticos da V2 e a verificação de órfãos da V3. Corrija na
   origem qualquer inconsistência usando dados confirmados, e refaça o backup.
3. Ensaie o deploy desta versão na restauração, com as mesmas configurações de
   produção e `ddl-auto=validate`. Confira valores, contagens, vínculos e IDs.
   Em banco não vazio sem histórico Flyway, `baseline-on-migrate=true` registra
   versão 1 e pula V1; V2 em diante precisam encontrar as tabelas legadas.
   Banco vazio executa V1 e todas as migrações seguintes.
4. Pare todas as instâncias antigas antes de migrar: V3 remove tabelas usadas
   pelo binário anterior. Não use deploy com versões antigas e novas atendendo
   vendas simultaneamente. Não volte a `ddl-auto=update`.
5. Suba uma única instância nova com perfil `prod`, conexão PostgreSQL e
   `APP_SENHA`. Flyway migra antes da validação JPA; confira `flyway_schema_history`
   e a inicialização completa. Se os diagnósticos falharem, mantenha o serviço
   fora do ar, corrija a causa e repita o ensaio antes de prosseguir.
6. Confira login, estoque, prévia, histórico, cancelamento, fechamento e CSV antes de liberar as
   vendas e subir as demais instâncias.
7. **Conferência da V6 e da V7.** As consultas de "antes" rodam no esquema antigo
   (antes do deploy); as de "depois" só usam colunas que continuam existindo,
   então valem também quando V6 e V7 sobem juntas.

   7.1 Antes do deploy (versão anterior no ar, sem vendas acontecendo), guarde
   cada venda e o fechamento por forma. A consulta depende de onde o banco está;
   veja com `SELECT max(version) FROM flyway_schema_history;` (erro "relation does
   not exist" = banco ainda no esquema original, sem Flyway).

   Esquema original (sem Flyway, sem `valor_cobrado`):

   ```sql
   \copy (SELECT id, forma_pagamento AS forma, valor_total AS valor, false AS cancelada FROM venda ORDER BY id) TO 'vendas-antes.csv' CSV HEADER
   SELECT count(*) AS vendas FROM venda;
   SELECT forma_pagamento, count(*), sum(valor_total) FROM venda GROUP BY forma_pagamento ORDER BY 1;
   ```

   Já migrado até a V5 (tem `valor_cobrado`, ainda sem `pagamento_venda`):

   ```sql
   \copy (SELECT id, forma_pagamento AS forma, coalesce(valor_cobrado, valor_total) AS valor, cancelada FROM venda ORDER BY id) TO 'vendas-antes.csv' CSV HEADER
   SELECT count(*) AS vendas FROM venda;
   SELECT forma_pagamento, count(*), sum(coalesce(valor_cobrado, valor_total))
   FROM venda WHERE NOT cancelada GROUP BY forma_pagamento ORDER BY 1;
   ```

   Já na V6 (a forma de cada venda vem de `pagamento_venda`; vendas divididas
   aparecem uma vez por parte):

   ```sql
   \copy (SELECT v.id, p.forma, p.valor, v.cancelada FROM venda v JOIN pagamento_venda p ON p.venda_id = v.id ORDER BY v.id, p.id) TO 'vendas-antes.csv' CSV HEADER
   SELECT count(*) AS vendas FROM venda;
   SELECT p.forma, count(DISTINCT v.id), sum(p.valor) FROM pagamento_venda p JOIN venda v ON v.id = p.venda_id
   WHERE NOT v.cancelada GROUP BY p.forma ORDER BY 1;
   ```

   Na versão V6 o arquivo tem uma linha por parte; na comparação abaixo, troque
   `v.valor_cobrado <> a.valor` por nada (só a conferência das linhas vale) se
   houver venda dividida.

   7.2 Depois da subida (só leitura nas tabelas do app; a tabela temporária some
   ao fechar a sessão):

   ```sql
   -- As colunas antigas sumiram (0) e não há venda sem valor cobrado (0)
   SELECT count(*) FROM information_schema.columns
   WHERE table_name = 'venda' AND column_name IN ('forma_pagamento', 'forma_pagamento_outro', 'valor_total');
   SELECT count(*) FROM venda WHERE valor_cobrado IS NULL;

   -- Mesma contagem de vendas que antes (antes do deploy nenhuma venda nova foi feita)
   SELECT count(*) AS vendas FROM venda;

   -- vendas = vendas com pagamento + cortesias (as duas colunas iguais)
   SELECT (SELECT count(*) FROM venda) AS vendas,
          (SELECT count(DISTINCT venda_id) FROM pagamento_venda)
        + (SELECT count(*) FROM venda WHERE valor_cobrado = 0) AS com_pagamento_mais_cortesias;

   -- Cada venda antiga com o mesmo valor e, se tem valor, uma linha da forma de antes (vazio)
   CREATE TEMP TABLE vendas_antes (id bigint, forma varchar(255), valor numeric(38,2), cancelada boolean);
   \copy vendas_antes FROM 'vendas-antes.csv' CSV HEADER
   SELECT count(*) AS linhas_importadas FROM vendas_antes;  -- igual à contagem de 7.1
   SELECT a.id FROM vendas_antes a LEFT JOIN venda v ON v.id = a.id
   WHERE v.id IS NULL OR v.valor_cobrado <> a.valor
      OR (a.valor > 0 AND (SELECT count(*) FROM pagamento_venda p
                           WHERE p.venda_id = a.id AND p.forma = a.forma AND p.valor = a.valor) <> 1);

   -- Fechamento por forma: igual ao guardado em 7.1
   SELECT p.forma, sum(p.valor) FROM pagamento_venda p JOIN venda v ON v.id = p.venda_id
   WHERE NOT v.cancelada GROUP BY p.forma ORDER BY 1;
   ```

   A tela `/fechamento` com o período todo deve mostrar os mesmos totais, os
   mesmos ajustes manuais e nenhum alerta de "soma das formas não bate".
   Guarde `vendas-antes.csv` junto com o dump: é o registro da forma de pagamento
   das cortesias antigas, que a V7 apaga.

Não há downgrade automático. Após V3, rollback do aplicativo antigo exige também
restaurar o backup do banco durante a janela, sem perder vendas feitas depois.
**Rollback da V6:** restaure o dump feito antes do deploy (`pg_restore --clean` num
banco parado, ou num banco novo apontado pelo app) e reimplante a versão anterior.
A versão anterior não conhece `pagamento_venda` e precisa de
`forma_pagamento` preenchida, que as vendas novas deixam vazia; por isso não basta
reimplantar o app antigo sobre o banco migrado. Vendas feitas depois do deploy
precisam ser relançadas à mão após a restauração.
**Rollback da V7:** também só restaurando o dump. A V7 apaga colunas
(`forma_pagamento`, `forma_pagamento_outro`, `valor_total`) e a versão anterior do
app ainda mapeia `valor_total`: ela não sobe (`validate` falha) sobre o banco
migrado, e recriar as colunas vazias não devolveria a forma das cortesias antigas.
Restaure o dump (`pg_restore --clean` com o app parado, ou num banco novo apontado
pelo app), reimplante a versão anterior e relance à mão as vendas feitas depois do
deploy (o log `evento=venda` lista cada uma com id, valores e pagamento).
PostgreSQL oferece DDL transacional para estas migrações; H2 pode deixar DDL
parcial se houver falha. Para testar novamente no H2 após falha, prefira restaurar
a cópia anterior; não use `repair` para esconder SQL parcialmente aplicado.

Nenhum deploy ou comando de escrita em produção foi executado nesta tarefa.

### Comandos de backup e ensaio

Com as variáveis `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER` e `PGPASSWORD`
configuradas para a origem, sem colocar senhas na linha de comando:

```bash
pg_dump --format=custom --file=caixa-antes-flyway.dump
pg_restore --list caixa-antes-flyway.dump
# STAGING_DATABASE_URL deve apontar para um banco isolado e vazio.
pg_restore --no-owner --no-privileges --dbname="$STAGING_DATABASE_URL" caixa-antes-flyway.dump
```

A listagem verifica a legibilidade do arquivo; a restauração isolada é necessária
para confirmar a recuperação. O backup deve permanecer fora do repositório.
