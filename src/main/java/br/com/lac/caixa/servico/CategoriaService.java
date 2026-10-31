package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categorias de produto: listar, criar, renomear e remover. */
@Service
public class CategoriaService {

    private final CategoriaRepository categorias;

    private final Auditoria auditoria;

    public CategoriaService(CategoriaRepository categorias, Auditoria auditoria) {
        this.categorias = categorias;
        this.auditoria = auditoria;
    }

    public List<Categoria> listar() {
        return categorias.findAllByOrderByNomeAsc();
    }

    public Categoria buscar(Long id) {
        return categorias
                .findById(id)
                .orElseThrow(() -> new RegraDeNegocioException("Categoria não encontrada: " + id));
    }

    @Transactional
    public Categoria renomear(Long id, String nomeDigitado) {
        Categoria categoria = buscar(id);
        String normalizado = Categoria.normalizar(nomeDigitado);
        categorias
                .findByNomeNormalizado(normalizado)
                .filter(outra -> !outra.getId().equals(id))
                .ifPresent(outra -> {
                    throw new RegraDeNegocioException(
                            "Já existe a categoria \"" + outra.getNome() + "\". Escolha outro nome.");
                });
        String antes = categoria.getNome();
        categoria.renomear(nomeDigitado);
        Categoria salva = categorias.save(categoria);
        auditoria.registrar("categoria_renomeada", "id", id, "antes", antes, "depois", salva.getNome());
        return salva;
    }

    @Transactional
    public Categoria obterOuCriar(String nomeDigitado) {
        String normalizado = Categoria.normalizar(nomeDigitado);
        if (normalizado.isEmpty()) {
            throw new RegraDeNegocioException("Nome de categoria vazio.");
        }
        return categorias
                .findByNomeNormalizado(normalizado)
                .orElseGet(() -> categorias.save(new Categoria(nomeDigitado)));
    }
}
