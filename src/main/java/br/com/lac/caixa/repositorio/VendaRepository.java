package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Venda;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso às vendas: filtros do histórico, páginas e CSV. */
public interface VendaRepository extends JpaRepository<Venda, Long>, JpaSpecificationExecutor<Venda>, VendaConsultas {

    Sort MAIS_RECENTES = Sort.by(Sort.Order.desc("dataHora"), Sort.Order.desc("id"));

    Optional<Venda> findByToken(String token);

    boolean existsByToken(String token);

    @EntityGraph(attributePaths = {"itens", "itens.produto"})
    @Query("select distinct v from Venda v where v.id in :ids order by v.dataHora desc, v.id desc")
    List<Venda> carregarPagina(@Param("ids") List<Long> ids);

    @Override
    @EntityGraph(attributePaths = {"itens", "itens.produto"})
    List<Venda> findAll(Specification<Venda> filtro, Sort ordem);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venda v where v.id = :id")
    Optional<Venda> buscarParaCancelar(@Param("id") Long id);

    @Query("select v.id from Venda v join v.pagamentos p where v.cancelada = false and p.mpOrderId = :order")
    List<Long> ativasComOrder(@Param("order") String order);
}
