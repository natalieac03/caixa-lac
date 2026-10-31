package br.com.lac.caixa;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio que só anda quando o teste manda. */
public class RelogioDeTeste extends Clock {
    private Instant agora = Instant.parse("2026-01-01T00:00:00Z");

    public synchronized void definir(java.time.LocalDateTime quando) {
        agora = quando.toInstant(ZoneOffset.UTC);
    }

    public synchronized void avancar(long segundos) {
        agora = agora.plusSeconds(segundos);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }

    @Override
    public synchronized Instant instant() {
        return agora;
    }
}
