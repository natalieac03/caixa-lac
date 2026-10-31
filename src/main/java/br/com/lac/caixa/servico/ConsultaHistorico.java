package br.com.lac.caixa.servico;

import br.com.lac.caixa.consulta.FiltroHistorico;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.IndicadoresHistorico;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.repositorio.VendaSpecs;
import java.util.List;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta do histórico: página, indicadores e CSV do mesmo filtro. */
@Service
@Transactional(readOnly = true)
public class ConsultaHistorico {

    static final int TAMANHO_PAGINA = 50;

    private final VendaRepository vendas;

    public ConsultaHistorico(VendaRepository vendas) {
        this.vendas = vendas;
    }

    public record Resultado(Page<Venda> pagina, IndicadoresHistorico resumo) {}

    public Resultado consultar(FiltroHistorico filtro, int pagina) {
        if (pagina < 0) throw new RegraDeNegocioException("Página inválida.");
        var cortada = vendas.findAll(
                VendaSpecs.doFiltro(filtro), PageRequest.of(pagina, TAMANHO_PAGINA, VendaRepository.MAIS_RECENTES));
        List<Long> ids = cortada.getContent().stream().map(Venda::getId).toList();
        List<Venda> conteudo = ids.isEmpty() ? List.of() : vendas.carregarPagina(ids);
        carregarPagamentos(conteudo);
        var resumo = vendas.indicadores(VendaSpecs.doFiltro(filtro).and(VendaSpecs.naoCancelada()));
        return new Resultado(new PageImpl<>(conteudo, cortada.getPageable(), cortada.getTotalElements()), resumo);
    }

    public List<Venda> exportar(FiltroHistorico filtro) {
        var lista = vendas.findAll(
                VendaSpecs.doFiltro(filtro).and(VendaSpecs.naoCancelada()), VendaRepository.MAIS_RECENTES);
        carregarPagamentos(lista);
        return lista;
    }

    private static void carregarPagamentos(List<Venda> lista) {
        lista.forEach(v -> Hibernate.initialize(v.getPagamentos()));
    }
}
