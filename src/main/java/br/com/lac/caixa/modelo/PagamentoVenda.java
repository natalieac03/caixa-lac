package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.pagamento.PagamentoFactory;
import br.com.lac.caixa.util.Dinheiro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(nullable = false)
    private BigDecimal valor;

    /** Só para OUTRO (ex.: "Vale"). */
    private String descricao;

    /** Só para DINHEIRO: quanto a pessoa entregou. */
    private BigDecimal valorRecebido;

    /** Só para PIX: MERCADO_PAGO (confirmado pela API) ou MANUAL (conferido no app). */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrigemPagamento origem;

    /** Id da order do Mercado Pago que pagou esta parte (só com origem MERCADO_PAGO). */
    @Column(length = 64)
    private String mpOrderId;

    /** Taxa estimada da forma, pela taxa vigente no dia da venda (zero se não há taxa). */
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal taxaEstimada = BigDecimal.ZERO.setScale(2);

    /** false nas partes anteriores às taxas (V15): "sem taxa registrada". */
    @Column(nullable = false)
    private boolean taxaRegistrada;

    /** A configuração de taxa usada (null se não havia taxa para a forma). */
    private Long taxaId;

    protected PagamentoVenda() {}

    /** Parte comum; Pix sai como manual. */
    public PagamentoVenda(FormaPagamento forma, BigDecimal valor, String descricao, BigDecimal valorRecebido) {
        this(forma, valor, descricao, valorRecebido, null, null);
    }

    /**
     * @param origem    só para PIX (null = MANUAL); outras formas não têm origem
     * @param mpOrderId obrigatório com MERCADO_PAGO, proibido nos outros casos
     */
    public PagamentoVenda(
            FormaPagamento forma,
            BigDecimal valor,
            String descricao,
            BigDecimal valorRecebido,
            OrigemPagamento origem,
            String mpOrderId) {
        if (forma == null) throw new RegraDeNegocioException("Escolha a forma de pagamento.");
        if (valor == null || valor.signum() <= 0) {
            throw new RegraDeNegocioException("O valor de cada parte do pagamento deve ser maior que zero.");
        }
        String texto = descricao == null ? "" : descricao.trim();
        if (forma == FormaPagamento.OUTRO && texto.isEmpty()) {
            throw new RegraDeNegocioException("Pagamento em Outro precisa de descrição (ex.: vale, permuta).");
        }
        if (texto.length() > 255) throw new RegraDeNegocioException("Descrição do pagamento com até 255 caracteres.");
        if (valorRecebido != null) {
            if (forma != FormaPagamento.DINHEIRO) {
                throw new RegraDeNegocioException("Valor recebido só vale para pagamento em dinheiro.");
            }
            if (valorRecebido.compareTo(valor) < 0) {
                throw new RegraDeNegocioException("Recebido em dinheiro (" + Dinheiro.reais(valorRecebido)
                        + ") é menor que o valor em dinheiro (" + Dinheiro.reais(valor) + ").");
            }
        }
        this.forma = forma;
        this.valor = valor;
        this.descricao = forma == FormaPagamento.OUTRO ? texto : null;
        this.valorRecebido = valorRecebido;
        if (forma != FormaPagamento.PIX) {
            if (origem != null || mpOrderId != null) {
                throw new RegraDeNegocioException("Só o Pix tem origem (Mercado Pago ou manual).");
            }
        } else {
            this.origem = origem == null ? OrigemPagamento.MANUAL : origem;
            if (this.origem == OrigemPagamento.MERCADO_PAGO && (mpOrderId == null || mpOrderId.isBlank())) {
                throw new RegraDeNegocioException("Pix do Mercado Pago sem a order que o pagou.");
            }
            if (this.origem == OrigemPagamento.MANUAL && mpOrderId != null) {
                throw new RegraDeNegocioException("Pix manual não tem order do Mercado Pago.");
            }
            this.mpOrderId = mpOrderId;
        }
    }

    /** Grava a taxa estimada pela taxa vigente (null = nenhuma taxa para a forma: zero). */
    public void registrarTaxa(TaxaPagamento taxa) {
        if (taxa != null && taxa.getForma() != forma) throw new IllegalArgumentException("Taxa de outra forma.");
        this.taxaEstimada = taxa == null ? BigDecimal.ZERO.setScale(2) : taxa.estimar(valor);
        this.taxaId = taxa == null ? null : taxa.getId();
        this.taxaRegistrada = true;
    }

    public BigDecimal getTaxaEstimada() {
        return taxaEstimada;
    }

    public boolean isTaxaRegistrada() {
        return taxaRegistrada;
    }

    public Long getTaxaId() {
        return taxaId;
    }

    /** Troco do dinheiro; null se não informou o recebido. */
    public BigDecimal getTroco() {
        return valorRecebido == null ? null : valorRecebido.subtract(valor);
    }

    /** "Pix", "Dinheiro" ou a descrição do Outro (pela estratégia da forma). */
    public String getNomeForma() {
        return PagamentoFactory.criar(forma).nome(this);
    }

    /** "Pix R$ 10,00" ou "Dinheiro R$ 8,00 (troco R$ 2,00)" (pela estratégia da forma). */
    public String getTexto() {
        return PagamentoFactory.criar(forma).resumir(this);
    }

    public Long getId() {
        return id;
    }

    public FormaPagamento getForma() {
        return forma;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public OrigemPagamento getOrigem() {
        return origem;
    }

    public String getMpOrderId() {
        return mpOrderId;
    }

    public BigDecimal getValorRecebido() {
        return valorRecebido;
    }
}
