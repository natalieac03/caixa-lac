package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Promoção por categoria: pacote com preço fechado ou preço a partir de uma quantidade. */
@Entity
public class Promocao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "promocao_id", nullable = false)
    private List<PromocaoItem> itens = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    private TipoPromocao tipo = TipoPromocao.A_PARTIR_DE;

    private BigDecimal preco;

    private boolean ativo = true;

    public Promocao() {}

    public Promocao(List<PromocaoItem> itens, TipoPromocao tipo, BigDecimal preco) {
        if (preco == null || preco.signum() <= 0) throw new RegraDeNegocioException("Preço deve ser maior que zero.");
        this.itens = new ArrayList<>(itens);
        this.tipo = tipo;
        this.preco = preco;
    }

    public void alterar(List<PromocaoItem> novosItens, BigDecimal novoPreco) {
        if (novoPreco == null || novoPreco.signum() <= 0)
            throw new RegraDeNegocioException("Preço deve ser maior que zero.");
        if (novosItens == null || novosItens.isEmpty()) {
            throw new RegraDeNegocioException("Selecione ao menos uma categoria com quantidade.");
        }
        itens.clear();
        itens.addAll(novosItens);
        preco = novoPreco;
    }

    public Map<Long, Integer> getQuantidadePorCategoria() {
        return itens.stream().collect(Collectors.toMap(i -> i.getCategoria().getId(), PromocaoItem::getQuantidade));
    }

    public String getNomesCategorias() {
        return itens.stream()
                .sorted(Comparator.comparing(i -> i.getCategoria().getNome()))
                .map(i -> i.getCategoria().getNome() + " x" + i.getQuantidade())
                .collect(Collectors.joining(" + "));
    }

    public int combosDisponiveis(Map<Long, Integer> qtdPorCategoria) {
        if (itens.isEmpty()) return 0;
        int min = Integer.MAX_VALUE;
        for (PromocaoItem pi : itens) {
            int disp = qtdPorCategoria.getOrDefault(pi.getCategoria().getId(), 0);
            min = Math.min(min, disp / pi.getQuantidade());
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    public Long getId() {
        return id;
    }

    public List<PromocaoItem> getItens() {
        return itens;
    }

    public TipoPromocao getTipo() {
        return tipo;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
