package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Promocao;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acesso às promoções. */
public interface PromocaoRepository extends JpaRepository<Promocao, Long> {
    @EntityGraph(attributePaths = {"itens", "itens.categoria"})
    @Query("select p from Promocao p where p.ativo = true order by p.id")
    List<Promocao> findByAtivoTrue();

    @Override
    @EntityGraph(attributePaths = {"itens", "itens.categoria"})
    List<Promocao> findAll();
}
