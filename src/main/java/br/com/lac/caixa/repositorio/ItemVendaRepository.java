package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.ItemVenda;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso aos itens de venda. */
public interface ItemVendaRepository extends JpaRepository<ItemVenda, Long> {
    long countByProdutoId(Long produtoId);
}
