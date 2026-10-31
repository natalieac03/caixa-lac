package br.com.lac.caixa.controle;

import br.com.lac.caixa.servico.AlertasService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON /avisos: as mesmas seções do sininho que a página desenha. */
@RestController
public class AvisosController {
    private final AlertasService alertas;

    public AvisosController(AlertasService alertas) {
        this.alertas = alertas;
    }

    public record Resposta(int total, List<AlertasService.Secao> secoes) {}

    @GetMapping(value = "/avisos", produces = MediaType.APPLICATION_JSON_VALUE)
    public Resposta avisos() {
        var a = alertas.calcular();
        return new Resposta(a.total(), a.secoes());
    }
}
