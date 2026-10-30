package br.com.lac.caixa.controle;

import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.servico.*;
import br.com.lac.caixa.util.ExportadorCSV;
import java.time.LocalDate;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class FechamentoController {
    private final FechamentoService fechamento;

    public FechamentoController(FechamentoService fechamento) {
        this.fechamento = fechamento;
    }

    private FiltroHistorico intervalo(String inicio, String fim) {
        if ((inicio == null || inicio.isBlank()) && (fim == null || fim.isBlank())) {
            inicio = LocalDate.now().toString();
            fim = inicio;
        }
        return FiltroHistorico.criar(null, inicio, fim, null, "");
    }

    @GetMapping("/fechamento")
    public String tela(
            @RequestParam(required = false) String inicio, @RequestParam(required = false) String fim, Model model) {
        var filtro = intervalo(inicio, fim);
        model.addAttribute("inicio", filtro.inicio());
        model.addAttribute("fim", filtro.fim());
        model.addAttribute("fechamento", fechamento.consultar(filtro.inicio(), filtro.fim()));
        return "fechamento";
    }

    @GetMapping("/fechamento/exportar.csv")
    public ResponseEntity<Resource> exportar(
            @RequestParam(required = false) String inicio, @RequestParam(required = false) String fim) {
        var filtro = intervalo(inicio, fim);
        var resultado = fechamento.consultar(filtro.inicio(), filtro.fim());
        return RespostaCsv.arquivo(
                ExportadorCSV.doFechamento(resultado, filtro.inicio(), filtro.fim()), "fechamento.csv");
    }
}
