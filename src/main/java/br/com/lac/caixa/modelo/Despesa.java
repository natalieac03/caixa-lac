package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Uma despesa do grupo, opcionalmente ligada a um evento. */
@Entity
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private String descricao;

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaDespesa categoria;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal valor;

    @ManyToOne
    @JoinColumn(name = "evento_id")
    private Evento evento;

    private String observacao;

    @Column(nullable = false)
    private LocalDateTime registradaEm;

    protected Despesa() {}

    public Despesa(
            LocalDate data,
            String descricao,
            CategoriaDespesa categoria,
            BigDecimal valor,
            Evento evento,
            String observacao,
            LocalDateTime agora) {
        this.registradaEm = agora;
        alterar(data, descricao, categoria, valor, evento, observacao);
    }

    public void alterar(
            LocalDate data,
            String descricao,
            CategoriaDespesa categoria,
            BigDecimal valor,
            Evento evento,
            String observacao) {
        if (data == null) throw new RegraDeNegocioException("Informe a data da despesa.");
        String d = descricao == null ? "" : descricao.trim();
        if (d.isEmpty() || d.length() > 255)
            throw new RegraDeNegocioException("Informe a descrição (até 255 caracteres).");
        if (categoria == null) throw new RegraDeNegocioException("Escolha a categoria da despesa.");
        if (valor == null || valor.signum() <= 0)
            throw new RegraDeNegocioException("O valor da despesa deve ser maior que zero.");
        if (valor.stripTrailingZeros().scale() > 2)
            throw new RegraDeNegocioException("Valor com no máximo 2 casas decimais.");
        String obs = observacao == null || observacao.isBlank() ? null : observacao.trim();
        if (obs != null && obs.length() > 255) throw new RegraDeNegocioException("Observação com até 255 caracteres.");
        this.data = data;
        this.descricao = d;
        this.categoria = categoria;
        this.valor = valor.setScale(2);
        this.evento = evento;
        this.observacao = obs;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getData() {
        return data;
    }

    public String getDescricao() {
        return descricao;
    }

    public CategoriaDespesa getCategoria() {
        return categoria;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Evento getEvento() {
        return evento;
    }

    public String getObservacao() {
        return observacao;
    }

    public LocalDateTime getRegistradaEm() {
        return registradaEm;
    }
}
