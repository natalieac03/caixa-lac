package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Produto;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acesso aos produtos, com baixa e devolução atômicas do estoque. */
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    @EntityGraph(attributePaths = "categoria")
    List<Produto> findByAtivoTrueOrderByNomeAsc();

    @Modifying(flushAutomatically = true)
    @Query("""
            update Produto p set p.quantidade = p.quantidade - :q, p.versao = p.versao + 1
            where p.id = :id and p.quantidade >= :q
            """)
    int baixarSeHouver(@Param("id") Long id, @Param("q") int quantidade);

    @Modifying(flushAutomatically = true)
    @Query("update Produto p set p.quantidade = p.quantidade + :q, p.versao = p.versao + 1 where p.id = :id")
    int reporAtomico(@Param("id") Long id, @Param("q") int quantidade);

    @Query("select p.quantidade from Produto p where p.id = :id")
    Integer quantidadeAtual(@Param("id") Long id);
}
