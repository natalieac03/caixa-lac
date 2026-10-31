package br.com.lac.caixa.servico.financas;

import static br.com.lac.caixa.servico.financas.Contas.dinheiro;

import br.com.lac.caixa.repositorio.ConsultasFinanceiras;
import br.com.lac.caixa.repositorio.FiltroFinancas;
import br.com.lac.caixa.servico.financas.Financas.LinhaGiro;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O estoque nas finanças: giro, cobertura, produtos parados e valor do estoque. */
@Service
@Transactional(readOnly = true)
public class EstoqueFinanceiro {
    private final ConsultasFinanceiras consultas;

    public EstoqueFinanceiro(ConsultasFinanceiras consultas) {
        this.consultas = consultas;
    }

    public List<LinhaGiro> giro(FiltroFinancas f) {
        long dias = f.dias();
        List<LinhaGiro> linhas = new ArrayList<>();
        for (var e : consultas.estoque(f)) {
            BigDecimal media =
                    BigDecimal.valueOf(e.vendidas()).divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_EVEN);
            boolean semGiro = e.vendidas() == 0;
            BigDecimal diasAteAcabar = semGiro
                    ? null
                    : BigDecimal.valueOf(e.quantidade())
                            .multiply(BigDecimal.valueOf(dias))
                            .divide(BigDecimal.valueOf(e.vendidas()), 1, RoundingMode.HALF_EVEN);
            linhas.add(new LinhaGiro(
                    e.produtoId(),
                    e.nome(),
                    e.categoria(),
                    e.vendidas(),
                    media,
                    e.quantidade(),
                    diasAteAcabar,
                    semGiro,
                    e.custoUnitario() == null
                            ? null
                            : dinheiro(e.custoUnitario().multiply(BigDecimal.valueOf(e.quantidade()))),
                    dinheiro(e.preco().multiply(BigDecimal.valueOf(e.quantidade())))));
        }
        return linhas;
    }

    public List<LinhaGiro> parados(FiltroFinancas f) {
        List<LinhaGiro> linhas = new ArrayList<>();
        for (var e : consultas.parados(f)) {
            linhas.add(new LinhaGiro(
                    e.produtoId(),
                    e.nome(),
                    e.categoria(),
                    0,
                    BigDecimal.ZERO.setScale(2),
                    e.quantidade(),
                    null,
                    true,
                    e.custoUnitario() == null
                            ? null
                            : dinheiro(e.custoUnitario().multiply(BigDecimal.valueOf(e.quantidade()))),
                    dinheiro(e.preco().multiply(BigDecimal.valueOf(e.quantidade())))));
        }
        return linhas;
    }

    public Financas.ValorEstoque valorDoEstoque(FiltroFinancas f) {
        var v = consultas.valorDoEstoque(f);
        return new Financas.ValorEstoque(dinheiro(v.aCusto()), dinheiro(v.aPreco()), v.produtosSemCusto());
    }
}
