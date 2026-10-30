package br.com.lac.caixa.servico;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Evento;
import br.com.lac.caixa.repositorio.EventoRepository;
import br.com.lac.caixa.repositorio.PeriodoDoEvento;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Eventos: criar, editar e listar; eventos não podem se sobrepor. */
@Service
public class EventoService {
    private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EventoRepository eventos;
    private final Auditoria auditoria;

    public EventoService(EventoRepository eventos, Auditoria auditoria) {
        this.eventos = eventos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<Evento> listar() {
        return eventos.findAllByOrderByInicioDesc();
    }

    @Transactional(readOnly = true)
    public Evento buscar(Long id) {
        return eventos.findById(id).orElseThrow(() -> new RegraDeNegocioException("Evento não encontrado."));
    }

    /** Para os filtros: o evento escolhido vira o período [inicio, fim). */
    @Transactional(readOnly = true)
    public PeriodoDoEvento periodo(Long id) {
        Evento e = buscar(id);
        return new PeriodoDoEvento(e.getId(), e.getNome(), e.getInicio(), e.getFim());
    }

    @Transactional(readOnly = true)
    public Optional<Evento> doMomento(LocalDateTime quando) {
        return eventos.doMomento(quando);
    }

    @Transactional
    public Evento criar(String nome, LocalDateTime inicio, LocalDateTime fim, String observacao) {
        Evento e = new Evento(nome, inicio, fim, observacao);
        recusarSobreposicao(e, null);
        Evento salvo = eventos.save(e);
        auditoria.registrar("evento_criado", "id", salvo.getId(), "depois", descrever(salvo));
        return salvo;
    }

    @Transactional
    public Evento editar(Long id, String nome, LocalDateTime inicio, LocalDateTime fim, String observacao) {
        Evento e = buscar(id);
        String antes = descrever(e);
        e.alterar(nome, inicio, fim, observacao);
        recusarSobreposicao(e, id);
        Evento salvo = eventos.save(e);
        auditoria.registrar("evento_editado", "id", id, "antes", antes, "depois", descrever(salvo));
        return salvo;
    }

    private void recusarSobreposicao(Evento e, Long ignorar) {
        var conflitos = eventos.sobrepostos(e.getInicio(), e.getFim(), ignorar);
        if (!conflitos.isEmpty()) {
            Evento outro = conflitos.get(0);
            throw new RegraDeNegocioException("O período cruza o evento \"" + outro.getNome() + "\" ("
                    + outro.getInicio().format(DIA_E_HORA) + " até "
                    + outro.getFim().format(DIA_E_HORA)
                    + "). Eventos não podem se sobrepor.");
        }
    }

    private static String descrever(Evento e) {
        return e.getNome() + " | " + e.getInicio().format(DIA_E_HORA) + " - "
                + e.getFim().format(DIA_E_HORA);
    }
}
