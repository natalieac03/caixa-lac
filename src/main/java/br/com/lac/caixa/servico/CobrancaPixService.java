package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.CobrancaPix;
import br.com.lac.caixa.modelo.StatusCobranca;
import br.com.lac.caixa.pix.MercadoPagoException;
import br.com.lac.caixa.pix.MercadoPagoPix;
import br.com.lac.caixa.repositorio.CobrancaPixRepository;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cobranças Pix do Mercado Pago para as partes Pix de uma venda em andamento.
 * Regra de ouro: PAGA só com a resposta do Mercado Pago (consulta ou webhook
 * seguido de consulta), conferindo que o valor pago é o valor da cobrança.
 *
 * <p>Nenhuma chamada ao Mercado Pago acontece com a linha travada: primeiro se
 * pergunta (sem transação), depois uma transação curta trava a cobrança
 * (PESSIMISTIC_WRITE), confere que a order ainda é a mesma e aplica.
 *
 * <p>Uma order só é trocada por outra quando o Mercado Pago confirmou que ela
 * acabou (cancelada ou expirada): se ele recusar o cancelamento (409, ex.:
 * pagamento em processamento), a cobrança continua PENDENTE e a troca de valor
 * é recusada. Assim nenhum pagamento chega a uma order esquecida.
 */
@Service
public class CobrancaPixService {

    private static final Logger LOG = LoggerFactory.getLogger(CobrancaPixService.class);

    /** A tela consulta a cada 3 s; o servidor não pergunta ao Mercado Pago mais que uma vez a cada 5 s. */
    static final Duration CACHE_DA_CONSULTA = Duration.ofSeconds(5);

    private final CobrancaPixRepository cobrancas;
    private final VendaRepository vendas;
    private final MercadoPagoPix mp;
    private final Clock relogio;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;

    public CobrancaPixService(
            CobrancaPixRepository cobrancas,
            VendaRepository vendas,
            MercadoPagoPix mp,
            Clock relogio,
            Auditoria auditoria,
            PlatformTransactionManager transacoes) {
        this.cobrancas = cobrancas;
        this.vendas = vendas;
        this.mp = mp;
        this.relogio = relogio;
        this.auditoria = auditoria;
        // Transação própria e curta só para travar e aplicar (não participa da venda que chama).
        this.transacao = new TransactionTemplate(transacoes);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

    private CobrancaPix daParte(String token, int parte, String mensagem) {
        return cobrancas.findByTokenAndParte(token, parte).orElseThrow(() -> new RegraDeNegocioException(mensagem));
    }

    /**
     * Cria a cobrança da parte ou reaproveita a que já existe com o mesmo valor.
     * Com outro valor, a pendente é cancelada no Mercado Pago e outra é criada,
     * mas só se o cancelamento foi confirmado; já paga, recusa.
     */
    public CobrancaPix gerar(String token, int parte, BigDecimal valor) {
        exigirLigado();
        exigirToken(token);
        if (valor == null || valor.signum() <= 0)
            throw new RegraDeNegocioException("O valor do Pix deve ser maior que zero.");
        if (vendas.existsByToken(token))
            throw new RegraDeNegocioException(
                    "Esta venda já foi registrada; abra uma venda nova para gerar outro Pix.");
        CobrancaPix c = cobrancas.findByTokenAndParte(token, parte).orElse(null);
        if (c != null) {
            boolean mesmoValor = c.getValor().compareTo(valor) == 0;
            if (c.getStatus() == StatusCobranca.PENDENTE) c = atualizarPeloMercadoPago(c);
            if (c.getStatus() == StatusCobranca.PAGA) {
                if (mesmoValor) return c;
                throw new RegraDeNegocioException("Esta parte já foi paga no Pix (" + Dinheiro.reais(c.getValor())
                        + "). Ajuste as outras partes.");
            }
            if (c.getStatus() == StatusCobranca.PENDENTE) {
                if (mesmoValor && c.getValorPago() == null) return c;
                c = cancelarNoMercadoPago(c);
                if (c.getStatus() == StatusCobranca.PAGA) {
                    throw new RegraDeNegocioException("Esta parte acabou de ser paga no Pix ("
                            + Dinheiro.reais(c.getValor()) + "). Ajuste as outras partes.");
                }
                if (c.getStatus() == StatusCobranca.PENDENTE) {
                    throw new RegraDeNegocioException("O Pix anterior está em processamento; espere alguns segundos.");
                }
            }
        } else {
            c = new CobrancaPix(token, parte, valor);
        }
        return criarOrder(c, valor);
    }

    private CobrancaPix criarOrder(CobrancaPix base, BigDecimal valor) {
        String referencia = base.referenciaDaProximaTentativa(valor);
        var order = mp.criarPix(valor, referencia);
        if (order.orderId() == null || order.qrCode() == null) {
            throw new MercadoPagoException("Mercado Pago: criar Pix respondeu sem order ou sem QR", 0, null, null);
        }
        return transacao.execute(st -> {
            CobrancaPix c = base.getId() == null
                    ? base
                    : cobrancas.buscarPorIdParaAtualizar(base.getId()).orElseThrow();
            // O mesmo pedido duas vezes (idempotência no Mercado Pago): a order já está registrada.
            if (order.orderId().equals(c.getOrderId())) return c;
            c.registrarOrder(valor, order.orderId(), order.qrCode(), order.qrCodeBase64(), agora());
            CobrancaPix salva = cobrancas.save(c);
            auditoria.registrar("pix_gerado", "order", order.orderId(), "valor", valor, "parte", c.getParte());
            return salva;
        });
    }

    /**
     * Situação da parte; se pendente, pergunta ao Mercado Pago (no máximo uma vez a
     * cada 5 s; falha do Mercado Pago: continua pendente).
     */
    public CobrancaPix consultar(String token, int parte) {
        exigirToken(token);
        CobrancaPix c = daParte(token, parte, "Não há Pix gerado para esta parte.");
        if (c.getStatus() == StatusCobranca.PENDENTE && mp.ligado() && consultaVencida(c)) {
            try {
                c = atualizarPeloMercadoPago(c);
            } catch (MercadoPagoException e) {
                LOG.warn("Consulta do Pix {} falhou; continua pendente.", c.getOrderId());
            }
        }
        return c;
    }

    private boolean consultaVencida(CobrancaPix c) {
        return c.getConsultadaEm() == null || !c.getConsultadaEm().isAfter(agora().minus(CACHE_DA_CONSULTA));
    }

    /**
     * Cancela a cobrança pendente da parte (o total mudou ou a pessoa desistiu).
     * Se o Mercado Pago recusar, devolve a situação real: PAGA (pago agora há
     * pouco) ou PENDENTE (em processamento); a tela decide o que mostrar.
     */
    public CobrancaPix cancelar(String token, int parte) {
        exigirLigado();
        exigirToken(token);
        CobrancaPix c = daParte(token, parte, "Não há Pix gerado para esta parte.");
        if (c.getStatus() == StatusCobranca.PENDENTE) c = cancelarNoMercadoPago(c);
        return c;
    }

    /**
     * Para o POST /venda: a parte Pix do Mercado Pago só vale se a cobrança está
     * PAGA com o mesmo valor. Se ainda pendente, consulta o Mercado Pago uma vez.
     *
     * @return o id da order que pagou
     */
    public String exigirPaga(String token, int parte, BigDecimal valorDaParte) {
        CobrancaPix c = daParte(
                token, parte, "Não há Pix do Mercado Pago gerado para esta parte. " + "Gere o Pix ou use Pix manual.");
        if (c.getStatus() == StatusCobranca.PENDENTE && mp.ligado()) {
            try {
                c = atualizarPeloMercadoPago(c);
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
    static final Duration CARENCIA_SEM_VENDA = Duration.ofMinutes(10);

    /** Cobrança pendente mais velha que isso é cancelada pela limpeza agendada. */
    static final Duration ABANDONADA_APOS = Duration.ofMinutes(20);

    /**
     * Aviso do webhook (já com a assinatura conferida): a order é consultada no
     * Mercado Pago (sem cache: o aviso diz que algo mudou) e a cobrança
     * atualizada. Order que não é nossa ou cobrança que já saiu de PENDENTE:
     * nada muda (o mesmo aviso duas vezes é inofensivo).
     */
    public boolean atualizarPorOrder(String orderId) {
        if (orderId == null || orderId.isBlank() || !mp.ligado()) return false;
        var cobranca = cobrancas.findByOrderId(orderId);
        if (cobranca.isEmpty()) {
            // Ex.: o botão "Simular notificação" do painel do Mercado Pago manda um id que não existe.
            LOG.warn("Webhook: a order {} não é de nenhuma cobrança deste caixa; aviso ignorado.", paraLog(orderId));
            return false;
        }
        CobrancaPix c = cobranca.get();
        if (c.getStatus() != StatusCobranca.PENDENTE) return false;
        StatusCobranca antes = c.getStatus();
        BigDecimal pagoAntes = c.getValorPago();
        CobrancaPix depois = atualizarPeloMercadoPago(c);
        return antes != depois.getStatus() || !Objects.equals(pagoAntes, depois.getValorPago());
    }

    /** Id vindo de fora, pronto para o log: sem quebras de linha (não forja linhas) e com no máximo 64 caracteres. */
    static String paraLog(String externo) {
        String limpo = externo.replaceAll("[\\r\\n\\t]", "_");
        return limpo.length() > 64 ? limpo.substring(0, 64) + "..." : limpo;
    }

    /**
     * Pix que entrou (pago, ou pago com valor diferente) sem venda válida, para
     * resolver à mão: nunca usado numa venda, ou usado só em venda cancelada (aí
     * é para estornar).
     */
    @Transactional(readOnly = true)
    public List<CobrancaPix> pixSemVenda() {
        return cobrancas.pixSemVenda(agora().minus(CARENCIA_SEM_VENDA));
    }

    /** Das orders dadas, as usadas em venda cancelada (o aviso diz "estorne o Pix"). */
    @Transactional(readOnly = true)
    public Set<String> deVendaCancelada(List<CobrancaPix> pix) {
        if (pix.isEmpty()) return Set.of();
        return new HashSet<>(cobrancas.ordersDeVendaCancelada(
                pix.stream().map(CobrancaPix::getOrderId).toList()));
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
    public List<Long> abandonadas() {
        return cobrancas.findByStatusAndCriadaEmBefore(StatusCobranca.PENDENTE, agora().minus(ABANDONADA_APOS)).stream()
                .map(CobrancaPix::getId)
                .toList();
    }

    /** Pergunta de novo (pode ter sido paga) e, se ainda pendente, cancela no Mercado Pago. */
    public void encerrarSeAbandonada(Long id) {
        var c = cobrancas.findById(id).orElse(null);
        if (c == null || c.getStatus() != StatusCobranca.PENDENTE || !mp.ligado()) return;
        if (!c.getCriadaEm().isBefore(agora().minus(ABANDONADA_APOS))) return;
        c = atualizarPeloMercadoPago(c);
        if (c.getStatus() == StatusCobranca.PENDENTE) cancelarNoMercadoPago(c);
    }

    /** Pergunta ao Mercado Pago (sem trava) e aplica numa transação curta (só sai de PENDENTE). */
    CobrancaPix atualizarPeloMercadoPago(CobrancaPix c) {
        var s = mp.consultar(c.getOrderId());
        return transacao.execute(st -> {
            CobrancaPix alvo = cobrancas.buscarPorIdParaAtualizar(c.getId()).orElseThrow();
            // Outra order entrou nesse meio tempo: a resposta é de uma order que já não vale.
            if (!c.getOrderId().equals(alvo.getOrderId())) return alvo;
            alvo.marcarConsultada(agora());
            if (alvo.atualizar(s.status(), s.statusDetail(), s.valorPago(), agora())) {
                auditoria.registrar(
                        "pix_" + alvo.getStatus().name().toLowerCase(),
                        "order",
                        alvo.getOrderId(),
                        "valor",
                        alvo.getValor(),
                        "pago",
                        alvo.getValorPago());
            }
            return alvo;
        });
    }

    /**
     * Cancela no Mercado Pago. Se ele recusar (409), consulta: pode ter sido pago
     * ou estar em processamento. Só marca CANCELADA com o cancelamento confirmado.
     */
    CobrancaPix cancelarNoMercadoPago(CobrancaPix c) {
        try {
            mp.cancelar(c.getOrderId());
        } catch (MercadoPagoException e) {
            if (e.getStatus() != 409) throw e;
            return atualizarPeloMercadoPago(c);
        }
        return transacao.execute(st -> {
            CobrancaPix alvo = cobrancas.buscarPorIdParaAtualizar(c.getId()).orElseThrow();
            if (!c.getOrderId().equals(alvo.getOrderId()) || alvo.getStatus() != StatusCobranca.PENDENTE) return alvo;
            alvo.marcarCancelada();
            auditoria.registrar("pix_cancelado", "order", alvo.getOrderId(), "valor", alvo.getValor());
            return alvo;
        });
    }
}
