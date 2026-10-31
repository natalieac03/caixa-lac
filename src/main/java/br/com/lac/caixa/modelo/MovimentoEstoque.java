package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Um movimento do estoque, com sinal: a soma dos movimentos é o estoque do produto. */
@Entity
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoMovimento tipo;

    @Column(nullable = false)
    private int quantidade;

    @Column(precision = 38, scale = 4)
    private BigDecimal custoUnitario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id")
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_id")
    private CompraEstoque compra;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    private String observacao;

    protected MovimentoEstoque() {}

    public MovimentoEstoque(
            Produto produto,
            TipoMovimento tipo,
            int unidades,
            BigDecimal custoUnitario,
            Venda venda,
            CompraEstoque compra,
            LocalDateTime dataHora,
            String observacao) {
        if (unidades < 1) throw new RegraDeNegocioException("Movimento de estoque sem unidades.");
        boolean daVenda = tipo == TipoMovimento.SAIDA_VENDA || tipo == TipoMovimento.DEVOLUCAO_CANCELAMENTO;
        if (daVenda != (venda != null)) throw new IllegalStateException("Movimento " + tipo + " e venda não combinam.");
        if ((tipo == TipoMovimento.ENTRADA_COMPRA) != (compra != null)) {
            throw new IllegalStateException("Movimento " + tipo + " e compra não combinam.");
        }
        String obs = observacao == null || observacao.isBlank() ? null : observacao.trim();
        this.produto = produto;
        this.tipo = tipo;
        this.quantidade = tipo.getSinal() * unidades;
        this.custoUnitario = custoUnitario;
        this.venda = venda;
        this.compra = compra;
        this.dataHora = dataHora;
        this.observacao = obs != null && obs.length() > 255 ? obs.substring(0, 255) : obs;
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public TipoMovimento getTipo() {
        return tipo;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public BigDecimal getCustoUnitario() {
        return custoUnitario;
    }

    public Venda getVenda() {
        return venda;
    }

    public CompraEstoque getCompra() {
        return compra;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getObservacao() {
        return observacao;
    }
}
