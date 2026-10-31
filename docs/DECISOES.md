# Decisões

Os porquês que o código não mostra. Uma linha por decisão, com a classe ou a propriedade onde ela mora.

## Railway e implantação

- `server.port=${PORT:8080}`: o Railway define a porta na variável PORT; localmente fica 8080.
- `RAILPACK_JDK_VERSION=21` (Variables do Railway): o Railpack não lê o `system.properties`; a versão do Java do build vem dessa variável.
- `spring.threads.virtual.enabled` fica desligado: o `LimiteLogin` usa `synchronized` e as chamadas ao Mercado Pago seguram conexão do banco; ligar threads virtuais pede revisar os dois antes.
- `server.forward-headers-strategy=native` (prod): o Tomcat só usa o X-Forwarded-For quando a conexão vem da rede privada (o proxy do Railway), então o limite de login conta o IP real de cada pessoa em vez de bloquear todo mundo pelo IP do proxy; cabeçalho vindo direto da internet é ignorado.
- `management.endpoint.health.probes.enabled=false`: o Spring Boot 4 liga as sondas de liveness/readiness por padrão, o que acrescenta `"groups"` ao `/actuator/health` e abre `/actuator/health/liveness` e `/readiness` sem login; o Railway só precisa do status.
- `management.endpoints` / actuator: só `/actuator/health`, sem detalhes e sem login, para o healthcheck do Railway; a página de descoberta `/actuator` fica desligada porque o actuator não passa pelo interceptor de login.
- `RelogioConfig`: o relógio é sempre `America/Sao_Paulo`, porque o contêiner do Railway roda em UTC e as colunas de data são TIMESTAMP sem fuso guardando a hora de Brasília.
- `CaixaApplication.fixarFuso`: a JVM vai para Brasília antes de o Spring subir, para logs e bibliotecas que usam o fuso padrão baterem com o relógio da aplicação.
- `MercadoPagoInicio`: duas linhas fixas no log ao subir ("Mercado Pago: ligado|desligado" e "webhook: assinatura configurada|não configurada"), sem segredo, para conferir no Railway depois do deploy.

## Sessão, login e segurança

- `server.servlet.session.tracking-modes=cookie`: sem isso o Tomcat reescrevia o primeiro redirect do login para `/;jsessionid=...`, que dava 404; também evita vazar o id da sessão em links e no histórico do navegador.
- `server.servlet.session.timeout=12h`: um dia de evento inteiro sem cair a sessão (o padrão do Tomcat é 30 min).
- `server.servlet.session.cookie.same-site=strict` e `secure=true` (prod): o cookie de sessão não vai em pedido vindo de outro site nem por HTTP.
- `app.senha` / `SenhaDeAcesso`: a senha só existe em APP_SENHA, em todos os perfis e sem valor padrão; ausente, vazia ou com menos de 8 caracteres, a aplicação não sobe; a comparação é em tempo constante e o valor nunca vai para log ou mensagem.
- `SenhaInvalidaFailureAnalyzer`: troca a pilha do Spring por uma mensagem curta, para quem sobe o app sem APP_SENHA entender o que falta.
- `LoginController`: o id da sessão é trocado depois do login, para um id obtido antes de autenticar não servir mais.
- `LoginController.sair`: logout só por POST, para um link ou imagem em outra página não deslogar a pessoa.
- `LimiteLogin`: espera progressiva (1, 2, 4, 8, 16 e 30 s) em vez de bloquear no primeiro erro, para não travar o caixa do evento por erro de digitação; 20 erros em 10 minutos bloqueiam o IP por 10 minutos.
- `LimiteLogin`: IPv6 conta pela rede /64, porque quem tem uma /64 troca o fim do endereço à vontade.
- `LimiteLogin`: teto global de 60 erros por minuto somando todos os IPs, contra tentativa distribuída por muitos IPs.
- `LimiteLogin`: no máximo 10.000 IPs guardados, o mais antigo sai, para a memória não crescer sem limite.
- `LimiteLogin`: tentativa durante a espera ou o bloqueio é recusada sem conferir a senha e sem contar como erro.
- `OrigemDoPost`: reforço do SameSite contra formulário de outro site que manda o navegador do caixa registrar ou cancelar venda; compara só o host, porque atrás do proxy do Railway a porta e o esquema que o app vê podem não ser os do navegador.
- `OrigemDoPost`: sem Sec-Fetch-Site nem Origin (navegador antigo, curl), o POST passa.
- `AutenticacaoInterceptor`: chamada do JS sem sessão recebe 401 em JSON, porque um redirect para o login seria lido como resposta da prévia.
- `ErrosDeNegocio.origem`: volta para o Referer só se for do próprio site e só com caminho e busca, para não virar redirecionamento para outro endereço.
- `ErrosDeNegocio`: IllegalArgumentException é erro de programa: a pilha vai para o log e a tela mostra só "Algo deu errado".
- `Auditoria`: grava só depois do commit (o que foi desfeito não aparece), nunca recebe senha e neutraliza quebras de linha e aspas dos valores, para ninguém forjar linhas no log.
- `ExportadorCSV.campo`: texto que começa com = + - @ tab ou CR ganha um apóstrofo, para a planilha não executar o texto do usuário como fórmula.
- `CobrancaPixService`: id vindo de fora vai para o log sem quebras de linha e com no máximo 64 caracteres.

## Mercado Pago e Pix

- `MercadoPagoConfig`: token, segredo do webhook e e-mail do pagador só por variável de ambiente; sem token ou sem e-mail o Pix automático desliga e o caixa usa Pix manual; `toString` nunca mostra os segredos.
- `mercadopago.payer-first-name` / `MP_PAYER_NOME`: no modo teste do Mercado Pago, "APRO" aprova o Pix sozinho; em produção fica vazio. MP_PAYER_FIRST_NAME continua aceito por compatibilidade.
- `MercadoPagoConfig.expiracao`: a documentação exige validade de no mínimo 30 minutos.
- `MercadoPagoPix`: timeout de 5 s e um 429 repetido uma vez depois do Retry-After (limitado), para não travar a tela de venda.
- `MercadoPagoPix.criar`: a referência vai como X-Idempotency-Key e external_reference, para o clique duplo devolver a mesma order em vez de gerar dois Pix.
- `CobrancaPix.atualizar`: só vira PAGA pela resposta do Mercado Pago e só com o valor pago exatamente igual ao da cobrança; pago com valor diferente não confirma.
- `CobrancaPix.atualizar`: refunded e charged_back viram ESTORNADA, porque o dinheiro não ficou com a LAC.
- `CobrancaPix.registrarOrder`: recusa trocar a order quando já há valor pago, para nenhum pagamento ir para uma order esquecida.
- `CobrancaPixService`: nenhuma chamada ao Mercado Pago com a linha travada; primeiro consulta sem transação, depois uma transação curta (REQUIRES_NEW, PESSIMISTIC_WRITE) confere que a order é a mesma e aplica.
- `CobrancaPixService.cancelar`: se o Mercado Pago recusar o cancelamento (409, pagamento em processamento), a cobrança continua PENDENTE e a troca de valor é recusada; só marca CANCELADA com o cancelamento confirmado.
- `CobrancaPixService.consultar`: a tela consulta a cada 3 s, mas o servidor pergunta ao Mercado Pago no máximo uma vez a cada 5 s.
- `CobrancaPixService.gerar`: recusa token que já virou venda.
- `CaixaService`: no POST /venda o servidor confere de novo que a cobrança da parte está PAGA com o mesmo valor; não confia no que a tela diz.
- `CaixaService.cancelarVenda`: cancelar a venda não devolve o Pix do Mercado Pago; a mensagem pede o estorno e o Pix volta para "Pix recebido sem venda".
- `CobrancaPixService.pixSemVenda`: um Pix pago só aparece depois da carência, porque alguém ainda pode estar confirmando a venda na tela.
- `LimpezaDePix`: a cada 5 minutos, cobranças pendentes há mais de 20 minutos são consultadas (podem ter sido pagas) e, se ainda pendentes, canceladas no Mercado Pago.
- `WebhookController`: responde 200 na hora e trabalha em segundo plano, porque o Mercado Pago espera no máximo 22 s.
- `WebhookController`: sempre consulta a order no Mercado Pago (sem cache) em vez de confiar no corpo do aviso, que não é assinado.
- `WebhookController`: o id da order vem só do data.id da URL, que entra na assinatura; sem ele, 400.
- `WebhookController`: order que não é nossa ou cobrança que já saiu de PENDENTE não muda nada, então o mesmo aviso duas vezes é inofensivo (e o "Simular notificação" do painel não quebra nada).
- `AssinaturaWebhook`: HMAC-SHA256 do manifesto comparado em tempo constante; o ts é aceito em segundos ou milissegundos, porque a documentação diz milissegundos e o exemplo dela está em segundos.
- `SegurancaConfig`: o webhook fica fora do login; quem o autentica é a assinatura x-signature.

## Dinheiro e finanças

- `Contas` e `TaxaPagamento.estimar`: arredondamento sempre HALF_EVEN (meio centavo para o par), para não acumular viés para cima.
- `Rateio`: o desconto da venda é rateado pelo maior resto, para a soma dos líquidos dos itens ser exatamente o valor cobrado, sem centavo sobrando ou faltando; empate vai para o primeiro item.
- `Rateio`: a V12 repete o mesmo cálculo em SQL para as vendas antigas.
- `Produto.ESCALA_CUSTO`: o custo tem 4 casas, porque o custo médio ponderado é uma divisão e 2 casas perderiam centavos ao multiplicar pela quantidade.
- `ConsultasFinanceiras.CMV`: custo x quantidade é arredondado ao centavo em cada item, para o resumo, a série e os rankings somarem os mesmos centavos.
- `ItemVenda`: preço e custo do momento da venda ficam congelados no item, para mudanças de preço ou custo não alterarem o histórico.
- `VendaDesconto`: a descrição do desconto fica congelada e promocao_id vira null quando a promoção é apagada, para a venda antiga continuar explicada.
- `TaxaService.definir`: vigência no passado é recusada, porque as vendas já gravaram a taxa da época.
- `CaixaService.alterarDataHora`: recusa data futura e venda cancelada, e recalcula a taxa pela vigente no novo dia.
- `CaixaService.alterarDataHora`: se o minuto não mudou, mantém os segundos originais, para não embaralhar vendas do mesmo minuto.
- `ConsultasFinanceiras.despesas`: despesa ligada a evento conta no dia em que o evento começa; sem evento, na data dela; assim eventos + dia a dia somam o mesmo que o período.
- `DespesaService.total`: na tela de Despesas o total é a soma das linhas listadas (pela data ou pelo vínculo do filtro), por isso é somado da própria lista; as contas financeiras (painel, relatório, comparativo) usam só `ConsultasFinanceiras.despesas`.
- `RankingsFinanceiros.formasDePagamento`: com filtro de categoria a tabela de formas some, porque o pagamento é da venda inteira e não se separa por item.
- `FiltrosDaTelaFinancas`: período personalizado de no máximo 3 anos, para um pedido enorme não travar o servidor.
- `FiltroFinancas.fimConsulta`: um período que ainda não acabou termina 1 µs depois de agora (a precisão do banco), para a venda feita agora entrar.
- `ResumoFinanceiro.comparar`: o período anterior é cortado no mesmo recorte de horas ("hoje até 10:00" contra "ontem até 10:00").
- `Dinheiro.numero`: o CSV usa número sem separador de milhar, para o Excel ler como número.
- `RespostaCsv`: BOM UTF-8 no começo do CSV, para o Excel mostrar os acentos.
- `ExportadorCSV`: separador ';', porque o Excel em pt-BR abre direto.

## Estoque e concorrência

- `ProdutoRepository.baixarSeHouver`: baixa atômica (quantidade >= q numa única instrução), para vendas simultâneas do mesmo produto esperarem a vez em vez de falhar pela versão.
- `EstoqueService.darBaixa`: depois do UPDATE atômico recarrega o produto do banco, porque o objeto em memória ainda tem a quantidade e a versão antigas.
- `Produto.versao`: controle otimista, para duas baixas do mesmo produto não passarem juntas por outros caminhos.
- `CaixaService.cancelarVenda`: a trava da venda serializa cancelamentos; a versão do produto protege o estoque.
- `CaixaController`: cada formulário leva um token novo; reenviar o mesmo devolve a venda existente em vez de criar outra, e a corrida bate no índice único do token.
- `CaixaController`: venda recusada volta para a tela com o mesmo carrinho e o mesmo token, para corrigir sem digitar tudo e sem duplicar.
- `Quantidades`: teto de 9999 por linha, para as contas em centavos e a busca de pacotes terem tamanho conhecido.
- `BuscaDePacotes`: programação dinâmica iterativa, sem recursão, para milhares de unidades não estourarem a pilha.
- `CalculadoraDePreco`: acima do orçamento de estados a venda não é recusada; usa heurística, marca o orçamento como aproximado e avisa no log (o total aproximado nunca fica abaixo do exato).
- `EstoqueService.remover`: produto com vendas ou movimentos é arquivado em vez de apagado, para o histórico não quebrar.
- `MovimentoEstoque`: todo lugar que mexe no estoque grava movimento com sinal, para a soma dos movimentos ser sempre a quantidade.

## Pacotes

- `consulta`: FiltroHistorico, FiltroFinancas e PeriodoDoEvento ficam fora de `repositorio` porque controle, serviço e repositório os usam; assim o controle não depende do repositório.
- `controle.csv`: os CSVs são formato de download (texto + resposta HTTP), por isso ficam junto do controle e não em `util`.
- `modelo.pagamento`: as estratégias recebem `DadosDaParte` (forma, valor, descrição, troco) e não a entidade PagamentoVenda, para o Strategy não depender da persistência.

## Banco, Flyway e consultas

- `h2.version=2.3.232` (pom.xml): o H2 2.4.240, que vem com o Spring Boot 4.1, tem uma regressão: uma CHECK com `IN (...)` criada numa conexão falha com "database has been closed" quando avaliada em outra depois que a primeira fecha (as migrações rodam numa conexão do pool e as vendas em outra). Fica na 2.3.232 até sair uma versão corrigida.
- `spring.jpa.hibernate.ddl-auto=validate`: o Flyway é dono do DDL; o Hibernate só confere o modelo.
- `spring.flyway.locations`: `db/migration` roda em todos os bancos e `db/migration-{vendor}` só no banco dele (V20 e V22 só existem no PostgreSQL).
- Migrações já aplicadas não mudam nem em comentário: o Flyway confere o checksum e o deploy quebra.
- `spring.jpa.open-in-view=false`: as consultas carregam explicitamente o que as telas usam, dentro da transação, em vez de abrir consultas escondidas durante o desenho da página.
- `spring.jpa.properties.hibernate.default_batch_fetch_size=100`: coleções preguiçosas (pagamentos do histórico) em lotes de até 100 vendas, em vez de uma consulta por venda.
- `ConsultaHistorico`: itens e pagamentos não vêm no mesmo JOIN, porque o Hibernate recusa duas listas juntas (MultipleBagFetchException).
- `VendaRepository`: a página do histórico é cortada no banco sem os itens, e os itens da página vêm depois por id, para não paginar em memória.
- `VendaConsultasImpl`: o total não faz JOIN nos itens, para não se multiplicar pela quantidade de itens.
- `ConsultasFinanceiras` e `DespesaRepository`: só entram as cláusulas dos filtros preenchidos (ou `cast(... as LocalDate)`), porque o Postgres recusa `? is null` sem tipo.
- `FiltroHistorico`: a busca sem acento usa `lower(translate(...))`, que funciona no H2 e no Postgres sem extensão.
- `EventoService.salvar` / V22: no Postgres o índice único parcial `ux_evento_aberto` recusa um segundo evento aberto quando dois cliques passam juntos pela validação.
- `PromocaoRepository`: ordem por id, para o desempate entre promoções de mesmo preço ser estável.

## Eventos

- `Evento`: o intervalo é [inicio, fim); uma venda às 18:00 de um evento que vai até 18:00 já não é dele.
- `Evento`: vendas não guardam o evento; ele é calculado pela data e hora da venda.
- `EventoService.fimEfetivo`: o evento aberto vale até agora, ou até o início do próximo evento se ele já começou, para nenhuma venda cair em dois eventos; `ConsultasFinanceiras.DIA_A_DIA` e `VendaSpecs` usam a mesma regra.
- `Despesa`: o evento da despesa é escolhido à mão, porque o material pode ser comprado na véspera.

## Telas e JS

- `spring.web.resources.chain.strategy.content`: estáticos com cache de um ano e o hash do conteúdo na URL (montada pelo `@{...}` do Thymeleaf), para uma versão nova chegar na hora.
- `AvisosNaTela`: os avisos do sininho só são calculados quando a página desenha o topo, uma vez por requisição; JSON, CSV e redirecionamentos não pagam nada.
- `AlertasService`: o id de cada aviso muda quando o conteúdo muda, porque o navegador guarda os ids já vistos para contar os novos.
- `venda.js`: uma consulta do Pix por vez e a prévia anterior cancelada quando outra é pedida, para só a última resposta desenhar.
- `venda.js`: "Usar Pix manual" espera o cancelamento no servidor e fica com o Pix do Mercado Pago se ele já estiver pago.
- `financas.js` e `GraficoSvg`: a segunda série vai listrada, para não depender só de cor.
- `PainelFinancas` e `financas.js`: nenhuma conta de dinheiro no navegador; valores e textos vêm prontos do servidor.
- `estilo.css`: o cartão principal da tela inicial é roxo com texto on-roxo, com contraste AA (6,2:1 no claro e 6,4:1 no escuro).
- `DadosIniciais`: produtos de exemplo só no perfil local; em produção um banco vazio continua vazio.

## Testes

- `ExigeDocker` e `Navegador`: sem Docker ou sem navegador, os testes são pulados no computador e falham no CI (CI=true), para não passarem despercebidos.
- `Navegador`: sem download automático do Playwright (cerca de 1 GB); usa o navegador já instalado ou o Google Chrome.
- `FakeMercadoPago` e `MercadoPagoPixTest`: os testes nunca chamam o Mercado Pago de verdade.
- `MassaFinanceira`: a massa fixa tem os resultados calculados à mão e roda no H2 e no Postgres, porque o H2 aceita SQL que o Postgres recusa.
- `PrintsDoRelatorioTest`: só roda com `-Dprints=true`, porque gera arquivos em docs/prints/.
