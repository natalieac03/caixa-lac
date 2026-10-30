# Caixa LAC 🦫

Caixa eletrônico da Liga Acadêmica de Computação, em **Java + Spring Boot**, com
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

Precisa de **Java 17+** e **Maven** instalados.

```bash
mvn spring-boot:run
```

Abra **http://localhost:8080**. No modo local o banco é um arquivo H2 (pasta
`./dados/`), então não precisa configurar nada — é só rodar, e os dados
continuam lá entre reinicializações. O console do H2 fica em
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

> As tabelas são versionadas pelo Flyway; o Hibernate usa `ddl-auto=validate`.
> Consulte [migrações e ordem de deploy](docs/MIGRACOES.md) antes de atualizar uma base existente.

## Imagens (logo e caixa)

Coloque seus PNGs (de preferência sem fundo) em `src/main/resources/static/img/`:

- `logo.png` — aparece nas telas iniciais (início e login)
- `caixa.png` — aparece na tela "Abrir caixa"

Enquanto os arquivos não existirem, um emoji 🦫 aparece no lugar. As imagens
ficam dentro de um círculo roxo claro.

## Senha de acesso

A interface exige login com senha compartilhada. Em produção, `APP_SENHA` é
obrigatória: a aplicação falha na inicialização se a variável faltar. No perfil
local existe um padrão definido apenas em `application-local.properties`;
`APP_SENHA` também permite substituí-lo nesse perfil.

Após cinco erros por IP em uma janela móvel de dez minutos, o IP fica bloqueado
por dez minutos. Um login correto limpa as falhas anteriores e renova o ID da
sessão. O controle fica em memória e reinicia junto com a aplicação. O endereço
usado é o IP remoto informado pelo servidor, sem confiar diretamente em
cabeçalhos enviados pelo cliente. Em produção, o cookie de sessão exige HTTPS e
usa `SameSite=Strict`. O perfil local escuta apenas em `127.0.0.1`.

## Regras de preço e promoção

- A calculadora Java é a única fonte dos valores. A tela consulta
  `POST /venda/previa` e apresenta o orçamento retornado, sem regras de promoção
  em JavaScript. Produtos sem categoria mantêm o preço normal.
- `A_PARTIR_DE`: os mínimos são contados por categoria no carrinho. Em combos
  de várias categorias, todas precisam atingir seus mínimos. O preço se aplica
  a todas as unidades das categorias envolvidas, somente quando reduz o preço
  normal de cada produto. Entre promoções elegíveis, vence o menor preço por
  categoria; empates são processados pelo menor ID.
- `PACOTE`: uma busca exata com memoização escolhe a combinação com maior
  economia, incluindo a opção de não usar pacotes. Unidades fora dos pacotes
  conservam seu preço unitário aplicado. Por exemplo, quatro bótons de R$ 2,00,
  com pacotes de três por R$ 4,00 e dois por R$ 2,80, custam R$ 5,60.
- **A confirmar com a LAC:** os itens mais caros de cada categoria entram
  primeiro no pacote; `A_PARTIR_DE` e `PACOTE` podem se somar. A busca de pacotes
  usa os preços já reduzidos por `A_PARTIR_DE`.
- O orçamento inclui itens com preço unitário aplicado, economias descritas e
  total. As economias de `A_PARTIR_DE` já estão refletidas nos preços dos itens.
- O campo **Valor ajustado** é opcional. Vazio, cobra o total calculado; se for
  diferente, exige motivo. O valor pode ser zero e nunca negativo. Aceita
  valores como `1.234,56`. A venda guarda total calculado após promoções,
  desconto das promoções, valor cobrado e motivo do ajuste. O ajuste manual
  fica separado do desconto promocional. Vendas antigas exibem o total legado.
- Cada formulário tem um UUID único. Reenviar o mesmo token volta ao histórico,
  sem criar outra venda nem baixar o estoque novamente. O estoque usa versão
  otimista e rejeita baixas que ultrapassem o disponível.

## Verificação e dados

Execute a suíte normal com `mvn -B verify` (Java 17). Os testes HTTP usam um H2
em memória, separado do banco local. As colunas novas de venda aceitam `null`
para preservar os registros antigos. O Flyway aplica as migrações antes de
`ddl-auto=validate`; veja [o roteiro de produção](docs/MIGRACOES.md).

O **ID** da venda é o número da compra. Os CSVs usam ponto e vírgula, BOM UTF-8
e valores monetários com vírgula decimal. Os PNGs mantêm transparência e têm
largura máxima de 512 pixels.

## Histórico

O histórico filtra no banco por forma de pagamento, intervalo de dias (inclusive)
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
tela abre o dia atual. O relatório mostra quantidade e total cobrado por forma
de pagamento, descontos promocionais e ajustes manuais. **OUTRO** é agrupado
como uma única forma, independentemente da descrição. Ajuste é `cobrado -
calculado`, com sinal; a soma pode compensar aumentos e reduções. Vendas antigas
usam `valor_total` e contribuem com zero em descontos/ajustes não registrados.
Os CSVs incluem o intervalo consultado e seguem a mesma regra de exclusão de
canceladas. As datas usam o horário local do aplicativo, como nas vendas atuais.

Veja [as mudanças de esquema e o roteiro de produção](docs/MIGRACOES.md),
incluindo backup e interrupção das instâncias antigas antes da migração V3.


## CI e Maven Wrapper

O GitHub Actions executa `mvn -B verify` com Java 17 em pushes e pull requests
para `main`, sem banco externo nem segredos de produção. A suíte integra Flyway,
JPA e HTTP usando H2 em memória; o H2 local em arquivo é preservado.

O Maven Wrapper oficial 3.3.4 fixa Maven 3.9.9. Para usá-lo, execute
`./mvnw -B verify` no Linux/macOS ou `mvnw.cmd -B verify` no Windows. A primeira
execução precisa baixar a distribuição Maven se ela ainda não estiver no cache.
Nesta tarefa as verificações foram executadas somente com `mvn -B verify`.
