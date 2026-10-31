package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.CobrancaPix;
import br.com.lac.caixa.modelo.StatusCobranca;
import br.com.lac.caixa.pix.MercadoPagoException;
import br.com.lac.caixa.pix.MercadoPagoPix;
import br.com.lac.caixa.repositorio.CobrancaPixRepository;
import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobranças Pix do Mercado Pago para as partes Pix de uma venda em andamento.
 * Regra de ouro: PAGA só com a resposta do Mercado Pago (consulta ou webhook
 * seguido de consulta), conferindo que o valor pago é o valor da cobrança.
 */
@Service
public class CobrancaPixService {

    private static final Logger LOG = LoggerFactory.getLogger(CobrancaPixService.class);

    private final CobrancaPixRepository cobrancas;
    private final MercadoPagoPix mp;
    private final Clock relogio;
    private final Auditoria auditoria;

    public CobrancaPixService(CobrancaPixRepository cobrancas, MercadoPagoPix mp, Clock relogio, Auditoria auditoria) {
        this.cobrancas = cobrancas;
        this.mp = mp;
        this.relogio = relogio;
        this.auditoria = auditoria;
    }

    public boolean ligado() {
        return mp.ligado();
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio);
    }

    private void exigirLigado() {
        if (!mp.ligado()) throw new RegraDeNegocioException("Pix automático desligado. Use Pix manual.");
    }

    private static void exigirToken(String token) {
        try {
            if (token != null && UUID.fromString(token).toString().equals(token)) return;
        } catch (IllegalArgumentException naoEUuid) {
            // cai na mensagem abaixo
        }
        throw new RegraDeNegocioException("Token de venda inválido.");
    }

    /**
     * Cria a cobrança da parte ou reaproveita a que já existe com o mesmo valor.
     * Com outro valor, a pendente é cancelada no Mercado Pago e outra é criada;
     * se já foi paga, recusa.
     */
    @Transactional
    public CobrancaPix gerar(String token, int parte, BigDecimal valor) {
        exigirLigado();
        exigirToken(token);
        if (valor == null || valor.signum() <= 0)
            throw new RegraDeNegocioException("O valor do Pix deve ser maior que zero.");
        var existente = cobrancas.buscarParaAtualizar(token, parte);
        if (existente.isPresent()) {
            CobrancaPix c = existente.get();
            boolean mesmoValor = c.getValor().compareTo(valor) == 0;
            if (c.getStatus() == StatusCobranca.PENDENTE) atualizarPeloMercadoPago(c);
            if (c.getStatus() == StatusCobranca.PAGA) {
                if (mesmoValor) return c;
                throw new RegraDeNegocioException("Esta parte já foi paga no Pix (" + Dinheiro.reais(c.getValor())
                        + "). Ajuste as outras partes.");
            }
            if (c.getStatus() == StatusCobranca.PENDENTE) {
                if (mesmoValor) return c;
                cancelarNoMercadoPago(c);
                if (c.getStatus() == StatusCobranca.PAGA) {
                    throw new RegraDeNegocioException("Esta parte acabou de ser paga no Pix ("
                            + Dinheiro.reais(c.getValor()) + "). Ajuste as outras partes.");
                }
            }
            return criarOrder(c, valor);
        }
        return criarOrder(new CobrancaPix(token, parte, valor), valor);
    }

    private CobrancaPix criarOrder(CobrancaPix c, BigDecimal valor) {
        String referencia = c.referenciaDaProximaTentativa(valor);
        var order = mp.criarPix(valor, referencia);
        if (order.orderId() == null || order.qrCode() == null) {
            throw new MercadoPagoException("Mercado Pago: criar Pix respondeu sem order ou sem QR", 0, null, null);
        }
        c.registrarOrder(valor, order.orderId(), order.qrCode(), order.qrCodeBase64(), agora());
        CobrancaPix salva = cobrancas.save(c);
        auditoria.registrar("pix_gerado", "order", order.orderId(), "valor", valor, "parte", c.getParte());
        return salva;
    }

    /** Situação da parte; se pendente, pergunta ao Mercado Pago (falha do MP: continua pendente). */
    @Transactional
    public CobrancaPix consultar(String token, int parte) {
        exigirToken(token);
        CobrancaPix c = cobrancas
                .buscarParaAtualizar(token, parte)
                .orElseThrow(() -> new RegraDeNegocioException("Não há Pix gerado para esta parte."));
        if (c.getStatus() == StatusCobranca.PENDENTE && mp.ligado()) {
            try {
                atualizarPeloMercadoPago(c);
            } catch (MercadoPagoException e) {
                LOG.warn("Consulta do Pix {} falhou; continua pendente.", c.getOrderId());
            }
        }
        return c;
    }

    /** Cancela a cobrança pendente da parte (o total mudou ou a pessoa desistiu). */
    @Transactional
    public CobrancaPix cancelar(String token, int parte) {
        exigirLigado();
        exigirToken(token);
        CobrancaPix c = cobrancas
                .buscarParaAtualizar(token, parte)
                .orElseThrow(() -> new RegraDeNegocioException("Não há Pix gerado para esta parte."));
        if (c.getStatus() == StatusCobranca.PENDENTE) cancelarNoMercadoPago(c);
        return c;
    }

    /**
     * Para o POST /venda: a parte Pix do Mercado Pago só vale se a cobrança está
     * PAGA com o mesmo valor. Se ainda pendente, consulta o Mercado Pago uma vez.
     *
     * @return o id da order que pagou
     */
    @Transactional
    public String exigirPaga(String token, int parte, BigDecimal valorDaParte) {
        CobrancaPix c = cobrancas
                .buscarParaAtualizar(token, parte)
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Não há Pix do Mercado Pago gerado para esta parte. " + "Gere o Pix ou use Pix manual."));
        if (c.getStatus() == StatusCobranca.PENDENTE && mp.ligado()) {
            try {
                atualizarPeloMercadoPago(c);
            } catch (MercadoPagoException e) {
                throw new RegraDeNegocioException(
                        "Não foi possível confirmar o Pix no Mercado Pago agora. " + "Tente de novo em instantes.");
            }
        }
        if (c.isValorDivergente()) {
            throw new RegraDeNegocioException("O Pix foi pago com " + Dinheiro.reais(c.getValorPago())
                    + ", diferente do valor da cobrança (" + Dinheiro.reais(c.getValor()) + "). Resolva à mão.");
        }
        if (c.getStatus() != StatusCobranca.PAGA) {
            throw new RegraDeNegocioException("O Pix de " + Dinheiro.reais(c.getValor()) + " ainda não foi pago.");
        }
        if (valorDaParte == null
                || c.getValor().compareTo(valorDaParte) != 0
                || c.getValorPago().compareTo(valorDaParte) != 0) {
            throw new RegraDeNegocioException("O Pix pago (" + Dinheiro.reais(c.getValorPago())
                    + ") não bate com o valor da parte (" + Dinheiro.reais(valorDaParte) + ").");
        }
        return c.getOrderId();
    }

    /** Pix que não sai do aviso enquanto alguém pode estar confirmando a venda na tela. */
    static final java.time.Duration CARENCIA_SEM_VENDA = java.time.Duration.ofMinutes(10);

    /** Cobrança pendente mais velha que isso é cancelada pela limpeza agendada. */
    static final java.time.Duration ABANDONADA_APOS = java.time.Duration.ofMinutes(20);

    /**
     * Aviso do webhook (já com a assinatura conferida): a order é consultada no
     * Mercado Pago e a cobrança atualizada. Order que não é nossa ou cobrança que
     * já saiu de PENDENTE: nada muda (o mesmo aviso duas vezes é inofensivo).
     */
    @Transactional
    public boolean atualizarPorOrder(String orderId) {
        if (orderId == null || orderId.isBlank() || !mp.ligado()) return false;
        var cobranca = cobrancas.buscarPorOrderParaAtualizar(orderId);
        if (cobranca.isEmpty()) {
            // Ex.: o botão "Simular notificação" do painel do Mercado Pago manda um id que não existe.
            LOG.warn("Webhook: a order {} não é de nenhuma cobrança deste caixa; aviso ignorado.", paraLog(orderId));
            return false;
        }
        if (cobranca.get().getStatus() != StatusCobranca.PENDENTE) return false;
        StatusCobranca antes = cobranca.get().getStatus();
        BigDecimal pagoAntes = cobranca.get().getValorPago();
        atualizarPeloMercadoPago(cobranca.get());
        return antes != cobranca.get().getStatus()
                || !java.util.Objects.equals(pagoAntes, cobranca.get().getValorPago());
    }

    /** Id vindo de fora, pronto para o log: sem quebras de linha (não forja linhas) e com no máximo 64 caracteres. */
    static String paraLog(String externo) {
        String limpo = externo.replaceAll("[\\r\\n\\t]", "_");
        return limpo.length() > 64 ? limpo.substring(0, 64) + "..." : limpo;
    }

    /** Pix que entrou (pago, ou pago com valor diferente) sem venda confirmada, para resolver à mão. */
    @Transactional(readOnly = true)
    public java.util.List<CobrancaPix> pixSemVenda() {
        return cobrancas.pixSemVenda(agora().minus(CARENCIA_SEM_VENDA));
    }

    @Transactional
    public void resolver(Long id, String como) {
        CobrancaPix c = cobrancas
                .buscarPorIdParaAtualizar(id)
                .orElseThrow(() -> new RegraDeNegocioException("Pix não encontrado: " + id));
        c.resolver(como, agora());
        auditoria.registrar(
                "pix_sem_venda_resolvido",
                "order",
                c.getOrderId(),
                "valor",
                c.getValorPago(),
                "resolucao",
                c.getResolucao());
    }

    /** Ids das cobranças pendentes há mais de 20 minutos (a limpeza agendada encerra uma a uma). */
    @Transactional(readOnly = true)
    public java.util.List<Long> abandonadas() {
        return cobrancas.findByStatusAndCriadaEmBefore(StatusCobranca.PENDENTE, agora().minus(ABANDONADA_APOS)).stream()
                .map(CobrancaPix::getId)
                .toList();
    }

    /** Pergunta de novo (pode ter sido paga) e, se ainda pendente, cancela no Mercado Pago. */
    @Transactional
    public void encerrarSeAbandonada(Long id) {
        var c = cobrancas.buscarPorIdParaAtualizar(id).orElse(null);
        if (c == null || c.getStatus() != StatusCobranca.PENDENTE || !mp.ligado()) return;
        if (!c.getCriadaEm().isBefore(agora().minus(ABANDONADA_APOS))) return;
        atualizarPeloMercadoPago(c);
        if (c.getStatus() == StatusCobranca.PENDENTE) cancelarNoMercadoPago(c);
    }

    /** Pergunta ao Mercado Pago e aplica (só sai de PENDENTE). */
    void atualizarPeloMercadoPago(CobrancaPix c) {
        var s = mp.consultar(c.getOrderId());
        if (c.atualizar(s.status(), s.statusDetail(), s.valorPago(), agora())) {
            auditoria.registrar(
                    "pix_" + c.getStatus().name().toLowerCase(),
                    "order",
                    c.getOrderId(),
                    "valor",
                    c.getValor(),
                    "pago",
                    c.getValorPago());
        }
    }

    /** Cancela no Mercado Pago; se ele recusar (409), consulta: pode ter sido pago. */
    void cancelarNoMercadoPago(CobrancaPix c) {
        try {
            mp.cancelar(c.getOrderId());
            c.marcarCancelada();
            auditoria.registrar("pix_cancelado", "order", c.getOrderId(), "valor", c.getValor());
        } catch (MercadoPagoException e) {
            if (e.getStatus() != 409) throw e;
            atualizarPeloMercadoPago(c);
            if (c.getStatus() == StatusCobranca.PENDENTE) c.marcarCancelada();
        }
    }
}
