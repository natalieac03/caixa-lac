package br.com.lac.caixa.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

/**
 * Um item do estoque da LAC: tem nome, preco, quantidade disponivel
 * e uma categoria (ex.: "Bóton", "Adesivo Grande").
 */
@Entity
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(nullable = false)
    private String nome;

    private BigDecimal preco;

    private int quantidade;
    @jakarta.persistence.Version
    @jakarta.persistence.Column(columnDefinition = "bigint default 0")
    private long versao;


    @ManyToOne
    private Categoria categoria;

    /**
     * Produto arquivado (false) não aparece no estoque nem na tela de venda,
     * mas continua existindo para não quebrar o histórico de vendas antigas.
     */
    private boolean ativo = true;

    public Produto() {
    }

    public Produto(String nome, BigDecimal preco, int quantidade) {
        this.nome = nome;
        setPreco(preco);
        if (quantidade < 0) throw new IllegalArgumentException("Quantidade não pode ser negativa.");
        this.quantidade = quantidade;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        if (preco == null || preco.signum() <= 0) throw new IllegalArgumentException("Preço deve ser maior que zero.");
        this.preco = preco;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void baixar(int qtd) {
        if (qtd < 1) throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        if (qtd > quantidade) throw new br.com.lac.caixa.servico.EstoqueInsuficienteException("Estoque insuficiente de " + nome + " (disponível: " + quantidade + ").");
        quantidade -= qtd;
    }
    public void repor(int qtd) {
        if (qtd < 1) throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
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

    public boolean temEstoquePara(int qtd) {
        return this.quantidade >= qtd;
    }
}
