package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.CompraEstoque;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.CompraEstoqueRepository;
import br.com.lac.caixa.repositorio.ItemVendaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras do estoque. Por ser um @Service, o Spring mantem uma unica
 * instancia desta classe (Singleton), sem precisar do getInstancia().
 */
@Service
public class EstoqueService {

    private final ProdutoRepository produtos;
    private final ItemVendaRepository itensVenda;

    private final Auditoria auditoria;

    private final CompraEstoqueRepository compras;
    private final Clock relogio;

    public EstoqueService(
            ProdutoRepository produtos,
            ItemVendaRepository itensVenda,
            Auditoria auditoria,
            CompraEstoqueRepository compras,
            Clock relogio) {
        this.produtos = produtos;
        this.itensVenda = itensVenda;
        this.auditoria = auditoria;
        this.compras = compras;
        this.relogio = relogio;
    }

    private static String descrever(Produto p) {
        return p.getNome() + " | " + p.getPreco().toPlainString() + " | " + p.getNomeCategoria() + " | custo "
                + (p.getCustoUnitario() == null ? "-" : p.getCustoUnitario().toPlainString());
    }

    /**
     * Entrada de estoque por compra: soma ao estoque e recalcula o custo médio
     * ponderado (Produto.registrarCompra). Guarda o documento da compra.
     */
    @Transactional
    public CompraEstoque registrarCompra(
            Long produtoId, int quantidade, BigDecimal custoTotal, LocalDate data, String observacao) {
        Produto p = buscar(produtoId);
        if (!p.isAtivo()) throw new RegraDeNegocioException("Produto arquivado.");
        CompraEstoque compra = compras.save(
                CompraEstoque.registrar(p, quantidade, custoTotal, data, observacao, LocalDateTime.now(relogio)));
        produtos.save(p);
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

    /** Só os produtos ativos: os arquivados somem das telas. */
    public List<Produto> listar() {
        return produtos.findByAtivoTrueOrderByNomeAsc();
    }

    public Produto buscar(Long id) {
        return produtos.findById(id).orElseThrow(() -> new RegraDeNegocioException("Produto não encontrado: " + id));
    }

    @Transactional
    public Produto criarNovo(String nome, BigDecimal preco, int quantidade, Categoria categoria) {
        return criarNovo(nome, preco, quantidade, categoria, null);
    }

    /** Cadastro com custo unitário opcional (null = sem custo). */
    @Transactional
    public Produto criarNovo(String nome, BigDecimal preco, int quantidade, Categoria categoria, BigDecimal custo) {
        Produto p = new Produto(nome.trim(), preco, quantidade);
        p.setCategoria(categoria);
        p.setCustoUnitario(custo);
        Produto salvo = produtos.save(p);
        auditoria.registrar(
                "produto_criado", "id", salvo.getId(), "depois", descrever(salvo), "quantidade", quantidade);
        return salvo;
    }

    /** Edita nome, preço e categoria. Vendas já feitas guardam o preço da época. */
    @Transactional
    public Produto editar(Long id, String nome, BigDecimal preco, Categoria categoria) {
        return editar(id, nome, preco, categoria, buscar(id).getCustoUnitario());
    }

    /** Edição com custo (vazio = sem custo). Vendas já feitas guardam preço e custo da época. */
    @Transactional
    public Produto editar(Long id, String nome, BigDecimal preco, Categoria categoria, BigDecimal custo) {
        Produto p = buscar(id);
        String antes = descrever(p);
        p.editar(nome, preco, categoria, custo);
        Produto salvo = produtos.save(p);
        auditoria.registrar("produto_editado", "id", id, "antes", antes, "depois", descrever(salvo));
        return salvo;
    }

    /** Soma unidades ao estoque de um produto que ja existe. */
    @Transactional
    public void adicionarUnidades(Long id, int quantidade) {
        Produto p = buscar(id);
        int antes = p.getQuantidade();
        p.repor(quantidade);
        produtos.save(p);
        auditoria.registrar("estoque_reposto", "id", id, "antes", antes, "depois", p.getQuantidade());
    }

    /**
     * Tira unidades do estoque (ex.: venda feita fora do sistema). O estoque
     * pode chegar a 0: o produto continua na lista, só aparece como esgotado.
     */
    @Transactional
    public void removerUnidades(Long produtoId, int quantidade) {
        Produto p = buscar(produtoId);
        int antes = p.getQuantidade();
        p.baixar(quantidade);
        produtos.save(p);
        auditoria.registrar("estoque_retirado", "id", produtoId, "antes", antes, "depois", p.getQuantidade());
    }

    /**
     * Remove um produto. Se ele ja aparece em alguma venda, nao da para
     * apagar de vez (o historico ficaria quebrado): nesse caso o produto e
     * ARQUIVADO, somindo das telas mas preservando as vendas antigas.
     *
     * @return true se foi arquivado, false se foi apagado de vez.
     */
    @Transactional
    public boolean remover(Long id) {
        Produto p = buscar(id);
        boolean jaVendido = itensVenda.countByProdutoId(id) > 0;
        if (jaVendido) {
            p.setAtivo(false);
            produtos.save(p);
            auditoria.registrar("produto_arquivado", "id", id, "antes", descrever(p));
            return true;
        }
        produtos.delete(p);
        auditoria.registrar("produto_removido", "id", id, "antes", descrever(p));
        return false;
    }

    /** Da baixa no estoque ao confirmar uma venda. */
    @Transactional
    public void darBaixa(Produto produto, int quantidade) {
        produto.baixar(quantidade);
        produtos.save(produto);
    }
}
