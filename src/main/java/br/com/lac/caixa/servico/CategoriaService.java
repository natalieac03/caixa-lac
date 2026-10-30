package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.repositorio.CategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoriaService {

    private final CategoriaRepository categorias;

    public CategoriaService(CategoriaRepository categorias) {
        this.categorias = categorias;
    }

    public List<Categoria> listar() {
        return categorias.findAllByOrderByNomeAsc();
    }

    public Categoria buscar(Long id) {
        return categorias.findById(id)
                .orElseThrow(() -> new RegraDeNegocioException("Categoria não encontrada: " + id));
    }

    /**
     * Devolve a categoria com esse nome, criando se não existir.
     * "BÓTON", "bóton" e "Bóton" caem na mesma categoria.
     */
    @Transactional
    public Categoria obterOuCriar(String nomeDigitado) {
        String normalizado = Categoria.normalizar(nomeDigitado);
        if (normalizado.isEmpty()) {
            throw new RegraDeNegocioException("Nome de categoria vazio.");
        }
        return categorias.findByNomeNormalizado(normalizado)
                .orElseGet(() -> categorias.save(new Categoria(nomeDigitado)));
    }
}
