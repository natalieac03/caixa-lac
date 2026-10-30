package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.servico.CaixaService;
import br.com.lac.caixa.util.ExportadorCSV;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class HistoricoController {

    private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final CaixaService caixa;

    public HistoricoController(CaixaService caixa) {
        this.caixa = caixa;
    }

    @GetMapping("/historico")
    public String historico(@RequestParam(required = false) FormaPagamento forma,
                            @RequestParam(required = false) String produto,
                            @RequestParam(required = false) String dia,
                            Model model) {

        List<Venda> vendas = filtrar(forma, produto, dia);

        model.addAttribute("vendas", vendas);
        model.addAttribute("formas", FormaPagamento.values());
        model.addAttribute("forma", forma);
        model.addAttribute("produto", produto);
        model.addAttribute("dia", dia);
        return "historico";
    }

    @GetMapping("/historico/exportar.csv")
    public ResponseEntity<Resource> exportar(@RequestParam(required = false) FormaPagamento forma,
                                             @RequestParam(required = false) String produto,
                                             @RequestParam(required = false) String dia) {
        String csv = ExportadorCSV.doHistorico(filtrar(forma, produto, dia));
        return EstoqueController.arquivoCsv(csv, "historico.csv");
    }

    /**
     * Corrige o dia e a hora de uma venda. Os filtros da tela chegam como
     * campos ocultos e voltam na URL, para o historico reabrir igual.
     */
    @PostMapping("/historico/{id}/data")
    public String alterarDataHora(@PathVariable Long id,
                                  @RequestParam(required = false) String data,
                                  @RequestParam(required = false) String hora,
                                  @RequestParam(required = false) FormaPagamento forma,
                                  @RequestParam(required = false) String produto,
                                  @RequestParam(required = false) String dia,
                                  RedirectAttributes flash) {
        LocalDate novaData = parseData(data);
        LocalTime novaHora = parseHora(hora);
        LocalDateTime novaDataHora = (novaData == null || novaHora == null)
                ? null
                : LocalDateTime.of(novaData, novaHora);

        try {
            caixa.alterarDataHora(id, novaDataHora);
            flash.addFlashAttribute("sucesso",
                    "Venda #" + id + " agora está em " + novaDataHora.format(DIA_E_HORA) + ".");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }

        if (forma != null) flash.addAttribute("forma", forma);
        if (produto != null && !produto.isBlank()) flash.addAttribute("produto", produto);
        if (dia != null && !dia.isBlank()) flash.addAttribute("dia", dia);
        return "redirect:/historico";
    }

    /** Filtra a lista por forma de pagamento, nome de produto e/ou dia. */
    private List<Venda> filtrar(FormaPagamento forma, String produto, String dia) {
        LocalDate data = parseData(dia);
        String busca = (produto == null) ? "" : produto.trim().toLowerCase();

        return caixa.historico().stream()
                .filter(v -> forma == null || v.getFormaPagamento() == forma)
                .filter(v -> busca.isEmpty()
                        || v.getResumoProdutos().toLowerCase().contains(busca))
                .filter(v -> data == null || v.getDataHora().toLocalDate().equals(data))
                .toList();
    }

    private LocalDate parseData(String dia) {
        if (dia == null || dia.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dia.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** Le o "HH:mm" do input type="time"; devolve null se vazio ou invalido. */
    private LocalTime parseHora(String hora) {
        if (hora == null || hora.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(hora.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
