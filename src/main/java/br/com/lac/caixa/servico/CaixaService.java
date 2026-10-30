package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.pagamento.PagamentoFactory;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.preco.CalculadoraDePreco;
import br.com.lac.caixa.servico.preco.Carrinho;
import br.com.lac.caixa.servico.preco.Orcamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CaixaService {

    private final VendaRepository vendas;
    private final EstoqueService estoque;
    private final PromocaoService promocoes;
    private final CalculadoraDePreco calculadora = new CalculadoraDePreco();

    public CaixaService(VendaRepository vendas, EstoqueService estoque, PromocaoService promocoes) {
        this.vendas = vendas;
        this.estoque = estoque;
        this.promocoes = promocoes;
    }


    /**
     * Corrige o dia e a hora de uma venda já registrada (ex.: venda anotada
     * no papel e lançada no caixa depois).
     */
    @Transactional
    public void alterarDataHora(Long vendaId, LocalDateTime novaDataHora) {
        if (novaDataHora == null) {
            throw new RegraDeNegocioException("Informe o dia e a hora da venda.");
        }
        Venda venda = vendas.findById(vendaId)
                .orElseThrow(() -> new RegraDeNegocioException("Venda não encontrada: " + vendaId));

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
                                         List<PagamentoInformado> pagamentos,
                                         BigDecimal totalManual, String motivoAjuste, String token) {
        if (token == null || !UUID.fromString(token).toString().equals(token)) throw new RegraDeNegocioException("Token de venda inválido.");
        var existente = vendas.findByToken(token);
        // Idempotência: o mesmo formulário (token) enviado de novo devolve a venda que já existe.
        if (existente.isPresent()) return new ResultadoVenda(existente.get(), "Venda já registrada.", true);


        Venda venda = Venda.nova();
        venda.setToken(token);
        List<Promocao> ativas = promocoes.listarAtivas();

        // Resolve os produtos antes de calcular e alterar o estoque.
        Map<Long, Produto> selecionados = new LinkedHashMap<>();

        for (Map.Entry<Long, Integer> e : quantidades.entrySet()) {
            int qtd = e.getValue() == null ? 0 : e.getValue();
            if (qtd < 0) throw new RegraDeNegocioException("Quantidade inválida.");
            if (qtd == 0) continue;
            Produto p = estoque.buscar(e.getKey());
            if (!p.isAtivo()) throw new RegraDeNegocioException("Produto arquivado.");
            selecionados.put(e.getKey(), p);
        }

        if (selecionados.isEmpty()) {
            throw new RegraDeNegocioException("Selecione ao menos um produto.");
        }

        var carrinho = new Carrinho(selecionados.values().stream()
                .map(p -> new Carrinho.Linha(p, quantidades.get(p.getId()))).toList());
        var orcamento = calculadora.calcular(carrinho, ativas);
        BigDecimal calculado = orcamento.total();
        BigDecimal total = totalManual != null ? totalManual : calculado;
        if (total.signum() < 0) throw new RegraDeNegocioException("O valor ajustado não pode ser negativo.");
        if (total.compareTo(calculado) != 0 && (motivoAjuste == null || motivoAjuste.isBlank()))
            throw new RegraDeNegocioException("Informe o motivo do ajuste de valor.");
        venda.registrarValores(calculado, orcamento.descontos().stream().map(Orcamento.Desconto::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add), total, motivoAjuste);
        // O servidor confere o pagamento de novo: soma, formas, descrição do Outro, recebido.
        venda.registrarPagamentos(partes(pagamentos, total));

        for (var item : orcamento.itens()) {
            Produto p = selecionados.get(item.produtoId());
            estoque.darBaixa(p, item.quantidade());
            venda.adicionarItem(new ItemVenda(p, item.quantidade(), item.precoUnitario()));
        }

        Venda salva = vendas.save(venda);
        return new ResultadoVenda(salva, PagamentoFactory.mensagem(salva.getPagamentos()), false);
    }

    /**
     * Parte única sem valor = o total (um toque). Na cortesia (total zero),
     * essa parte some: cortesia não tem pagamento.
     */
    private static List<PagamentoVenda> partes(List<PagamentoInformado> informados, BigDecimal total) {
        if (informados == null || informados.isEmpty()) return List.of();
        if (informados.size() == 1 && informados.get(0).valor() == null) {
            PagamentoInformado unico = informados.get(0);
            if (total.signum() == 0) return List.of();
            return List.of(new PagamentoVenda(unico.forma(), total, unico.descricao(), unico.recebido()));
        }
        List<PagamentoVenda> partes = new ArrayList<>();
        for (PagamentoInformado p : informados) {
            if (p.valor() == null) throw new RegraDeNegocioException("Informe o valor de cada parte do pagamento.");
            partes.add(new PagamentoVenda(p.forma(), p.valor(), p.descricao(), p.recebido()));
        }
        return partes;
    }
    @Transactional(readOnly = true)
    public Orcamento previa(Map<Long,Integer> quantidades) {
        var linhas = new ArrayList<Carrinho.Linha>();
        quantidades.forEach((id,qtd) -> {
            if (qtd < 0) throw new RegraDeNegocioException("Quantidade inválida.");
            if (qtd > 0) linhas.add(new Carrinho.Linha(estoque.buscar(id), qtd));
        });
        return calculadora.calcular(new Carrinho(linhas), promocoes.listarAtivas());
    }
    @Transactional(readOnly = true)
    public boolean tokenRegistrado(String token) { return token != null && vendas.existsByToken(token); }
    /** O bloqueio da venda serializa cancelamentos; a versão protege o estoque. */
    @Transactional
    public void cancelarVenda(Long id, String motivo) {
        Venda venda = vendas.buscarParaCancelar(id)
                .orElseThrow(() -> new RegraDeNegocioException("Venda não encontrada: " + id));
        if (venda.isCancelada()) return;
        venda.cancelar(motivo, LocalDateTime.now());
        for (ItemVenda item : venda.getItens()) estoque.adicionarUnidades(item.getProduto().getId(), item.getQuantidade());
        vendas.save(venda);
    }
}
