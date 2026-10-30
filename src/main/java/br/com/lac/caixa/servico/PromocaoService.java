package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.repositorio.PromocaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class PromocaoService {

    private final PromocaoRepository promocoes;
    private final CategoriaService categorias;

    private final Auditoria auditoria;

    public PromocaoService(PromocaoRepository promocoes, CategoriaService categorias, Auditoria auditoria) {
        this.promocoes = promocoes;
        this.categorias = categorias;
        this.auditoria = auditoria;
    }

    private static String descrever(Promocao p) {
        return p.getTipo() + " | " + p.getNomesCategorias() + " | " + p.getPreco().toPlainString() + (p.isAtivo() ? "" : " | desligada");
    }

    public List<Promocao> listar() {
        return promocoes.findAll().stream()
                .filter(p -> !p.getItens().isEmpty())
                .sorted(Comparator.comparing(Promocao::getNomesCategorias))
                .toList();
    }

    public List<Promocao> listarAtivas() {
        return promocoes.findByAtivoTrue().stream()
                .filter(p -> !p.getItens().isEmpty())
                .toList();
    }

    /**
     * @param qtdPorCategoria mapa catId -> quantidade exigida no combo (> 0)
     */
    @Transactional
    public void criar(Map<Long, Integer> qtdPorCategoria, TipoPromocao tipo, BigDecimal preco) {
        Promocao salva = promocoes.save(new Promocao(itens(qtdPorCategoria), tipo, preco));
        auditoria.registrar("promocao_criada", "id", salva.getId(), "depois", descrever(salva));
    }

    /** Edição: mesmo formulário da criação; troca preço e quantidades, mantém o tipo. */
    @Transactional
    public Promocao editar(Long id, Map<Long, Integer> qtdPorCategoria, BigDecimal preco) {
        Promocao p = buscar(id);
        String antes = descrever(p);
        p.alterar(itens(qtdPorCategoria), preco);
        Promocao salva = promocoes.save(p);
        auditoria.registrar("promocao_editada", "id", id, "antes", antes, "depois", descrever(salva));
        return salva;
    }

    @Transactional(readOnly = true)
    public Promocao buscar(Long id) {
        Promocao p = promocoes.findById(id)
                .orElseThrow(() -> new RegraDeNegocioException("Promoção não encontrada."));
        // O formulário de edição lê as categorias fora da transação.
        p.getItens().forEach(i -> i.getCategoria().getNome());
        return p;
    }

    private List<PromocaoItem> itens(Map<Long, Integer> qtdPorCategoria) {
        if (qtdPorCategoria == null || qtdPorCategoria.isEmpty()) {
            throw new RegraDeNegocioException("Selecione ao menos uma categoria.");
        }
        List<PromocaoItem> itens = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : qtdPorCategoria.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                Categoria cat = categorias.buscar(e.getKey());
                itens.add(new PromocaoItem(cat, e.getValue()));
            }
        }
        if (itens.isEmpty()) {
            throw new RegraDeNegocioException("Selecione ao menos uma categoria com quantidade.");
        }
        return itens;
    }

    @Transactional
    public void remover(Long id) {
        promocoes.deleteById(id);
        auditoria.registrar("promocao_removida", "id", id);
    }

    @Transactional
    public void alternar(Long id) {
        Promocao p = buscar(id);
        p.setAtivo(!p.isAtivo());
        promocoes.save(p);
        auditoria.registrar(p.isAtivo() ? "promocao_ligada" : "promocao_desligada", "id", id);
    }
}
