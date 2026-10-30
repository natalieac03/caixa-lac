package br.com.lac.caixa.servico.financas;

import br.com.lac.caixa.repositorio.ConsultasFinanceiras;
import br.com.lac.caixa.repositorio.EventoRepository;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import br.com.lac.caixa.repositorio.PeriodoDoEvento;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import br.com.lac.caixa.servico.financas.Financas.Cobertura;
import br.com.lac.caixa.servico.financas.Financas.Comparacao;
import br.com.lac.caixa.servico.financas.Financas.Resumo;
import br.com.lac.caixa.servico.financas.Financas.Variacao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Camada de consulta das finanças (sem tela ainda). As somas são feitas no
 * banco (ConsultasFinanceiras); aqui ficam arredondamento, percentuais,
 * ordenação e o preenchimento de dias e horas sem venda.
 */
@Service
@Transactional(readOnly = true)
public class FinancasService {
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ConsultasFinanceiras consultas;
    private final EventoRepository eventos;
    private final PromocaoRepository promocoes;

    public FinancasService(ConsultasFinanceiras consultas, EventoRepository eventos, PromocaoRepository promocoes) {
        this.consultas = consultas;
        this.eventos = eventos;
        this.promocoes = promocoes;
    }

    static BigDecimal dinheiro(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_EVEN);
    }

    /** parte / todo x 100, 2 casas; null se o todo é zero ou ausente. */
    static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        if (parte == null || todo == null || todo.signum() == 0) return null;
        return parte.multiply(CEM).divide(todo, 2, RoundingMode.HALF_EVEN);
    }

    static BigDecimal razao(BigDecimal a, long b) {
        return b == 0 ? null : a.divide(BigDecimal.valueOf(b), 2, RoundingMode.HALF_EVEN);
    }

    // ---------------------------------------------------------------- A: resumo

    public Resumo resumo(FiltroFinancas f) {
        var itens = consultas.somaItens(f);
        var vendas = consultas.somaVendas(f);
        boolean porCategoria = f.categoriaId() != null;

        BigDecimal liquido = dinheiro(itens.liquido());
        BigDecimal cmv = dinheiro(itens.cmv());
        BigDecimal margem = liquido.subtract(cmv);
        BigDecimal taxas = porCategoria ? null : dinheiro(consultas.taxas(f));
        BigDecimal despesas = f.aceitaDespesas() ? dinheiro(consultas.despesas(f)) : null;
        BigDecimal lucro = taxas == null || despesas == null
                ? null
                : liquido.subtract(taxas).subtract(cmv).subtract(despesas);

        BigDecimal comCusto = dinheiro(itens.liquidoComCusto());
        var cobertura = new Cobertura(
                comCusto,
                liquido.subtract(comCusto),
                percentual(comCusto, liquido),
                itens.linhasSemCusto(),
                itens.linhasSemCusto() > 0);

        return new Resumo(
                dinheiro(itens.bruto()),
                porCategoria ? null : dinheiro(vendas.descontos()),
                porCategoria ? null : dinheiro(vendas.ajustes()),
                liquido,
                taxas,
                cmv,
                margem,
                percentual(margem, liquido),
                despesas,
                lucro,
                percentual(lucro, liquido),
                vendas.vendas(),
                itens.unidades(),
                razao(liquido, vendas.vendas()),
                razao(BigDecimal.valueOf(itens.unidades()), vendas.vendas()),
                vendas.cortesias(),
                cobertura,
                porCategoria ? 0 : vendas.semItens(),
                porCategoria ? BigDecimal.ZERO.setScale(2) : dinheiro(vendas.cobradoSemItens()));
    }

    /** O resumo e o do período anterior de mesmo tamanho (ou do evento anterior), com as variações. */
    public Comparacao comparar(FiltroFinancas f) {
        Resumo atual = resumo(f);
        FiltroFinancas anterior;
        String descricao;
        if (f.evento() != null) {
            var ev =
                    eventos.findFirstByFimLessThanEqualOrderByFimDesc(f.evento().inicio());
            if (ev.isEmpty()) return new Comparacao(atual, null, null, variacoes(atual, null));
            var e = ev.get();
            anterior = f.noEvento(new PeriodoDoEvento(e.getId(), e.getNome(), e.getInicio(), e.getFim()));
            descricao = "evento " + e.getNome();
        } else {
            long dias = f.dias();
            LocalDate ate = f.inicio().minusDays(1);
            LocalDate de = ate.minusDays(dias - 1);
            anterior = f.noPeriodo(de, ate);
            descricao = de.format(DATA) + " a " + ate.format(DATA);
        }
        Resumo antes = resumo(anterior);
        return new Comparacao(atual, antes, descricao, variacoes(atual, antes));
    }

    private static List<Variacao> variacoes(Resumo atual, Resumo antes) {
        Map<String, Function<Resumo, BigDecimal>> indicadores = new LinkedHashMap<>();
        indicadores.put("receitaBruta", Resumo::receitaBruta);
        indicadores.put("descontosPromocionais", Resumo::descontosPromocionais);
        indicadores.put("ajustes", Resumo::ajustes);
        indicadores.put("receitaLiquida", Resumo::receitaLiquida);
        indicadores.put("taxas", Resumo::taxas);
        indicadores.put("cmv", Resumo::cmv);
        indicadores.put("margemBruta", Resumo::margemBruta);
        indicadores.put("margemBrutaPercentual", Resumo::margemBrutaPercentual);
        indicadores.put("despesas", Resumo::despesas);
        indicadores.put("lucroLiquido", Resumo::lucroLiquido);
        indicadores.put("lucroLiquidoPercentual", Resumo::lucroLiquidoPercentual);
        indicadores.put("vendas", r -> BigDecimal.valueOf(r.vendas()));
        indicadores.put("itensVendidos", r -> BigDecimal.valueOf(r.itensVendidos()));
        indicadores.put("ticketMedio", Resumo::ticketMedio);
        indicadores.put("itensPorVenda", Resumo::itensPorVenda);
        indicadores.put("cortesias", r -> BigDecimal.valueOf(r.cortesias()));
        List<Variacao> lista = new ArrayList<>();
        indicadores.forEach((nome, valor) -> {
            BigDecimal a = valor.apply(atual);
            BigDecimal b = antes == null ? null : valor.apply(antes);
            BigDecimal diferenca = a == null || b == null ? null : a.subtract(b);
            lista.add(new Variacao(nome, a, b, diferenca, diferenca == null ? null : percentual(diferenca, b.abs())));
        });
        return lista;
    }
}
