package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Venda;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendaRepository extends JpaRepository<Venda, Long> {
    java.util.List<Venda> findAllByOrderByDataHoraDesc();
}
