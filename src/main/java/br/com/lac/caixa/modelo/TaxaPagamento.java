package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Taxa de uma forma de pagamento a partir de uma data: percentual mais valor fixo. */
@Entity
public class TaxaPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FormaPagamento forma;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal percentual;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal valorFixo;

    @Column(nullable = false)
    private LocalDate vigenteDesde;

    @Column(nullable = false)
    private LocalDateTime registradaEm;

    protected TaxaPagamento() {}

    public TaxaPagamento(
            FormaPagamento forma,
            BigDecimal percentual,
            BigDecimal valorFixo,
            LocalDate vigenteDesde,
            LocalDateTime agora) {
        if (forma == null) throw new RegraDeNegocioException("Escolha a forma de pagamento.");
        if (vigenteDesde == null) throw new RegraDeNegocioException("Informe a data de vigência.");
        this.forma = forma;
        this.vigenteDesde = vigenteDesde;
        alterar(percentual, valorFixo, agora);
    }

    public void alterar(BigDecimal percentual, BigDecimal valorFixo, LocalDateTime agora) {
        BigDecimal p = percentual == null ? BigDecimal.ZERO : percentual;
        BigDecimal f = valorFixo == null ? BigDecimal.ZERO : valorFixo;
        if (p.signum() < 0 || p.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new RegraDeNegocioException("O percentual da taxa deve estar entre 0 e 100.");
        if (p.stripTrailingZeros().scale() > 4)
            throw new RegraDeNegocioException("Percentual com no máximo 4 casas decimais.");
        if (f.signum() < 0) throw new RegraDeNegocioException("O valor fixo da taxa não pode ser negativo.");
        if (f.stripTrailingZeros().scale() > 2) throw new RegraDeNegocioException("Valor fixo com no máximo 2 casas.");
        this.percentual = p.setScale(4);
        this.valorFixo = f.setScale(2);
        this.registradaEm = agora;
    }

    public BigDecimal estimar(BigDecimal valor) {
        return valor.multiply(percentual)
                .divide(BigDecimal.valueOf(100))
                .add(valorFixo)
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    public Long getId() {
        return id;
    }

    public FormaPagamento getForma() {
        return forma;
    }

    public BigDecimal getPercentual() {
        return percentual;
    }

    public BigDecimal getValorFixo() {
        return valorFixo;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }

    public LocalDateTime getRegistradaEm() {
        return registradaEm;
    }
}
