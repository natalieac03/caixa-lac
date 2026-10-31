package br.com.lac.caixa.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Freio de tentativas de login: espera progressiva, bloqueio por IP e teto global por minuto. */
@Component
public class LimiteLogin {

    static final Duration JANELA = Duration.ofMinutes(10);
    static final Duration BLOQUEIO = Duration.ofMinutes(10);
    static final int ERROS_PARA_BLOQUEAR = 20;
    static final long ESPERA_MAXIMA_SEGUNDOS = 30;
    static final int TETO_POR_MINUTO = 60;
    static final Duration MINUTO = Duration.ofMinutes(1);
    static final int MAXIMO_DE_ENTRADAS = 10_000;

    public enum Situacao {
        ACEITA,
        ERRO,
        AGUARDE,
        BLOQUEADO
    }

    public record Tentativa(Situacao situacao, long segundos) {
        public boolean aceita() {
            return situacao == Situacao.ACEITA;
        }
    }

    private static class Historico {
        final Deque<Instant> erros = new ArrayDeque<>();
        Instant liberadoEm;
        Instant bloqueadoAte;
    }

    private final Clock relogio;
    private final int tetoPorMinuto;
    private final int maximoDeEntradas;
    private final Map<String, Historico> ips = new LinkedHashMap<>();

    private final Deque<Instant> errosRecentes = new ArrayDeque<>();

    public LimiteLogin() {
        this(Clock.systemUTC());
    }

    public LimiteLogin(Clock relogio) {
        this(relogio, TETO_POR_MINUTO, MAXIMO_DE_ENTRADAS);
    }

    LimiteLogin(Clock relogio, int tetoPorMinuto, int maximoDeEntradas) {
        this.relogio = relogio;
        this.tetoPorMinuto = tetoPorMinuto;
        this.maximoDeEntradas = maximoDeEntradas;
    }

    synchronized int tamanho() {
        return ips.size();
    }

    static long esperaAposErros(int erros) {
        if (erros <= 0) return 0;
        if (erros > 6) return ESPERA_MAXIMA_SEGUNDOS;
        return Math.min(1L << (erros - 1), ESPERA_MAXIMA_SEGUNDOS);
    }

    static String chave(String ip) {
        if (ip == null || ip.indexOf(':') < 0) return ip;
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            if (bytes.length != 16) return ip;
            return HexFormat.of().formatHex(bytes, 0, 8) + "/64";
        } catch (UnknownHostException | SecurityException ex) {
            return ip;
        }
    }

    public synchronized Tentativa tentar(String ip, boolean senhaConfere) {
        Instant agora = relogio.instant();
        limparAntigos(agora);
        String chave = chave(ip);
        Historico h = ips.get(chave);
        if (h == null) {
            if (ips.size() >= maximoDeEntradas) {
                Iterator<String> maisAntigo = ips.keySet().iterator();
                maisAntigo.next();
                maisAntigo.remove();
            }
            h = new Historico();
            ips.put(chave, h);
        }

        if (h.bloqueadoAte != null && agora.isBefore(h.bloqueadoAte)) {
            return new Tentativa(Situacao.BLOQUEADO, segundosAte(agora, h.bloqueadoAte));
        }
        if (h.liberadoEm != null && agora.isBefore(h.liberadoEm)) {
            return new Tentativa(Situacao.AGUARDE, segundosAte(agora, h.liberadoEm));
        }
        if (errosRecentes.size() >= tetoPorMinuto) {
            return new Tentativa(
                    Situacao.AGUARDE,
                    segundosAte(agora, errosRecentes.getFirst().plus(MINUTO)));
        }
        if (senhaConfere) {
            ips.remove(chave);
            return new Tentativa(Situacao.ACEITA, 0);
        }

        errosRecentes.addLast(agora);
        h.erros.addLast(agora);
        if (h.erros.size() >= ERROS_PARA_BLOQUEAR) {
            h.bloqueadoAte = agora.plus(BLOQUEIO);
            h.erros.clear();
            h.liberadoEm = null;
            return new Tentativa(Situacao.BLOQUEADO, segundosAte(agora, h.bloqueadoAte));
        }
        long espera = esperaAposErros(h.erros.size());
        h.liberadoEm = agora.plusSeconds(espera);
        return new Tentativa(Situacao.ERRO, espera);
    }

    private void limparAntigos(Instant agora) {
        Instant umMinuto = agora.minus(MINUTO);
        while (!errosRecentes.isEmpty() && !errosRecentes.getFirst().isAfter(umMinuto)) errosRecentes.removeFirst();
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
