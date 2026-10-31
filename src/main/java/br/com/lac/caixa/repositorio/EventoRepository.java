package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Evento;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso aos eventos: sobreposição, evento do momento, aberto, próximo e anterior. */
public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findAllByOrderByInicioDesc();

    List<Evento> findAllByOrderByInicioAsc();

    @Query("""
            select e from Evento e
            where e.inicio < :fim and coalesce(e.fim, :agora) > :inicio and (:ignorar is null or e.id <> :ignorar)
            order by e.inicio
            """)
    List<Evento> sobrepostos(
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("agora") LocalDateTime agora,
            @Param("ignorar") Long ignorar);

    @Query("""
            select e from Evento e
            where e.inicio <= :quando
              and (e.fim > :quando
                   or (e.fim is null and :agora > :quando
                       and not exists (select p.id from Evento p where p.inicio > e.inicio and p.inicio <= :quando)))
            order by e.inicio desc
            """)
    List<Evento> doMomento(@Param("quando") LocalDateTime quando, @Param("agora") LocalDateTime agora);

    Optional<Evento> findFirstByFimIsNullOrderByInicioDesc();

    Optional<Evento> findFirstByInicioGreaterThanOrderByInicioAsc(LocalDateTime inicio);

    Optional<Evento> findFirstByInicioLessThanEqualOrderByInicioDesc(LocalDateTime agora);

    Optional<Evento> findFirstByFimLessThanEqualOrderByFimDesc(LocalDateTime inicio);
}
