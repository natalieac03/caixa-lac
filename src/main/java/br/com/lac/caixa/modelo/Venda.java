package br.com.lac.caixa.modelo;

import br.com.lac.caixa.util.Dinheiro;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Uma venda registrada no caixa. O id funciona como numero da compra.
 * Guarda o total calculado, desconto promocional e valor cobrado com eventual
 * motivo de ajuste. valorTotal permanece como compatibilidade com vendas antigas.
 *
 * O pagamento fica em PagamentoVenda (uma linha por forma; venda dividida tem
 * várias). A soma das linhas é exatamente o valor cobrado; cortesia (cobrado
 * zero) não tem linha. As colunas antigas forma_pagamento e
 * forma_pagamento_outro continuam no banco, sem uso desde a V6.
 */
@Entity
public class Venda {

    /** Limite de partes num pagamento dividido. */
    public static final int MAXIMO_DE_PAGAMENTOS = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "venda_id", nullable = false)
    private List<ItemVenda> itens = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "venda_id", nullable = false)
    @OrderBy("id")
    private List<PagamentoVenda> pagamentos = new ArrayList<>();

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

    @Column(unique = true)
    private String token;

    public Venda() {
    }

    /** Venda nova, com a hora de agora. */
    public static Venda nova() {
        Venda v = new Venda();
        v.dataHora = LocalDateTime.now();
        return v;
    }

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

    /**
     * Troca as partes do pagamento. Regras: soma exatamente igual ao valor
     * cobrado, ao centavo; cortesia (cobrado zero) não tem pagamento e exige
     * motivo do ajuste; no máximo MAXIMO_DE_PAGAMENTOS partes.
     */
    public void registrarPagamentos(List<PagamentoVenda> partes) {
        BigDecimal cobrado = getValorCobrado();
        if (cobrado == null) throw new IllegalStateException("Registre os valores da venda antes do pagamento.");
        if (cobrado.signum() == 0) {
            if (!partes.isEmpty()) throw new IllegalArgumentException("Cortesia (valor zero) não tem pagamento.");
            if (motivoAjuste == null || motivoAjuste.isBlank()) {
                throw new IllegalArgumentException("Cortesia (valor zero) exige o motivo do ajuste.");
            }
        } else {
            if (partes.isEmpty()) throw new IllegalArgumentException("Escolha a forma de pagamento.");
            if (partes.size() > MAXIMO_DE_PAGAMENTOS) {
                throw new IllegalArgumentException("No máximo " + MAXIMO_DE_PAGAMENTOS + " partes no pagamento.");
            }
            BigDecimal soma = partes.stream().map(PagamentoVenda::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            int diferenca = soma.compareTo(cobrado);
            if (diferenca != 0) {
                String quanto = Dinheiro.reais(soma.subtract(cobrado).abs());
                throw new IllegalArgumentException("A soma do pagamento (" + Dinheiro.reais(soma)
                        + ") não bate com o total (" + Dinheiro.reais(cobrado) + "): "
                        + (diferenca < 0 ? "falta " : "sobra ") + quanto + ".");
            }
        }
        pagamentos.clear();
        pagamentos.addAll(partes);
    }

    public List<PagamentoVenda> getPagamentos() { return pagamentos; }

    public boolean isCortesia() { return pagamentos.isEmpty(); }

    public boolean isPagamentoDividido() { return pagamentos.size() > 1; }

    /** "Pix R$ 10,00 + Dinheiro R$ 8,00 (troco R$ 2,00)", ou "Cortesia". */
    public String getPagamentoTexto() {
        if (pagamentos.isEmpty()) return "Cortesia";
        return pagamentos.stream().map(PagamentoVenda::getTexto).collect(Collectors.joining(" + "));
    }

    /** Soma das partes pagas numa forma (zero se não usou). */
    public BigDecimal valorPago(FormaPagamento forma) {
        return pagamentos.stream().filter(p -> p.getForma() == forma)
                .map(PagamentoVenda::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
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

    public Long getId() {
        return id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
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
