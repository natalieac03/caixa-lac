package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entrada de estoque por compra: o documento da compra (custo total exato, data
 * e observação) e o cálculo do custo médio que ela gerou (antes e depois).
 */
@Entity
public class CompraEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private int quantidade;

    @Column(nullable = false)
    private BigDecimal custoTotal;

    @Column(nullable = false)
    private LocalDate dataCompra;

    private String observacao;

    @Column(nullable = false)
    private int estoqueAnterior;

    @Column(precision = 38, scale = 4)
    private BigDecimal custoAnterior;

    @Column(nullable = false, precision = 38, scale = 4)
    private BigDecimal custoNovo;

    @Column(nullable = false)
    private LocalDateTime registradaEm;

    protected CompraEstoque() {}

    /**
     * Aplica a compra no produto (estoque e custo médio) e guarda o registro.
     * A data da compra não pode ser futura.
     */
    public static CompraEstoque registrar(
            Produto produto,
            int quantidade,
            BigDecimal custoTotal,
            LocalDate dataCompra,
            String observacao,
            LocalDateTime agora) {
        if (dataCompra == null) throw new RegraDeNegocioException("Informe a data da compra.");
        if (dataCompra.isAfter(agora.toLocalDate()))
            throw new RegraDeNegocioException("A data da compra não pode ser futura.");
        String obs = observacao == null || observacao.isBlank() ? null : observacao.trim();
        if (obs != null && obs.length() > 255) throw new RegraDeNegocioException("Observação com até 255 caracteres.");
        var c = new CompraEstoque();
        c.produto = produto;
        c.quantidade = quantidade;
        c.custoTotal = custoTotal;
        c.dataCompra = dataCompra;
        c.observacao = obs;
        c.estoqueAnterior = produto.getQuantidade();
        c.custoAnterior = produto.getCustoUnitario();
        produto.registrarCompra(quantidade, custoTotal);
        c.custoNovo = produto.getCustoUnitario();
        c.registradaEm = agora;
        return c;
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

    public BigDecimal getCustoTotal() {
        return custoTotal;
    }

    public LocalDate getDataCompra() {
        return dataCompra;
    }

    public String getObservacao() {
        return observacao;
    }

    public int getEstoqueAnterior() {
        return estoqueAnterior;
    }

    public BigDecimal getCustoAnterior() {
        return custoAnterior;
    }

    public BigDecimal getCustoNovo() {
        return custoNovo;
    }

    public LocalDateTime getRegistradaEm() {
        return registradaEm;
    }
}
