package br.com.lac.caixa.controle;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.servico.CategoriaService;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.util.Dinheiro;
import br.com.lac.caixa.util.ExportadorCSV;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EstoqueController {

    private final EstoqueService estoque;
    private final CategoriaService categorias;
    private final Clock relogio;

    public EstoqueController(EstoqueService estoque, CategoriaService categorias, Clock relogio) {
        this.estoque = estoque;
        this.categorias = categorias;
        this.relogio = relogio;
    }

    /** semCusto=true: só os produtos sem custo (link do aviso de lucro parcial nas Finanças). */
    @GetMapping("/estoque")
    public String listar(@RequestParam(required = false) boolean semCusto, Model model) {
        var produtos = estoque.listar();
        model.addAttribute("soSemCusto", semCusto);
        model.addAttribute(
                "produtos",
                semCusto ? produtos.stream().filter(p -> p.isSemCusto()).toList() : produtos);
        model.addAttribute("categorias", categorias.listar());
        model.addAttribute("hoje", LocalDate.now(relogio));
        return "estoque";
    }

    /**
     * Cadastra um produto novo. A categoria pode vir de duas formas:
     * - novaCategoria preenchida -> cria (ou reaproveita) essa categoria;
     * - senão, usa a categoriaId selecionada na lista.
     * "BÓTON" e "bóton" caem na mesma categoria (nome padronizado).
     */
    @PostMapping("/estoque/novo")
    public String novo(
            @RequestParam String nome,
            @RequestParam String preco,
            @RequestParam @PositiveOrZero(message = "Quantidade não pode ser negativa.") int quantidade,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) String novaCategoria,
            @RequestParam(required = false) String custo,
            @RequestParam(required = false) String estoqueMinimo,
            RedirectAttributes flash) {
        BigDecimal valor = Dinheiro.parse(preco);
        Categoria categoria = categoriaEscolhida(categoriaId, novaCategoria);
        var criado = estoque.criarNovo(nome, valor, quantidade, categoria, Dinheiro.parse(custo));
        if (estoqueMinimo != null && !estoqueMinimo.isBlank())
            estoque.definirLimite(criado.getId(), limite(estoqueMinimo));
        flash.addFlashAttribute(
                "sucesso", "Produto \"" + nome + "\" cadastrado na categoria " + categoria.getNome() + ".");
        return "redirect:/estoque";
    }

    /** Edição inline: nome, preço e categoria. O preço novo só vale para vendas novas. */
    @PostMapping("/estoque/{id}/editar")
    public String editar(
            @PathVariable Long id,
            @RequestParam String nome,
            @RequestParam String preco,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) String novaCategoria,
            @RequestParam(required = false) String custo,
            @RequestParam(defaultValue = "false") boolean removerCusto,
            @RequestParam(required = false) String estoqueMinimo,
            RedirectAttributes flash) {
        BigDecimal valor = Dinheiro.parse(preco);
        if (valor == null) throw new RegraDeNegocioException("Informe o preço.");
        Categoria categoria = categoriaEscolhida(categoriaId, novaCategoria);
        // Custo vazio = mantém o atual (que pode ter 4 casas, do custo médio);
        // "sem custo" marcado = apaga.
        BigDecimal novoCusto = removerCusto
                ? null
                : custo == null || custo.isBlank() ? estoque.buscar(id).getCustoUnitario() : Dinheiro.parse(custo);
        var p = estoque.editar(id, nome, valor, categoria, novoCusto);
        // Campo ausente = mantém; presente e vazio = sem limite.
        if (estoqueMinimo != null) estoque.definirLimite(id, estoqueMinimo.isBlank() ? null : limite(estoqueMinimo));
        flash.addFlashAttribute(
                "sucesso", "Produto \"" + p.getNome() + "\" atualizado. O preço novo vale só para vendas novas.");
        return "redirect:/estoque";
    }

    @PostMapping("/estoque/categorias/{id}/renomear")
    public String renomearCategoria(@PathVariable Long id, @RequestParam String nome, RedirectAttributes flash) {
        var c = categorias.renomear(id, nome);
        flash.addFlashAttribute("sucesso", "Categoria renomeada para \"" + c.getNome() + "\".");
        return "redirect:/estoque";
    }

    /** Nova categoria digitada (cria ou reaproveita) ou a selecionada na lista. */
    private Categoria categoriaEscolhida(Long categoriaId, String novaCategoria) {
        if (novaCategoria != null && !novaCategoria.isBlank()) return categorias.obterOuCriar(novaCategoria);
        if (categoriaId != null) return categorias.buscar(categoriaId);
        throw new RegraDeNegocioException("Escolha uma categoria ou crie uma nova.");
    }

    /** Soma unidades a um produto existente. */
    /**
     * Entrada de estoque por compra: soma ao estoque e recalcula o custo médio.
     * Data vazia = hoje. O "+ Adicionar" de cada linha continua, sem custo.
     */
    @PostMapping("/estoque/compra")
    public String registrarCompra(
            @RequestParam Long produtoId,
            @RequestParam @Min(value = 1, message = "Quantidade deve ser maior que zero.") int quantidade,
            @RequestParam String custoTotal,
            @RequestParam(required = false) String data,
            @RequestParam(required = false) String observacao,
            RedirectAttributes flash) {
        BigDecimal custo = Dinheiro.parse(custoTotal);
        if (custo == null) throw new RegraDeNegocioException("Informe o custo total da compra.");
        LocalDate dia;
        try {
            dia = data == null || data.isBlank() ? LocalDate.now(relogio) : LocalDate.parse(data.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new RegraDeNegocioException("Data da compra inválida.");
        }
        var compra = estoque.registrarCompra(produtoId, quantidade, custo, dia, observacao);
        flash.addFlashAttribute(
                "sucesso",
                "Entrada de " + quantidade + " un. de \"" + compra.getProduto().getNome()
                        + "\" registrada. Custo médio: " + Dinheiro.reais(compra.getCustoNovo()) + ".");
        return "redirect:/estoque";
    }

    @PostMapping("/estoque/{id}/adicionar")
    public String adicionar(
            @PathVariable Long id,
            @RequestParam @Min(value = 1, message = "Quantidade deve ser maior que zero.") int quantidade,
            RedirectAttributes flash) {
        estoque.adicionarUnidades(id, quantidade);
        flash.addFlashAttribute("sucesso", "Estoque atualizado.");
        return "redirect:/estoque";
    }

    /** Tira unidades de um produto existente (sem apagar o produto). */
    @PostMapping("/estoque/{id}/remover-unidades")
    public String removerUnidades(
            @PathVariable Long id,
            @RequestParam @Min(value = 1, message = "Quantidade deve ser maior que zero.") int quantidade,
            RedirectAttributes flash) {
        estoque.removerUnidades(id, quantidade);
        flash.addFlashAttribute("sucesso", "Estoque atualizado.");
        return "redirect:/estoque";
    }

    @PostMapping("/estoque/{id}/remover")
    public String remover(@PathVariable Long id, RedirectAttributes flash) {
        boolean arquivado = estoque.remover(id);
        flash.addFlashAttribute(
                "sucesso",
                arquivado
                        ? "Produto arquivado: ele já aparece em vendas antigas, então saiu do estoque mas continua no histórico."
                        : "Produto removido.");
        return "redirect:/estoque";
    }

    /** Botao "Exportar": baixa o estoque em CSV. */
    @GetMapping("/estoque/exportar.csv")
    public ResponseEntity<Resource> exportar() {
        String csv = ExportadorCSV.doEstoque(estoque.listar());
        return RespostaCsv.arquivo(csv, "estoque.csv");
    }

    private static Integer limite(String texto) {
        try {
            return Integer.valueOf(texto.trim());
        } catch (NumberFormatException e) {
            throw new RegraDeNegocioException("Limite de estoque baixo inválido.");
        }
    }
}
