package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Venda;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/** Agregações com Specification (o JpaSpecificationExecutor não agrega). */
public interface VendaConsultas {

    IndicadoresHistorico indicadores(Specification<Venda> filtro);

    /** Por forma, somando as partes de pagamento (pagamento_venda). */
    List<ResumoPorForma> resumoPorForma(Specification<Venda> filtro);

    TotaisDasVendas totais(Specification<Venda> filtro);
}
