package br.com.lac.caixa.controle;

import br.com.lac.caixa.modelo.Evento;
import java.time.LocalDateTime;
import java.util.List;

/** Para a tela: o nome do evento que contém a data e hora de uma venda, ou "" se nenhum. */
public record EventosDaVenda(List<Evento> eventos) {
    public String nome(LocalDateTime dataHora) {
        if (dataHora == null) return "";
        return eventos.stream()
                .filter(e -> e.contem(dataHora))
                .map(Evento::getNome)
                .findFirst()
                .orElse("");
    }
}
