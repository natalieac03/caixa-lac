package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.OrigemPagamento;
import br.com.lac.caixa.modelo.Venda;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/** Agregações com Specification (o JpaSpecificationExecutor não agrega). */
public interface VendaConsultas {

    IndicadoresHistorico indicadores(Specification<Venda> filtro);

    /** Por forma, somando as partes de pagamento (pagamento_venda). */
    List<ResumoPorForma> resumoPorForma(Specification<Venda> filtro);

    TotaisDasVendas totais(Specification<Venda> filtro);

    /** Soma das partes Pix por origem (Mercado Pago ou manual; Pix sem origem conta como manual). */
    Map<OrigemPagamento, BigDecimal> pixPorOrigem(Specification<Venda> filtro);
}
