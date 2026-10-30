package br.com.lac.caixa.controle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RespostaCsvTest {
    @Test
    @DisplayName("Download do CSV: anexo com o nome do arquivo e BOM UTF-8 antes do texto")
    void anexoComBom() throws Exception {
        var resposta = RespostaCsv.arquivo("Teste;ç", "estoque.csv");

        assertEquals("attachment; filename=\"estoque.csv\"", resposta.getHeaders().getFirst("Content-Disposition"));
        assertNotNull(resposta.getBody());
        byte[] bytes = resposta.getBody().getContentAsByteArray();
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, java.util.Arrays.copyOf(bytes, 3));
        assertEquals("Teste;ç", new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8));
    }
}
