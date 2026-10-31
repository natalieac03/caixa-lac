package br.com.lac.caixa.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.lac.caixa.RelogioDeTeste;
import br.com.lac.caixa.config.LimiteLogin.Situacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimiteLoginTest {
    final RelogioDeTeste relogio = new RelogioDeTeste();
    final LimiteLogin limite = new LimiteLogin(relogio);

    /** Erra e espera o tempo pedido; devolve a espera imposta. */
    long errarEEsperar(String ip) {
        var t = limite.tentar(ip, false);
        relogio.avancar(t.segundos());
        return t.segundos();
    }

    @Test
    void esperaDobraAteTrintaSegundos() {
        long[] esperado = {1, 2, 4, 8, 16, 30, 30, 30};
        for (long segundos : esperado) assertEquals(segundos, errarEEsperar("ip"));
    }

    @Test
    void tentativaDuranteEsperaERecusadaSemContarErro() {
        assertEquals(new LimiteLogin.Tentativa(Situacao.ERRO, 1), limite.tentar("ip", false));
        assertEquals(new LimiteLogin.Tentativa(Situacao.AGUARDE, 1), limite.tentar("ip", true));
        assertEquals(Situacao.AGUARDE, limite.tentar("ip", false).situacao());
        relogio.avancar(1);
        // Se a tentativa durante a espera tivesse contado, a espera agora seria 4s.
        assertEquals(new LimiteLogin.Tentativa(Situacao.ERRO, 2), limite.tentar("ip", false));
    }

    @Test
    void acertoZeraOContadorDaqueleIp() {
        for (int i = 0; i < 5; i++) errarEEsperar("ip");
        assertTrue(limite.tentar("ip", true).aceita());
        assertEquals(1, limite.tentar("ip", false).segundos());
    }

    @Test
    void bloqueioDepoisDoVigesimoErro() {
        var r = limite.tentar("ip", false);
        int erros = 1;
        while (r.situacao() == Situacao.ERRO) {
            relogio.avancar(r.segundos());
            r = limite.tentar("ip", false);
            erros++;
        }
        assertEquals(20, erros);
        assertEquals(new LimiteLogin.Tentativa(Situacao.BLOQUEADO, 600), r);
        assertEquals(Situacao.BLOQUEADO, limite.tentar("ip", true).situacao());
        assertTrue(limite.tentar("outro", true).aceita());
        relogio.avancar(599);
        assertEquals(new LimiteLogin.Tentativa(Situacao.BLOQUEADO, 1), limite.tentar("ip", true));
        relogio.avancar(1);
        assertTrue(limite.tentar("ip", true).aceita());
    }

    @Test
    void errosEspalhadosEmMaisDeDezMinutosNaoBloqueiam() {
        for (int i = 0; i < 40; i++) {
            var r = limite.tentar("ip", false);
            assertEquals(Situacao.ERRO, r.situacao(), "erro " + (i + 1));
            relogio.avancar(45);
        }
    }

    @Test
    void errosAntigosDeixamDeContar() {
        for (int i = 0; i < 5; i++) errarEEsperar("ip");
        relogio.avancar(600);
        assertEquals(1, limite.tentar("ip", false).segundos());
    }

    @Test
    @DisplayName("IPv6: o mesmo /64 é um IP só (trocar o fim do endereço não zera a espera)")
    void ipv6AgrupadoPor64() {
        assertEquals(Situacao.ERRO, limite.tentar("2001:db8:1:2::1", false).situacao());
        assertEquals(
                Situacao.AGUARDE, limite.tentar("2001:db8:1:2:ffff::9", true).situacao());
        assertEquals(
                Situacao.AGUARDE, limite.tentar("2001:DB8:1:2:0:0:0:abc", true).situacao());
        assertTrue(limite.tentar("2001:db8:1:3::1", true).aceita()); // outra rede /64
        assertTrue(limite.tentar("192.168.0.1", true).aceita()); // IPv4 continua por endereço
    }

    @Test
    @DisplayName("Teto global: 60 erros no último minuto, somando todos os IPs, seguram qualquer tentativa")
    void tetoGlobalPorMinuto() {
        for (int i = 0; i < 60; i++) {
            assertEquals(Situacao.ERRO, limite.tentar("10.0.0." + i, false).situacao());
        }
        // Nem a senha certa é conferida enquanto o teto está cheio.
        assertEquals(new LimiteLogin.Tentativa(Situacao.AGUARDE, 60), limite.tentar("10.0.1.1", true));
        relogio.avancar(30);
        assertEquals(new LimiteLogin.Tentativa(Situacao.AGUARDE, 30), limite.tentar("10.0.1.1", false));
        relogio.avancar(30);
        assertTrue(limite.tentar("10.0.1.1", true).aceita());
    }

    @Test
    @DisplayName("Mapa limitado: passando do máximo de IPs guardados, o mais antigo sai")
    void maximoDeEntradas() {
        var pequeno = new LimiteLogin(relogio, 1000, 3);
        for (int i = 0; i < 5; i++) pequeno.tentar("ip" + i, false);
        assertEquals(3, pequeno.tamanho());
        assertTrue(pequeno.tentar("ip0", true).aceita()); // esquecido
        assertEquals(Situacao.AGUARDE, pequeno.tentar("ip4", true).situacao());
        assertEquals(10_000, LimiteLogin.MAXIMO_DE_ENTRADAS);
    }
}
