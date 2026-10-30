package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.ItemVenda;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemVendaRepository extends JpaRepository<ItemVenda, Long> {
    /** Quantas vendas já usaram este produto. */
    long countByProdutoId(Long produtoId);
}
