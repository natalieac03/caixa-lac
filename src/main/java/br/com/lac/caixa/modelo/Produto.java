package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.dominio.EstoqueInsuficienteException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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

    @Column(nullable = false)
    private String nome;

    @Positive(message = "Preço deve ser maior que zero.")
    private BigDecimal preco;

    @PositiveOrZero(message = "Quantidade não pode ser negativa.")
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

    public Produto() {
    }

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
        if (nome == null || nome.isBlank()) throw new RegraDeNegocioException("O nome do produto não pode ficar vazio.");
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

    public boolean temEstoquePara(int qtd) {
        return this.quantidade >= qtd;
    }
}
