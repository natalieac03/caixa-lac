package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Produto;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    /** Apenas os produtos ativos (não arquivados), já com a categoria. */
    @EntityGraph(attributePaths = "categoria")
    List<Produto> findByAtivoTrueOrderByNomeAsc();

    /**
     * Baixa atômica: só tira se houver (quantidade >= q), numa única instrução.
     * Vendas simultâneas do mesmo produto esperam a vez na linha em vez de
     * falhar pela versão. Devolve 1 se baixou, 0 se não havia estoque.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Produto p set p.quantidade = p.quantidade - :q, p.versao = p.versao + 1
            where p.id = :id and p.quantidade >= :q
            """)
    int baixarSeHouver(@Param("id") Long id, @Param("q") int quantidade);

    /** Devolução atômica (cancelamento de venda). */
    @Modifying(flushAutomatically = true)
    @Query("update Produto p set p.quantidade = p.quantidade + :q, p.versao = p.versao + 1 where p.id = :id")
    int reporAtomico(@Param("id") Long id, @Param("q") int quantidade);

    @Query("select p.quantidade from Produto p where p.id = :id")
    Integer quantidadeAtual(@Param("id") Long id);
}
