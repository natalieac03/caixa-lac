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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Uma venda registrada no caixa. O id funciona como numero da compra.
 * O valorTotal e calculado a partir dos itens, mas pode ser ajustado
 * manualmente antes de confirmar.
 */
@Entity
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dataHora;

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    /** Preenchido apenas quando a forma de pagamento e OUTRO. */
    private String formaPagamentoOutro;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();

    private BigDecimal valorTotal;

    public Venda() {
    }

    public Venda(FormaPagamento formaPagamento, String formaPagamentoOutro) {
        this.dataHora = LocalDateTime.now();
        this.formaPagamento = formaPagamento;
        this.formaPagamentoOutro = formaPagamentoOutro;
    }

    public void adicionarItem(ItemVenda item) {
        this.itens.add(item);
    }

    /** Soma os subtotais de todos os itens. */
    public BigDecimal calcularTotal() {
        return itens.stream()
                .map(ItemVenda::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getQuantidadeTotal() {
        return itens.stream().mapToInt(ItemVenda::getQuantidade).sum();
    }

    /** Texto curto para a tabela do historico: "X-Burger x2, Agua x1". */
    public String getResumoProdutos() {
        return itens.stream()
                .map(i -> i.getProduto().getNome() + " x" + i.getQuantidade())
                .collect(Collectors.joining(", "));
    }

    public String getFormaPagamentoTexto() {
        if (formaPagamento == FormaPagamento.OUTRO
                && formaPagamentoOutro != null && !formaPagamentoOutro.isBlank()) {
            return formaPagamentoOutro;
        }
        return formaPagamento.getRotulo();
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public String getFormaPagamentoOutro() {
        return formaPagamentoOutro;
    }

    public List<ItemVenda> getItens() {
        return itens;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }
}
