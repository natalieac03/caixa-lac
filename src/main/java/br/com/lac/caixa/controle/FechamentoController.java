package br.com.lac.caixa.controle;

import br.com.lac.caixa.exportacao.ExportadorCSV;
import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.servico.FechamentoService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final Clock relogio;

    public FechamentoController(FechamentoService fechamento, EventoService eventos, Clock relogio) {
        this.fechamento = fechamento;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /** Evento escolhido vale como período; Dia a dia, dentro das datas; sem datas, o dia de hoje. */
    private FiltroHistorico intervalo(String inicio, String fim, SeletorDeEvento sel) {
        if (sel.id() != null) return FiltroHistorico.doEvento(eventos.periodo(sel.id()));
        if ((inicio == null || inicio.isBlank()) && (fim == null || fim.isBlank())) {
            inicio = LocalDate.now(relogio).toString();
            fim = inicio;
        }
        var filtro = FiltroHistorico.criar(null, inicio, fim, null, "");
        return sel.diaADia() ? filtro.comDiaADia(LocalDateTime.now(relogio)) : filtro;
    }

    @GetMapping("/fechamento")
    public String tela(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String evento,
            Model model) {
        var sel = SeletorDeEvento.ler(evento);
        var filtro = intervalo(inicio, fim, sel);
        model.addAttribute("inicio", filtro.inicio());
        model.addAttribute("fim", filtro.fim());
        model.addAttribute("evento", sel.valor());
        model.addAttribute("eventos", eventos.listar());
        model.addAttribute("fechamento", fechamento.consultar(filtro));
        return "fechamento";
    }

    @GetMapping("/fechamento/exportar.csv")
    public ResponseEntity<Resource> exportar(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String evento) {
        var filtro = intervalo(inicio, fim, SeletorDeEvento.ler(evento));
        var resultado = fechamento.consultar(filtro);
        // Com evento, as colunas Início e Fim levam os dias do evento.
        LocalDate de = filtro.evento() == null
                ? filtro.inicio()
                : filtro.evento().inicio().toLocalDate();
        // O fim do evento é exclusivo: o que acaba à meia-noite termina no dia anterior.
        LocalDate ate = filtro.evento() == null
                ? filtro.fim()
                : filtro.evento().fim().minusNanos(1).toLocalDate();
        return RespostaCsv.arquivo(ExportadorCSV.doFechamento(resultado, de, ate), "fechamento.csv");
    }
}
