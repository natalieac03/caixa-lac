package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.servico.CategoriaService;
import br.com.lac.caixa.servico.EstoqueService;
import br.com.lac.caixa.util.ExportadorCSV;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Controller
public class EstoqueController {

    private final EstoqueService estoque;
    private final CategoriaService categorias;

    public EstoqueController(EstoqueService estoque, CategoriaService categorias) {
        this.estoque = estoque;
        this.categorias = categorias;
    }

    @GetMapping("/estoque")
    public String listar(Model model) {
        model.addAttribute("produtos", estoque.listar());
        model.addAttribute("categorias", categorias.listar());
        return "estoque";
    }

    /**
     * Cadastra um produto novo. A categoria pode vir de duas formas:
     * - novaCategoria preenchida -> cria (ou reaproveita) essa categoria;
     * - senão, usa a categoriaId selecionada na lista.
     * "BÓTON" e "bóton" caem na mesma categoria (nome padronizado).
     */
    @PostMapping("/estoque/novo")
    public String novo(@RequestParam String nome,
                       @RequestParam String preco,
                       @RequestParam int quantidade,
                       @RequestParam(required = false) Long categoriaId,
                       @RequestParam(required = false) String novaCategoria,
                       RedirectAttributes flash) {
        try {
            BigDecimal valor = new BigDecimal(preco.trim().replace(",", "."));

            Categoria categoria;
            if (novaCategoria != null && !novaCategoria.isBlank()) {
                categoria = categorias.obterOuCriar(novaCategoria);
            } else if (categoriaId != null) {
                categoria = categorias.buscar(categoriaId);
            } else {
                flash.addFlashAttribute("erro", "Escolha uma categoria ou crie uma nova.");
                return "redirect:/estoque";
            }

            estoque.criarNovo(nome, valor, quantidade, categoria);
            flash.addFlashAttribute("sucesso",
                    "Produto \"" + nome + "\" cadastrado na categoria " + categoria.getNome() + ".");
        } catch (NumberFormatException e) {
            flash.addFlashAttribute("erro", "Preço inválido.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/estoque";
    }

    /** Soma unidades a um produto existente. */
    @PostMapping("/estoque/{id}/adicionar")
    public String adicionar(@PathVariable Long id,
                            @RequestParam int quantidade,
                            RedirectAttributes flash) {
        estoque.adicionarUnidades(id, quantidade);
        flash.addFlashAttribute("sucesso", "Estoque atualizado.");
        return "redirect:/estoque";
    }

    /** Tira unidades de um produto existente (sem apagar o produto). */
    @PostMapping("/estoque/{id}/remover-unidades")
    public String removerUnidades(@PathVariable Long id,
                                  @RequestParam int quantidade,
                                  RedirectAttributes flash) {
        try {
            estoque.removerUnidades(id, quantidade);
            flash.addFlashAttribute("sucesso", "Estoque atualizado.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/estoque";
    }

    @PostMapping("/estoque/{id}/remover")
    public String remover(@PathVariable Long id, RedirectAttributes flash) {
        try {
            boolean arquivado = estoque.remover(id);
            flash.addFlashAttribute("sucesso", arquivado
                    ? "Produto arquivado: ele já aparece em vendas antigas, então saiu do estoque mas continua no histórico."
                    : "Produto removido.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/estoque";
    }

    /** Botao "Exportar": baixa o estoque em CSV. */
    @GetMapping("/estoque/exportar.csv")
    public ResponseEntity<Resource> exportar() {
        String csv = ExportadorCSV.doEstoque(estoque.listar());
        return arquivoCsv(csv, "estoque.csv");
    }

    static ResponseEntity<Resource> arquivoCsv(String csv, String nomeArquivo) {
        // BOM UTF-8 para o Excel exibir os acentos corretamente.
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] corpo = csv.getBytes(StandardCharsets.UTF_8);
        byte[] conteudo = new byte[bom.length + corpo.length];
        System.arraycopy(bom, 0, conteudo, 0, bom.length);
        System.arraycopy(corpo, 0, conteudo, bom.length, corpo.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(new ByteArrayResource(conteudo));
    }
}
