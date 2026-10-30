package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.Venda;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

/** Implementação de VendaConsultas, encontrada pelo Spring Data pelo sufixo Impl. */
class VendaConsultasImpl implements VendaConsultas {

    private final EntityManager em;

    VendaConsultasImpl(EntityManager em) {
        this.em = em;
    }

    /** Valor cobrado; vendas antigas só têm valor_total. */
    static Expression<BigDecimal> cobrado(CriteriaBuilder cb, Root<Venda> v) {
        return cb.coalesce(v.get("valorCobrado"), v.<BigDecimal>get("valorTotal"));
    }

    @Override
    public IndicadoresHistorico indicadores(Specification<Venda> filtro) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Sem JOIN nos itens: o total não se multiplica pela quantidade de itens.
        CriteriaQuery<Tuple> vendas = cb.createTupleQuery();
        Root<Venda> v = vendas.from(Venda.class);
        vendas.multiselect(cb.count(v), cb.coalesce(cb.sum(cobrado(cb, v)), BigDecimal.ZERO))
              .where(filtro.toPredicate(v, vendas, cb));
        Tuple t = em.createQuery(vendas).getSingleResult();

        CriteriaQuery<Long> itens = cb.createQuery(Long.class);
        Root<Venda> vi = itens.from(Venda.class);
        Join<Venda, ItemVenda> i = vi.join("itens");
        itens.select(cb.coalesce(cb.sumAsLong(i.get("quantidade")), 0L))
             .where(filtro.toPredicate(vi, itens, cb));

        return new IndicadoresHistorico(t.get(0, Long.class), em.createQuery(itens).getSingleResult(),
                t.get(1, BigDecimal.class));
    }

    @Override
    public List<ResumoPorForma> resumoPorForma(Specification<Venda> filtro) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Venda> v = q.from(Venda.class);
        Expression<BigDecimal> cobrado = cobrado(cb, v);
        Expression<BigDecimal> calculado = cb.<BigDecimal>coalesce()
                .value(v.get("valorCalculado")).value(v.get("valorCobrado")).value(v.get("valorTotal"));
        q.multiselect(v.get("formaPagamento"), cb.count(v),
                        cb.coalesce(cb.sum(cobrado), BigDecimal.ZERO),
                        cb.coalesce(cb.sum(cb.coalesce(v.<BigDecimal>get("desconto"), BigDecimal.ZERO)), BigDecimal.ZERO),
                        cb.coalesce(cb.sum(cb.diff(cobrado, calculado)), BigDecimal.ZERO))
         .where(filtro.toPredicate(v, q, cb))
         .groupBy(v.get("formaPagamento"))
         .orderBy(cb.asc(v.get("formaPagamento")));
        return em.createQuery(q).getResultList().stream()
                .map(r -> new ResumoPorForma(r.get(0, FormaPagamento.class), r.get(1, Long.class),
                        r.get(2, BigDecimal.class), r.get(3, BigDecimal.class), r.get(4, BigDecimal.class)))
                .toList();
    }
}
