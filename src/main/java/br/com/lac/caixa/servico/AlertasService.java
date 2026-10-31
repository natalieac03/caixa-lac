package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.EventoRepository;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import br.com.lac.caixa.repositorio.PeriodoDoEvento;
import br.com.lac.caixa.servico.financas.Financas.LinhaAjuste;
import br.com.lac.caixa.servico.financas.Financas.LinhaGiro;
import br.com.lac.caixa.servico.financas.FinancasService;
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

/**
 * Os avisos do sininho (no topo de todas as telas logadas). Aparecem só quando há algo:
 * <ul>
 *   <li>estoque acabando: com limite, quantidade até o limite; sem limite,
 *       estoque para menos de 1 dia de venda pela média do último evento que já
 *       terminou (quantidade x dias do evento &lt; vendidas no evento);</li>
 *   <li>produtos ativos sem custo;</li>
 *   <li>vendas de hoje com ajuste manual;</li>
 *   <li>fechamento de hoje em que a soma das formas não bate com o total;</li>
 *   <li>evento aberto (sem fim) há mais de 12 horas, com link para encerrar.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AlertasService {
    private final EstoqueService estoque;
    private final EventoRepository eventos;
    private final FinancasService financas;
    private final FechamentoService fechamento;
    private final Clock relogio;
    private final EventoService servicoDeEventos;

    public AlertasService(
            EstoqueService estoque,
            EventoRepository eventos,
            FinancasService financas,
            FechamentoService fechamento,
            Clock relogio,
            EventoService servicoDeEventos) {
        this.estoque = estoque;
        this.eventos = eventos;
        this.financas = financas;
        this.fechamento = fechamento;
        this.relogio = relogio;
        this.servicoDeEventos = servicoDeEventos;
    }

    /** motivo já escrito: "limite de 5 un." ou "média de 4,00 un./dia na Feira 1". */
    public record EstoqueAcabando(Long produtoId, String nome, int estoque, String motivo) {}

    public record SemCusto(Long produtoId, String nome) {}

    /** Evento em andamento há mais de {@link #HORAS_EVENTO_ABERTO} horas. */
    public record EventoEsquecido(Long eventoId, String nome, LocalDateTime desde) {}

    /** Depois de quantas horas um evento aberto vira aviso. */
    public static final long HORAS_EVENTO_ABERTO = 12;

    /**
     * Um aviso do sininho. O id é estável e muda quando o conteúdo muda (ex.:
     * produto + quantidade): o navegador guarda os ids já vistos para contar os novos.
     */
    public record Aviso(String id, String texto, String link, String linkTexto) {}

    /** Uma seção do painel (estoque acabando, sem custo, ajustes, fechamento). */
    public record Secao(String id, String titulo, List<Aviso> avisos) {}

    public record Alertas(
            List<EstoqueAcabando> estoqueAcabando,
            List<SemCusto> semCusto,
            List<LinhaAjuste> ajustesHoje,
            boolean fechamentoNaoBate,
            BigDecimal totalHoje,
            BigDecimal formasHoje,
            LocalDate dia,
            List<EventoEsquecido> eventosAbertos) {

        public boolean isTemAlgo() {
            return total() > 0;
        }

        public int total() {
            return secoes().stream().mapToInt(x -> x.avisos().size()).sum();
        }

        /** As seções com os textos e links já escritos (o mesmo conteúdo na página e no JSON /avisos). */
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
            return secoes;
        }
    }

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public Alertas calcular() {
        LocalDateTime agora = LocalDateTime.now(relogio);
        LocalDate hoje = agora.toLocalDate();
        List<Produto> ativos =
                estoque.listar().stream().filter(Produto::isAtivo).toList();

        // Média do último evento que já terminou.
        Map<Long, LinhaGiro> giro = new HashMap<>();
        long dias = 0;
        String evento = null;
        var ultimo = eventos.findFirstByFimLessThanEqualOrderByFimDesc(agora);
        if (ultimo.isPresent()) {
            var e = ultimo.get();
            var f = FiltroFinancas.doEvento(new PeriodoDoEvento(e.getId(), e.getNome(), e.getInicio(), e.getFim()));
            dias = f.dias();
            evento = e.getNome();
            financas.giro(f).forEach(l -> giro.put(l.produtoId(), l));
        }
        List<EstoqueAcabando> acabando = new ArrayList<>();
        for (Produto p : ativos) {
            if (p.getEstoqueMinimo() != null) {
                if (p.isAbaixoDoLimite()) {
                    acabando.add(new EstoqueAcabando(
                            p.getId(), p.getNome(), p.getQuantidade(), "limite de " + p.getEstoqueMinimo() + " un."));
                }
                continue;
            }
            LinhaGiro g = giro.get(p.getId());
            if (g != null && g.vendidas() > 0 && (long) p.getQuantidade() * dias < g.vendidas()) {
                acabando.add(new EstoqueAcabando(
                        p.getId(),
                        p.getNome(),
                        p.getQuantidade(),
                        "média de " + g.mediaPorDia().toPlainString().replace('.', ',') + " un./dia em " + evento));
            }
        }
        List<SemCusto> semCusto = ativos.stream()
                .filter(Produto::isSemCusto)
                .map(p -> new SemCusto(p.getId(), p.getNome()))
                .toList();
        var ajustes = financas.ajustesManuais(FiltroFinancas.periodo(hoje, hoje));
        var fechado = fechamento.consultar(hoje, hoje);
        var esquecidos = servicoDeEventos
                .aberto()
                .filter(e -> e.getInicio().plusHours(HORAS_EVENTO_ABERTO).isBefore(agora))
                .map(e -> List.of(new EventoEsquecido(e.getId(), e.getNome(), e.getInicio())))
                .orElse(List.of());
        return new Alertas(
                acabando,
                semCusto,
                ajustes,
                !fechado.isConfere(),
                fechado.total(),
                fechado.totalFormas(),
                hoje,
                esquecidos);
    }
}
