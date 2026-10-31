package br.com.lac.caixa.consulta;

import java.time.LocalDateTime;

/** O intervalo [inicio, fim) de um evento usado como filtro de período. */
public record PeriodoDoEvento(Long id, String nome, LocalDateTime inicio, LocalDateTime fim, boolean aberto) {
    public PeriodoDoEvento(Long id, String nome, LocalDateTime inicio, LocalDateTime fim) {
        this(id, nome, inicio, fim, false);
    }
}
