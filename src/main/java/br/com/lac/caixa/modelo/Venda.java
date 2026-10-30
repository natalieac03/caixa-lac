package br.com.lac.caixa.modelo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Uma venda registrada no caixa. O id funciona como numero da compra.
 * Guarda o total calculado, desconto promocional e valor cobrado com eventual
 * motivo de ajuste. valorTotal permanece como compatibilidade com vendas antigas.
 */
@Entity
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormaPagamento formaPagamento;

    /** Preenchido apenas quando a forma de pagamento e OUTRO. */
    private String formaPagamentoOutro;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "venda_id", nullable = false)
    private List<ItemVenda> itens = new ArrayList<>();

    private BigDecimal valorTotal;
    private BigDecimal valorCalculado;
    private BigDecimal desconto;
    private BigDecimal valorCobrado;
    private String motivoAjuste;

    @Column(nullable = false)
    private boolean cancelada;
    private LocalDateTime dataCancelamento;
    private String motivoCancelamento;
    @Version
    private long versao;

    public boolean isCancelada() { return cancelada; }
    public LocalDateTime getDataCancelamento() { return dataCancelamento; }
    public String getMotivoCancelamento() { return motivoCancelamento; }

    /** Cancela uma vez; os valores e o token originais continuam no histórico. */
    public void cancelar(String motivo, LocalDateTime quando) {
        if (cancelada) return;
        if (motivo == null || motivo.isBlank() || motivo.trim().length() > 255)
            throw new IllegalArgumentException("Informe um motivo de cancelamento com até 255 caracteres.");
        if (quando == null) throw new IllegalArgumentException("Informe a data do cancelamento.");
        cancelada = true; dataCancelamento = quando; motivoCancelamento = motivo.trim();
    }

    @Column(unique = true)
    private String token;
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public BigDecimal getValorCalculado() { return valorCalculado; }
    public BigDecimal getDesconto() { return desconto; }
    public BigDecimal getValorCobrado() { return valorCobrado != null ? valorCobrado : valorTotal; }
    public String getMotivoAjuste() { return motivoAjuste; }
    public void registrarValores(BigDecimal calculado, BigDecimal desconto, BigDecimal cobrado, String motivo) {
        this.valorCalculado = calculado; this.desconto = desconto; this.valorCobrado = cobrado;
        this.valorTotal = cobrado; this.motivoAjuste = motivo;
    }


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
        return getValorCobrado();
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }
}
