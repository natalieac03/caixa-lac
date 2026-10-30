package br.com.lac.caixa.servico;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Freio de tentativas de login por IP, em memória, sem travar o caixa do
 * evento por um erro de digitação:
 * - cada erro impõe uma espera antes da próxima tentativa: 1s, 2s, 4s, 8s,
 *   16s e daí 30s (máximo);
 * - 20 erros em 10 minutos bloqueiam o IP por 10 minutos;
 * - um acerto zera tudo daquele IP.
 * Tentativa durante a espera ou o bloqueio é recusada sem conferir a senha
 * e sem contar como erro.
 */
@Component
public class LimiteLogin {

    static final Duration JANELA = Duration.ofMinutes(10);
    static final Duration BLOQUEIO = Duration.ofMinutes(10);
    static final int ERROS_PARA_BLOQUEAR = 20;
    static final long ESPERA_MAXIMA_SEGUNDOS = 30;

    public enum Situacao { ACEITA, ERRO, AGUARDE, BLOQUEADO }

    /** segundos: quanto falta para poder tentar de novo (zero se ACEITA). */
    public record Tentativa(Situacao situacao, long segundos) {
        public boolean aceita() { return situacao == Situacao.ACEITA; }
    }

    private static class Historico {
        final Deque<Instant> erros = new ArrayDeque<>();
        Instant liberadoEm;
        Instant bloqueadoAte;
    }

    private final Clock relogio;
    private final Map<String, Historico> ips = new HashMap<>();

    public LimiteLogin() {
        this(Clock.systemUTC());
    }

    public LimiteLogin(Clock relogio) {
        this.relogio = relogio;
    }

    /** Espera depois do n-ésimo erro seguido: 1, 2, 4, 8, 16, 30, 30... segundos. */
    static long esperaAposErros(int erros) {
        if (erros <= 0) return 0;
        if (erros > 6) return ESPERA_MAXIMA_SEGUNDOS;
        return Math.min(1L << (erros - 1), ESPERA_MAXIMA_SEGUNDOS);
    }

    public synchronized Tentativa tentar(String ip, boolean senhaConfere) {
        Instant agora = relogio.instant();
        limparAntigos(agora);
        Historico h = ips.computeIfAbsent(ip, k -> new Historico());

        if (h.bloqueadoAte != null && agora.isBefore(h.bloqueadoAte)) {
            return new Tentativa(Situacao.BLOQUEADO, segundosAte(agora, h.bloqueadoAte));
        }
        if (h.liberadoEm != null && agora.isBefore(h.liberadoEm)) {
            return new Tentativa(Situacao.AGUARDE, segundosAte(agora, h.liberadoEm));
        }
        if (senhaConfere) {
            ips.remove(ip);
            return new Tentativa(Situacao.ACEITA, 0);
        }

        h.erros.addLast(agora);
        if (h.erros.size() >= ERROS_PARA_BLOQUEAR) {
            h.bloqueadoAte = agora.plus(BLOQUEIO);
            // Ao fim do bloqueio a contagem recomeça do zero.
            h.erros.clear();
            h.liberadoEm = null;
            return new Tentativa(Situacao.BLOQUEADO, segundosAte(agora, h.bloqueadoAte));
        }
        long espera = esperaAposErros(h.erros.size());
        h.liberadoEm = agora.plusSeconds(espera);
        return new Tentativa(Situacao.ERRO, espera);
    }

    /** Erros com mais de 10 minutos não contam; IPs sem nada pendente saem do mapa. */
    private void limparAntigos(Instant agora) {
        Instant limite = agora.minus(JANELA);
        ips.values().removeIf(h -> {
            while (!h.erros.isEmpty() && !h.erros.getFirst().isAfter(limite)) h.erros.removeFirst();
            boolean bloqueado = h.bloqueadoAte != null && agora.isBefore(h.bloqueadoAte);
            boolean esperando = h.liberadoEm != null && agora.isBefore(h.liberadoEm);
            return h.erros.isEmpty() && !bloqueado && !esperando;
        });
    }

    private static long segundosAte(Instant agora, Instant fim) {
        long milis = Duration.between(agora, fim).toMillis();
        return Math.max(1, (milis + 999) / 1000);
    }
}
