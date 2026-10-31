package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.EstoqueInsuficienteException;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Um item do estoque: nome, preço, quantidade, categoria e custo médio. */
@Entity
public class Produto {
    public static final int ESCALA_CUSTO = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    private BigDecimal preco;

    private int quantidade;

    @Version
    @Column(columnDefinition = "bigint default 0")
    private long versao;

    @ManyToOne
    private Categoria categoria;

    private boolean ativo = true;

    private Integer estoqueMinimo;

    @Column(precision = 38, scale = ESCALA_CUSTO)
    private BigDecimal custoUnitario;

    public Produto() {}

    public Produto(String nome, BigDecimal preco, int quantidade) {
        setNome(nome);
        setPreco(preco);
        if (quantidade < 0) throw new RegraDeNegocioException("Quantidade não pode ser negativa.");
        this.quantidade = quantidade;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        if (nome == null || nome.isBlank())
            throw new RegraDeNegocioException("O nome do produto não pode ficar vazio.");
        if (nome.trim().length() > 255) throw new RegraDeNegocioException("Nome do produto com até 255 caracteres.");
        this.nome = nome.trim();
    }

    public void editar(String nome, BigDecimal preco, Categoria categoria) {
        setNome(nome);
        setPreco(preco);
        setCategoria(categoria);
    }

    public void editar(String nome, BigDecimal preco, Categoria categoria, BigDecimal custo) {
        editar(nome, preco, categoria);
        setCustoUnitario(custo);
    }

    public BigDecimal getCustoUnitario() {
        return custoUnitario;
    }

    public void setCustoUnitario(BigDecimal custo) {
        if (custo != null && custo.signum() < 0) throw new RegraDeNegocioException("O custo não pode ser negativo.");
        this.custoUnitario = custo == null ? null : custo.setScale(ESCALA_CUSTO, RoundingMode.HALF_EVEN);
    }

    public boolean isSemCusto() {
        return custoUnitario == null;
    }

    public void registrarCompra(int qtd, BigDecimal custoTotal) {
        if (qtd < 1) throw new RegraDeNegocioException("Quantidade deve ser maior que zero.");
        if (custoTotal == null || custoTotal.signum() < 0) {
            throw new RegraDeNegocioException("Informe o custo total da compra (zero ou mais).");
        }
        BigDecimal novoCusto;
        if (custoUnitario == null) {
            novoCusto = custoTotal.divide(BigDecimal.valueOf(qtd), ESCALA_CUSTO, RoundingMode.HALF_EVEN);
        } else {
            BigDecimal valorEmEstoque = custoUnitario.multiply(BigDecimal.valueOf(quantidade));
            novoCusto = valorEmEstoque
                    .add(custoTotal)
                    .divide(BigDecimal.valueOf((long) quantidade + qtd), ESCALA_CUSTO, RoundingMode.HALF_EVEN);
        }
        repor(qtd);
        custoUnitario = novoCusto;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        if (preco == null || preco.signum() <= 0) throw new RegraDeNegocioException("Preço deve ser maior que zero.");
        this.preco = preco;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void baixar(int qtd) {
        if (qtd < 1) throw new RegraDeNegocioException("Quantidade deve ser maior que zero.");
        if (qtd > quantidade) {
            throw new EstoqueInsuficienteException(
                    "Estoque insuficiente de " + nome + " (disponível: " + quantidade + ").");
        }
        quantidade -= qtd;
    }

    public void repor(int qtd) {
        if (qtd < 1) throw new RegraDeNegocioException("Quantidade deve ser maior que zero.");
        quantidade = Math.addExact(quantidade, qtd);
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public String getNomeCategoria() {
        return categoria != null ? categoria.getNome() : "";
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Integer getEstoqueMinimo() {
        return estoqueMinimo;
    }

    public void setEstoqueMinimo(Integer limite) {
        if (limite != null && limite < 0)
            throw new RegraDeNegocioException("O limite de estoque baixo não pode ser negativo.");
        this.estoqueMinimo = limite;
    }

    public boolean isAbaixoDoLimite() {
        return estoqueMinimo != null && quantidade <= estoqueMinimo;
    }
}
