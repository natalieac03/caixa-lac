package br.com.lac.caixa.modelo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Combo de promoção: cada PromocaoItem define quantas unidades de qual
 * categoria entram no combo. O preco é o valor total do combo (PACOTE) ou
 * o valor por unidade de todas as categorias (A_PARTIR_DE).
 *
 * Ex. PACOTE: 2 Bóton + 3 Adesivo Grande = R$ 25 o conjunto.
 * Ex. A_PARTIR_DE: a partir de 2 Bóton, cada um sai a R$ 5.
 */
@Entity
public class Promocao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PromocaoItem> itens = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private TipoPromocao tipo = TipoPromocao.A_PARTIR_DE;

    /** Preço unitário (A_PARTIR_DE) ou total do pacote (PACOTE). */
    private BigDecimal preco;

    private boolean ativo = true;

    public Promocao() {
    }

    public Promocao(List<PromocaoItem> itens, TipoPromocao tipo, BigDecimal preco) {
        this.itens = itens;
        this.tipo = tipo;
        this.preco = preco;
    }

    /** "Bóton x2 + Adesivo Grande x3" */
    public String getNomesCategorias() {
        return itens.stream()
                .sorted(Comparator.comparing(i -> i.getCategoria().getNome()))
                .map(i -> i.getCategoria().getNome() + " x" + i.getQuantidade())
                .collect(Collectors.joining(" + "));
    }

    /**
     * Quantos combos completos cabem dado o estoque disponível no carrinho.
     * Ex.: exige 2 Bóton + 3 Adesivo Grande, tem 5 bótons e 7 adesivos
     * → min(5/2, 7/3) = min(2, 2) = 2 combos.
     */
    public int combosDisponiveis(Map<Long, Integer> qtdPorCategoria) {
        if (itens.isEmpty()) return 0;
        int min = Integer.MAX_VALUE;
        for (PromocaoItem pi : itens) {
            int disp = qtdPorCategoria.getOrDefault(pi.getCategoria().getId(), 0);
            min = Math.min(min, disp / pi.getQuantidade());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /** Total de unidades exigidas pelo combo (soma de todas as categorias). */
    public int getTotalUnidades() {
        return itens.stream().mapToInt(PromocaoItem::getQuantidade).sum();
    }

    /** Preço por unidade do combo: usado para aplicar o melhor negócio primeiro. */
    public double getPrecoPorUnidade() {
        int total = getTotalUnidades();
        return total == 0 ? Double.MAX_VALUE : preco.doubleValue() / total;
    }

    public Long getId() { return id; }
    public List<PromocaoItem> getItens() { return itens; }
    public TipoPromocao getTipo() { return tipo; }
    public BigDecimal getPreco() { return preco; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
