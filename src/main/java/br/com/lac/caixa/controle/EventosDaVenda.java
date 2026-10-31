package br.com.lac.caixa.controle;

import br.com.lac.caixa.repositorio.PeriodoDoEvento;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Para a tela: a etiqueta de cada venda, com o nome do evento que contém a data
 * e hora dela (pelo fim efetivo; o aberto vale até agora) ou "Dia a dia".
 */
public record EventosDaVenda(List<PeriodoDoEvento> periodos) {
    public static final String DIA_A_DIA = "Dia a dia";

    public String nome(LocalDateTime dataHora) {
        if (dataHora == null) return DIA_A_DIA;
        return periodos.stream()
                .filter(p -> !dataHora.isBefore(p.inicio()) && dataHora.isBefore(p.fim()))
                .map(PeriodoDoEvento::nome)
                .findFirst()
                .orElse(DIA_A_DIA);
    }
}
