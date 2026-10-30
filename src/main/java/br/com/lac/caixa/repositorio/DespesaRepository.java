package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Despesa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    /** Filtro da tela: com evento, as despesas ligadas a ele; sem, pelo período (datas inclusivas, vazio = aberto). */
    @Query("""
            select d from Despesa d join fetch d.categoria left join fetch d.evento
            where (:evento is null or d.evento.id = :evento)
              and (:inicio is null or d.data >= :inicio)
              and (:fim is null or d.data <= :fim)
            order by d.data desc, d.id desc
            """)
    List<Despesa> filtrar(@Param("evento") Long evento, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("""
            select coalesce(sum(d.valor), 0) from Despesa d
            where (:evento is null or d.evento.id = :evento)
              and (:inicio is null or d.data >= :inicio)
              and (:fim is null or d.data <= :fim)
            """)
    BigDecimal total(@Param("evento") Long evento, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    boolean existsByCategoriaId(Long categoriaId);
}
