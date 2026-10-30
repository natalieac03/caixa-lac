# Caixa LAC 🦫

Caixa eletrônico da Liga Acadêmica de Computação, em **Java 17 + Spring Boot 3.5**, com
banco **PostgreSQL** e telas web (Thymeleaf). Tem três áreas: **Abrir caixa**
(registrar vendas), **Estoque** (produtos e quantidades) e **Histórico** (todas
as vendas, com filtro e exportação em CSV). A aba **Descontos** gerencia
promoções por quantidade em uma **categoria** (ex.: a partir de 2 unidades de
qualquer bóton, cada uma sai por R$ 5). Produtos são organizados em categorias
com nome padronizado — "BÓTON" e "bóton" são a mesma categoria.

## Como o projeto está organizado

```
src/main/java/br/com/lac/caixa/
├── CaixaApplication.java     liga o servidor
├── modelo/                   o miolo POO (Produto, ItemVenda, Venda, FormaPagamento)
├── pagamento/                padrão Strategy (Pix, Débito, Crédito, Outro)
├── repositorio/              conversa com o Postgres (Spring Data JPA)
├── servico/                  regras: EstoqueService e CaixaService (são Singletons do Spring)
├── controle/                 recebe os cliques das telas
├── util/ExportadorCSV.java   gera os arquivos .csv
└── config/DadosIniciais.java popula alguns produtos na primeira execução

src/main/resources/
├── templates/                as telas (HTML Thymeleaf)
├── static/css/estilo.css     o visual (tema capivara)
└── application*.properties   configuração (local usa H2, prod usa Postgres)
```

Os padrões do projeto de POO continuam visíveis: o **Strategy** de pagamento
está em `pagamento/`, e cada `@Service` é um **Singleton** mantido pelo Spring
(sem precisar do `getInstancia()` escrito à mão).

## Rodar no seu computador

Precisa de **Java 17+**. O Maven vem junto pelo Maven Wrapper.

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Antes, defina uma senha de acesso (mínimo 8 caracteres), de um destes jeitos:

```bash
# Opção 1: variável de ambiente (vale para esta janela do terminal)
export APP_SENHA='uma-senha-sua'          # Windows (PowerShell): $env:APP_SENHA='uma-senha-sua'

# Opção 2: arquivo local, lido só no perfil local e fora do git
cp senha-local.properties.exemplo senha-local.properties   # depois edite o valor
```

Sem isso a aplicação não sobe e avisa: *Defina a variável APP_SENHA (mínimo 8
caracteres)*. Abra **http://localhost:8080**. No modo local o banco é um arquivo
H2 (pasta `./dados/`), e os dados continuam lá entre reinicializações. O console do H2 fica em
`http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./dados/caixa`,
usuário `sa`, senha em branco).

## Subir no Railway (com Postgres que não perde dados)

1. **Suba o código para um repositório no GitHub.**

2. No [Railway](https://railway.app): **New Project → Deploy from GitHub repo**
   e escolha este repositório. O Railway detecta que é Spring Boot/Maven e faz o
   build sozinho.

3. No mesmo projeto, clique em **Add → Database → PostgreSQL**. Isso cria um
   serviço chamado `Postgres`.

4. Abra o serviço do **app**, vá em **Variables** e adicione:

   ```
   SPRING_PROFILES_ACTIVE = prod
   SPRING_DATASOURCE_URL   = jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   PGUSER                  = ${{Postgres.PGUSER}}
   PGPASSWORD              = ${{Postgres.PGPASSWORD}}
   APP_SENHA               = <defina uma senha de acesso>
   ```

   Esses `${{Postgres.XXX}}` são variáveis de referência do Railway — ele
   preenche com os dados reais do banco. (Se o serviço do banco tiver outro
   nome, troque `Postgres` por esse nome.)

5. Em **Settings → Networking** do serviço do app, clique em **Generate Domain**
   para receber a URL pública. Pronto: o caixa está no ar, e os dados ficam
   guardados no Postgres mesmo se o app reiniciar ou você subir uma versão nova.

6. **Healthcheck:** em **Settings → Deploy → Healthcheck Path** do serviço do app,
   coloque `/actuator/health`. O Railway só troca a versão no ar quando esse
   endereço responde `{"status":"UP"}` (o que inclui conseguir falar com o
   banco). É o único endpoint do Actuator ligado, não precisa de login e não
   mostra detalhes.

> As tabelas são versionadas pelo Flyway; o Hibernate usa `ddl-auto=validate`.
> Consulte [migrações e ordem de deploy](docs/MIGRACOES.md) antes de atualizar uma base existente.

## Imagens (logo e caixa)

Coloque seus PNGs (de preferência sem fundo) em `src/main/resources/static/img/`:

- `logo.png` — aparece nas telas iniciais (início e login)
- `caixa.png` — aparece na tela "Abrir caixa"

Enquanto os arquivos não existirem, um emoji 🦫 aparece no lugar. As imagens
ficam dentro de um círculo roxo claro.

## Senha de acesso

A interface exige login com uma senha compartilhada, que **não fica no código**:
existe só na variável `APP_SENHA`. Em produção ela fica em **Variables** do
serviço do app no Railway. Em qualquer perfil, se `APP_SENHA` faltar, estiver
vazia ou tiver menos de 8 caracteres, a aplicação não sobe e o log diz *Defina a
variável APP_SENHA (mínimo 8 caracteres)*. O valor nunca é escrito no log.

**Para trocar a senha:** no Railway, abra o serviço do app → **Variables** →
edite `APP_SENHA` → aplique. O Railway reimplanta o app, e como as sessões ficam
na memória do servidor, **todas as sessões abertas caem** nesse deploy: quem
estiver usando o caixa precisa entrar de novo com a senha nova. Troque fora do
horário de vendas e avise a equipe antes. Os testes usam uma senha própria, em
`src/test/resources/application-test.properties`.

Erro de senha não trava o caixa do evento: cada erro só impõe uma **espera
progressiva** por IP antes da próxima tentativa (1 s, 2 s, 4 s, 8 s, 16 s e daí
30 s no máximo), e a tela de login mostra quantos segundos faltam. Tentar durante
a espera não conta como erro. Só **20 erros em 10 minutos** bloqueiam o IP, por 10
minutos. Um login correto zera o contador daquele IP e renova o ID da sessão. O
controle fica em memória e reinicia junto com a aplicação. O endereço
usado é o IP remoto: no Railway, com `server.forward-headers-strategy=native`, o
Tomcat lê o `X-Forwarded-For` só quando a conexão vem do proxy (rede privada);
cabeçalho vindo direto da internet é ignorado. Sem isso, todo mundo apareceria
com o IP do proxy e cinco erros de qualquer pessoa bloqueariam a todos.
Em produção, o cookie de sessão exige HTTPS e usa `SameSite=Strict`. **Sair** é
um POST (um link em outra página não consegue deslogar ninguém). O perfil local
escuta apenas em `127.0.0.1`, que é o que protege o console do H2: ele é um
servlet à parte e não passa pelo interceptor de login.

## Editar cadastro

- **Produto** (Estoque → Editar): nome, preço e categoria (escolher uma ou criar
  nova), num formulário que abre na própria linha. O preço novo vale **só para
  vendas novas**: cada venda guarda o preço unitário da época, então o histórico
  e o CSV das vendas antigas não mudam. O nome mostrado nas vendas antigas é o
  nome atual do produto.
- **Promoção** (Descontos → Editar): o mesmo formulário da criação, preenchido;
  muda preço e quantidades por categoria. O tipo não muda (crie outra promoção).
- **Categoria** (Estoque → Categorias → Renomear): recusa se outra categoria já
  tiver o mesmo nome padronizado ("BÓTON" = "bóton").
- Preço maior que zero e nome não vazio são conferidos no servidor.

## Regras de preço e promoção

- A calculadora Java é a única fonte dos valores. A tela consulta
  `POST /venda/previa` e apresenta o orçamento retornado, sem regras de promoção
  em JavaScript. Produtos sem categoria mantêm o preço normal.
- **Regra única: o cliente paga o menor total possível, e cada unidade entra em
  no máximo uma promoção.** `A_PARTIR_DE` e `PACOTE` não se somam na mesma
  unidade; a calculadora testa todas as combinações e fica com a mais barata.
- `A_PARTIR_DE`: os mínimos são contados por categoria no carrinho (em combos de
  várias categorias, todas precisam atingir seus mínimos). Quando usada, todas
  as unidades das categorias envolvidas saem pelo preço promocional, só nos
  produtos em que ele é mais barato que o normal; essas unidades não entram em
  pacote. Com duas na mesma categoria, vale a de menor preço.
- `PACOTE`: as unidades que não usaram `A_PARTIR_DE` podem formar pacotes; cada
  pacote leva as unidades mais caras que restam na categoria (é o que mais
  economiza). O que sobra sai no preço normal.
- Exemplos, bóton de R$ 6,00, "a partir de 2 por R$ 5,00" e pacote "3 por R$ 12,00":
  - 3 bótons: 3 × 5 = 15,00 ou o pacote = 12,00 → **R$ 12,00** (só o pacote);
  - 4 bótons: 4 × 5 = 20,00 ou 12 + 6 = 18,00 → **R$ 18,00**;
  - 6 bótons: 6 × 5 = 30,00 ou 2 pacotes = 24,00 → **R$ 24,00**;
  - se a promoção fosse "a partir de 2 por R$ 4,00", 4 bótons: 4 × 4 = **R$ 16,00**.
- Outro exemplo: 4 bótons de R$ 2,00 com pacotes "3 por R$ 4,00" e "2 por R$ 2,80"
  custam R$ 5,60 (dois pacotes de 2), não R$ 6,00.
- Empate entre combinações: vale a que usa menos `A_PARTIR_DE` (e as de menor id);
  entre pacotes de mesma economia, o de menor id. O resultado não depende da
  ordem em que as promoções foram cadastradas.
- Carrinho grande: a busca é exata enquanto cabe no orçamento de trabalho (3
  milhões de estados; até 12 `A_PARTIR_DE` ligadas a pacotes). `A_PARTIR_DE` que
  não compartilha categoria com nenhum pacote é aplicada direto, e subconjuntos
  que deixam as mesmas unidades reaproveitam a busca. Acima do orçamento a venda
  **não é recusada**: os pacotes saem da melhor de duas heurísticas gulosas, o
  orçamento é marcado como aproximado (a tela avisa) e o log registra um aviso.
  Medido: 6 categorias × 10 unidades + 6 `A_PARTIR_DE` em ~40 ms; 7 × 7 em ~30 ms.
- O orçamento inclui itens com preço unitário aplicado, economias descritas e
  total. As economias de `A_PARTIR_DE` já estão refletidas nos preços dos itens.
- O campo **Valor ajustado** é opcional. Vazio, cobra o total calculado; se for
  diferente, exige motivo. O valor pode ser zero (cortesia) e nunca negativo. Aceita
  valores como `1.234,56`. A venda guarda total calculado após promoções,
  desconto das promoções, valor cobrado e motivo do ajuste. O ajuste manual
  fica separado do desconto promocional. Vendas antigas exibem o total legado.
- Cada formulário tem um UUID único. Reenviar o mesmo token volta ao histórico,
  sem criar outra venda nem baixar o estoque novamente. O estoque usa versão
  otimista e rejeita baixas que ultrapassem o disponível.

## Pagamento dividido e troco

- **Caso comum, um toque:** escolha a forma (Pix, Débito, Crédito, Dinheiro ou
  Outro) e confirme; ela paga o total. Em **Outro**, diga qual (ex.: vale). Em
  **Dinheiro**, o campo "Recebido" é opcional e mostra o troco na hora.
- **Dividir pagamento** abre linhas forma + valor (adicione ou remova linhas). A
  tela mostra ao vivo "Falta R$ X" ou "Sobra R$ X", e **Confirmar** só habilita
  quando a diferença é zero. Linha de dinheiro tem "Recebido" e mostra o troco.
- O servidor confere tudo de novo: a soma das partes tem que ser **exatamente**
  o valor cobrado, ao centavo; cada parte maior que zero; Outro com descrição;
  recebido só em dinheiro e nunca menor que a parte (troco = recebido − valor).
  No máximo 10 partes.
- **Cortesia** (valor ajustado zero, com motivo obrigatório) não tem pagamento.
- A confirmação sai uma parte por linha, pelo Strategy de pagamento:
  *"Recebido R$ 10,00 via Pix + R$ 8,00 em dinheiro (troco R$ 2,00)."*
- No **histórico**, a coluna Pagamento mostra "Pix R$ 10,00 + Dinheiro R$ 8,00
  (troco R$ 2,00)". O filtro por forma traz a venda se **qualquer** parte usou
  aquela forma. O **CSV** tem uma coluna por forma (Pix, Débito, Crédito,
  Dinheiro, Outro) com o valor pago nela, e a coluna "Descrição do Outro".
- Vendas anteriores ao pagamento dividido viraram uma parte só, com a forma e o
  valor que tinham (migração V6); as de valor zero viraram cortesias.

## Verificação e dados

Execute a suíte normal com `./mvnw -B verify` (Java 17). Os testes HTTP usam um H2
em memória, separado do banco local. Com Docker disponível, `PostgresLegadoTest`
e `PostgresBancoNovoTest` sobem um PostgreSQL 16 (Testcontainers) e repetem a
migração da produção e a de um banco vazio. No computador, sem Docker, eles são
pulados; no CI (`CI=true`) falta de Docker é erro. As colunas novas de venda aceitam `null`
para preservar os registros antigos. O Flyway aplica as migrações antes de
`ddl-auto=validate`; veja [o roteiro de produção](docs/MIGRACOES.md).

O **ID** da venda é o número da compra. Os CSVs usam ponto e vírgula, BOM UTF-8
e valores monetários com vírgula decimal, sem separador de milhar (`1234,56`);
nas telas e mensagens o valor aparece como `R$ 1.234,56`. Os PNGs mantêm
transparência e têm largura máxima de 512 pixels. CSS e imagens vão com
`Cache-Control` de um ano; a URL leva o hash do conteúdo
(`/css/estilo-<hash>.css`), então uma versão nova chega ao navegador na hora.

## Histórico

O histórico filtra no banco (Specification: só entra o filtro preenchido) por forma de pagamento, intervalo de dias (inclusive)
e trecho literal do nome do produto, sem diferenciar maiúsculas de minúsculas.
Cada página tem até 50 vendas, ordenadas por data e ID decrescentes. Os
indicadores e o CSV usam todo o filtro, sem limitar à página visível. A carga dos
itens é explícita; `spring.jpa.open-in-view=false` em todos os perfis.


## Cancelamento e fechamento

No histórico, informe o motivo e confirme **Cancelar venda**. A venda permanece
riscada, com data e motivo; cada item volta ao estoque uma única vez, inclusive
quando o produto foi arquivado. Não há exclusão nem estorno de pagamento externo:
o operador precisa cuidar do reembolso pelo meio utilizado. Canceladas ficam
fora dos indicadores, do fechamento e do CSV padrão.

Em `/fechamento`, escolha o intervalo de dias (ambos inclusivos); sem datas, a
tela abre o dia atual. O total por forma vem da **soma das partes de pagamento**
(uma venda dividida Pix + Dinheiro soma cada parte na sua forma; a coluna Vendas
conta a venda nas duas). O relatório mostra também o total de vendas e o total
cobrado, descontos promocionais, ajustes manuais, **vendas com pagamento
dividido** e cortesias. A soma das formas tem que bater com o total cobrado; se
não bater, a tela mostra um alerta vermelho (não feche o caixa por ela e confira
as vendas). **OUTRO** é agrupado como uma única forma, independentemente da
descrição. Ajuste é `cobrado -
calculado`, com sinal; a soma pode compensar aumentos e reduções. Vendas antigas
(anteriores ao cálculo no servidor) têm valor calculado igual ao cobrado desde a
V7 e contribuem com zero em ajustes; sem desconto registrado, com zero em descontos.
Os CSVs incluem o intervalo consultado e seguem a mesma regra de exclusão de
canceladas. As datas usam o horário local do aplicativo, como nas vendas atuais.

Veja [as mudanças de esquema e o roteiro de produção](docs/MIGRACOES.md),
incluindo backup e interrupção das instâncias antigas antes da migração V3.


## Auditoria

O logger `auditoria` escreve uma linha por venda, ajuste de valor, correção de
data, cancelamento e mudança de cadastro (produto, estoque, promoção, categoria),
no formato `evento=venda id=12 ip=203.0.113.9 calculado=18.00 cobrado=15.00 ...`,
com os valores de antes e depois e o IP de quem fez. A linha só é escrita depois
que a mudança foi gravada (o que foi desfeito não aparece). Senha nunca vai para
o log. No Railway, filtre os logs do serviço por `evento=`.

## CI e Maven Wrapper

O GitHub Actions executa `./mvnw -B verify` com Java 17 em pushes e pull requests
para `main`, sem banco externo nem segredos de produção. A suíte integra Flyway,
JPA e HTTP usando H2 em memória, e PostgreSQL via Testcontainers (o runner do
GitHub tem Docker); o H2 local em arquivo é preservado. No CI os testes com
PostgreSQL **não podem ser pulados**: com `CI=true`, falta de Docker é erro
(`ExigeDocker`), e um passo final lê os relatórios do surefire e falha se esses
testes não rodaram; o número de testes com PostgreSQL real aparece no resumo do
job.

**Testes de sistema (Playwright):** `sistema/VendaNoNavegadorTest` abre a tela
de venda num Chromium e faz uma venda dividida Pix + Dinheiro com troco do
início ao fim, e confere o aviso de sessão expirada. Eles não baixam navegador
sozinhos: usam o Chromium instalado pelo Playwright ou o Google Chrome da
máquina; sem nenhum dos dois são pulados (no CI, com `CI=true`, é erro). O CI
instala o Chromium antes do `verify` e confere no fim que eles rodaram.

**Estilo:** o `verify` também roda o Spotless (palantir-java-format, sem imports
com `*` nem sem uso). Se o build reclamar de formatação, rode
`./mvnw spotless:apply` e faça o commit do resultado.

**Cobertura:** o `verify` gera o relatório do JaCoCo em
`target/site/jacoco/index.html` (sem piso mínimo); no CI ele fica como artefato
`cobertura-jacoco` do job.

O Maven Wrapper oficial 3.3.4 fixa Maven 3.9.9: `./mvnw -B verify` no
Linux/macOS ou `mvnw.cmd -B verify` no Windows. A primeira execução baixa a
distribuição Maven se ela ainda não estiver no cache.
