package br.com.lac.caixa.servico;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class LimiteLoginTest {
    static class Relogio extends Clock {
        Instant agora=Instant.parse("2026-01-01T00:00:00Z");
        void avancar(long segundos) { agora=agora.plusSeconds(segundos); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zona) { return this; }
        public Instant instant() { return agora; }
    }
    @Test void cincoErrosBloqueiamSoIpPorDezMinutos() {
        var clock=new Relogio(); var limite=new LimiteLogin(clock);
        for(int i=0;i<5;i++) assertFalse(limite.autenticar("ip1","certa","errada"));
        assertFalse(limite.autenticar("ip1","certa","certa"));
        assertTrue(limite.autenticar("ip2","certa","certa"));
        clock.avancar(599); assertFalse(limite.autenticar("ip1","certa","certa"));
        clock.avancar(1); assertTrue(limite.autenticar("ip1","certa","certa"));
    }
    @Test void errosAntigosExpiramEAcertoLimpaFalhas() {
        var clock=new Relogio(); var limite=new LimiteLogin(clock);
        for(int i=0;i<4;i++) limite.autenticar("ip","certa","errada");
        clock.avancar(601); assertFalse(limite.autenticar("ip","certa","errada"));
        assertTrue(limite.autenticar("ip","certa","certa"));
        for(int i=0;i<4;i++) limite.autenticar("ip","certa","errada");
        assertTrue(limite.autenticar("ip","certa","certa"));
    }
}
