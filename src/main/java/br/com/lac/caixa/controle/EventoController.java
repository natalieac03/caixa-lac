package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.util.Dinheiro;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EventoController {
    private final EventoService eventos;

    public EventoController(EventoService eventos) {
        this.eventos = eventos;
    }

    @GetMapping("/eventos")
    public String tela(@RequestParam(required = false) Long editar, Model model) {
        model.addAttribute("eventos", eventos.listar());
        model.addAttribute("editando", editar == null ? null : eventos.buscar(editar));
        return "eventos";
    }

    @PostMapping("/eventos")
    public String criar(
            @RequestParam String nome,
            @RequestParam String inicio,
            @RequestParam String fim,
            @RequestParam(required = false) String observacao,
            @RequestParam(required = false) String metaReceita,
            @RequestParam(required = false) String metaLucro,
            RedirectAttributes flash) {
        var e = eventos.criar(
                nome,
                dataHora(inicio),
                dataHora(fim),
                observacao,
                Dinheiro.parse(metaReceita),
                Dinheiro.parse(metaLucro));
        flash.addFlashAttribute("sucesso", "Evento \"" + e.getNome() + "\" criado.");
        return "redirect:/eventos";
    }

    @PostMapping("/eventos/{id}/editar")
    public String editar(
            @PathVariable Long id,
            @RequestParam String nome,
            @RequestParam String inicio,
            @RequestParam String fim,
            @RequestParam(required = false) String observacao,
            @RequestParam(required = false) String metaReceita,
            @RequestParam(required = false) String metaLucro,
            RedirectAttributes flash) {
        // Campos de meta vazios = sem meta.
        var e = eventos.editar(
                id,
                nome,
                dataHora(inicio),
                dataHora(fim),
                observacao,
                Dinheiro.parse(metaReceita),
                Dinheiro.parse(metaLucro));
        flash.addFlashAttribute("sucesso", "Evento \"" + e.getNome() + "\" atualizado.");
        return "redirect:/eventos";
    }

    /** "2024-06-01T10:00" (campo datetime-local). */
    static LocalDateTime dataHora(String texto) {
        if (texto == null || texto.isBlank()) throw new RegraDeNegocioException("Informe o início e o fim do evento.");
        try {
            return LocalDateTime.parse(texto.trim());
        } catch (DateTimeParseException ex) {
            throw new RegraDeNegocioException("Data e hora inválidas.");
        }
    }
}
