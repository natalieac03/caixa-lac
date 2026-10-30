package br.com.lac.caixa.servico.preco;
import br.com.lac.caixa.modelo.Promocao;
import java.math.BigDecimal;
import java.util.*;
public class APartirDe implements RegraPromocao {
    public void preparar(Promocao p, Map<Long,Integer> quantidades, Map<Long,BigDecimal> precos, List<Promocao> pacotes) {
        if (p.combosDisponiveis(quantidades) == 0) return;
        for (var item : p.getItens()) precos.merge(item.getCategoria().getId(), p.getPreco(), BigDecimal::min);
    }
}
