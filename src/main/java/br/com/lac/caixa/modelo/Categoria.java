package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Categoria de produto (ex.: "Bóton", "Adesivo Grande").
 * O nome é padronizado: "ADESIVO GRANDE", "adesivo grande" e
 * "Adesivo  Grande" são a MESMA categoria. Guardamos duas formas:
 * - nome: bonito para exibir (Título Assim);
 * - nomeNormalizado: minúsculo e sem espaços extras, único no banco,
 *   usado para comparar e impedir duplicatas.
 */
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

    /** Novo nome (padronizado). Quem chama confere se já existe outra igual. */
    public void renomear(String nomeDigitado) {
        String normalizado = normalizar(nomeDigitado);
        if (normalizado.isEmpty()) throw new RegraDeNegocioException("Nome de categoria vazio.");
        if (normalizado.length() > 255) throw new RegraDeNegocioException("Nome de categoria com até 255 caracteres.");
        this.nomeNormalizado = normalizado;
        this.nome = titulo(nomeDigitado);
    }

    /** minusculo, sem espaços nas pontas e sem espaços duplicados. */
    public static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /** Primeira Letra De Cada Palavra Maiúscula, para exibição. */
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
