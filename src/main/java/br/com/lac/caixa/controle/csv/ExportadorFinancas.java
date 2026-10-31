package br.com.lac.caixa.controle.csv;

import br.com.lac.caixa.servico.financas.PainelFinancas.Painel;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/** Gera o texto do CSV de cada tabela da tela de Finanças, com o período na primeira coluna. */
public final class ExportadorFinancas {

    public static final List<String> TABELAS =
            Arrays.stream(TabelaCsv.values()).map(TabelaCsv::nome).toList();

    private ExportadorFinancas() {}

    public static String gerar(TabelaCsv tabela, Painel painel, String periodo) {
        StringBuilder csv = new StringBuilder(tabela.cabecalho()).append('\n');
        tabela.linhas(painel)
                .map(campos -> LinhaCsv.de(
                        Stream.concat(Stream.of(periodo), Arrays.stream(campos)).toArray()))
                .forEach(csv::append);
        return csv.toString();
    }
}
