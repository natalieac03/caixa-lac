package br.com.lac.caixa.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

/** Um produto e a quantidade dentro de uma venda, com preço, custo e desconto congelados. */
@Entity
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    private int quantidade;

    private BigDecimal precoUnitario;

    @Column(precision = 38, scale = 4)
    private BigDecimal custoUnitario;

    @Column(precision = 38, scale = 2)
    private BigDecimal valorBruto;

    @Column(precision = 38, scale = 2)
    private BigDecimal descontoRateado;

    @Column(precision = 38, scale = 2)
    private BigDecimal valorLiquido;

    public ItemVenda() {}

    public ItemVenda(Produto produto, int quantidade) {
        this(produto, quantidade, produto.getPreco());
    }

    public ItemVenda(Produto produto, int quantidade, BigDecimal precoUnitario) {
        this.produto = produto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.custoUnitario = produto.getCustoUnitario();
        this.valorBruto =
                produto.getPreco().multiply(BigDecimal.valueOf(quantidade)).setScale(2);
    }

    void registrarRateio(BigDecimal desconto) {
        this.descontoRateado = desconto;
        this.valorLiquido = valorBruto.subtract(desconto);
    }

    public BigDecimal getValorBruto() {
        return valorBruto;
    }

    public BigDecimal getDescontoRateado() {
        return descontoRateado;
    }

    public BigDecimal getValorLiquido() {
        return valorLiquido;
    }

    public BigDecimal getCustoUnitario() {
        return custoUnitario;
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public BigDecimal getSubtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
