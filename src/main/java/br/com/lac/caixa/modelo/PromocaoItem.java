package br.com.lac.caixa.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * Um "ramo" de um combo: quantas unidades de qual categoria são exigidas.
 * Ex.: 2 Bóton, 3 Adesivo Grande.
 */
@Entity
public class PromocaoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Categoria categoria;

    private int quantidade;

    public PromocaoItem() {
    }

    public PromocaoItem(Categoria categoria, int quantidade) {
        this.categoria = categoria;
        this.quantidade = quantidade;
    }

    public Long getId() {
        return id;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public int getQuantidade() {
        return quantidade;
    }
}
