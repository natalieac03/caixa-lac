# Deploy no Railway (com Mercado Pago)

Roteiro para subir o Caixa LAC no Railway sem perder dados: backup, ensaio,
deploy, conferência e, se precisar, rollback. Nada aqui grava segredos no git:
os valores reais ficam só nas **Variables** do serviço no Railway.

## 1. Variáveis de ambiente

Lista completa do que o app lê (`application*.properties` e código). Os
exemplos são **fictícios**.

| Variável | Obrigatória? | Segredo? | Exemplo fictício | Para quê |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | **Sim** em produção (o padrão é `local`, com H2 em arquivo) | Não | `prod` | Liga o perfil de produção (Postgres, cookie seguro, proxy). |
| `APP_SENHA` | **Sim** (em todos os perfis; o app não sobe sem ela) | **Sim** | `troque-por-uma-frase-longa` | Senha de acesso às telas. Mínimo de 8 caracteres. |
| `SPRING_DATASOURCE_URL` | **Sim** em produção | Não (mas não publique) | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` | Endereço JDBC do Postgres do Railway. Começa com `jdbc:postgresql://`. |
| `PGUSER` | **Sim** em produção | Não | `${{Postgres.PGUSER}}` | Usuário do banco. |
| `PGPASSWORD` | **Sim** em produção | **Sim** | `${{Postgres.PGPASSWORD}}` | Senha do banco. |
| `PORT` | Não: o Railway define sozinho | Não | `8080` | Porta HTTP (o healthcheck usa a mesma). Sem ela, 8080. Não crie à mão. |
| `MP_ACCESS_TOKEN` | Não (sem ela o **Mercado Pago fica desligado** e o caixa usa Pix manual) | **Sim** | `APP_USR-0000000000000000-000000-00000000000000000000000000000000-000000000` | Access token da aplicação no Mercado Pago (Orders API). |
| `MP_PAYER_EMAIL` | Só para ligar o Mercado Pago (token sem e-mail = desligado) | Não | `caixa@exemplo.org` | E-mail do pagador exigido pela API do Pix. |
| `MP_WEBHOOK_SECRET` | Não (sem ela os avisos do webhook são **recusados**; a tela ainda confere a cobrança a cada 3 s) | **Sim** | `exemplo-de-assinatura-secreta` | Assinatura secreta do webhook (cabeçalho `x-signature`). |
| `MP_PAYER_NOME` | Não. **Deixe vazia em produção** | Não | `APRO` | Quando existe, vai como `payer.first_name` na criação da order Pix. No modo teste do Mercado Pago, `APRO` faz o Pix ser aprovado sozinho. |
| `MP_PAYER_FIRST_NAME` | Não (nome antigo de `MP_PAYER_NOME`, ainda aceito; `MP_PAYER_NOME` tem preferência) | Não | `APRO` | O mesmo que `MP_PAYER_NOME`. Use o nome novo. |

Lidas pelo Railway (não pelo app):

| Variável | Valor | Para quê |
|---|---|---|
| `RAILWAY_HEALTHCHECK_TIMEOUT_SEC` | `300` | Tempo máximo do healthcheck no deploy. Recomendo criar: vale mesmo sem o `railway.json` (veja a seção 2). |

Ajustes finos, **não precisam** ser definidos (o Spring aceita qualquer
propriedade `mercadopago.*` por variável): `MERCADOPAGO_URL`
(`https://api.mercadopago.com`), `MERCADOPAGO_TIMEOUT` (`5s`),
`MERCADOPAGO_EXPIRACAO` (`PT30M`, o mínimo da API) e
`MERCADOPAGO_ESPERA_MAXIMA_RETRY` (`5s`). `CI=true` só é usado pelos testes.

Ao subir, o log mostra (sem nenhum valor secreto):

```
Mercado Pago: ligado            (ou: desligado)
webhook: assinatura configurada (ou: não configurada)
```

## 2. `railway.json` e o painel do Railway

O `railway.json` na raiz define o deploy:

- healthcheck em `/actuator/health`, com timeout de 300 s (as migrações podem demorar);
- 1 réplica;
- reinício só em caso de falha (`ON_FAILURE`, até 10 tentativas).

O `/actuator/health` responde só `{"status":"UP"}` (sem detalhes) e não pede
login. O healthcheck do Railway chega pelo host `healthcheck.railway.app`, e o
app não filtra host.

> **Atenção: Config as Code está sendo descontinuado pelo Railway.** Pelo
> [changelog de 20/08/2026](https://railway.com/changelog/2026-08-20-railway-anon),
> serviços de **projetos novos** (criados a partir de 28/08/2026) já não usam o
> `railway.json`, e o sistema antigo sai do ar em **01/12/2026**. Depois disso o
> arquivo é ignorado. Para não depender dele, confira as mesmas opções em
> *Service → Settings*: Healthcheck Path `/actuator/health`, Restart Policy
> *On Failure*, 1 réplica, e crie `RAILWAY_HEALTHCHECK_TIMEOUT_SEC=300`.
> O substituto oficial é o
> [Infrastructure as Code](https://docs.railway.com/infrastructure-as-code#migrating-from-config-as-code).

## 3. Migrações Flyway

O Flyway roda sozinho ao subir. Num banco antigo, sem `flyway_schema_history`,
ele faz o *baseline* na V1 e aplica da V2 em diante. Cada migração roda igual no
Postgres e no H2.

| Versão | O que faz |
|---|---|
| V1 `baseline` | Esquema equivalente ao criado pelo Hibernate da versão original (num banco antigo só é marcada, não roda). |
| V2 `validacoes_e_compatibilidade` | Colunas de valor da venda (calculado, desconto, cobrado, motivo, token) e restrições: preço > 0, quantidade ≥ 0, campos obrigatórios. **Para** se houver dado inválido. |
| V3 `itens_com_chave_do_pai` | Troca as tabelas de junção por `item_venda.venda_id` e `promocao_item.promocao_id`. **Para** se houver item órfão. |
| V4 `indice_data_venda` | Índice em `venda(data_hora)`. |
| V5 `cancelamento_de_vendas` | Cancelamento de venda (cancelada, data, motivo) e versão para controle de concorrência. |
| V6 `pagamento_dividido` | Cria `pagamento_venda` e copia a forma e o valor de cada venda antiga para uma linha. **Para** se houver venda sem valor ou com valor negativo. |
| V7 `remove_colunas_legadas` | Copia `valor_total` para `valor_cobrado` e **apaga** `forma_pagamento`, `forma_pagamento_outro` e `valor_total` da venda. |
| V8 `cobranca_pix` | Cobranças Pix do Mercado Pago e origem (MP ou manual) em cada parte do pagamento. |
| V9 `pix_sem_venda` | Registro de "Pix recebido sem venda" resolvido à mão. |
| V10 `custo_e_compras` | Custo unitário no produto e no item vendido, e entradas de estoque por compra. |
| V11 `movimento_estoque` | Livro de movimentos do estoque, com um movimento "saldo inicial" por produto. |
| V12 `rateio_por_item` | Valor bruto, desconto rateado e líquido por item (vendas antigas rateadas e marcadas como retroativas) e `venda_desconto`. **Para** se houver item sem preço unitário. |
| V13 `eventos` | Tabela de eventos (início e fim). |
| V14 `despesas` | Categorias de despesa (6 iniciais) e despesas. |
| V15 `taxas_pagamento` | Taxas por forma com vigência e taxa estimada em cada parte (as antigas ficam "sem taxa registrada"). |
| V16 `indice_item_produto` | Índice em `item_venda(produto_id)` para os produtos parados das finanças. |
| V17 `estoque_minimo` | Limite de estoque baixo opcional por produto. |
| V18 `metas_do_evento` | Metas de receita e de lucro opcionais por evento. |
| V19 `evento_aberto` | Fim do evento opcional (evento em andamento) e índice em `evento(inicio, fim)`. |

**Quais vão rodar em produção** depende do que o banco já tem. A seção 3 do
`scripts/conferir-producao.sql` mostra a última versão aplicada:

- sem `flyway_schema_history`: baseline + **V2 a V19** (18 migrações);
- na versão N: da **V(N+1) à V19**.

No ensaio local com o esquema da versão original, as 18 migrações rodaram em
0,5 s. Num banco maior, a V11 e a V12 percorrem todos os produtos e itens, mas
continuam bem abaixo dos 300 s.

As migrações que **param** (V2, V3, V6, V7, V12) não estragam nada: o Postgres
desfaz a migração inteira, o app não sobe e o deploy falha no healthcheck. A
seção 4 do script (pré-voo) mostra antes se alguma vai parar: tudo deve dar 0.

## 4. Passo a passo

Ferramentas: `psql` e `pg_dump`/`pg_restore` **da mesma versão do Postgres do
Railway ou mais nova**, e Docker para o ensaio. A URL de conexão de fora do
Railway é a variável `DATABASE_PUBLIC_URL` do serviço Postgres (no painel). Use
`export` no terminal; não grave a URL em arquivo do projeto.

### 4.1 Antes: anotar, fazer backup e conferir

1. Anote o **commit que está em produção hoje** (Railway → Deployments → o ativo).
   É para onde o rollback volta.
2. Exporte os CSVs do histórico e do fechamento pela própria tela (cópia legível extra).
3. Faça o backup do banco (fica fora do git: `*.dump` está no `.gitignore`):

   ```bash
   export DATABASE_PUBLIC_URL='postgresql://...'   # copie do painel, não grave em arquivo
   pg_dump --format=custom --no-owner --no-privileges \
     --file="caixa-$(date +%Y%m%d-%H%M).dump" "$DATABASE_PUBLIC_URL"
   pg_restore --list caixa-*.dump | head        # o dump abre e lista as tabelas
   ```

4. Rode a conferência e guarde a saída:

   ```bash
   psql "$DATABASE_PUBLIC_URL" -f scripts/conferir-producao.sql > conferencia-antes.txt
   ```

   O script só lê (transação `READ ONLY`, desfeita no fim). Confira que o pré-voo
   (seção 4) está todo em **0**.

### 4.2 Ensaio local com uma cópia da produção

```bash
docker run -d --name ensaio -e POSTGRES_PASSWORD=ensaio -e POSTGRES_USER=lac -e POSTGRES_DB=caixa \
  -p 127.0.0.1:55432:5432 postgres:16-alpine
pg_restore --no-owner --dbname=postgresql://lac:ensaio@127.0.0.1:55432/caixa caixa-*.dump
./mvnw -B -DskipTests package
SPRING_PROFILES_ACTIVE=prod SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55432/caixa \
  PGUSER=lac PGPASSWORD=ensaio APP_SENHA=uma-senha-de-ensaio PORT=18085 java -jar target/caixa-lac.jar
```

- Abra `http://127.0.0.1:18085/actuator/health`: deve responder `{"status":"UP"}`.
- No log devem aparecer as linhas do Flyway (`Successfully applied ... now at version v19`) e as duas do Mercado Pago.
- Rode a conferência na cópia migrada e compare com a de antes:

  ```bash
  psql postgresql://lac:ensaio@127.0.0.1:55432/caixa -f scripts/conferir-producao.sql > conferencia-ensaio.txt
  ```

  Contagens de produto, venda e item, vendas canceladas e a **soma do cobrado**
  têm de ser iguais. `pagamento_venda` passa a existir (uma linha por venda com
  valor). O Flyway deve estar em 19 com `success = t`.
- Entre com a senha de ensaio e olhe histórico, fechamento e finanças.
- No fim: `docker rm -f ensaio` e apague o dump local quando não precisar mais.

### 4.3 Deploy

1. Em *Variables* do serviço: confira as da seção 1. Os segredos são `APP_SENHA`,
   `PGPASSWORD`, `MP_ACCESS_TOKEN` e `MP_WEBHOOK_SECRET`; use as **Variable
   References** `${{Postgres.*}}` para o banco. Em produção, `MP_PAYER_NOME` (e o antigo `MP_PAYER_FIRST_NAME`) ficam vazias.
2. Em *Settings*: Healthcheck Path `/actuator/health`, `RAILWAY_HEALTHCHECK_TIMEOUT_SEC=300`,
   Restart Policy *On Failure*, 1 réplica (por causa da descontinuação do `railway.json`).
3. Suba o commit (push da `main`, se o serviço está ligado ao GitHub, ou `railway up`).
4. Acompanhe o log do deploy: Flyway, `Mercado Pago: ...`, `webhook: ...`, `Started CaixaApplication`.
   O deploy só fica ativo quando o healthcheck responde.

### 4.4 Conferência depois

1. Rode de novo o script e compare com o de antes:

   ```bash
   psql "$DATABASE_PUBLIC_URL" -f scripts/conferir-producao.sql > conferencia-depois.txt
   diff conferencia-antes.txt conferencia-depois.txt
   ```

   O que pode mudar: `pagamento_venda`, que passa a existir, e as linhas do Flyway.
   Contagens e soma do cobrado iguais.
2. Entre no app, abra o histórico e o fechamento de um dia conhecido e confira os totais.
3. **Mercado Pago:**
   - cadastre o webhook em *Suas integrações → Webhooks*, com a URL
     `https://<seu-domínio>/webhooks/mercadopago` e o evento **Order**;
   - copie a assinatura secreta para `MP_WEBHOOK_SECRET`;
   - o botão **Simular notificação** manda um id de order que não existe: o caixa
     responde 200 e o log mostra "a order ... não é de nenhuma cobrança deste caixa;
     aviso ignorado". Isso confirma a URL e a assinatura (com a assinatura errada
     o log mostra "Webhook do Mercado Pago recusado" e a resposta é 401);
   - faça um Pix de valor baixo de ponta a ponta;
   - veja o README, seção "Pix automático".

### 4.5 Rollback

Algumas migrações mudam o esquema de um jeito que o código antigo não entende
(a V7 apaga colunas). Por isso o rollback é **restaurar o dump e subir o commit
anterior**, nessa ordem. Vendas feitas depois do deploy se perdem: exporte os
CSVs antes.

1. Tire o app do ar para ninguém vender durante a volta. No painel, remova o
   deploy ativo (*Remove*) ou ponha 0 réplicas.
2. Confira que o dump abre (`pg_restore --list caixa-AAAAMMDD-HHMM.dump | head`)
   e só então restaure **o banco inteiro como estava**.

   Não use só `pg_restore --clean`: ele apaga apenas o que está no dump. As
   tabelas criadas pelas migrações (`pagamento_venda`, `evento`,
   `flyway_schema_history`...) ficariam, e as chaves delas impediriam o restore.

   Este passo **apaga tudo** do schema `public` e recoloca o backup:

   ```bash
   psql "$DATABASE_PUBLIC_URL" -v ON_ERROR_STOP=1 -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
   pg_restore --no-owner --no-privileges --exit-on-error \
     --dbname="$DATABASE_PUBLIC_URL" caixa-AAAAMMDD-HHMM.dump
   ```
3. Suba o commit anotado no passo 4.1: Deployments → o deploy anterior → *Redeploy*
   (ou `git revert` na `main` e push).
4. Rode a conferência: tem de bater com `conferencia-antes.txt`.
