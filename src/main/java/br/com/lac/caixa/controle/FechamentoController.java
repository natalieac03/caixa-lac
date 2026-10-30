package br.com.lac.caixa.controle;

import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.FechamentoService;
import br.com.lac.caixa.util.ExportadorCSV;
import java.time.LocalDate;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FechamentoController {
    private final FechamentoService fechamento;
    private final EventoService eventos;

    public FechamentoController(FechamentoService fechamento, EventoService eventos) {
        this.fechamento = fechamento;
        this.eventos = eventos;
    }

    /** Evento escolhido vale como período; sem nada, o dia de hoje. */
    private FiltroHistorico intervalo(String inicio, String fim, Long evento) {
        if (evento != null) return FiltroHistorico.doEvento(eventos.periodo(evento));
        if ((inicio == null || inicio.isBlank()) && (fim == null || fim.isBlank())) {
            inicio = LocalDate.now().toString();
            fim = inicio;
        }
        return FiltroHistorico.criar(null, inicio, fim, null, "");
    }

    @GetMapping("/fechamento")
    public String tela(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) Long evento,
            Model model) {
        var filtro = intervalo(inicio, fim, evento);
        model.addAttribute("inicio", filtro.inicio());
        model.addAttribute("fim", filtro.fim());
        model.addAttribute("evento", evento);
        model.addAttribute("eventos", eventos.listar());
        model.addAttribute("fechamento", fechamento.consultar(filtro));
        return "fechamento";
    }

    @GetMapping("/fechamento/exportar.csv")
    public ResponseEntity<Resource> exportar(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) Long evento) {
        var filtro = intervalo(inicio, fim, evento);
        var resultado = fechamento.consultar(filtro);
        // Com evento, as colunas Início e Fim levam os dias do evento.
        LocalDate de = filtro.evento() == null
                ? filtro.inicio()
                : filtro.evento().inicio().toLocalDate();
        LocalDate ate =
                filtro.evento() == null ? filtro.fim() : filtro.evento().fim().toLocalDate();
        return RespostaCsv.arquivo(ExportadorCSV.doFechamento(resultado, de, ate), "fechamento.csv");
    }
}
