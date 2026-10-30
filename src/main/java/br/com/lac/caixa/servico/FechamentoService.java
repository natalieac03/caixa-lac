package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.repositorio.VendaSpecs;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FechamentoService {
    private final VendaRepository vendas;

    public FechamentoService(VendaRepository vendas) {
        this.vendas = vendas;
    }

    @Transactional(readOnly = true)
    public Fechamento consultar(LocalDate inicio, LocalDate fim) {
        var filtro = VendaSpecs.doFiltro(FiltroHistorico.periodo(inicio, fim)).and(VendaSpecs.naoCancelada());
        var porForma = vendas.resumoPorForma(filtro);
        var linhas = new ArrayList<Fechamento.Linha>();
        BigDecimal totalFormas = BigDecimal.ZERO;
        for (FormaPagamento forma : FormaPagamento.values()) {
            var linha = porForma.stream()
                    .filter(r -> r.forma() == forma)
                    .findFirst()
                    .map(r -> new Fechamento.Linha(forma, r.vendas(), r.total()))
                    .orElseGet(() -> new Fechamento.Linha(forma, 0, BigDecimal.ZERO));
            linhas.add(linha);
            totalFormas = totalFormas.add(linha.total());
        }
        var totais = vendas.totais(filtro);
        return new Fechamento(
                linhas,
                totais.vendas(),
                totais.cobrado(),
                totalFormas,
                totais.descontos(),
                totais.ajustes(),
                vendas.count(filtro.and(VendaSpecs.pagamentoDividido())),
                vendas.count(filtro.and(VendaSpecs.cortesia())));
    }
}
