package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.TaxaPagamento;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaxaPagamentoRepository extends JpaRepository<TaxaPagamento, Long> {

    /** A taxa vigente no dia: a de maior vigência que não seja depois dele. */
    Optional<TaxaPagamento> findFirstByFormaAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            FormaPagamento forma, LocalDate dia);

    Optional<TaxaPagamento> findByFormaAndVigenteDesde(FormaPagamento forma, LocalDate vigenteDesde);

    List<TaxaPagamento> findAllByOrderByFormaAscVigenteDesdeDesc();

    /** Vendas com alguma parte de pagamento sem taxa registrada (anteriores às taxas). */
    @Query("select count(distinct v) from Venda v join v.pagamentos p where p.taxaRegistrada = false")
    long vendasSemTaxaRegistrada();
}
