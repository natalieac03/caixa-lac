package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.MovimentoEstoque;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso aos movimentos de estoque e à conferência do invariante. */
public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {

    @Query("select coalesce(sum(m.quantidade), 0) from MovimentoEstoque m where m.produto.id = :produto")
    long saldo(@Param("produto") Long produtoId);

    boolean existsByProdutoId(Long produtoId);

    @Query("""
            select p.id from Produto p
            where p.quantidade <> (select coalesce(sum(m.quantidade), 0) from MovimentoEstoque m where m.produto = p)
            order by p.id
            """)
    List<Long> produtosComSaldoDivergente();

    List<MovimentoEstoque> findByProdutoIdOrderByIdAsc(Long produtoId);
}
