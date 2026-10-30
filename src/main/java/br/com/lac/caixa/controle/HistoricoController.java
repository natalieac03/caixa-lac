package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.CobrancaPixService;
import br.com.lac.caixa.servico.ConsultaHistorico;
import br.com.lac.caixa.util.ExportadorCSV;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HistoricoController {
    private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private final CaixaService caixa;
    private final ConsultaHistorico consulta;
    private final CobrancaPixService cobrancasPix;

    public HistoricoController(CaixaService caixa, ConsultaHistorico consulta, CobrancaPixService cobrancasPix) {
        this.caixa = caixa;
        this.consulta = consulta;
        this.cobrancasPix = cobrancasPix;
    }

    @GetMapping("/historico")
    public String historico(
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String produto,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String dia,
            @RequestParam(defaultValue = "0") int pagina,
            Model model) {
        var filtro = FiltroHistorico.criar(forma, inicio, fim, dia, produto);
        var resultado = consulta.consultar(filtro, pagina);
        model.addAttribute("vendas", resultado.pagina().getContent());
        model.addAttribute("pixSemVenda", cobrancasPix.pixSemVenda());
        model.addAttribute("pagina", resultado.pagina());
        model.addAttribute("resumo", resultado.resumo());
        model.addAttribute("formas", FormaPagamento.values());
        model.addAttribute("forma", filtro.forma());
        model.addAttribute("produto", filtro.produto());
        model.addAttribute("inicio", filtro.inicio());
        model.addAttribute("fim", filtro.fim());
        return "historico";
    }

    @GetMapping("/historico/exportar.csv")
    public ResponseEntity<Resource> exportar(
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String produto,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String dia) {
        var vendas = consulta.exportar(FiltroHistorico.criar(forma, inicio, fim, dia, produto));
        return RespostaCsv.arquivo(ExportadorCSV.doHistorico(vendas), "historico.csv");
    }

    @PostMapping("/historico/{id}/data")
    public String alterarDataHora(
            @PathVariable Long id,
            @RequestParam String data,
            @RequestParam String hora,
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String produto,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(defaultValue = "0") int pagina,
            RedirectAttributes flash) {
        LocalDateTime nova;
        try {
            nova = LocalDateTime.of(LocalDate.parse(data), LocalTime.parse(hora));
        } catch (DateTimeException ex) {
            throw new RegraDeNegocioException("Informe dia e hora válidos.");
        }
        caixa.alterarDataHora(id, nova);
        flash.addFlashAttribute("sucesso", "Venda #" + id + " agora está em " + nova.format(DIA_E_HORA) + ".");
        if (forma != null) flash.addAttribute("forma", forma);
        if (produto != null && !produto.isBlank()) flash.addAttribute("produto", produto);
        if (inicio != null && !inicio.isBlank()) flash.addAttribute("inicio", inicio);
        if (fim != null && !fim.isBlank()) flash.addAttribute("fim", fim);
        flash.addAttribute("pagina", pagina);
        return "redirect:/historico";
    }

    /** Pix recebido sem venda: alguém resolveu à mão (estornou, lançou a venda...). */
    @PostMapping("/historico/pix/{id}/resolver")
    public String resolverPix(@PathVariable Long id, @RequestParam String como, RedirectAttributes flash) {
        cobrancasPix.resolver(id, como);
        flash.addFlashAttribute("sucesso", "Pix marcado como resolvido.");
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
