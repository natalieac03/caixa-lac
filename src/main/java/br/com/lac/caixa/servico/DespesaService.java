package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Categoria;
import br.com.lac.caixa.modelo.CategoriaDespesa;
import br.com.lac.caixa.modelo.Despesa;
import br.com.lac.caixa.modelo.Evento;
import br.com.lac.caixa.repositorio.CategoriaDespesaRepository;
import br.com.lac.caixa.repositorio.DespesaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Despesas e categorias de despesa. Compras de mercadoria ficam fora (são custo). */
@Service
public class DespesaService {
    private final DespesaRepository despesas;
    private final CategoriaDespesaRepository categorias;
    private final EventoService eventos;
    private final Auditoria auditoria;
    private final Clock relogio;

    public DespesaService(
            DespesaRepository despesas,
            CategoriaDespesaRepository categorias,
            EventoService eventos,
            Auditoria auditoria,
            Clock relogio) {
        this.despesas = despesas;
        this.categorias = categorias;
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    public record Filtro(Long evento, LocalDate inicio, LocalDate fim) {
        public Filtro {
            if (evento != null) {
                inicio = null;
                fim = null;
            }
            if (inicio != null && fim != null && inicio.isAfter(fim))
                throw new RegraDeNegocioException("A data inicial deve ser anterior ou igual à final.");
        }
    }

    @Transactional(readOnly = true)
    public List<Despesa> filtrar(Filtro f) {
        return despesas.filtrar(f.evento(), f.inicio(), f.fim());
    }

    @Transactional(readOnly = true)
    public BigDecimal total(Filtro f) {
        return despesas.total(f.evento(), f.inicio(), f.fim());
    }

    @Transactional(readOnly = true)
    public Despesa buscar(Long id) {
        return despesas.findById(id).orElseThrow(() -> new RegraDeNegocioException("Despesa não encontrada."));
    }

    @Transactional
    public Despesa criar(
            LocalDate data, String descricao, Long categoriaId, BigDecimal valor, Long eventoId, String observacao) {
        Despesa d = despesas.save(new Despesa(
                data,
                descricao,
                categoria(categoriaId),
                valor,
                evento(eventoId),
                observacao,
                LocalDateTime.now(relogio)));
        auditoria.registrar("despesa_criada", "id", d.getId(), "depois", descrever(d));
        return d;
    }

    @Transactional
    public Despesa editar(
            Long id,
            LocalDate data,
            String descricao,
            Long categoriaId,
            BigDecimal valor,
            Long eventoId,
            String observacao) {
        Despesa d = buscar(id);
        String antes = descrever(d);
        d.alterar(data, descricao, categoria(categoriaId), valor, evento(eventoId), observacao);
        despesas.save(d);
        auditoria.registrar("despesa_editada", "id", id, "antes", antes, "depois", descrever(d));
        return d;
    }

    @Transactional
    public void apagar(Long id) {
        Despesa d = buscar(id);
        String antes = descrever(d);
        despesas.delete(d);
        auditoria.registrar("despesa_apagada", "id", id, "antes", antes);
    }

    @Transactional(readOnly = true)
    public List<CategoriaDespesa> categorias() {
        return categorias.findAllByOrderByNomeAsc();
    }

    @Transactional
    public CategoriaDespesa criarCategoria(String nome) {
        var nova = new CategoriaDespesa(nome);
        if (categorias.findByNomeNormalizado(nova.getNomeNormalizado()).isPresent())
            throw new RegraDeNegocioException("Já existe a categoria de despesa \"" + nova.getNome() + "\".");
        CategoriaDespesa salva = categorias.save(nova);
        auditoria.registrar("categoria_despesa_criada", "id", salva.getId(), "nome", salva.getNome());
        return salva;
    }

    @Transactional
    public CategoriaDespesa renomearCategoria(Long id, String nome) {
        CategoriaDespesa c = categoria(id);
        String antes = c.getNome();
        String normalizado = Categoria.normalizar(nome);
        categorias
                .findByNomeNormalizado(normalizado)
                .filter(outra -> !outra.getId().equals(id))
                .ifPresent(outra -> {
                    throw new RegraDeNegocioException("Já existe a categoria de despesa \"" + outra.getNome() + "\".");
                });
        c.renomear(nome);
        categorias.save(c);
        auditoria.registrar("categoria_despesa_renomeada", "id", id, "antes", antes, "depois", c.getNome());
        return c;
    }

    /** Só apaga categoria sem despesas (as despesas antigas não ficam sem categoria). */
    @Transactional
    public void apagarCategoria(Long id) {
        CategoriaDespesa c = categoria(id);
        if (despesas.existsByCategoriaId(id))
            throw new RegraDeNegocioException(
                    "A categoria \"" + c.getNome() + "\" tem despesas; renomeie em vez de apagar.");
        categorias.delete(c);
        auditoria.registrar("categoria_despesa_apagada", "id", id, "nome", c.getNome());
    }

    private CategoriaDespesa categoria(Long id) {
        if (id == null) throw new RegraDeNegocioException("Escolha a categoria da despesa.");
        return categorias
                .findById(id)
                .orElseThrow(() -> new RegraDeNegocioException("Categoria de despesa não encontrada."));
    }

    private Evento evento(Long id) {
        return id == null ? null : eventos.buscar(id);
    }

    private static String descrever(Despesa d) {
        return d.getData() + " | " + d.getDescricao() + " | " + d.getCategoria().getNome() + " | " + d.getValor()
                + " | evento " + (d.getEvento() == null ? "-" : d.getEvento().getNome());
    }
}
