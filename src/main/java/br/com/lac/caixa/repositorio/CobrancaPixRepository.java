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

    /** Sem bloqueio: para decidir e consultar o Mercado Pago (o bloqueio vem depois, só para aplicar). */
    Optional<CobrancaPix> findByTokenAndParte(String token, int parte);

    Optional<CobrancaPix> findByOrderId(String orderId);

    /** Com bloqueio: duas consultas ou um webhook ao mesmo tempo não se atropelam ao aplicar. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CobrancaPix c where c.id = :id")
    Optional<CobrancaPix> buscarPorIdParaAtualizar(@Param("id") Long id);

    List<CobrancaPix> findByStatusAndCriadaEmBefore(StatusCobranca status, LocalDateTime limite);

    /**
     * Dinheiro que entrou sem venda válida: Pix PAGO (ou pago com valor diferente,
     * e não estornado) que nenhuma parte de venda NÃO cancelada usa, ainda não
     * resolvido, criado antes do limite (quem está com a tela aberta ainda pode
     * confirmar). Pix de venda cancelada aparece: é para estornar.
     */
    @Query("""
            select c from CobrancaPix c
            where c.resolvidaEm is null and c.criadaEm < :limite
              and c.status <> br.com.lac.caixa.modelo.StatusCobranca.ESTORNADA
              and (c.status = br.com.lac.caixa.modelo.StatusCobranca.PAGA or c.valorPago is not null)
              and not exists (select p.id from Venda v join v.pagamentos p
                              where p.mpOrderId = c.orderId and v.cancelada = false)
            order by c.criadaEm
            """)
    List<CobrancaPix> pixSemVenda(@Param("limite") LocalDateTime limite);

    /** Das orders dadas, as que estão numa parte de pagamento de venda cancelada. */
    @Query("""
            select distinct p.mpOrderId from Venda v join v.pagamentos p
            where v.cancelada = true and p.mpOrderId in :orders
            """)
    List<String> ordersDeVendaCancelada(@Param("orders") List<String> orders);
}
