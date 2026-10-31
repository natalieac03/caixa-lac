package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.OrigemPagamento;
import br.com.lac.caixa.modelo.Venda;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/** Agregações das vendas com Specification. */
public interface VendaConsultas {

    IndicadoresHistorico indicadores(Specification<Venda> filtro);

    List<ResumoPorForma> resumoPorForma(Specification<Venda> filtro);

    TotaisDasVendas totais(Specification<Venda> filtro);

    Map<OrigemPagamento, BigDecimal> pixPorOrigem(Specification<Venda> filtro);
}
