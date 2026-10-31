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

/** Cobrança Pix do Mercado Pago para uma parte de uma venda em andamento. */
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

    private LocalDateTime resolvidaEm;

    private String resolucao;

    private LocalDateTime consultadaEm;

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

    public String referenciaDaProximaTentativa(BigDecimal novoValor) {
        exigirValor(novoValor);
        long centavos = novoValor.movePointRight(2).longValueExact();
        return token + "-p" + parte + "-" + centavos + "-t" + (tentativa + 1);
    }

    public void registrarOrder(
            BigDecimal novoValor, String novoOrderId, String novoQrCode, String novoQrCodeBase64, LocalDateTime agora) {
        if (status == StatusCobranca.PAGA) {
            throw new RegraDeNegocioException("Esta parte já foi paga no Pix; não dá para gerar outro.");
        }
        if (valorPago != null) {
            throw new RegraDeNegocioException(
                    "Esta parte foi paga no Pix com valor diferente; resolva à mão antes de gerar outro.");
        }
        if (status == StatusCobranca.PENDENTE && orderId != null) {
            throw new RegraDeNegocioException("Esta parte já tem um Pix aguardando pagamento; cancele-o antes.");
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
        this.consultadaEm = null;
        this.pagaEm = null;
    }

    public boolean atualizar(String statusMp, String detalheMp, BigDecimal pagoMp, LocalDateTime agora) {
        if (statusMp == null) return false;
        if (status == StatusCobranca.PAGA) {
            if (!"refunded".equals(statusMp) && !"charged_back".equals(statusMp)) return false;
            status = StatusCobranca.ESTORNADA;
            return true;
        }
        if (status != StatusCobranca.PENDENTE) return false;
        switch (statusMp) {
            case "processed" -> {
                if (pagoMp != null && pagoMp.compareTo(valor) == 0) {
                    status = StatusCobranca.PAGA;
                    valorPago = pagoMp;
                    pagaEm = agora;
                    return true;
                }
                boolean mudou = pagoMp != null && (valorPago == null || valorPago.compareTo(pagoMp) != 0);
                valorPago = pagoMp;
                return mudou;
            }
            case "refunded", "charged_back" -> {
                status = StatusCobranca.ESTORNADA;
                return true;
            }
            case "canceled", "failed" -> {
                status = "expired".equals(detalheMp) ? StatusCobranca.EXPIRADA : StatusCobranca.CANCELADA;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public void marcarConsultada(LocalDateTime agora) {
        this.consultadaEm = agora;
    }

    public LocalDateTime getConsultadaEm() {
        return consultadaEm;
    }

    public void marcarCancelada() {
        if (status == StatusCobranca.PENDENTE) status = StatusCobranca.CANCELADA;
    }

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
