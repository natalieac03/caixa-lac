package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Cobrança Pix criada no Mercado Pago para uma parte de uma venda em andamento
 * (token do formulário + número estável da parte). Só vira PAGA pela resposta
 * do Mercado Pago, e só se o valor pago for exatamente o valor da cobrança.
 */
@Entity
public class CobrancaPix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String token;

    @Column(nullable = false)
    private int parte;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private int tentativa;

    @Column(nullable = false, length = 64)
    private String orderId;

    @Column(length = 1000)
    private String qrCode;

    @Column(length = 20000)
    private String qrCodeBase64;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusCobranca status;

    private BigDecimal valorPago;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    private LocalDateTime pagaEm;

    /** Pix recebido sem venda: quando e como alguém resolveu à mão. */
    private LocalDateTime resolvidaEm;

    private String resolucao;

    protected CobrancaPix() {}

    public CobrancaPix(String token, int parte, BigDecimal valor) {
        if (token == null || token.isBlank()) throw new RegraDeNegocioException("Token da venda ausente.");
        if (parte < 0) throw new RegraDeNegocioException("Parte do pagamento inválida.");
        exigirValor(valor);
        this.token = token;
        this.parte = parte;
        this.valor = valor;
        this.tentativa = 0;
    }

    private static void exigirValor(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new RegraDeNegocioException("O valor do Pix deve ser maior que zero.");
        }
    }

    /**
     * Chave de idempotência e external_reference da próxima order: mesma
     * tentativa = mesma order no Mercado Pago (clique duplo não gera dois Pix).
     */
    public String referenciaDaProximaTentativa(BigDecimal novoValor) {
        exigirValor(novoValor);
        long centavos = novoValor.movePointRight(2).longValueExact();
        return token + "-p" + parte + "-" + centavos + "-t" + (tentativa + 1);
    }

    /** Registra a order criada no Mercado Pago (primeira ou substituta). */
    public void registrarOrder(
            BigDecimal novoValor, String novoOrderId, String novoQrCode, String novoQrCodeBase64, LocalDateTime agora) {
        if (status == StatusCobranca.PAGA) {
            throw new RegraDeNegocioException("Esta parte já foi paga no Pix; não dá para gerar outro.");
        }
        exigirValor(novoValor);
        this.valor = novoValor;
        this.tentativa++;
        this.orderId = novoOrderId;
        this.qrCode = novoQrCode;
        this.qrCodeBase64 = novoQrCodeBase64;
        this.status = StatusCobranca.PENDENTE;
        this.valorPago = null;
        this.criadaEm = agora;
        this.pagaEm = null;
    }

    /**
     * Aplica a situação informada pelo Mercado Pago (consulta ou webhook). Só
     * sai de PENDENTE: a mesma resposta duas vezes não muda nada.
     *
     * @return true se mudou alguma coisa
     */
    public boolean atualizar(String statusMp, String detalheMp, BigDecimal pagoMp, LocalDateTime agora) {
        if (status != StatusCobranca.PENDENTE || statusMp == null) return false;
        switch (statusMp) {
            case "processed" -> {
                if (pagoMp != null && pagoMp.compareTo(valor) == 0) {
                    status = StatusCobranca.PAGA;
                    valorPago = pagoMp;
                    pagaEm = agora;
                    return true;
                }
                // Pago com valor diferente (ou sem valor informado): não confirma.
                boolean mudou = pagoMp != null && (valorPago == null || valorPago.compareTo(pagoMp) != 0);
                valorPago = pagoMp;
                return mudou;
            }
            case "canceled", "failed" -> {
                status = "expired".equals(detalheMp) ? StatusCobranca.EXPIRADA : StatusCobranca.CANCELADA;
                return true;
            }
            default -> {
                return false; // created, action_required, processing: continua pendente
            }
        }
    }

    /** Cancelada do nosso lado (o Mercado Pago confirmou o cancelamento). */
    public void marcarCancelada() {
        if (status == StatusCobranca.PENDENTE) status = StatusCobranca.CANCELADA;
    }

    /** Pix recebido sem venda resolvido à mão (estorno, venda lançada, etc.). */
    public void resolver(String como, LocalDateTime agora) {
        if (como == null || como.isBlank() || como.trim().length() > 255) {
            throw new RegraDeNegocioException("Diga como foi resolvido (até 255 caracteres).");
        }
        if (resolvidaEm != null) return;
        resolvidaEm = agora;
        resolucao = como.trim();
    }

    public LocalDateTime getResolvidaEm() {
        return resolvidaEm;
    }

    public String getResolucao() {
        return resolucao;
    }

    public boolean isValorDivergente() {
        return valorPago != null && valorPago.compareTo(valor) != 0;
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public int getParte() {
        return parte;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public int getTentativa() {
        return tentativa;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getQrCode() {
        return qrCode;
    }

    public String getQrCodeBase64() {
        return qrCodeBase64;
    }

    public StatusCobranca getStatus() {
        return status;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public LocalDateTime getPagaEm() {
        return pagaEm;
    }
}
