package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.repositorio.VendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

@Service
public class FechamentoService {
    private final VendaRepository vendas;
    public FechamentoService(VendaRepository vendas) { this.vendas = vendas; }

    @Transactional(readOnly = true)
    public Fechamento consultar(LocalDate inicio, LocalDate fim) {
        var filtro = new FiltroHistorico(null, inicio, fim, "");
        var agregados = vendas.fechamento(filtro.inicioConsulta(), filtro.fimConsulta());
        var linhas = new ArrayList<Fechamento.Linha>();
        long quantidade = 0;
        BigDecimal total = BigDecimal.ZERO, descontos = BigDecimal.ZERO, ajustes = BigDecimal.ZERO;
        for (FormaPagamento forma : FormaPagamento.values()) {
            var resumo = agregados.stream().filter(r -> r.getForma() == forma).findFirst();
            var linha = resumo.map(r -> new Fechamento.Linha(forma, r.getQuantidade(), r.getTotal(), r.getDescontos(), r.getAjustes()))
                    .orElseGet(() -> new Fechamento.Linha(forma, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            linhas.add(linha);
            quantidade += linha.quantidade(); total = total.add(linha.total());
            descontos = descontos.add(linha.descontos()); ajustes = ajustes.add(linha.ajustes());
        }
        return new Fechamento(linhas, quantidade, total, descontos, ajustes);
    }
}
