package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.MovimentoEstoque;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {

    @Query("select coalesce(sum(m.quantidade), 0) from MovimentoEstoque m where m.produto.id = :produto")
    long saldo(@Param("produto") Long produtoId);

    boolean existsByProdutoId(Long produtoId);

    /** Invariante do estoque: produtos cuja soma dos movimentos difere da quantidade (deve voltar vazio). */
    @Query("""
            select p.id from Produto p
            where p.quantidade <> (select coalesce(sum(m.quantidade), 0) from MovimentoEstoque m where m.produto = p)
            order by p.id
            """)
    List<Long> produtosComSaldoDivergente();

    List<MovimentoEstoque> findByProdutoIdOrderByIdAsc(Long produtoId);
}
