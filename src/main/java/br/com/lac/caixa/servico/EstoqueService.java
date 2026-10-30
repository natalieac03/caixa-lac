package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.repositorio.ItemVendaRepository;
import br.com.lac.caixa.repositorio.ProdutoRepository;
import java.math.BigDecimal;
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

    public EstoqueService(ProdutoRepository produtos, ItemVendaRepository itensVenda, Auditoria auditoria) {
        this.produtos = produtos;
        this.itensVenda = itensVenda;
        this.auditoria = auditoria;
    }

    private static String descrever(Produto p) {
        return p.getNome() + " | " + p.getPreco().toPlainString() + " | " + p.getNomeCategoria();
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
        Produto p = new Produto(nome.trim(), preco, quantidade);
        p.setCategoria(categoria);
        Produto salvo = produtos.save(p);
        auditoria.registrar(
                "produto_criado", "id", salvo.getId(), "depois", descrever(salvo), "quantidade", quantidade);
        return salvo;
    }

    /** Edita nome, preço e categoria. Vendas já feitas guardam o preço da época. */
    @Transactional
    public Produto editar(Long id, String nome, BigDecimal preco, Categoria categoria) {
        Produto p = buscar(id);
        String antes = descrever(p);
        p.editar(nome, preco, categoria);
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
