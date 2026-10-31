package br.com.lac.caixa.controle;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Resposta HTTP de download de um CSV, com BOM UTF-8. */
final class RespostaCsv {

    private RespostaCsv() {}

    static ResponseEntity<Resource> arquivo(String csv, String nomeArquivo) {
        byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] corpo = csv.getBytes(StandardCharsets.UTF_8);
        byte[] conteudo = new byte[bom.length + corpo.length];
        System.arraycopy(bom, 0, conteudo, 0, bom.length);
        System.arraycopy(corpo, 0, conteudo, bom.length, corpo.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(new ByteArrayResource(conteudo));
    }
}
