package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Categoria de produto, com nome padronizado para não haver duplicatas. */
@Entity
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @Column(unique = true)
    private String nomeNormalizado;

    public Categoria() {}

    public Categoria(String nomeDigitado) {
        renomear(nomeDigitado);
    }

    public void renomear(String nomeDigitado) {
        String normalizado = normalizar(nomeDigitado);
        if (normalizado.isEmpty()) throw new RegraDeNegocioException("Nome de categoria vazio.");
        if (normalizado.length() > 255) throw new RegraDeNegocioException("Nome de categoria com até 255 caracteres.");
        this.nomeNormalizado = normalizado;
        this.nome = titulo(nomeDigitado);
    }

    public static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String titulo(String texto) {
        String base = normalizar(texto);
        StringBuilder sb = new StringBuilder(base.length());
        boolean inicio = true;
        for (char c : base.toCharArray()) {
            sb.append(inicio ? Character.toUpperCase(c) : c);
            inicio = (c == ' ');
        }
        return sb.toString();
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getNomeNormalizado() {
        return nomeNormalizado;
    }
}
