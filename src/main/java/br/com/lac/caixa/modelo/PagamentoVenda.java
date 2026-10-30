package br.com.lac.caixa.modelo;

import br.com.lac.caixa.util.Dinheiro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Positive;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Uma parte do pagamento de uma venda: forma + valor. Venda paga com Pix e
 * dinheiro tem duas linhas. Em dinheiro, o valor recebido (opcional) dá o troco.
 */
@Entity
public class PagamentoVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormaPagamento forma;

    @Positive(message = "O valor de cada parte do pagamento deve ser maior que zero.")
    @Column(nullable = false)
    private BigDecimal valor;

    /** Só para OUTRO (ex.: "Vale"). */
    private String descricao;

    /** Só para DINHEIRO: quanto a pessoa entregou. */
    private BigDecimal valorRecebido;

    protected PagamentoVenda() {
    }

    public PagamentoVenda(FormaPagamento forma, BigDecimal valor, String descricao, BigDecimal valorRecebido) {
        if (forma == null) throw new IllegalArgumentException("Escolha a forma de pagamento.");
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("O valor de cada parte do pagamento deve ser maior que zero.");
        }
        String texto = descricao == null ? "" : descricao.trim();
        if (forma == FormaPagamento.OUTRO && texto.isEmpty()) {
            throw new IllegalArgumentException("Pagamento em Outro precisa de descrição (ex.: vale, permuta).");
        }
        if (texto.length() > 255) throw new IllegalArgumentException("Descrição do pagamento com até 255 caracteres.");
        if (valorRecebido != null) {
            if (forma != FormaPagamento.DINHEIRO) {
                throw new IllegalArgumentException("Valor recebido só vale para pagamento em dinheiro.");
            }
            if (valorRecebido.compareTo(valor) < 0) {
                throw new IllegalArgumentException("Recebido em dinheiro (" + Dinheiro.reais(valorRecebido)
                        + ") é menor que o valor em dinheiro (" + Dinheiro.reais(valor) + ").");
            }
        }
        this.forma = forma;
        this.valor = valor;
        this.descricao = forma == FormaPagamento.OUTRO ? texto : null;
        this.valorRecebido = valorRecebido;
    }

    /** Troco do dinheiro; null se não informou o recebido. */
    public BigDecimal getTroco() {
        return valorRecebido == null ? null : valorRecebido.subtract(valor);
    }

    /** "Pix", "Dinheiro" ou a descrição do Outro. */
    public String getNomeForma() {
        if (forma == FormaPagamento.OUTRO && descricao != null && !descricao.isBlank()) return descricao;
        return forma.getRotulo();
    }

    /** "Pix R$ 10,00" ou "Dinheiro R$ 8,00 (troco R$ 2,00)". */
    public String getTexto() {
        String texto = getNomeForma() + " " + Dinheiro.reais(valor);
        return getTroco() == null ? texto : texto + " (troco " + Dinheiro.reais(getTroco()) + ")";
    }

    public Long getId() { return id; }
    public FormaPagamento getForma() { return forma; }
    public BigDecimal getValor() { return valor; }
    public String getDescricao() { return descricao; }
    public BigDecimal getValorRecebido() { return valorRecebido; }
}
