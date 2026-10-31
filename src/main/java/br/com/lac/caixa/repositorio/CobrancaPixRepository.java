package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.CobrancaPix;
import br.com.lac.caixa.modelo.StatusCobranca;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso às cobranças Pix, com e sem trava, e o Pix recebido sem venda. */
public interface CobrancaPixRepository extends JpaRepository<CobrancaPix, Long> {

    Optional<CobrancaPix> findByTokenAndParte(String token, int parte);

    Optional<CobrancaPix> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CobrancaPix c where c.id = :id")
    Optional<CobrancaPix> buscarPorIdParaAtualizar(@Param("id") Long id);

    List<CobrancaPix> findByStatusAndCriadaEmBefore(StatusCobranca status, LocalDateTime limite);

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

    @Query("""
            select distinct p.mpOrderId from Venda v join v.pagamentos p
            where v.cancelada = true and p.mpOrderId in :orders
            """)
    List<String> ordersDeVendaCancelada(@Param("orders") List<String> orders);

    @Query("""
            select new br.com.lac.caixa.repositorio.PixEstornadoDeVenda(c.orderId, c.valor, v.id, v.dataHora)
            from Venda v join v.pagamentos p join CobrancaPix c on c.orderId = p.mpOrderId
            where c.status = br.com.lac.caixa.modelo.StatusCobranca.ESTORNADA and v.cancelada = false
            order by v.dataHora desc, v.id desc
            """)
    List<PixEstornadoDeVenda> estornadosDeVendaAtiva();
}
