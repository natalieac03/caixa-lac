package br.com.lac.caixa.modelo;

import br.com.lac.caixa.dominio.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Categoria de despesa (Material, Transporte...). "transporte" e "Transporte" são a mesma. */
@Entity
public class CategoriaDespesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(nullable = false, length = 60, unique = true)
    private String nomeNormalizado;

    protected CategoriaDespesa() {}

    public CategoriaDespesa(String nome) {
        renomear(nome);
    }

    public void renomear(String nomeDigitado) {
        String limpo = nomeDigitado == null ? "" : nomeDigitado.trim().replaceAll("\\s+", " ");
        if (limpo.isEmpty() || limpo.length() > 60)
            throw new RegraDeNegocioException("Nome da categoria de despesa com 1 a 60 caracteres.");
        this.nome = limpo;
        this.nomeNormalizado = Categoria.normalizar(limpo);
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
