package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Um evento (feira, encontro) com início e fim. O intervalo é [inicio, fim):
 * uma venda às 18:00 de um evento que vai até 18:00 já não é dele. Vendas não
 * guardam o evento; ele é calculado pela data e hora da venda.
 */
@Entity
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(nullable = false)
    private LocalDateTime fim;

    private String observacao;

    /** Metas opcionais (maiores que zero): receita líquida e lucro líquido do evento. */
    @Column(precision = 38, scale = 2)
    private BigDecimal metaReceita;

    @Column(precision = 38, scale = 2)
    private BigDecimal metaLucro;

    protected Evento() {}

    public Evento(String nome, LocalDateTime inicio, LocalDateTime fim, String observacao) {
        alterar(nome, inicio, fim, observacao);
    }

    public void alterar(String nome, LocalDateTime inicio, LocalDateTime fim, String observacao) {
        String n = nome == null ? "" : nome.trim();
        if (n.isEmpty() || n.length() > 120)
            throw new RegraDeNegocioException("Informe o nome do evento (até 120 caracteres).");
        if (inicio == null || fim == null) throw new RegraDeNegocioException("Informe o início e o fim do evento.");
        if (!fim.isAfter(inicio)) throw new RegraDeNegocioException("O fim do evento deve ser depois do início.");
        String obs = observacao == null || observacao.isBlank() ? null : observacao.trim();
        if (obs != null && obs.length() > 255) throw new RegraDeNegocioException("Observação com até 255 caracteres.");
        this.nome = n;
        this.inicio = inicio;
        this.fim = fim;
        this.observacao = obs;
    }

    /** null = sem meta. */
    public void definirMetas(BigDecimal receita, BigDecimal lucro) {
        this.metaReceita = meta(receita, "receita");
        this.metaLucro = meta(lucro, "lucro");
    }

    private static BigDecimal meta(BigDecimal valor, String nome) {
        if (valor == null) return null;
        if (valor.signum() <= 0) throw new RegraDeNegocioException("A meta de " + nome + " deve ser maior que zero.");
        if (valor.stripTrailingZeros().scale() > 2)
            throw new RegraDeNegocioException("Meta de " + nome + " com no máximo 2 casas decimais.");
        return valor.setScale(2);
    }

    public BigDecimal getMetaReceita() {
        return metaReceita;
    }

    public BigDecimal getMetaLucro() {
        return metaLucro;
    }

    /** A data e hora está dentro do evento (início incluído, fim excluído)? */
    public boolean contem(LocalDateTime dataHora) {
        return !dataHora.isBefore(inicio) && dataHora.isBefore(fim);
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public String getObservacao() {
        return observacao;
    }
}
