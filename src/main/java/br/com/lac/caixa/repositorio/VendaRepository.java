package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Venda;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Os filtros nunca recebem null (veja FiltroHistorico): no Postgres um null
 * sem tipo vira bytea e "? is null or ..." quebra a consulta.
 */
public interface VendaRepository extends JpaRepository<Venda, Long> {

    String FILTRO = """
        from Venda v where v.formaPagamento in :formas
        and v.dataHora >= :inicio and v.dataHora < :fim
        and (:produto = '' or exists (select i.id from Venda outra join outra.itens i
             where outra.id = v.id and locate(:produto, lower(i.produto.nome)) > 0))
        """;
    String FILTRO_SQL = """
        from venda v where v.forma_pagamento in (:formas)
        and v.data_hora >= :inicio and v.data_hora < :fim
        and (:produto = '' or exists (select 1 from item_venda i join produto p on p.id = i.produto_id
             where i.venda_id = v.id and position(:produto in lower(p.nome)) > 0))
        """;

    Optional<Venda> findByToken(String token);

    boolean existsByToken(String token);

    List<Venda> findAllByOrderByDataHoraDesc();

    // Primeiro pagina apenas IDs no banco; a coleção é carregada após o LIMIT.
    @Query("select v.id " + FILTRO + " order by v.dataHora desc, v.id desc")
    Page<Long> paginarIds(@Param("formas") Collection<FormaPagamento> formas,
                          @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim,
                          @Param("produto") String produto, Pageable pagina);

    @EntityGraph(attributePaths = {"itens", "itens.produto"})
    @Query("select distinct v from Venda v where v.id in :ids order by v.dataHora desc, v.id desc")
    List<Venda> carregarPagina(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = {"itens", "itens.produto"})
    @Query("select distinct v " + FILTRO + " and v.cancelada = false order by v.dataHora desc, v.id desc")
    List<Venda> exportar(@Param("formas") Collection<FormaPagamento> formas,
                         @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim,
                         @Param("produto") String produto);

    // Sem JOIN na venda: valores não são multiplicados pela quantidade de itens.
    @Query(value = """
        select count(*) as vendas, coalesce(sum(coalesce(v.valor_cobrado, v.valor_total)), 0) as total,
               coalesce(sum((select coalesce(sum(i.quantidade), 0) from item_venda i where i.venda_id=v.id)), 0) as itens
        """ + FILTRO_SQL + " and v.cancelada = false", nativeQuery = true)
    IndicadoresHistorico indicadores(@Param("formas") Collection<String> formas,
                                     @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim,
                                     @Param("produto") String produto);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venda v where v.id = :id")
    Optional<Venda> buscarParaCancelar(@Param("id") Long id);

    @Query("""
        select v.formaPagamento as forma, count(v) as quantidade,
               coalesce(sum(coalesce(v.valorCobrado, v.valorTotal)), 0) as total,
               coalesce(sum(coalesce(v.desconto, 0)), 0) as descontos,
               coalesce(sum(coalesce(v.valorCobrado, v.valorTotal)
                   - coalesce(v.valorCalculado, v.valorCobrado, v.valorTotal)), 0) as ajustes
        from Venda v where v.cancelada = false
        and v.dataHora >= :inicio and v.dataHora < :fim
        group by v.formaPagamento order by v.formaPagamento
        """)
    List<ResumoPorForma> fechamento(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}
