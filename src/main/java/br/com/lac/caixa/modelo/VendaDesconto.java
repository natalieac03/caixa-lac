package br.com.lac.caixa.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;

/** Um desconto promocional de uma venda, congelado como estava na hora da venda. */
@Entity
public class VendaDesconto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long promocaoId;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal valor;

    protected VendaDesconto() {}

    public VendaDesconto(Long promocaoId, String descricao, BigDecimal valor) {
        this.promocaoId = promocaoId;
        this.descricao = descricao.length() > 255 ? descricao.substring(0, 255) : descricao;
        this.valor = valor;
    }

    public Long getId() {
        return id;
    }

    public Long getPromocaoId() {
        return promocaoId;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }
}
