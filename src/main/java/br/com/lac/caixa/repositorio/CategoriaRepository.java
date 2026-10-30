package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Categoria;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByNomeNormalizado(String nomeNormalizado);

    List<Categoria> findAllByOrderByNomeAsc();
}
