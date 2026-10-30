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
   ```

   Esses `${{Postgres.XXX}}` são variáveis de referência do Railway — ele
   preenche com os dados reais do banco. (Se o serviço do banco tiver outro
   nome, troque `Postgres` por esse nome.)

5. Em **Settings → Networking** do serviço do app, clique em **Generate Domain**
   para receber a URL pública. Pronto: o caixa está no ar, e os dados ficam
   guardados no Postgres mesmo se o app reiniciar ou você subir uma versão nova.

> As tabelas são criadas automaticamente na primeira execução
> (`spring.jpa.hibernate.ddl-auto=update`).

## Imagens (logo e caixa)

Coloque seus PNGs (de preferência sem fundo) em `src/main/resources/static/img/`:

- `logo.png` — aparece nas telas iniciais (início e login)
- `caixa.png` — aparece na tela "Abrir caixa"

Enquanto os arquivos não existirem, um emoji 🦫 aparece no lugar. As imagens
ficam dentro de um círculo roxo claro.

## Senha de acesso

A interface é protegida por uma senha única (compartilhada). A senha padrão é
`191103`. Ao subir no Railway, dá pra trocá-la sem mexer no código: adicione a
variável `APP_SENHA` com o novo valor nas *Variables* do serviço do app.

> É uma proteção simples para uso interno (uma senha só, sem usuários
> separados). Para algo mais robusto, dá para evoluir para Spring Security com
> contas individuais depois.

## Notas

- O app não tem login — é pensado pra uso interno da LAC. Dá pra adicionar
  autenticação depois, se precisar.
- O **ID** da venda é o número da compra, gerado automaticamente.
- O **valor total** é somado sozinho, mas dá pra ajustar à mão antes de confirmar.
