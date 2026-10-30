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
  de vendas antigas ficam nulos, preservando o fallback para `valor_total`.
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
- Fechamento não exige tabela nova: agrega vendas válidas por forma de pagamento.
  Cancelar preserva todos os valores originais e só altera a situação/auditoria.

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
caminho da produção e o de um banco vazio a cada `mvn verify`. Sem Docker, esses
testes aparecem como pulados. Diferenças do banco legado que ficam, sem efeito no
`validate`: CHECKs de enum gerados pelo Hibernate (`venda_forma_pagamento_check`,
`promocao_tipo_check`) convivem com os explícitos da V5, e as sequences de
identidade mantêm o nome gerado pelo Postgres.

Os testes e os perfis local/prod usam Flyway e `ddl-auto=validate`. Flyway está
fixado em 10.20.1 e o BOM Jackson em 2.18.2 para manter também o módulo TOML
alinhado; essas dependências estão disponíveis no cache do ambiente de trabalho.

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

Não há downgrade automático. Após V3, rollback do aplicativo antigo exige também
restaurar o backup do banco durante a janela, sem perder vendas feitas depois.
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
