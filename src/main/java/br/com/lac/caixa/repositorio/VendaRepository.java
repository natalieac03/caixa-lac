package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Venda;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Filtros do histórico via Specification (VendaSpecs); agregações em VendaConsultas. */
public interface VendaRepository extends JpaRepository<Venda, Long>, JpaSpecificationExecutor<Venda>, VendaConsultas {

    Sort MAIS_RECENTES = Sort.by(Sort.Order.desc("dataHora"), Sort.Order.desc("id"));

    Optional<Venda> findByToken(String token);

    boolean existsByToken(String token);

    List<Venda> findAllByOrderByDataHoraDesc();

    // A página é cortada no banco sem os itens (findAll(spec, pageable)); os itens
    // da página vêm depois, por id, para não paginar em memória.
    @EntityGraph(attributePaths = {"itens", "itens.produto"})
    @Query("select distinct v from Venda v where v.id in :ids order by v.dataHora desc, v.id desc")
    List<Venda> carregarPagina(@Param("ids") List<Long> ids);

    /** CSV: o filtro inteiro, já com os itens. */
    @Override
    @EntityGraph(attributePaths = {"itens", "itens.produto"})
    List<Venda> findAll(Specification<Venda> filtro, Sort ordem);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venda v where v.id = :id")
    Optional<Venda> buscarParaCancelar(@Param("id") Long id);
}
