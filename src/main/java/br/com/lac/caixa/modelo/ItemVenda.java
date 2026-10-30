package br.com.lac.caixa.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

/**
 * Liga um Produto a uma quantidade dentro de uma Venda.
 * Guarda o preco no momento da venda (precoUnitario), para que
 * mudancas de preco no estoque nao alterem o historico.
 */
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

    /**
     * Custo unitário do produto no momento da venda, congelado como o preço
     * (mudar o custo depois não altera vendas feitas). null = produto sem custo.
     */
    @Column(precision = 38, scale = 4)
    private BigDecimal custoUnitario;

    /**
     * Preço normal do produto x quantidade, sem promoção. O desconto da venda
     * (promoções + ajuste manual) é rateado entre os itens proporcional a este
     * valor: valorLiquido = valorBruto - descontoRateado, e a soma dos líquidos
     * é exatamente o valor cobrado. Vendas antigas: preenchido pela V12.
     */
    @Column(precision = 38, scale = 2)
    private BigDecimal valorBruto;

    /** Pode ser negativo (ajuste para mais). */
    @Column(precision = 38, scale = 2)
    private BigDecimal descontoRateado;

    @Column(precision = 38, scale = 2)
    private BigDecimal valorLiquido;

    public ItemVenda() {}

    public ItemVenda(Produto produto, int quantidade) {
        this(produto, quantidade, produto.getPreco());
    }

    /** Permite informar o preço unitário (por exemplo, já com promoção aplicada). */
    public ItemVenda(Produto produto, int quantidade, BigDecimal precoUnitario) {
        this.produto = produto;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
        this.custoUnitario = produto.getCustoUnitario();
        this.valorBruto =
                produto.getPreco().multiply(BigDecimal.valueOf(quantidade)).setScale(2);
    }

    /** Recebe a parte do desconto da venda (Venda.ratearDesconto). */
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
