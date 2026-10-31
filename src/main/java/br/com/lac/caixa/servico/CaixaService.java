package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.OrigemPagamento;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Promocao;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.modelo.VendaDesconto;
import br.com.lac.caixa.modelo.pagamento.PagamentoFactory;
import br.com.lac.caixa.repositorio.VendaRepository;
import br.com.lac.caixa.servico.preco.CalculadoraDePreco;
import br.com.lac.caixa.servico.preco.Carrinho;
import br.com.lac.caixa.servico.preco.Orcamento;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra, cancela e corrige vendas: preço, pagamento, taxas e estoque. */
@Service
public class CaixaService {

    private final VendaRepository vendas;
    private final EstoqueService estoque;
    private final PromocaoService promocoes;
    private final CalculadoraDePreco calculadora = new CalculadoraDePreco();

    private final Auditoria auditoria;

    private final CobrancaPixService cobrancasPix;

    private final Clock relogio;

    private final TaxaService taxas;

    public CaixaService(
            VendaRepository vendas,
            EstoqueService estoque,
            PromocaoService promocoes,
            Auditoria auditoria,
            CobrancaPixService cobrancasPix,
            Clock relogio,
            TaxaService taxas) {
        this.vendas = vendas;
        this.estoque = estoque;
        this.promocoes = promocoes;
        this.auditoria = auditoria;
        this.cobrancasPix = cobrancasPix;
        this.relogio = relogio;
        this.taxas = taxas;
    }

    @Transactional
    public void alterarDataHora(Long vendaId, LocalDateTime novaDataHora) {
        if (novaDataHora == null) {
            throw new RegraDeNegocioException("Informe o dia e a hora da venda.");
        }
        Venda venda = vendas.findById(vendaId)
                .orElseThrow(() -> new RegraDeNegocioException("Venda não encontrada: " + vendaId));
        if (venda.isCancelada()) throw new RegraDeNegocioException("Venda cancelada não tem a data corrigida.");
        if (novaDataHora.isAfter(LocalDateTime.now(relogio)))
            throw new RegraDeNegocioException("A data da venda não pode ser no futuro.");

        if (novaDataHora.equals(venda.getDataHora().truncatedTo(ChronoUnit.MINUTES))) {
            return;
        }
        LocalDateTime antes = venda.getDataHora();
        venda.setDataHora(novaDataHora);
        for (PagamentoVenda parte : venda.getPagamentos()) {
            parte.registrarTaxa(
                    taxas.vigente(parte.getForma(), novaDataHora.toLocalDate()).orElse(null));
        }
        vendas.save(venda);
        auditoria.registrar("correcao_data", "id", vendaId, "antes", antes, "depois", novaDataHora);
    }

    @Transactional
    public ResultadoVenda registrarVenda(
            Map<Long, Integer> quantidades,
            List<PagamentoInformado> pagamentos,
            BigDecimal totalManual,
            String motivoAjuste,
            String token) {
        if (!tokenValido(token)) throw new RegraDeNegocioException("Token de venda inválido.");
        var existente = vendas.findByToken(token);
        if (existente.isPresent()) return new ResultadoVenda(existente.get(), "Venda já registrada.", true);

        Venda venda = Venda.nova(LocalDateTime.now(relogio));
        venda.setToken(token);
        List<Promocao> ativas = promocoes.listarAtivas();

        Map<Long, Produto> selecionados = resolverProdutos(quantidades);
        var carrinho = new Carrinho(selecionados.values().stream()
                .map(p -> new Carrinho.Linha(p, quantidades.get(p.getId())))
                .toList());
        var orcamento = calculadora.calcular(carrinho, ativas);
        BigDecimal calculado = orcamento.total();
        BigDecimal total = totalManual != null ? totalManual : calculado;
        if (total.signum() < 0) throw new RegraDeNegocioException("O valor ajustado não pode ser negativo.");
        if (total.compareTo(calculado) != 0 && (motivoAjuste == null || motivoAjuste.isBlank()))
            throw new RegraDeNegocioException("Informe o motivo do ajuste de valor.");
        venda.registrarValores(
                calculado,
                orcamento.descontos().stream().map(Orcamento.Desconto::valor).reduce(BigDecimal.ZERO, BigDecimal::add),
                total,
                motivoAjuste);
        registrarPagamentosComTaxa(venda, partes(pagamentos, total, token));
        baixarEstoque(venda, orcamento, selecionados);
        venda.ratearDesconto();
        venda.registrarDescontos(orcamento.descontos().stream()
                .map(d -> new VendaDesconto(d.promocaoId(), d.descricao(), d.valor()))
                .toList());

        Venda salva = vendas.save(venda);
        estoque.registrarSaidasDaVenda(salva);
        auditarVenda(salva, calculado, total, motivoAjuste);
        return new ResultadoVenda(
                salva,
                PagamentoFactory.mensagem(salva.getPagamentos().stream()
                        .map(PagamentoVenda::dados)
                        .toList()),
                false);
    }

    private Map<Long, Produto> resolverProdutos(Map<Long, Integer> quantidades) {
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
        return selecionados;
    }

    private void registrarPagamentosComTaxa(Venda venda, List<PagamentoVenda> partes) {
        venda.registrarPagamentos(partes);
        for (PagamentoVenda parte : venda.getPagamentos()) {
            parte.registrarTaxa(
                    taxas.vigente(parte.getForma(), venda.getDataHora().toLocalDate())
                            .orElse(null));
        }
    }

    private void baixarEstoque(Venda venda, Orcamento orcamento, Map<Long, Produto> selecionados) {
        for (var item : orcamento.itens()) {
            Produto p = selecionados.get(item.produtoId());
            estoque.darBaixa(p, item.quantidade());
            venda.adicionarItem(new ItemVenda(p, item.quantidade(), item.precoUnitario()));
        }
    }

    private void auditarVenda(Venda salva, BigDecimal calculado, BigDecimal total, String motivoAjuste) {
        auditoria.registrar(
                "venda",
                "id",
                salva.getId(),
                "itens",
                salva.getResumoProdutos(),
                "calculado",
                calculado,
                "desconto",
                salva.getDesconto(),
                "cobrado",
                total,
                "pagamento",
                salva.getPagamentoTexto());
        if (total.compareTo(calculado) != 0) {
            auditoria.registrar(
                    "ajuste_valor", "id", salva.getId(), "antes", calculado, "depois", total, "motivo", motivoAjuste);
        }
    }

    private static boolean tokenValido(String token) {
        if (token == null) return false;
        try {
            return UUID.fromString(token).toString().equals(token);
        } catch (IllegalArgumentException naoEUuid) {
            return false;
        }
    }

    private List<PagamentoVenda> partes(List<PagamentoInformado> informados, BigDecimal total, String token) {
        if (informados == null || informados.isEmpty()) return List.of();
        if (informados.size() == 1 && informados.getFirst().valor() == null) {
            PagamentoInformado unico = informados.getFirst();
            if (total.signum() == 0) return List.of();
            return List.of(parte(unico, total, token));
        }
        List<PagamentoVenda> partes = new ArrayList<>();
        for (PagamentoInformado p : informados) {
            if (p.valor() == null) throw new RegraDeNegocioException("Informe o valor de cada parte do pagamento.");
            partes.add(parte(p, p.valor(), token));
        }
        return partes;
    }

    private PagamentoVenda parte(PagamentoInformado p, BigDecimal valor, String token) {
        String orderId = null;
        if (p.origem() == OrigemPagamento.MERCADO_PAGO) {
            if (p.forma() != FormaPagamento.PIX) {
                throw new RegraDeNegocioException("Só o Pix pode ser do Mercado Pago.");
            }
            orderId = cobrancasPix.exigirPaga(token, p.parte(), valor);
        }
        return new PagamentoVenda(p.forma(), valor, p.descricao(), p.recebido(), p.origem(), orderId);
    }

    @Transactional(readOnly = true)
    public Orcamento previa(Map<Long, Integer> quantidades) {
        var linhas = new ArrayList<Carrinho.Linha>();
        quantidades.forEach((id, qtd) -> {
            if (qtd < 0) throw new RegraDeNegocioException("Quantidade inválida.");
            if (qtd > 0) linhas.add(new Carrinho.Linha(estoque.buscar(id), qtd));
        });
        return calculadora.calcular(new Carrinho(linhas), promocoes.listarAtivas());
    }

    @Transactional(readOnly = true)
    public boolean tokenRegistrado(String token) {
        return token != null && vendas.existsByToken(token);
    }

    @Transactional
    public BigDecimal cancelarVenda(Long id, String motivo) {
        Venda venda = vendas.buscarParaCancelar(id)
                .orElseThrow(() -> new RegraDeNegocioException("Venda não encontrada: " + id));
        if (venda.isCancelada()) return BigDecimal.ZERO;
        venda.cancelar(motivo, LocalDateTime.now(relogio));
        estoque.devolverItensDaVenda(venda);
        vendas.save(venda);
        auditoria.registrar(
                "cancelamento",
                "id",
                id,
                "cobrado",
                venda.getValorCobrado(),
                "motivo",
                venda.getMotivoCancelamento(),
                "itens",
                venda.getResumoProdutos());
        return venda.getPagamentos().stream()
                .filter(p -> p.getOrigem() == OrigemPagamento.MERCADO_PAGO)
                .map(PagamentoVenda::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
