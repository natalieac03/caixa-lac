package br.com.lac.caixa.servico;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
@Component
public class LimiteLogin {
    private static final Duration JANELA = Duration.ofMinutes(10);
    private final Clock relogio;
    private final Map<String,Tentativas> ips = new HashMap<>();
    private static class Tentativas {
        final Deque<Instant> falhas = new ArrayDeque<>();
        Instant bloqueadoAte;
    }
    public LimiteLogin() { this(Clock.systemUTC()); }
    public LimiteLogin(Clock relogio) { this.relogio = relogio; }
    public synchronized boolean autenticar(String ip, boolean senhaConfere) {
        Instant agora = relogio.instant();
        ips.values().removeIf(t -> (t.bloqueadoAte == null || !agora.isBefore(t.bloqueadoAte))
                && (t.falhas.isEmpty() || !t.falhas.getLast().isAfter(agora.minus(JANELA))));
        Tentativas t = ips.computeIfAbsent(ip, k -> new Tentativas());
        if (t.bloqueadoAte != null && agora.isBefore(t.bloqueadoAte)) return false;
        while (!t.falhas.isEmpty() && !t.falhas.getFirst().isAfter(agora.minus(JANELA))) t.falhas.removeFirst();
        if (senhaConfere) {
            ips.remove(ip); return true;
        }
        t.falhas.addLast(agora);
        if (t.falhas.size() >= 5) t.bloqueadoAte = agora.plus(JANELA);
        return false;
    }
}
