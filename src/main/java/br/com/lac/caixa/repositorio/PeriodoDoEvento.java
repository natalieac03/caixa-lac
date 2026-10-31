package br.com.lac.caixa.repositorio;

import java.time.LocalDateTime;

/**
 * O intervalo [inicio, fim) de um evento usado como filtro de período. Num
 * evento em andamento (aberto), fim é o fim efetivo: agora, ou o início do
 * próximo evento se ele vier antes.
 */
public record PeriodoDoEvento(Long id, String nome, LocalDateTime inicio, LocalDateTime fim, boolean aberto) {
    public PeriodoDoEvento(Long id, String nome, LocalDateTime inicio, LocalDateTime fim) {
        this(id, nome, inicio, fim, false);
    }
}
