package br.com.lac.caixa.repositorio;

import br.com.lac.caixa.modelo.Promocao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromocaoRepository extends JpaRepository<Promocao, Long> {
    List<Promocao> findByAtivoTrue();
}
