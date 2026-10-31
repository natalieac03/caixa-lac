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

    List<Evento> findAllByOrderByInicioAsc();

    /**
     * Eventos que cruzam [inicio, fim), fora o próprio (null ao criar). Intervalos
     * encostados não contam. Um evento aberto conta como indo até agora.
     */
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

    /** Os eventos que contêm a data e hora (aberto: até {@code fimDoAberto}); o mais recente primeiro. */
    @Query("""
            select e from Evento e
            where e.inicio <= :quando and coalesce(e.fim, :agora) > :quando
            order by e.inicio desc
            """)
    List<Evento> doMomento(@Param("quando") LocalDateTime quando, @Param("agora") LocalDateTime agora);

    /** O evento em andamento (sem fim), se houver. */
    Optional<Evento> findFirstByFimIsNullOrderByInicioDesc();

    /** O próximo evento a começar depois deste início (o aberto não passa dele). */
    Optional<Evento> findFirstByInicioGreaterThanOrderByInicioAsc(LocalDateTime inicio);

    /** O último evento que já começou ("Este evento": o que está acontecendo ou o mais recente). */
    Optional<Evento> findFirstByInicioLessThanEqualOrderByInicioDesc(LocalDateTime agora);

    /** O evento que acabou por último antes do início deste (para comparar). */
    Optional<Evento> findFirstByFimLessThanEqualOrderByFimDesc(LocalDateTime inicio);
}
