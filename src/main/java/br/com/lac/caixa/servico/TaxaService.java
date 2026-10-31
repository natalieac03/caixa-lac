package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.TaxaPagamento;
import br.com.lac.caixa.repositorio.TaxaPagamentoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Taxas por forma de pagamento com vigência de uma data em diante. */
@Service
public class TaxaService {
    private final TaxaPagamentoRepository taxas;
    private final Auditoria auditoria;
    private final Clock relogio;

    public TaxaService(TaxaPagamentoRepository taxas, Auditoria auditoria, Clock relogio) {
        this.taxas = taxas;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Optional<TaxaPagamento> vigente(FormaPagamento forma, LocalDate dia) {
        return taxas.findFirstByFormaAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(forma, dia);
    }

    @Transactional(readOnly = true)
    public List<TaxaPagamento> historico() {
        return taxas.findAllByOrderByFormaAscVigenteDesdeDesc();
    }

    @Transactional(readOnly = true)
    public long vendasSemTaxaRegistrada() {
        return taxas.vendasSemTaxaRegistrada();
    }

    @Transactional
    public TaxaPagamento definir(
            FormaPagamento forma, BigDecimal percentual, BigDecimal valorFixo, LocalDate vigenteDesde) {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate desde = vigenteDesde == null ? hoje : vigenteDesde;
        if (desde.isBefore(hoje))
            throw new RegraDeNegocioException(
                    "A vigência não pode ser no passado: as vendas já feitas guardaram a taxa da época.");
        LocalDateTime agora = LocalDateTime.now(relogio);
        var existente =
                forma == null ? Optional.<TaxaPagamento>empty() : taxas.findByFormaAndVigenteDesde(forma, desde);
        TaxaPagamento taxa;
        if (existente.isPresent()) {
            taxa = existente.get();
            taxa.alterar(percentual, valorFixo, agora);
        } else {
            taxa = new TaxaPagamento(forma, percentual, valorFixo, desde, agora);
        }
        TaxaPagamento salva = taxas.save(taxa);
        auditoria.registrar(
                "taxa_definida",
                "id",
                salva.getId(),
                "forma",
                salva.getForma(),
                "percentual",
                salva.getPercentual(),
                "fixo",
                salva.getValorFixo(),
                "desde",
                salva.getVigenteDesde());
        return salva;
    }
}
