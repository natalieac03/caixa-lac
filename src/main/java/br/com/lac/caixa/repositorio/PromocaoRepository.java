package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Promocao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromocaoRepository extends JpaRepository<Promocao, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"itens", "itens.categoria"})
    @org.springframework.data.jpa.repository.Query("select p from Promocao p where p.ativo = true order by p.id")
    List<Promocao> findByAtivoTrue();
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"itens", "itens.categoria"})
    List<Promocao> findAll();
}
