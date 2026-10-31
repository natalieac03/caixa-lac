package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.exportacao.ExportadorCSV;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.repositorio.FiltroHistorico;
import br.com.lac.caixa.repositorio.PeriodoDoEvento;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.servico.CobrancaPixService;
import br.com.lac.caixa.servico.ConsultaHistorico;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import java.time.Clock;
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
    private final EventoService eventos;
    private final Clock relogio;

    public HistoricoController(
            CaixaService caixa,
            ConsultaHistorico consulta,
            CobrancaPixService cobrancasPix,
            EventoService eventos,
            Clock relogio) {
        this.caixa = caixa;
        this.consulta = consulta;
        this.cobrancasPix = cobrancasPix;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /** Evento (vale como período), Dia a dia (dentro das datas) ou Todos. */
    private FiltroHistorico filtro(
            FormaPagamento forma, String inicio, String fim, String dia, String produto, SeletorDeEvento sel) {
        PeriodoDoEvento periodo = sel.id() == null ? null : eventos.periodo(sel.id());
        var filtro = FiltroHistorico.criar(forma, inicio, fim, dia, produto, periodo);
        return sel.diaADia() ? filtro.comDiaADia(LocalDateTime.now(relogio)) : filtro;
    }

    @GetMapping("/historico")
    public String historico(
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String produto,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String dia,
            @RequestParam(required = false) String evento,
            @RequestParam(defaultValue = "0") int pagina,
            Model model) {
        var sel = SeletorDeEvento.ler(evento);
        var filtro = filtro(forma, inicio, fim, dia, produto, sel);
        var resultado = consulta.consultar(filtro, pagina);
        model.addAttribute("vendas", resultado.pagina().getContent());
        var pixSemVenda = cobrancasPix.pixSemVenda();
        model.addAttribute("pixSemVenda", pixSemVenda);
        model.addAttribute("pixParaEstornar", cobrancasPix.deVendaCancelada(pixSemVenda));
        model.addAttribute("pagina", resultado.pagina());
        model.addAttribute("resumo", resultado.resumo());
        model.addAttribute("formas", FormaPagamento.values());
        model.addAttribute("forma", filtro.forma());
        model.addAttribute("produto", filtro.produto());
        model.addAttribute("inicio", filtro.inicio());
        model.addAttribute("fim", filtro.fim());
        model.addAttribute("evento", sel.valor());
        model.addAttribute("eventos", eventos.listar());
        // Evento de cada venda, calculado pela data e hora (não há vínculo gravado).
        model.addAttribute("eventoDaVenda", new EventosDaVenda(eventos.periodos()));
        return "historico";
    }

    @GetMapping("/historico/exportar.csv")
    public ResponseEntity<Resource> exportar(
            @RequestParam(required = false) FormaPagamento forma,
            @RequestParam(required = false) String produto,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String dia,
            @RequestParam(required = false) String evento) {
        var vendas = consulta.exportar(filtro(forma, inicio, fim, dia, produto, SeletorDeEvento.ler(evento)));
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
            @RequestParam(required = false) String evento,
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
        if (evento != null && !evento.isBlank()) flash.addAttribute("evento", evento);
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
        BigDecimal pixMercadoPago = caixa.cancelarVenda(id, motivo);
        String aviso = pixMercadoPago.signum() > 0
                ? " Esta venda tinha " + Dinheiro.reais(pixMercadoPago) + " de Pix pelo Mercado Pago: estorne no app do"
                        + " Mercado Pago e depois marque como resolvido em \"Pix recebido sem venda\"."
                : "";
        flash.addFlashAttribute("sucesso", "Venda #" + id + " cancelada. Estoque devolvido." + aviso);
        return "redirect:/historico";
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String conflito(RedirectAttributes flash) {
        flash.addFlashAttribute("erro", "Os dados mudaram durante a operação. Confira o histórico e tente novamente.");
        return "redirect:/historico";
    }
}
