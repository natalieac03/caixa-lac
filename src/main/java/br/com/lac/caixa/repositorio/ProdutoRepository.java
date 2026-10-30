package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Produto;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    /** Apenas os produtos ativos (não arquivados), já com a categoria. */
    @EntityGraph(attributePaths = "categoria")
    List<Produto> findByAtivoTrueOrderByNomeAsc();
}
