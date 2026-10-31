# Caixa LAC

Sistema de caixa da **Liga Acadêmica de Computação (LAC)** para vender produtos
(bótons, adesivos, camisetas, canecas...) em feiras, encontros e no dia a dia.
Roda no navegador (computador, tablet ou celular), registra cada venda com o
pagamento usado, controla o estoque e mostra as finanças do grupo: receita,
custo, despesas, lucro e o desempenho de cada evento.

Feito em **Java 21 + Spring Boot 4.1**, com telas em Thymeleaf e banco
PostgreSQL em produção (Railway). Os detalhes técnicos estão em
**[docs/ARQUITETURA.md](docs/ARQUITETURA.md)**.

## O que o caixa faz

**Vender**
- Tela de venda com busca de produtos, quantidades e o total calculado na hora,
  já com as promoções aplicadas (o cliente sempre paga o menor total possível).
- Pagamento em Pix, débito, crédito, dinheiro (com troco) ou "outro" (vale,
  permuta), e **pagamento dividido** entre várias formas.
- **Pix automático pelo Mercado Pago** (opcional): a tela mostra o QR Code e
  confirma sozinha quando o pagamento cai. Sem Mercado Pago, o Pix é conferido
  à mão no app do banco.
- Ajuste manual de valor (com motivo) e cortesia.

**Estoque e promoções**
- Produtos organizados em categorias, com preço, quantidade, custo médio
  (calculado a partir das compras) e limite de estoque baixo.
- Entrada por compra, saídas e um livro de movimentos que explica cada mudança.
- Promoções por categoria: "a partir de N unidades, cada uma sai por R$ X" e
  pacotes ("3 por R$ 12", combos entre categorias).

**Acompanhar**
- **Histórico** de vendas com filtros (dia, forma, produto, evento), correção de
  data, cancelamento com motivo e exportação em CSV.
- **Fechamento do caixa** por dia ou por evento, com o total de cada forma de
  pagamento (e o Pix separado entre Mercado Pago e manual) para conferir com os
  extratos.
- **Eventos**: "Iniciar evento" com um toque, relatório do evento para imprimir
  e comparativo entre eventos. O que acontece fora dos eventos é o "dia a dia".
- **Finanças**: receita, CMV, margem, taxas das maquininhas, despesas e lucro;
  gráficos, rankings de produtos e categorias, curva ABC, mapa de calor das
  vendas, giro de estoque e metas por evento. Todas as tabelas exportam CSV.
- **Despesas** do grupo (material, transporte...) e **taxas** de cada forma de
  pagamento, com vigência.
- **Avisos** (o sininho no topo): estoque acabando, produto sem custo, ajustes
  do dia, fechamento que não bate e evento esquecido aberto.

**Proteger**
- Acesso com uma senha única do grupo, que fica só em variável de ambiente.
- Freio contra tentativas de senha, sessão de um dia de evento, proteção contra
  formulários de outros sites e log de auditoria de tudo o que muda dinheiro ou
  estoque.

## Tecnologias

| Parte | O que usa |
|---|---|
| Linguagem e framework | Java 21, Spring Boot 4.1 (Spring MVC, Data JPA, Validation, Actuator) |
| Telas | Thymeleaf, CSS próprio, JavaScript sem framework, Chart.js para os gráficos |
| Banco | PostgreSQL em produção, H2 em arquivo no computador, migrações com Flyway |
| Pagamentos | API de Orders do Mercado Pago (Pix), com webhook assinado |
| Testes | JUnit 5, MockMvc, Testcontainers (PostgreSQL), Playwright (navegador), ArchUnit |
| Hospedagem | Railway, com healthcheck em `/actuator/health` |

## Rodar no computador

Precisa de **Java 21+**. O Maven vem junto (Maven Wrapper).

1. Defina a senha de acesso (mínimo de 8 caracteres), de um destes jeitos:

   ```bash
   # variável de ambiente (vale para esta janela do terminal)
   export APP_SENHA='uma-senha-sua'          # PowerShell: $env:APP_SENHA='uma-senha-sua'

   # ou um arquivo local, fora do git
   cp senha-local.properties.exemplo senha-local.properties   # depois edite o valor
   ```

2. Suba a aplicação:

   ```bash
   ./mvnw spring-boot:run                    # Windows: mvnw.cmd spring-boot:run
   ```

3. Abra **http://localhost:8080** e entre com a senha.

No computador o banco é um arquivo H2 na pasta `./dados/` (os dados ficam entre
uma execução e outra) e alguns produtos de exemplo são criados na primeira vez.
O console do H2 fica em `http://localhost:8080/h2-console` (JDBC URL
`jdbc:h2:file:./dados/caixa`, usuário `sa`, senha em branco). Sem `APP_SENHA`,
a aplicação não sobe e avisa o que falta.

**Imagens:** coloque `logo.png` (telas de início e login) e `caixa.png` (tela
"Abrir caixa") em `src/main/resources/static/img/`. Sem elas, aparece um 🦫.

## Testes

```bash
./mvnw -B verify
```

Roda os testes de unidade, de tela (MockMvc), contra PostgreSQL real
(Testcontainers, precisa de Docker) e no navegador (Playwright, usa o Chromium do
Playwright ou o Google Chrome da máquina), além do Spotless (formatação), das
regras de arquitetura e do relatório de cobertura em
`target/site/jacoco/index.html`. Sem Docker ou sem navegador, esses testes são
pulados no computador; no CI (GitHub Actions) a falta deles é erro. Se o build
reclamar de formatação, rode `./mvnw spotless:apply`.

## Colocar no ar

O caixa roda no **Railway** com um banco PostgreSQL do próprio Railway. O roteiro
completo (variáveis, backup, migrações, deploy, conferência e rollback) está em
**[docs/DEPLOY.md](docs/DEPLOY.md)**. Em resumo, o serviço do app precisa de
`SPRING_PROFILES_ACTIVE=prod`, do endereço e do usuário do banco, de `APP_SENHA`,
de `RAILPACK_JDK_VERSION=21` e de `TZ=America/Sao_Paulo`. O Pix automático liga
com as variáveis do Mercado Pago (`MP_ACCESS_TOKEN`, `MP_PAYER_EMAIL`,
`MP_WEBHOOK_SECRET`); sem elas, o caixa segue com Pix manual.

## Documentação

| Documento | Para quê |
|---|---|
| [docs/ARQUITETURA.md](docs/ARQUITETURA.md) | Como o sistema é construído: stack, camadas, domínio, fluxos, segurança, testes e as regras de negócio em detalhe |
| [docs/DEPLOY.md](docs/DEPLOY.md) | Deploy no Railway, Mercado Pago, troca de senha, backup e rollback |
| [docs/MIGRACOES.md](docs/MIGRACOES.md) | Mudanças de esquema e o roteiro para migrar um banco existente |
| [docs/DECISOES.md](docs/DECISOES.md) | Os porquês das decisões que o código não mostra |

## Bibliotecas de terceiros embutidas

| Biblioteca | Versão | Arquivo | Licença |
|---|---|---|---|
| [Chart.js](https://www.chartjs.org/) | 4.4.1 (fixa) | `src/main/resources/static/js/vendor/chart-4.4.1.umd.js` | MIT (`chart-4.4.1.LICENSE.md` ao lado) |

O Chart.js fica dentro do projeto (sem CDN) para a tela funcionar com internet
ruim no evento. Para atualizar, troque o arquivo e o nome com a versão nova, e o
`<script>` em `templates/financas.html`.
