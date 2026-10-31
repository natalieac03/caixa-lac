package br.com.lac.caixa.servico;

import br.com.lac.caixa.consulta.PeriodoDoEvento;
import br.com.lac.caixa.dominio.RegraDeNegocioException;
import br.com.lac.caixa.modelo.Evento;
import br.com.lac.caixa.repositorio.EventoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Eventos: criar, editar, iniciar, encerrar e o fim efetivo do evento aberto. */
@Service
public class EventoService {
    private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final EventoRepository eventos;
    private final Auditoria auditoria;
    private final Clock relogio;

    public EventoService(EventoRepository eventos, Auditoria auditoria, Clock relogio) {
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio);
    }

    @Transactional(readOnly = true)
    public List<Evento> listar() {
        return eventos.findAllByOrderByInicioDesc();
    }

    @Transactional(readOnly = true)
    public Evento buscar(Long id) {
        return eventos.findById(id).orElseThrow(() -> new RegraDeNegocioException("Evento não encontrado."));
    }

    @Transactional(readOnly = true)
    public LocalDateTime fimEfetivo(Evento e) {
        if (!e.isAberto()) return e.getFim();
        return proximoInicio(e).orElse(depoisDeAgora());
    }

    LocalDateTime depoisDeAgora() {
        return agora().plusNanos(1000);
    }

    private Optional<LocalDateTime> proximoInicio(Evento e) {
        LocalDateTime agora = agora();
        return eventos.findFirstByInicioGreaterThanOrderByInicioAsc(e.getInicio())
                .map(Evento::getInicio)
                .filter(proximo -> !proximo.isAfter(agora));
    }

    @Transactional(readOnly = true)
    public PeriodoDoEvento periodo(Long id) {
        return periodo(buscar(id));
    }

    @Transactional(readOnly = true)
    public PeriodoDoEvento periodo(Evento e) {
        return new PeriodoDoEvento(e.getId(), e.getNome(), e.getInicio(), fimEfetivo(e), e.isAberto());
    }

    @Transactional(readOnly = true)
    public List<PeriodoDoEvento> periodos() {
        return eventos.findAllByOrderByInicioAsc().stream().map(this::periodo).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Evento> atual(LocalDateTime agora) {
        return eventos.findFirstByInicioLessThanEqualOrderByInicioDesc(agora);
    }

    @Transactional(readOnly = true)
    public Optional<Evento> aberto() {
        return eventos.findFirstByFimIsNullOrderByInicioDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Evento> doMomento(LocalDateTime quando) {
        return eventos.doMomento(quando, depoisDeAgora()).stream().findFirst();
    }

    public String desde(Evento e) {
        boolean hoje = e.getInicio().toLocalDate().equals(agora().toLocalDate());
        return e.getInicio().format(DateTimeFormatter.ofPattern(hoje ? "HH:mm" : "dd/MM HH:mm"));
    }

    public String nomeSugerido() {
        return "Evento " + agora().format(DIA_MES);
    }

    @Transactional
    public Evento iniciar(String nome) {
        String n = nome == null || nome.isBlank() ? nomeSugerido() : nome;
        Evento e = criar(n, agora(), null, null, null, null);
        auditoria.registrar("evento_iniciado", "id", e.getId(), "nome", e.getNome());
        return e;
    }

    @Transactional
    public Evento encerrar(Long id) {
        Evento e = buscar(id);
        if (!e.isAberto()) throw new RegraDeNegocioException("O evento \"" + e.getNome() + "\" já foi encerrado.");
        e.encerrar(proximoInicio(e).orElse(agora()));
        Evento salvo = eventos.save(e);
        auditoria.registrar("evento_encerrado", "id", id, "fim", salvo.getFim());
        return salvo;
    }

    @Transactional
    public Evento criar(String nome, LocalDateTime inicio, LocalDateTime fim, String observacao) {
        return criar(nome, inicio, fim, observacao, null, null);
    }

    @Transactional
    public Evento criar(
            String nome,
            LocalDateTime inicio,
            LocalDateTime fim,
            String observacao,
            BigDecimal metaReceita,
            BigDecimal metaLucro) {
        Evento e = new Evento(nome, inicio, fim, observacao);
        e.definirMetas(metaReceita, metaLucro);
        validar(e, null);
        Evento salvo = salvar(e);
        auditoria.registrar("evento_criado", "id", salvo.getId(), "depois", descrever(salvo));
        return salvo;
    }

    @Transactional
    public Evento editar(Long id, String nome, LocalDateTime inicio, LocalDateTime fim, String observacao) {
        Evento e = buscar(id);
        return editar(id, nome, inicio, fim, observacao, e.getMetaReceita(), e.getMetaLucro());
    }

    @Transactional
    public Evento editar(
            Long id,
            String nome,
            LocalDateTime inicio,
            LocalDateTime fim,
            String observacao,
            BigDecimal metaReceita,
            BigDecimal metaLucro) {
        Evento e = buscar(id);
        String antes = descrever(e);
        e.alterar(nome, inicio, fim, observacao);
        e.definirMetas(metaReceita, metaLucro);
        validar(e, id);
        Evento salvo = salvar(e);
        auditoria.registrar("evento_editado", "id", id, "antes", antes, "depois", descrever(salvo));
        return salvo;
    }

    private Evento salvar(Evento e) {
        try {
            return eventos.saveAndFlush(e);
        } catch (DataIntegrityViolationException violacao) {
            if (!e.isAberto()) throw violacao;
            throw new RegraDeNegocioException(
                    "Já há um evento em andamento (alguém iniciou outro agora mesmo). Atualize a tela para ver.");
        }
    }

    private void validar(Evento e, Long ignorar) {
        LocalDateTime agora = agora();
        if (e.isAberto()) {
            if (e.getInicio().isAfter(agora))
                throw new RegraDeNegocioException("Evento sem fim (em andamento) precisa ter começado: informe o fim.");
            var outro = eventos.findFirstByFimIsNullOrderByInicioDesc()
                    .filter(x -> !x.getId().equals(ignorar));
            if (outro.isPresent())
                throw new RegraDeNegocioException(
                        "Já há um evento em andamento: \"" + outro.get().getNome() + "\", desde "
                                + outro.get().getInicio().format(DIA_E_HORA) + ". Encerre-o antes de iniciar outro.");
        }
        LocalDateTime fim = e.isAberto() ? agora : e.getFim();
        var conflitos = eventos.sobrepostos(e.getInicio(), fim, agora, ignorar);
        if (!conflitos.isEmpty()) {
            Evento outro = conflitos.getFirst();
            throw new RegraDeNegocioException("O período cruza o evento \"" + outro.getNome() + "\" ("
                    + outro.getInicio().format(DIA_E_HORA) + " até "
                    + (outro.isAberto() ? "agora, em andamento" : outro.getFim().format(DIA_E_HORA))
                    + "). Eventos não podem se sobrepor.");
        }
    }

    private static String descrever(Evento e) {
        return e.getNome() + " | " + e.getInicio().format(DIA_E_HORA) + " - "
                + (e.isAberto() ? "em andamento" : e.getFim().format(DIA_E_HORA))
                + " | metas " + (e.getMetaReceita() == null ? "-" : e.getMetaReceita()) + "/"
                + (e.getMetaLucro() == null ? "-" : e.getMetaLucro());
    }
}
