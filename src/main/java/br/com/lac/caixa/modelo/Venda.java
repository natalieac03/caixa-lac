package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.util.Dinheiro;
import br.com.lac.caixa.util.Rateio;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Uma venda: itens, valores calculado e cobrado, partes do pagamento e cancelamento. */
@Entity
public class Venda {

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

    private BigDecimal valorCalculado;
    private BigDecimal desconto;

    @Column(nullable = false)
    private BigDecimal valorCobrado;

    private String motivoAjuste;

    @Column(nullable = false)
    private boolean cancelada;

    private LocalDateTime dataCancelamento;
    private String motivoCancelamento;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "venda_id", nullable = false)
    @OrderBy("id")
    private List<VendaDesconto> descontosAplicados = new ArrayList<>();

    @Column(nullable = false)
    private boolean rateioRetroativo;

    @Version
    private long versao;

    @Column(unique = true)
    private String token;

    public Venda() {}

    public static Venda nova(LocalDateTime quando) {
        Venda v = new Venda();
        v.dataHora = quando;
        return v;
    }

    public boolean isCancelada() {
        return cancelada;
    }

    public LocalDateTime getDataCancelamento() {
        return dataCancelamento;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public void cancelar(String motivo, LocalDateTime quando) {
        if (cancelada) return;
        if (motivo == null || motivo.isBlank() || motivo.trim().length() > 255)
            throw new RegraDeNegocioException("Informe um motivo de cancelamento com até 255 caracteres.");
        if (quando == null) throw new RegraDeNegocioException("Informe a data do cancelamento.");
        cancelada = true;
        dataCancelamento = quando;
        motivoCancelamento = motivo.trim();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public BigDecimal getValorCalculado() {
        return valorCalculado;
    }

    public BigDecimal getDesconto() {
        return desconto;
    }

    public BigDecimal getValorCobrado() {
        return valorCobrado;
    }

    public String getMotivoAjuste() {
        return motivoAjuste;
    }

    public void registrarValores(BigDecimal calculado, BigDecimal desconto, BigDecimal cobrado, String motivo) {
        this.valorCalculado = calculado;
        this.desconto = desconto;
        this.valorCobrado = cobrado;
        this.motivoAjuste = motivo;
    }

    public void registrarPagamentos(List<PagamentoVenda> partes) {
        BigDecimal cobrado = getValorCobrado();
        if (cobrado == null) throw new IllegalStateException("Registre os valores da venda antes do pagamento.");
        if (cobrado.signum() == 0) {
            if (!partes.isEmpty()) throw new RegraDeNegocioException("Cortesia (valor zero) não tem pagamento.");
            if (motivoAjuste == null || motivoAjuste.isBlank()) {
                throw new RegraDeNegocioException("Cortesia (valor zero) exige o motivo do ajuste.");
            }
        } else {
            if (partes.isEmpty()) throw new RegraDeNegocioException("Escolha a forma de pagamento.");
            if (partes.size() > MAXIMO_DE_PAGAMENTOS) {
                throw new RegraDeNegocioException("No máximo " + MAXIMO_DE_PAGAMENTOS + " partes no pagamento.");
            }
            BigDecimal soma = partes.stream().map(PagamentoVenda::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            int diferenca = soma.compareTo(cobrado);
            if (diferenca != 0) {
                String quanto = Dinheiro.reais(soma.subtract(cobrado).abs());
                throw new RegraDeNegocioException("A soma do pagamento (" + Dinheiro.reais(soma)
                        + ") não bate com o total (" + Dinheiro.reais(cobrado) + "): "
                        + (diferenca < 0 ? "falta " : "sobra ") + quanto + ".");
            }
        }
        pagamentos.clear();
        pagamentos.addAll(partes);
    }

    public List<PagamentoVenda> getPagamentos() {
        return pagamentos;
    }

    public boolean isCortesia() {
        return pagamentos.isEmpty();
    }

    public boolean isPagamentoDividido() {
        return pagamentos.size() > 1;
    }

    public String getPagamentoTexto() {
        if (pagamentos.isEmpty()) return "Cortesia";
        return pagamentos.stream().map(PagamentoVenda::getTexto).collect(Collectors.joining(" + "));
    }

    public BigDecimal valorPago(FormaPagamento forma) {
        return pagamentos.stream()
                .filter(p -> p.getForma() == forma)
                .map(PagamentoVenda::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void adicionarItem(ItemVenda item) {
        this.itens.add(item);
    }

    public void registrarDescontos(List<VendaDesconto> descontos) {
        descontosAplicados.clear();
        descontosAplicados.addAll(descontos);
    }

    public List<VendaDesconto> getDescontosAplicados() {
        return descontosAplicados;
    }

    public boolean isRateioRetroativo() {
        return rateioRetroativo;
    }

    public void ratearDesconto() {
        if (valorCobrado == null) throw new IllegalStateException("Registre os valores da venda antes do rateio.");
        long[] brutos = itens.stream()
                .mapToLong(i -> Rateio.centavos(i.getValorBruto()))
                .toArray();
        long[] quantidades = itens.stream().mapToLong(ItemVenda::getQuantidade).toArray();
        long desconto = Arrays.stream(brutos).sum() - Rateio.centavos(valorCobrado);
        long[] partes = Rateio.ratear(desconto, brutos, quantidades);
        for (int i = 0; i < itens.size(); i++) itens.get(i).registrarRateio(Rateio.reais(partes[i]));
    }

    public BigDecimal calcularTotal() {
        return itens.stream().map(ItemVenda::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getQuantidadeTotal() {
        return itens.stream().mapToInt(ItemVenda::getQuantidade).sum();
    }

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
}
