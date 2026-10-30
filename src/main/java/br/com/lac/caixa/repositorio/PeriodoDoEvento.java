package br.com.lac.caixa.repositorio;

import java.time.LocalDateTime;

/** O intervalo [inicio, fim) de um evento usado como filtro de período. */
public record PeriodoDoEvento(Long id, String nome, LocalDateTime inicio, LocalDateTime fim) {}
