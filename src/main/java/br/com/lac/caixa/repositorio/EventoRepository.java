package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Evento;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findAllByOrderByInicioDesc();

    /** Eventos que cruzam [inicio, fim), fora o próprio (null ao criar). Intervalos encostados não contam. */
    @Query("""
            select e from Evento e
            where e.inicio < :fim and e.fim > :inicio and (:ignorar is null or e.id <> :ignorar)
            order by e.inicio
            """)
    List<Evento> sobrepostos(
            @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, @Param("ignorar") Long ignorar);

    /** O evento que contém a data e hora (no máximo um, pois não se sobrepõem). */
    @Query("select e from Evento e where e.inicio <= :quando and e.fim > :quando")
    Optional<Evento> doMomento(@Param("quando") LocalDateTime quando);

    /** O último evento que já começou ("Este evento": o que está acontecendo ou o mais recente). */
    Optional<Evento> findFirstByInicioLessThanEqualOrderByInicioDesc(LocalDateTime agora);

    /** O evento que acabou por último antes do início deste (para comparar). */
    Optional<Evento> findFirstByFimLessThanEqualOrderByFimDesc(LocalDateTime inicio);
}
