package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConsultaHistorico {
    private final VendaRepository vendas;
    public ConsultaHistorico(VendaRepository vendas) { this.vendas = vendas; }
    public record Resultado(Page<Venda> pagina, IndicadoresHistorico resumo) {}

    public Resultado consultar(FiltroHistorico filtro, int pagina) {
        if (pagina < 0) throw new IllegalArgumentException("Página inválida.");
        var ids = vendas.paginarIds(filtro.formas(), filtro.inicioConsulta(), filtro.fimConsulta(), filtro.produto(), PageRequest.of(pagina, 50));
        List<Venda> conteudo = ids.isEmpty() ? List.of() : vendas.carregarPagina(ids.getContent());
        var resumo = vendas.indicadores(filtro.formasConsulta(), filtro.inicioConsulta(), filtro.fimConsulta(), filtro.produto());
        return new Resultado(new PageImpl<>(conteudo, ids.getPageable(), ids.getTotalElements()), resumo);
    }
    public List<Venda> exportar(FiltroHistorico filtro) {
        return vendas.exportar(filtro.formas(), filtro.inicioConsulta(), filtro.fimConsulta(), filtro.produto());
    }
}
