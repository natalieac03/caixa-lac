package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.OrigemPagamento;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.Venda;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/** Implementação de VendaConsultas, encontrada pelo Spring Data pelo sufixo Impl. */
class VendaConsultasImpl implements VendaConsultas {

    private final EntityManager em;

    VendaConsultasImpl(EntityManager em) {
        this.em = em;
    }

    /** Valor cobrado (sempre preenchido desde a V7). */
    static Expression<BigDecimal> cobrado(CriteriaBuilder cb, Root<Venda> v) {
        return v.get("valorCobrado");
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
        itens.select(cb.coalesce(cb.sumAsLong(i.get("quantidade")), 0L)).where(filtro.toPredicate(vi, itens, cb));

        return new IndicadoresHistorico(
                t.get(0, Long.class), em.createQuery(itens).getSingleResult(), t.get(1, BigDecimal.class));
    }

    @Override
    public List<ResumoPorForma> resumoPorForma(Specification<Venda> filtro) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Venda> v = q.from(Venda.class);
        Join<Venda, PagamentoVenda> p = v.join("pagamentos");
        q.multiselect(
                        p.get("forma"),
                        cb.countDistinct(v),
                        cb.coalesce(cb.sum(p.<BigDecimal>get("valor")), BigDecimal.ZERO))
                .where(filtro.toPredicate(v, q, cb))
                .groupBy(p.get("forma"))
                .orderBy(cb.asc(p.get("forma")));
        return em.createQuery(q).getResultList().stream()
                .map(r -> new ResumoPorForma(
                        r.get(0, FormaPagamento.class), r.get(1, Long.class), r.get(2, BigDecimal.class)))
                .toList();
    }

    @Override
    public TotaisDasVendas totais(Specification<Venda> filtro) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Venda> v = q.from(Venda.class);
        Expression<BigDecimal> cobrado = cobrado(cb, v);
        // Sem valor calculado, o ajuste conta zero (calculado = cobrado).
        Expression<BigDecimal> calculado = cb.coalesce(v.<BigDecimal>get("valorCalculado"), cobrado);
        q.multiselect(
                        cb.count(v),
                        cb.coalesce(cb.sum(cobrado), BigDecimal.ZERO),
                        cb.coalesce(
                                cb.sum(cb.coalesce(v.<BigDecimal>get("desconto"), BigDecimal.ZERO)), BigDecimal.ZERO),
                        cb.coalesce(cb.sum(cb.diff(cobrado, calculado)), BigDecimal.ZERO))
                .where(filtro.toPredicate(v, q, cb));
        Tuple t = em.createQuery(q).getSingleResult();
        return new TotaisDasVendas(
                t.get(0, Long.class),
                t.get(1, BigDecimal.class),
                t.get(2, BigDecimal.class),
                t.get(3, BigDecimal.class));
    }

    @Override
    public Map<OrigemPagamento, BigDecimal> pixPorOrigem(Specification<Venda> filtro) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Venda> v = q.from(Venda.class);
        Join<Venda, PagamentoVenda> p = v.join("pagamentos");
        q.multiselect(p.get("origem"), cb.coalesce(cb.sum(p.<BigDecimal>get("valor")), BigDecimal.ZERO))
                .where(cb.and(filtro.toPredicate(v, q, cb), cb.equal(p.get("forma"), FormaPagamento.PIX)))
                .groupBy(p.get("origem"));
        var porOrigem = new EnumMap<OrigemPagamento, BigDecimal>(OrigemPagamento.class);
        porOrigem.put(OrigemPagamento.MERCADO_PAGO, BigDecimal.ZERO);
        porOrigem.put(OrigemPagamento.MANUAL, BigDecimal.ZERO);
        for (Tuple t : em.createQuery(q).getResultList()) {
            var origem = t.get(0, OrigemPagamento.class);
            if (origem == null) origem = OrigemPagamento.MANUAL;
            porOrigem.merge(origem, t.get(1, BigDecimal.class), BigDecimal::add);
        }
        return porOrigem;
    }
}
