package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.CategoriaDespesa;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso às categorias de despesa. */
public interface CategoriaDespesaRepository extends JpaRepository<CategoriaDespesa, Long> {
    List<CategoriaDespesa> findAllByOrderByNomeAsc();

    Optional<CategoriaDespesa> findByNomeNormalizado(String nomeNormalizado);
}
