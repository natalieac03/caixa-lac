package br.com.lac.caixa.servico;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.PromocaoItem;
import br.com.lac.caixa.modelo.TipoPromocao;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.pagamento.Pagamento;
import br.com.lac.caixa.pagamento.PagamentoFactory;
import br.com.lac.caixa.repositorio.VendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CaixaService {

    private final VendaRepository vendas;
    private final EstoqueService estoque;
    private final PromocaoService promocoes;

    public CaixaService(VendaRepository vendas, EstoqueService estoque, PromocaoService promocoes) {
        this.vendas = vendas;
        this.estoque = estoque;
        this.promocoes = promocoes;
    }

    public List<Venda> historico() {
        return vendas.findAllByOrderByDataHoraDesc();
    }

    /**
     * Corrige o dia e a hora de uma venda já registrada (ex.: venda anotada
     * no papel e lançada no caixa depois).
     */
    @Transactional
    public void alterarDataHora(Long vendaId, LocalDateTime novaDataHora) {
        if (novaDataHora == null) {
            throw new IllegalArgumentException("Informe o dia e a hora da venda.");
        }
        Venda venda = vendas.findById(vendaId)
                .orElseThrow(() -> new IllegalArgumentException("Venda não encontrada: " + vendaId));

        // A tela só edita até os minutos: se o minuto não mudou, mantém os
        // segundos originais para não embaralhar vendas do mesmo minuto.
        if (novaDataHora.equals(venda.getDataHora().truncatedTo(ChronoUnit.MINUTES))) {
            return;
        }
        venda.setDataHora(novaDataHora);
        vendas.save(venda);
    }

    @Transactional
    public ResultadoVenda registrarVenda(Map<Long, Integer> quantidades,
                                         FormaPagamento forma,
                                         String outro,
                                         BigDecimal totalManual) {

        Venda venda = new Venda(forma, outro);
        List<Promocao> ativas = promocoes.listarAtivas();

        // -- Passo 1: resolve produtos e soma quantidades por categoria --
        Map<Long, Produto> selecionados = new LinkedHashMap<>();
        Map<Long, Integer> qtdPorCat = new HashMap<>();

        for (Map.Entry<Long, Integer> e : quantidades.entrySet()) {
            int qtd = e.getValue() == null ? 0 : e.getValue();
            if (qtd <= 0) continue;
            Produto p = estoque.buscar(e.getKey());
            selecionados.put(e.getKey(), p);
            if (p.getCategoria() != null) {
                qtdPorCat.merge(p.getCategoria().getId(), qtd, Integer::sum);
            }
        }

        if (selecionados.isEmpty()) {
            throw new IllegalArgumentException("Selecione ao menos um produto.");
        }

        // -- Passo 2: identifica promoções A_PARTIR_DE que se ativam --
        // Guarda catId -> preço promocional para aplicar em cada item.
        Map<Long, BigDecimal> precoAPartirDe = new HashMap<>();
        Map<Long, Integer> remaining = new HashMap<>(qtdPorCat);

        for (Promocao promo : ativas) {
            if (promo.getTipo() != TipoPromocao.A_PARTIR_DE) continue;
            int combos = promo.combosDisponiveis(remaining);
            if (combos == 0) continue;
            // Aplica o preco promocional a TODAS as unidades das categorias envolvidas.
            for (PromocaoItem pi : promo.getItens()) {
                Long catId = pi.getCategoria().getId();
                precoAPartirDe.put(catId, promo.getPreco());
                remaining.put(catId, 0); // "consome" para não entrar em outro promo
            }
        }

        // -- Passo 3: dá baixa no estoque e monta os itens --
        for (Map.Entry<Long, Produto> e : selecionados.entrySet()) {
            Produto p = e.getValue();
            int qtd = quantidades.get(e.getKey());
            estoque.darBaixa(p, qtd);

            BigDecimal precoUnit = (p.getCategoria() != null
                    && precoAPartirDe.containsKey(p.getCategoria().getId()))
                    ? precoAPartirDe.get(p.getCategoria().getId())
                    : p.getPreco();

            venda.adicionarItem(new ItemVenda(p, qtd, precoUnit));
        }

        // -- Passo 4: calcula desconto dos combos PACOTE --
        // Preços dos itens por categoria (ordenados decrescente para
        // maximizar o desconto: os mais caros entram primeiro no combo).
        Map<Long, List<BigDecimal>> precosPorCat = new HashMap<>();
        for (ItemVenda iv : venda.getItens()) {
            if (iv.getProduto().getCategoria() == null) continue;
            Long catId = iv.getProduto().getCategoria().getId();
            List<BigDecimal> lista = precosPorCat.computeIfAbsent(catId, k -> new ArrayList<>());
            for (int k = 0; k < iv.getQuantidade(); k++) {
                lista.add(iv.getPrecoUnitario());
            }
        }
        precosPorCat.values().forEach(l -> l.sort(Comparator.reverseOrder()));

        Map<Long, Integer> remainingPacote = new HashMap<>(qtdPorCat);
        BigDecimal descontoPacote = BigDecimal.ZERO;

        // Pacotes ordenados do melhor negócio para o pior (menor preço por
        // unidade primeiro). Assim "5 por R$6" (R$1,20/un) é aplicado antes
        // de "3 por R$4" (R$1,33/un), e o cliente sempre paga o menor total.
        List<Promocao> pacotesOrdenados = ativas.stream()
                .filter(p -> p.getTipo() == TipoPromocao.PACOTE)
                .sorted(Comparator.comparingDouble(Promocao::getPrecoPorUnidade))
                .toList();

        for (Promocao promo : pacotesOrdenados) {
            int combos = promo.combosDisponiveis(remainingPacote);
            if (combos == 0) continue;

            BigDecimal custNormal = BigDecimal.ZERO;
            for (PromocaoItem pi : promo.getItens()) {
                Long catId = pi.getCategoria().getId();
                List<BigDecimal> precos = precosPorCat.getOrDefault(catId, List.of());
                // índice de início = unidades já consumidas por combos anteriores desta cat
                int inicio = qtdPorCat.getOrDefault(catId, 0) - remainingPacote.getOrDefault(catId, 0);
                int fim = inicio + combos * pi.getQuantidade();
                for (int k = inicio; k < Math.min(fim, precos.size()); k++) {
                    custNormal = custNormal.add(precos.get(k));
                }
                // Consome as unidades usadas no combo
                remainingPacote.merge(catId, -combos * pi.getQuantidade(), Integer::sum);
            }

            BigDecimal custPromo = promo.getPreco().multiply(BigDecimal.valueOf(combos));
            BigDecimal economia = custNormal.subtract(custPromo);
            if (economia.compareTo(BigDecimal.ZERO) > 0) {
                descontoPacote = descontoPacote.add(economia);
            }
        }

        BigDecimal calculado = venda.calcularTotal().subtract(descontoPacote);
        BigDecimal total = (totalManual != null) ? totalManual : calculado;
        venda.setValorTotal(total);

        Venda salva = vendas.save(venda);
        Pagamento estrategia = PagamentoFactory.criar(forma, outro);
        return new ResultadoVenda(salva, estrategia.pagar(total));
    }
}
