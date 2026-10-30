package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.EventoRepository;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import br.com.lac.caixa.repositorio.PeriodoDoEvento;
import br.com.lac.caixa.servico.financas.Financas.LinhaAjuste;
import br.com.lac.caixa.servico.financas.Financas.LinhaGiro;
import br.com.lac.caixa.servico.financas.FinancasService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O cartão "Atenção" da tela inicial. Aparece só quando há algo:
 * <ul>
 *   <li>estoque acabando: com limite, quantidade até o limite; sem limite,
 *       estoque para menos de 1 dia de venda pela média do último evento que já
 *       terminou (quantidade x dias do evento &lt; vendidas no evento);</li>
 *   <li>produtos ativos sem custo;</li>
 *   <li>vendas de hoje com ajuste manual;</li>
 *   <li>fechamento de hoje em que a soma das formas não bate com o total.</li>
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

    public AlertasService(
            EstoqueService estoque,
            EventoRepository eventos,
            FinancasService financas,
            FechamentoService fechamento,
            Clock relogio) {
        this.estoque = estoque;
        this.eventos = eventos;
        this.financas = financas;
        this.fechamento = fechamento;
        this.relogio = relogio;
    }

    /** motivo já escrito: "limite de 5 un." ou "média de 4,00 un./dia na Feira 1". */
    public record EstoqueAcabando(Long produtoId, String nome, int estoque, String motivo) {}

    public record Alertas(
            List<EstoqueAcabando> estoqueAcabando,
            List<String> semCusto,
            List<LinhaAjuste> ajustesHoje,
            boolean fechamentoNaoBate,
            BigDecimal totalHoje,
            BigDecimal formasHoje) {

        public boolean isTemAlgo() {
            return !estoqueAcabando.isEmpty() || !semCusto.isEmpty() || !ajustesHoje.isEmpty() || fechamentoNaoBate;
        }
    }

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
        List<String> semCusto = ativos.stream()
                .filter(Produto::isSemCusto)
                .map(Produto::getNome)
                .toList();
        var ajustes = financas.ajustesManuais(FiltroFinancas.periodo(hoje, hoje));
        var fechado = fechamento.consultar(hoje, hoje);
        return new Alertas(acabando, semCusto, ajustes, !fechado.isConfere(), fechado.total(), fechado.totalFormas());
    }
}
