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

    private String nome;

    private BigDecimal preco;

    private int quantidade;

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
        this.preco = preco;
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
        this.preco = preco;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
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
