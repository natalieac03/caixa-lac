package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.TaxaPagamento;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acesso às taxas: a vigente no dia e as vendas sem taxa registrada. */
public interface TaxaPagamentoRepository extends JpaRepository<TaxaPagamento, Long> {

    Optional<TaxaPagamento> findFirstByFormaAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            FormaPagamento forma, LocalDate dia);

    Optional<TaxaPagamento> findByFormaAndVigenteDesde(FormaPagamento forma, LocalDate vigenteDesde);

    List<TaxaPagamento> findAllByOrderByFormaAscVigenteDesdeDesc();

    @Query("select count(distinct v) from Venda v join v.pagamentos p where p.taxaRegistrada = false")
    long vendasSemTaxaRegistrada();
}
