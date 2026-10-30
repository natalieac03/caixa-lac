package br.com.lac.caixa.servico.preco;
import br.com.lac.caixa.modelo.Promocao;
import java.math.BigDecimal;
import java.util.*;
public interface RegraPromocao {
    void preparar(Promocao p, Map<Long,Integer> quantidades, Map<Long,BigDecimal> precos, List<Promocao> pacotes);
}
