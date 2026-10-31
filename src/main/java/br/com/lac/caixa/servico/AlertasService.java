package br.com.lac.caixa.servico;

import br.com.lac.caixa.consulta.FiltroFinancas;
import br.com.lac.caixa.consulta.PeriodoDoEvento;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.EventoRepository;
import br.com.lac.caixa.repositorio.PixEstornadoDeVenda;
import br.com.lac.caixa.servico.financas.EstoqueFinanceiro;
import br.com.lac.caixa.servico.financas.Financas.LinhaAjuste;
import br.com.lac.caixa.servico.financas.Financas.LinhaGiro;
import br.com.lac.caixa.servico.financas.OcorrenciasFinanceiras;
import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Os avisos do sininho: estoque acabando, sem custo, ajustes, fechamento e evento esquecido. */
@Service
@Transactional(readOnly = true)
public class AlertasService {
    private final EstoqueService estoque;
    private final EventoRepository eventos;
    private final EstoqueFinanceiro estoqueFinanceiro;
    private final OcorrenciasFinanceiras ocorrencias;
    private final FechamentoService fechamento;
    private final Clock relogio;
    private final EventoService servicoDeEventos;
    private final CobrancaPixService cobrancasPix;

    public AlertasService(
            EstoqueService estoque,
            EventoRepository eventos,
            EstoqueFinanceiro estoqueFinanceiro,
            OcorrenciasFinanceiras ocorrencias,
            FechamentoService fechamento,
            Clock relogio,
            EventoService servicoDeEventos,
            CobrancaPixService cobrancasPix) {
        this.estoque = estoque;
        this.eventos = eventos;
        this.estoqueFinanceiro = estoqueFinanceiro;
        this.ocorrencias = ocorrencias;
        this.fechamento = fechamento;
        this.relogio = relogio;
        this.servicoDeEventos = servicoDeEventos;
        this.cobrancasPix = cobrancasPix;
    }

    public record EstoqueAcabando(Long produtoId, String nome, int estoque, String motivo) {}

    public record SemCusto(Long produtoId, String nome) {}

    public record EventoEsquecido(Long eventoId, String nome, LocalDateTime desde) {}

    public static final long HORAS_EVENTO_ABERTO = 12;

    public record Aviso(String id, String texto, String link, String linkTexto) {}

    public record Secao(String id, String titulo, List<Aviso> avisos) {}

    public record Alertas(
            List<EstoqueAcabando> estoqueAcabando,
            List<SemCusto> semCusto,
            List<LinhaAjuste> ajustesHoje,
            boolean fechamentoNaoBate,
            BigDecimal totalHoje,
            BigDecimal formasHoje,
            LocalDate dia,
            List<EventoEsquecido> eventosAbertos,
            List<PixEstornadoDeVenda> pixEstornados) {

        public boolean isTemAlgo() {
            return total() > 0;
        }

        public int total() {
            return secoes().stream().mapToInt(x -> x.avisos().size()).sum();
        }

        public List<Secao> secoes() {
            List<Secao> secoes = new ArrayList<>();
            if (!estoqueAcabando.isEmpty()) {
                secoes.add(new Secao(
                        "alerta-estoque",
                        "Estoque acabando",
                        estoqueAcabando.stream()
                                .map(a -> new Aviso(
                                        "estoque-" + a.produtoId() + "-" + a.estoque(),
                                        a.nome() + ": " + a.estoque() + " un. (" + a.motivo() + ")",
                                        "/estoque#entrada-estoque",
                                        "Registrar entrada de estoque"))
                                .toList()));
            }
            if (!semCusto.isEmpty()) {
                secoes.add(new Secao(
                        "alerta-sem-custo",
                        "Produtos sem custo (o lucro sai parcial)",
                        semCusto.stream()
                                .map(p -> new Aviso(
                                        "sem-custo-" + p.produtoId(),
                                        p.nome(),
                                        "/estoque?semCusto=true",
                                        "Informar o custo"))
                                .toList()));
            }
            if (!ajustesHoje.isEmpty()) {
                secoes.add(new Secao(
                        "alerta-ajustes",
                        "Vendas de hoje com ajuste manual",
                        ajustesHoje.stream()
                                .map(a -> new Aviso(
                                        "ajuste-" + a.vendaId() + "-"
                                                + a.cobrado().toPlainString(),
                                        "#" + a.vendaId() + " às "
                                                + a.dataHora().format(HORA) + ": "
                                                + Dinheiro.reais(a.calculado()) + " → " + Dinheiro.reais(a.cobrado())
                                                + (a.motivo() == null ? "" : " (" + a.motivo() + ")"),
                                        "/historico?dia=" + dia,
                                        "Ver no histórico"))
                                .toList()));
            }
            if (fechamentoNaoBate) {
                secoes.add(new Secao(
                        "alerta-fechamento",
                        "Fechamento de hoje não bate",
                        List.of(new Aviso(
                                "fechamento-" + dia + "-" + totalHoje.toPlainString() + "-"
                                        + formasHoje.toPlainString(),
                                "A soma das formas de pagamento (" + Dinheiro.reais(formasHoje)
                                        + ") é diferente do total cobrado (" + Dinheiro.reais(totalHoje) + ").",
                                "/fechamento",
                                "Conferir o fechamento"))));
            }
            if (!eventosAbertos.isEmpty()) {
                secoes.add(new Secao(
                        "alerta-evento-aberto",
                        "Evento aberto há mais de " + HORAS_EVENTO_ABERTO + " horas",
                        eventosAbertos.stream()
                                .map(e -> new Aviso(
                                        "evento-aberto-" + e.eventoId(),
                                        "\"" + e.nome() + "\" está em andamento desde "
                                                + e.desde().format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"))
                                                + ". Esqueceu de encerrar?",
                                        "/eventos#evento-aberto",
                                        "Encerrar"))
                                .toList()));
            }
            if (!pixEstornados.isEmpty()) {
                secoes.add(new Secao(
                        "alerta-pix-estornado",
                        "Pix estornado de venda ativa",
                        pixEstornados.stream()
                                .map(x -> new Aviso(
                                        "pix-estornado-" + x.orderId(),
                                        "Venda #" + x.vendaId() + " ("
                                                + x.dataHora().format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"))
                                                + "): o Pix de " + Dinheiro.reais(x.valor())
                                                + " foi devolvido no Mercado Pago. Confira e cancele a venda se for o caso.",
                                        "/historico?dia=" + x.dataHora().toLocalDate() + "#venda-" + x.vendaId(),
                                        "Ver a venda"))
                                .toList()));
            }
            return secoes;
        }
    }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public Alertas calcular() {
        LocalDateTime agora = LocalDateTime.now(relogio);
        LocalDate hoje = agora.toLocalDate();
        List<Produto> ativos =
                estoque.listar().stream().filter(Produto::isAtivo).toList();
        var acabando = estoqueAcabando(ativos, agora);
        var semCusto = semCusto(ativos);
        var ajustes = ocorrencias.ajustesManuais(FiltroFinancas.periodo(hoje, hoje));
        var fechado = fechamento.consultar(hoje, hoje);
        return new Alertas(
                acabando,
                semCusto,
                ajustes,
                !fechado.isConfere(),
                fechado.total(),
                fechado.totalFormas(),
                hoje,
                eventosEsquecidos(agora),
                cobrancasPix.estornadosDeVendaAtiva());
    }

    private record GiroDoUltimoEvento(Map<Long, LinhaGiro> porProduto, long dias, String evento) {}

    private GiroDoUltimoEvento giroDoUltimoEvento(LocalDateTime agora) {
        Map<Long, LinhaGiro> giro = new HashMap<>();
        var ultimo = eventos.findFirstByFimLessThanEqualOrderByFimDesc(agora);
        if (ultimo.isEmpty()) return new GiroDoUltimoEvento(giro, 0, null);
        var e = ultimo.get();
        var f = FiltroFinancas.doEvento(new PeriodoDoEvento(e.getId(), e.getNome(), e.getInicio(), e.getFim()));
        estoqueFinanceiro.giro(f).forEach(l -> giro.put(l.produtoId(), l));
        return new GiroDoUltimoEvento(giro, f.dias(), e.getNome());
    }

    private List<EstoqueAcabando> estoqueAcabando(List<Produto> ativos, LocalDateTime agora) {
        var giro = giroDoUltimoEvento(agora);
        List<EstoqueAcabando> acabando = new ArrayList<>();
        for (Produto p : ativos) {
            if (p.getEstoqueMinimo() != null) {
                if (p.isAbaixoDoLimite()) {
                    acabando.add(new EstoqueAcabando(
                            p.getId(), p.getNome(), p.getQuantidade(), "limite de " + p.getEstoqueMinimo() + " un."));
                }
                continue;
            }
            LinhaGiro g = giro.porProduto().get(p.getId());
            if (g != null && g.vendidas() > 0 && (long) p.getQuantidade() * giro.dias() < g.vendidas()) {
                acabando.add(new EstoqueAcabando(
                        p.getId(),
                        p.getNome(),
                        p.getQuantidade(),
                        "média de " + g.mediaPorDia().toPlainString().replace('.', ',') + " un./dia em "
                                + giro.evento()));
            }
        }
        return acabando;
    }

    private static List<SemCusto> semCusto(List<Produto> ativos) {
        return ativos.stream()
                .filter(Produto::isSemCusto)
                .map(p -> new SemCusto(p.getId(), p.getNome()))
                .toList();
    }

    private List<EventoEsquecido> eventosEsquecidos(LocalDateTime agora) {
        return servicoDeEventos
                .aberto()
                .filter(e -> e.getInicio().plusHours(HORAS_EVENTO_ABERTO).isBefore(agora))
                .map(e -> List.of(new EventoEsquecido(e.getId(), e.getNome(), e.getInicio())))
                .orElse(List.of());
    }
}
