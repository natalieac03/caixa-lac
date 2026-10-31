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

Lidas pelo Railway ou pela JVM (não pelas properties do app):

| Variável | Valor | Para quê |
|---|---|---|
| `RAILPACK_JDK_VERSION` | `21` | Fixa o Java 21 no build do Railway: o Railpack não lê o `system.properties`, e o projeto exige Java 21+. |
| `TZ` | `America/Sao_Paulo` | Fuso da máquina. O app já usa o horário de Brasília sozinho (relógio fixo e `TimeZone.setDefault` no `main`); `TZ` deixa logs e ferramentas do contêiner no mesmo horário. |
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

### Fuso horário: atenção ao primeiro deploy desta versão

Até aqui o app em produção rodava com a JVM em UTC. As colunas de data são
`TIMESTAMP` sem fuso, então as vendas foram gravadas com a hora UTC: a consulta
de horas da seção 5 do script mostra o pico de madrugada. Esta versão grava a hora
de Brasília. Para o banco não ficar misturado, **as datas antigas precisam ser
convertidas no mesmo deploy**.

A **V20** (só no Postgres) faz essa conversão no mesmo deploy. Ela converte as
colunas gravadas pelo relógio e não mexe nas digitadas. Por decisão da LAC, as
vendas corrigidas à mão no histórico também são convertidas e ficam 3 h
adiantadas: confira as poucas que foram corrigidas e ajuste na tela. **O relógio
novo e a V20 têm de subir juntos.** Não suba só uma parte, senão as vendas feitas
nesse intervalo seriam deslocadas de novo.

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
| V20 `horarios_em_brasilia` | **Só no PostgreSQL** (`db/migration-postgresql`). Converte de UTC para Brasília os horários gravados pelo relógio (vendas, cancelamentos, movimentos, compras, Pix, despesas e taxas). Não mexe no que foi digitado (eventos do formulário, datas). As vendas corrigidas à mão também são convertidas e ficam 3 h adiantadas (decisão da LAC). |
| V21 `pix_estornado_e_consulta` | Situação ESTORNADA do Pix (refunded ou charged_back no Mercado Pago) e hora da última consulta (`consultada_em`), para a tela não perguntar de novo em menos de 5 s. |
| V22 `evento_aberto_unico` | **Só no PostgreSQL**. Índice único parcial `ux_evento_aberto`: no máximo um evento em andamento (sem fim), mesmo com dois cliques em "Iniciar" ao mesmo tempo. **Para** se já houver mais de um aberto. |

**Quais vão rodar em produção** depende do que o banco já tem. A seção 3 do
`scripts/conferir-producao.sql` mostra a última versão aplicada:

- sem `flyway_schema_history`: baseline + **V2 a V22** (21 migrações);
- na versão N: da **V(N+1) à V22**.

O H2 local não roda a V20: as pastas são `db/migration` (todos os bancos) e
`db/migration-{vendor}` (`spring.flyway.locations`).

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

1. Em *Variables* do serviço: confira as da seção 1, incluindo `TZ=America/Sao_Paulo`. Os segredos são `APP_SENHA`,
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
   - veja a seção 5, "Mercado Pago".

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

## 5. Mercado Pago

Opcional. Sem `MP_ACCESS_TOKEN` (ou sem `MP_PAYER_EMAIL`), o Pix automático fica
desligado e o caixa usa o Pix manual, conferido no app do banco. Se o Mercado
Pago cair no meio do evento, a tela avisa e o botão **Pix manual** continua
funcionando.

### 5.1 Criar a aplicação e as credenciais

1. **Chave Pix:** a conta Mercado Pago da LAC precisa ter uma chave Pix
   cadastrada (app do Mercado Pago → Pix → Minhas chaves). Sem ela a API não
   gera QR.
2. **Aplicação:** em [Suas integrações](https://www.mercadopago.com.br/developers/panel/app)
   → **Criar aplicação** → pagamentos online com **Checkout Transparente**
   (Orders API).
3. **Access Token:** na aplicação, menu **Credenciais**. Use o de teste para
   ensaiar e o de produção no evento (os dois começam com `APP_USR`). Ele vai em
   `MP_ACCESS_TOKEN`, só nas Variables.
4. **Webhook:** **Configuração de Webhooks** → Teste ou Produção → URL
   `https://<seu-app>.up.railway.app/webhooks/mercadopago` → evento **Order
   (Mercado Pago)** → **Salvar**. A **Assinatura secreta** ("revelar") vai em
   `MP_WEBHOOK_SECRET`.
5. No Railway, em Variables: `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` e
   `MP_PAYER_EMAIL`. No deploy seguinte o log mostra "Mercado Pago: ligado" e
   "webhook: assinatura configurada" (sem os valores).

### 5.2 Ensaiar com as credenciais de teste

1. Variables com o Access Token de teste, a assinatura do webhook do modo Teste
   e `MP_PAYER_NOME=APRO`.
2. Faça uma venda com **Gerar Pix**: com `APRO`, o Mercado Pago aprova sozinho e
   a tela mostra "Pix recebido" em poucos segundos.
3. Confira em Suas integrações → aplicação → **Pagamentos de teste**, e no
   histórico e no fechamento do caixa (parte marcada como Mercado Pago).
4. **Simular notificação** no painel manda um aviso assinado com uma order que
   não existe: o caixa responde 200 e só registra no log que ignorou o aviso.
5. Antes do evento, troque para as credenciais e o webhook de produção e **apague
   `MP_PAYER_NOME`**.

### 5.3 No dia do evento

- O QR vale 30 minutos. Se o total mudar depois de gerar, o Pix antigo é
  cancelado e é preciso gerar outro; um Pix já pago não muda de valor.
- Pix pago que não virou venda (tela fechada, venda cancelada) aparece no
  histórico como **Pix recebido sem venda**, com hora, valor e order: lance a
  venda ou estorne no Mercado Pago e marque como resolvido.
- No fechamento, o Pix aparece separado em "Pix pelo Mercado Pago" e "Pix
  manual", para conferir com cada extrato.

## 6. Trocar a senha de acesso

No Railway, serviço do app → **Variables** → edite `APP_SENHA` (mínimo de 8
caracteres) → aplique. O Railway reimplanta o app e, como as sessões ficam na
memória do servidor, **todas as sessões abertas caem**: quem estiver usando o
caixa precisa entrar de novo com a senha nova. Troque fora do horário de vendas
e avise a equipe antes.
