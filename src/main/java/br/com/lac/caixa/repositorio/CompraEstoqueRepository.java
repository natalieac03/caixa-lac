package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.CompraEstoque;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso às compras de estoque. */
public interface CompraEstoqueRepository extends JpaRepository<CompraEstoque, Long> {}
