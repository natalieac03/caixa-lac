package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.servico.*;
import br.com.lac.caixa.util.ExportadorCSV;
import org.springframework.core.io.Resource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.*;
import java.time.format.DateTimeFormatter;

@Controller
public class HistoricoController {
    private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private final CaixaService caixa;
    private final ConsultaHistorico consulta;

    public HistoricoController(CaixaService caixa, ConsultaHistorico consulta) {
        this.caixa = caixa; this.consulta = consulta;
    }

    @GetMapping("/historico")
    public String historico(@RequestParam(required=false) FormaPagamento forma,
            @RequestParam(required=false) String produto, @RequestParam(required=false) String inicio,
            @RequestParam(required=false) String fim, @RequestParam(required=false) String dia,
            @RequestParam(defaultValue="0") int pagina, Model model) {
        var filtro = FiltroHistorico.criar(forma, inicio, fim, dia, produto);
        var resultado = consulta.consultar(filtro, pagina);
        model.addAttribute("vendas", resultado.pagina().getContent());
        model.addAttribute("pagina", resultado.pagina());
        model.addAttribute("resumo", resultado.resumo());
        model.addAttribute("formas", FormaPagamento.values());
        model.addAttribute("forma", filtro.forma()); model.addAttribute("produto", filtro.produto());
        model.addAttribute("inicio", filtro.inicio()); model.addAttribute("fim", filtro.fim());
        return "historico";
    }

    @GetMapping("/historico/exportar.csv")
    public ResponseEntity<Resource> exportar(@RequestParam(required=false) FormaPagamento forma,
            @RequestParam(required=false) String produto, @RequestParam(required=false) String inicio,
            @RequestParam(required=false) String fim, @RequestParam(required=false) String dia) {
        var vendas = consulta.exportar(FiltroHistorico.criar(forma, inicio, fim, dia, produto));
        return ExportadorCSV.arquivoCsv(ExportadorCSV.doHistorico(vendas), "historico.csv");
    }

    @PostMapping("/historico/{id}/data")
    public String alterarDataHora(@PathVariable Long id, @RequestParam String data, @RequestParam String hora,
            @RequestParam(required=false) FormaPagamento forma, @RequestParam(required=false) String produto,
            @RequestParam(required=false) String inicio, @RequestParam(required=false) String fim,
            @RequestParam(defaultValue="0") int pagina, RedirectAttributes flash) {
        LocalDateTime nova;
        try { nova = LocalDateTime.of(LocalDate.parse(data), LocalTime.parse(hora)); }
        catch (DateTimeException ex) { throw new IllegalArgumentException("Informe dia e hora válidos."); }
        caixa.alterarDataHora(id, nova);
        flash.addFlashAttribute("sucesso", "Venda #" + id + " agora está em " + nova.format(DIA_E_HORA) + ".");
        if (forma != null) flash.addAttribute("forma", forma);
        if (produto != null && !produto.isBlank()) flash.addAttribute("produto", produto);
        if (inicio != null && !inicio.isBlank()) flash.addAttribute("inicio", inicio);
        if (fim != null && !fim.isBlank()) flash.addAttribute("fim", fim);
        flash.addAttribute("pagina", pagina);
        return "redirect:/historico";
    }
    @PostMapping("/historico/{id}/cancelar")
    public String cancelar(@PathVariable Long id, @RequestParam String motivo, RedirectAttributes flash) {
        caixa.cancelarVenda(id, motivo);
        flash.addFlashAttribute("sucesso", "Venda #" + id + " cancelada. Estoque devolvido.");
        return "redirect:/historico";
    }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String conflito(RedirectAttributes flash) {
        flash.addFlashAttribute("erro", "Os dados mudaram durante a operação. Confira o histórico e tente novamente.");
        return "redirect:/historico";
    }
}
