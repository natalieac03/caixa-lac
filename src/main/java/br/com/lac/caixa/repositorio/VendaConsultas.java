package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Venda;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/** Agregações com Specification (o JpaSpecificationExecutor não agrega). */
public interface VendaConsultas {

    IndicadoresHistorico indicadores(Specification<Venda> filtro);

    List<ResumoPorForma> resumoPorForma(Specification<Venda> filtro);
}
