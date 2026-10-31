package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.EstoqueInsuficienteException;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.CompraEstoque;
import br.com.lac.caixa.modelo.ItemVenda;
import br.com.lac.caixa.modelo.MovimentoEstoque;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.TipoMovimento;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.repositorio.CompraEstoqueRepository;
import br.com.lac.caixa.repositorio.ItemVendaRepository;
import br.com.lac.caixa.repositorio.MovimentoEstoqueRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras do estoque: cadastro, entradas, saídas, compras, arquivamento e movimentos. */
@Service
public class EstoqueService {

    @PersistenceContext
    private EntityManager em;

    private final ProdutoRepository produtos;
    private final ItemVendaRepository itensVenda;

    private final Auditoria auditoria;

    private final CompraEstoqueRepository compras;
    private final Clock relogio;
    private final MovimentoEstoqueRepository movimentos;

    public EstoqueService(
            ProdutoRepository produtos,
            ItemVendaRepository itensVenda,
            Auditoria auditoria,
            CompraEstoqueRepository compras,
            Clock relogio,
            MovimentoEstoqueRepository movimentos) {
        this.produtos = produtos;
        this.itensVenda = itensVenda;
        this.auditoria = auditoria;
        this.compras = compras;
        this.relogio = relogio;
        this.movimentos = movimentos;
    }

    private void movimento(
            Produto p,
            TipoMovimento tipo,
            int unidades,
            BigDecimal custo,
            Venda venda,
            CompraEstoque compra,
            String obs) {
        movimentos.save(new MovimentoEstoque(p, tipo, unidades, custo, venda, compra, LocalDateTime.now(relogio), obs));
    }

    private static String descrever(Produto p) {
        return p.getNome() + " | " + p.getPreco().toPlainString() + " | " + p.getNomeCategoria() + " | custo "
                + (p.getCustoUnitario() == null ? "-" : p.getCustoUnitario().toPlainString());
    }

    @Transactional
    public CompraEstoque registrarCompra(
            Long produtoId, int quantidade, BigDecimal custoTotal, LocalDate data, String observacao) {
        Produto p = buscar(produtoId);
        if (!p.isAtivo()) throw new RegraDeNegocioException("Produto arquivado.");
        CompraEstoque compra = compras.save(
                CompraEstoque.registrar(p, quantidade, custoTotal, data, observacao, LocalDateTime.now(relogio)));
        produtos.save(p);
        BigDecimal custoDaCompra =
                custoTotal.divide(BigDecimal.valueOf(quantidade), Produto.ESCALA_CUSTO, RoundingMode.HALF_EVEN);
        movimento(p, TipoMovimento.ENTRADA_COMPRA, quantidade, custoDaCompra, null, compra, compra.getObservacao());
        auditoria.registrar(
                "compra_estoque",
                "id",
                compra.getId(),
                "produto",
                produtoId,
                "quantidade",
                quantidade,
                "custo_total",
                custoTotal,
                "custo_antes",
                compra.getCustoAnterior(),
                "custo_depois",
                compra.getCustoNovo(),
                "estoque_antes",
                compra.getEstoqueAnterior(),
                "estoque_depois",
                p.getQuantidade());
        return compra;
    }

    public List<Produto> listar() {
        return produtos.findByAtivoTrueOrderByNomeAsc();
    }

    public Produto buscar(Long id) {
        return produtos.findById(id).orElseThrow(() -> new RegraDeNegocioException("Produto não encontrado: " + id));
    }

    @Transactional
    public Produto criarNovo(String nome, BigDecimal preco, int quantidade, Categoria categoria, BigDecimal custo) {
        Produto p = new Produto(nome.trim(), preco, quantidade);
        p.setCategoria(categoria);
        p.setCustoUnitario(custo);
        Produto salvo = produtos.save(p);
        if (quantidade > 0) {
            movimento(
                    salvo,
                    TipoMovimento.ENTRADA_AJUSTE,
                    quantidade,
                    salvo.getCustoUnitario(),
                    null,
                    null,
                    "estoque inicial do cadastro");
        }
        auditoria.registrar(
                "produto_criado", "id", salvo.getId(), "depois", descrever(salvo), "quantidade", quantidade);
        return salvo;
    }

    @Transactional
    public Produto editar(Long id, String nome, BigDecimal preco, Categoria categoria, BigDecimal custo) {
        Produto p = buscar(id);
        String antes = descrever(p);
        p.editar(nome, preco, categoria, custo);
        Produto salvo = produtos.save(p);
        auditoria.registrar("produto_editado", "id", id, "antes", antes, "depois", descrever(salvo));
        return salvo;
    }

    @Transactional
    public void adicionarUnidades(Long id, int quantidade) {
        Produto p = buscar(id);
        int antes = p.getQuantidade();
        p.repor(quantidade);
        produtos.save(p);
        movimento(p, TipoMovimento.ENTRADA_AJUSTE, quantidade, p.getCustoUnitario(), null, null, "ajuste manual");
        auditoria.registrar("estoque_reposto", "id", id, "antes", antes, "depois", p.getQuantidade());
    }

    @Transactional
    public void removerUnidades(Long produtoId, int quantidade) {
        Produto p = buscar(produtoId);
        int antes = p.getQuantidade();
        p.baixar(quantidade);
        produtos.save(p);
        movimento(p, TipoMovimento.SAIDA_AJUSTE, quantidade, p.getCustoUnitario(), null, null, "ajuste manual");
        auditoria.registrar("estoque_retirado", "id", produtoId, "antes", antes, "depois", p.getQuantidade());
    }

    @Transactional
    public boolean remover(Long id) {
        Produto p = buscar(id);
        boolean temHistorico = itensVenda.countByProdutoId(id) > 0 || movimentos.existsByProdutoId(id);
        if (temHistorico) {
            p.setAtivo(false);
            produtos.save(p);
            auditoria.registrar("produto_arquivado", "id", id, "antes", descrever(p));
            return true;
        }
        produtos.delete(p);
        auditoria.registrar("produto_removido", "id", id, "antes", descrever(p));
        return false;
    }

    @Transactional
    public void darBaixa(Produto produto, int quantidade) {
        if (quantidade < 1) throw new RegraDeNegocioException("Quantidade deve ser maior que zero.");
        if (produtos.baixarSeHouver(produto.getId(), quantidade) == 0) {
            throw new EstoqueInsuficienteException("Estoque insuficiente de " + produto.getNome() + " (disponível: "
                    + produtos.quantidadeAtual(produto.getId()) + ").");
        }
        recarregar(produto);
    }

    private void recarregar(Produto produto) {
        if (em.contains(produto)) em.refresh(produto);
    }

    @Transactional
    public void registrarSaidasDaVenda(Venda venda) {
        for (ItemVenda item : venda.getItens()) {
            movimento(
                    item.getProduto(),
                    TipoMovimento.SAIDA_VENDA,
                    item.getQuantidade(),
                    item.getCustoUnitario(),
                    venda,
                    null,
                    "venda #" + venda.getId());
        }
    }

    @Transactional
    public void devolverItensDaVenda(Venda venda) {
        for (ItemVenda item : venda.getItens()) {
            Produto p = buscar(item.getProduto().getId());
            produtos.reporAtomico(p.getId(), item.getQuantidade());
            recarregar(p);
            movimento(
                    p,
                    TipoMovimento.DEVOLUCAO_CANCELAMENTO,
                    item.getQuantidade(),
                    item.getCustoUnitario(),
                    venda,
                    null,
                    "cancelamento da venda #" + venda.getId());
        }
    }

    @Transactional
    public Produto definirLimite(Long id, Integer limite) {
        Produto p = buscar(id);
        Integer antes = p.getEstoqueMinimo();
        p.setEstoqueMinimo(limite);
        Produto salvo = produtos.save(p);
        if (!Objects.equals(antes, limite)) {
            auditoria.registrar("estoque_minimo", "id", id, "antes", antes, "depois", limite);
        }
        return salvo;
    }
}
