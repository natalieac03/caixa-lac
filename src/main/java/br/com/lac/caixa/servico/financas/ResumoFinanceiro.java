package br.com.lac.caixa.servico.financas;

import static br.com.lac.caixa.servico.financas.Contas.dinheiro;
import static br.com.lac.caixa.servico.financas.Contas.percentual;
import static br.com.lac.caixa.servico.financas.Contas.razao;

import br.com.lac.caixa.consulta.FiltroFinancas;
import br.com.lac.caixa.consulta.PeriodoDoEvento;
import br.com.lac.caixa.repositorio.ConsultasFinanceiras;
import br.com.lac.caixa.repositorio.EventoRepository;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.financas.Financas.Cobertura;
import br.com.lac.caixa.servico.financas.Financas.Comparacao;
import br.com.lac.caixa.servico.financas.Financas.LinhaDemonstrativo;
import br.com.lac.caixa.servico.financas.Financas.Meta;
import br.com.lac.caixa.servico.financas.Financas.Resumo;
import br.com.lac.caixa.servico.financas.Financas.ResumoDoEvento;
import br.com.lac.caixa.servico.financas.Financas.Variacao;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O resumo do período, a comparação com o anterior, o demonstrativo, as metas e o comparativo de eventos. */
@Service
@Transactional(readOnly = true)
public class ResumoFinanceiro {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ConsultasFinanceiras consultas;
    private final EventoRepository eventos;
    private final EventoService servicoDeEventos;
    private final Clock relogio;

    public ResumoFinanceiro(
            ConsultasFinanceiras consultas, EventoRepository eventos, EventoService servicoDeEventos, Clock relogio) {
        this.consultas = consultas;
        this.eventos = eventos;
        this.servicoDeEventos = servicoDeEventos;
        this.relogio = relogio;
    }

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
            anterior = f.noPeriodo(de, ate, dias);
            descricao = de.equals(ate) ? de.format(DATA) : de.format(DATA) + " a " + ate.format(DATA);
            if (anterior.fimConsulta().isBefore(anterior.fimEscolhido())) {
                descricao += " até " + anterior.fimConsulta().format(HORA);
            }
        }
        Resumo antes = resumo(anterior);
        return new Comparacao(atual, antes, descricao, variacoes(atual, antes));
    }

    private static List<Variacao> variacoes(Resumo atual, Resumo antes) {
        List<Variacao> lista = new ArrayList<>();
        Resumo.INDICADORES.forEach((nome, valor) -> {
            BigDecimal a = valor.apply(atual);
            BigDecimal b = antes == null ? null : valor.apply(antes);
            BigDecimal diferenca = a == null || b == null ? null : a.subtract(b);
            lista.add(new Variacao(nome, a, b, diferenca, diferenca == null ? null : percentual(diferenca, b.abs())));
        });
        return lista;
    }

    public List<LinhaDemonstrativo> demonstrativo(Resumo r) {
        BigDecimal base = r.receitaLiquida();
        List<LinhaDemonstrativo> linhas = new ArrayList<>();
        linhas.add(linha("Receita bruta", "", r.receitaBruta(), base, false));
        if (r.descontosPromocionais() != null) {
            linhas.add(linha("Descontos promocionais", "(-)", r.descontosPromocionais(), base, false));
            linhas.add(linha("Ajustes manuais", "(+/-)", r.ajustes(), base, false));
        } else {
            linhas.add(linha(
                    "Descontos e ajustes rateados", "(-)", r.receitaBruta().subtract(base), base, false));
        }
        linhas.add(linha("Receita líquida", "=", base, base, true));
        linhas.add(linha("Taxas estimadas", "(-)", r.taxas(), base, false));
        linhas.add(linha("CMV (custo da mercadoria vendida)", "(-)", r.cmv(), base, false));
        linhas.add(linha("Margem bruta", "=", r.margemBruta(), base, true));
        linhas.add(linha("Despesas", "(-)", r.despesas(), base, false));
        linhas.add(linha("Lucro líquido", "=", r.lucroLiquido(), base, true));
        return linhas;
    }

    private static LinhaDemonstrativo linha(
            String rotulo, String operador, BigDecimal valor, BigDecimal base, boolean resultado) {
        return new LinhaDemonstrativo(rotulo, operador, valor, percentual(valor, base), resultado);
    }

    public List<Meta> metas(FiltroFinancas f, Resumo r) {
        if (f.evento() == null || !f.aceitaDespesas()) return List.of();
        var evento = eventos.findById(f.evento().id());
        if (evento.isEmpty()) return List.of();
        var e = evento.get();
        List<Meta> metas = new ArrayList<>();
        if (e.getMetaReceita() != null) {
            metas.add(Meta.de("receita", "Meta de receita líquida", e.getMetaReceita(), r.receitaLiquida(), false));
        }
        if (e.getMetaLucro() != null && r.lucroLiquido() != null) {
            metas.add(
                    Meta.de("lucro", "Meta de lucro líquido", e.getMetaLucro(), r.lucroLiquido(), r.isLucroParcial()));
        }
        return metas;
    }

    public List<ResumoDoEvento> comparativoDeEventos() {
        var periodos = servicoDeEventos.periodos();
        List<ResumoDoEvento> lista = new ArrayList<>();
        for (PeriodoDoEvento p : periodos) {
            lista.add(new ResumoDoEvento(p.id(), p.nome(), p.inicio(), p.fim(), resumo(FiltroFinancas.doEvento(p))));
        }
        if (!periodos.isEmpty()) {
            LocalDate de = periodos.get(0).inicio().toLocalDate();
            LocalDate ate = periodos.stream()
                    .map(p -> p.fim().minusNanos(1).toLocalDate())
                    .max(Comparator.naturalOrder())
                    .orElse(de);
            var diaADia = FiltroFinancas.diaADia(de, ate, LocalDateTime.now(relogio));
            lista.add(new ResumoDoEvento(
                    null, "Dia a dia", de.atStartOfDay(), ate.plusDays(1).atStartOfDay(), resumo(diaADia)));
        }
        return lista;
    }
}
