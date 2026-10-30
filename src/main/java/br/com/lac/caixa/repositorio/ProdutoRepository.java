package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    /** Apenas os produtos ativos (não arquivados). */
    List<Produto> findByAtivoTrueOrderByNomeAsc();
}
