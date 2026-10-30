package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.CobrancaPix;
import br.com.lac.caixa.modelo.StatusCobranca;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CobrancaPixRepository extends JpaRepository<CobrancaPix, Long> {

    /** Com bloqueio: duas consultas ou um webhook ao mesmo tempo não se atropelam. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CobrancaPix c where c.token = :token and c.parte = :parte")
    Optional<CobrancaPix> buscarParaAtualizar(@Param("token") String token, @Param("parte") int parte);

    Optional<CobrancaPix> findByTokenAndParte(String token, int parte);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CobrancaPix c where c.orderId = :orderId")
    Optional<CobrancaPix> buscarPorOrderParaAtualizar(@Param("orderId") String orderId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CobrancaPix c where c.id = :id")
    Optional<CobrancaPix> buscarPorIdParaAtualizar(@Param("id") Long id);

    List<CobrancaPix> findByStatusAndCriadaEmBefore(StatusCobranca status, LocalDateTime limite);

    /**
     * Dinheiro que entrou sem venda: Pix PAGO (ou pago com valor diferente) que
     * nenhuma parte de venda usa, ainda não resolvido, criado antes do limite
     * (quem está com a tela aberta ainda pode confirmar).
     */
    @Query("""
            select c from CobrancaPix c
            where c.resolvidaEm is null and c.criadaEm < :limite
              and (c.status = br.com.lac.caixa.modelo.StatusCobranca.PAGA or c.valorPago is not null)
              and not exists (select p.id from PagamentoVenda p where p.mpOrderId = c.orderId)
            order by c.criadaEm
            """)
    List<CobrancaPix> pixSemVenda(@Param("limite") LocalDateTime limite);
}
