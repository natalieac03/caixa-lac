package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.servico.FiltroHistorico;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Predicados do filtro do histórico: só entra o que foi preenchido. */
public final class VendaSpecs {

    private VendaSpecs() {
    }

    public static Specification<Venda> doFiltro(FiltroHistorico filtro) {
        return (v, consulta, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (filtro.forma() != null) {
                predicados.add(cb.equal(v.get("formaPagamento"), filtro.forma()));
            }
            if (filtro.inicio() != null) {
                predicados.add(cb.greaterThanOrEqualTo(v.get("dataHora"), filtro.inicioConsulta()));
            }
            if (filtro.fim() != null) {
                predicados.add(cb.lessThan(v.get("dataHora"), filtro.fimConsulta()));
            }
            if (!filtro.produto().isEmpty()) {
                // Trecho literal do nome (sem curingas de LIKE), em qualquer item da venda.
                Subquery<Long> item = consulta.subquery(Long.class);
                Root<Venda> mesma = item.correlate(v);
                Join<Venda, ItemVenda> i = mesma.join("itens");
                item.select(i.get("id")).where(
                        cb.greaterThan(cb.locate(cb.lower(i.get("produto").get("nome")), filtro.produto()), 0));
                predicados.add(cb.exists(item));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    public static Specification<Venda> naoCancelada() {
        return (v, consulta, cb) -> cb.isFalse(v.get("cancelada"));
    }
}
