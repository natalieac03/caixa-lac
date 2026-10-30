package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.Venda;
import jakarta.persistence.criteria.Expression;
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
                // Venda dividida entra se QUALQUER parte usou a forma.
                Subquery<Long> parte = consulta.subquery(Long.class);
                Root<Venda> mesma = parte.correlate(v);
                Join<Venda, PagamentoVenda> p = mesma.join("pagamentos");
                parte.select(p.get("id")).where(cb.equal(p.get("forma"), filtro.forma()));
                predicados.add(cb.exists(parte));
            }
            if (filtro.inicio() != null) {
                predicados.add(cb.greaterThanOrEqualTo(v.get("dataHora"), filtro.inicioConsulta()));
            }
            if (filtro.fim() != null) {
                predicados.add(cb.lessThan(v.get("dataHora"), filtro.fimConsulta()));
            }
            if (!filtro.produto().isEmpty()) {
                // Trecho literal do nome (sem curingas de LIKE), sem acento, em qualquer item da venda.
                Subquery<Long> item = consulta.subquery(Long.class);
                Root<Venda> mesma = item.correlate(v);
                Join<Venda, ItemVenda> i = mesma.join("itens");
                Expression<String> nome = cb.lower(cb.function("translate", String.class, i.get("produto").get("nome"),
                        cb.literal(FiltroHistorico.COM_ACENTO), cb.literal(FiltroHistorico.SEM_ACENTO)));
                item.select(i.get("id")).where(cb.greaterThan(cb.locate(nome, filtro.produto()), 0));
                predicados.add(cb.exists(item));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }

    /** Mais de uma parte no pagamento. */
    public static Specification<Venda> pagamentoDividido() {
        return (v, consulta, cb) -> cb.greaterThan(cb.size(v.<java.util.Collection<PagamentoVenda>>get("pagamentos")), 1);
    }

    /** Sem parte de pagamento: valor cobrado zero. */
    public static Specification<Venda> cortesia() {
        return (v, consulta, cb) -> cb.isEmpty(v.<java.util.Collection<PagamentoVenda>>get("pagamentos"));
    }

    public static Specification<Venda> naoCancelada() {
        return (v, consulta, cb) -> cb.isFalse(v.get("cancelada"));
    }
}
