package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.servico.DespesaService;
import br.com.lac.caixa.servico.EventoService;
import br.com.lac.caixa.util.Dinheiro;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Tela de despesas e das categorias de despesa. */
@Controller
public class DespesaController {
    private final DespesaService despesas;
    private final EventoService eventos;
    private final Clock relogio;

    public DespesaController(DespesaService despesas, EventoService eventos, Clock relogio) {
        this.despesas = despesas;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @GetMapping("/despesas")
    public String tela(
            @RequestParam(required = false) Long evento,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) Long editar,
            Model model) {
        var filtro = new DespesaService.Filtro(evento, data(inicio), data(fim));
        model.addAttribute("despesas", despesas.filtrar(filtro));
        model.addAttribute("total", despesas.total(filtro));
        model.addAttribute("categorias", despesas.categorias());
        model.addAttribute("eventos", eventos.listar());
        model.addAttribute("evento", filtro.evento());
        model.addAttribute("inicio", filtro.inicio());
        model.addAttribute("fim", filtro.fim());
        model.addAttribute("hoje", LocalDate.now(relogio));
        model.addAttribute("editando", editar == null ? null : despesas.buscar(editar));
        return "despesas";
    }

    @PostMapping("/despesas")
    public String criar(
            @RequestParam(required = false) String data,
            @RequestParam String descricao,
            @RequestParam(required = false) Long categoria,
            @RequestParam String valor,
            @RequestParam(required = false) Long evento,
            @RequestParam(required = false) String observacao,
            RedirectAttributes flash) {
        var d = despesas.criar(obrigatoria(data), descricao, categoria, Dinheiro.parse(valor), evento, observacao);
        flash.addFlashAttribute(
                "sucesso", "Despesa \"" + d.getDescricao() + "\" de " + Dinheiro.reais(d.getValor()) + " registrada.");
        return "redirect:/despesas";
    }

    @PostMapping("/despesas/{id}/editar")
    public String editar(
            @PathVariable Long id,
            @RequestParam(required = false) String data,
            @RequestParam String descricao,
            @RequestParam(required = false) Long categoria,
            @RequestParam String valor,
            @RequestParam(required = false) Long evento,
            @RequestParam(required = false) String observacao,
            RedirectAttributes flash) {
        var d = despesas.editar(id, obrigatoria(data), descricao, categoria, Dinheiro.parse(valor), evento, observacao);
        flash.addFlashAttribute("sucesso", "Despesa \"" + d.getDescricao() + "\" atualizada.");
        return "redirect:/despesas";
    }

    @PostMapping("/despesas/{id}/apagar")
    public String apagar(@PathVariable Long id, RedirectAttributes flash) {
        despesas.apagar(id);
        flash.addFlashAttribute("sucesso", "Despesa apagada.");
        return "redirect:/despesas";
    }

    @PostMapping("/despesas/categorias")
    public String criarCategoria(@RequestParam String nome, RedirectAttributes flash) {
        var c = despesas.criarCategoria(nome);
        flash.addFlashAttribute("sucesso", "Categoria de despesa \"" + c.getNome() + "\" criada.");
        return "redirect:/despesas#categorias-despesa";
    }

    @PostMapping("/despesas/categorias/{id}/renomear")
    public String renomearCategoria(@PathVariable Long id, @RequestParam String nome, RedirectAttributes flash) {
        var c = despesas.renomearCategoria(id, nome);
        flash.addFlashAttribute("sucesso", "Categoria renomeada para \"" + c.getNome() + "\".");
        return "redirect:/despesas#categorias-despesa";
    }

    @PostMapping("/despesas/categorias/{id}/apagar")
    public String apagarCategoria(@PathVariable Long id, RedirectAttributes flash) {
        despesas.apagarCategoria(id);
        flash.addFlashAttribute("sucesso", "Categoria de despesa apagada.");
        return "redirect:/despesas#categorias-despesa";
    }

    private static LocalDate obrigatoria(String texto) {
        LocalDate d = data(texto);
        if (d == null) throw new RegraDeNegocioException("Informe a data da despesa.");
        return d;
    }

    private static LocalDate data(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException ex) {
            throw new RegraDeNegocioException("Data inválida.");
        }
    }
}
