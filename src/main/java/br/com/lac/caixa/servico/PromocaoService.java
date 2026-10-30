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

    public PromocaoService(PromocaoRepository promocoes, CategoriaService categorias) {
        this.promocoes = promocoes;
        this.categorias = categorias;
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
        promocoes.save(new Promocao(itens, tipo, preco));
    }

    @Transactional
    public void remover(Long id) {
        promocoes.deleteById(id);
    }

    @Transactional
    public void alternar(Long id) {
        Promocao p = promocoes.findById(id)
                .orElseThrow(() -> new RegraDeNegocioException("Promoção não encontrada."));
        p.setAtivo(!p.isAtivo());
        promocoes.save(p);
    }
}
