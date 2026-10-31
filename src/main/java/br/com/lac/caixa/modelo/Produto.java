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

/**
 * Um item do estoque da LAC: tem nome, preco, quantidade disponivel
 * e uma categoria (ex.: "Bóton", "Adesivo Grande").
 */
@Entity
public class Produto {
    /** Casas decimais do custo (o custo médio é uma divisão). */
    public static final int ESCALA_CUSTO = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    private BigDecimal preco;

    private int quantidade;

    /** Controle otimista: duas baixas simultâneas do mesmo produto não passam juntas. */
    @Version
    @Column(columnDefinition = "bigint default 0")
    private long versao;

    @ManyToOne
    private Categoria categoria;

    /**
     * Produto arquivado (false) não aparece no estoque nem na tela de venda,
     * mas continua existindo para não quebrar o histórico de vendas antigas.
     */
    private boolean ativo = true;

    /** Limite de estoque baixo (opcional): o alerta avisa quando a quantidade chega a ele. */
    private Integer estoqueMinimo;

    /**
     * Custo unitário atual (custo médio ponderado das compras), com
     * {@link #ESCALA_CUSTO} casas. Opcional: null = ainda não informado ("sem
     * custo"). Só para as finanças; não muda o preço cobrado.
     */
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

    /**
     * Edição do cadastro. O preço novo só vale para vendas novas: cada item de
     * venda guarda o preço unitário do momento da venda (ItemVenda).
     */
    public void editar(String nome, BigDecimal preco, Categoria categoria) {
        setNome(nome);
        setPreco(preco);
        setCategoria(categoria);
    }

    /** Edição com custo: custo vazio (null) = sem custo. */
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

    /**
     * Entrada por compra: soma ao estoque e recalcula o custo médio ponderado,
     * novo = (estoque x custo atual + custo da compra) / (estoque + quantidade).
     * Sem custo atual, assume o custo unitário da compra.
     */
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

    /** Tira unidades do estoque; nunca deixa negativo. */
    public void baixar(int qtd) {
        if (qtd < 1) throw new RegraDeNegocioException("Quantidade deve ser maior que zero.");
        if (qtd > quantidade) {
            throw new EstoqueInsuficienteException(
                    "Estoque insuficiente de " + nome + " (disponível: " + quantidade + ").");
        }
        quantidade -= qtd;
    }

    /** Devolve ou soma unidades ao estoque. */
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

    /** Nome da categoria para exibição (vazio se não tiver). */
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

    /** null = sem limite (o alerta usa a média de vendas do último evento). */
    public void setEstoqueMinimo(Integer limite) {
        if (limite != null && limite < 0)
            throw new RegraDeNegocioException("O limite de estoque baixo não pode ser negativo.");
        this.estoqueMinimo = limite;
    }

    /** Com limite: a quantidade chegou a ele (menor ou igual). */
    public boolean isAbaixoDoLimite() {
        return estoqueMinimo != null && quantidade <= estoqueMinimo;
    }
}
