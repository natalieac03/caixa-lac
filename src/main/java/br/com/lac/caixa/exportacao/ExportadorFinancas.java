package br.com.lac.caixa.exportacao;

import br.com.lac.caixa.servico.financas.Financas.LinhaGiro;
import br.com.lac.caixa.servico.financas.PainelFinancas.Painel;
import br.com.lac.caixa.util.Dinheiro;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Gera o texto do CSV de cada tabela da tela de Finanças. */
public final class ExportadorFinancas {
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ExportadorFinancas() {}

    public static final List<String> TABELAS = List.of(
            "demonstrativo", "serie", "produtos", "categorias", "formas", "mapa", "promocoes", "giro", "parados");

    public static String gerar(String tabela, Painel painel, String periodo) {
        String p = ExportadorCSV.campo(periodo) + ";";
        StringBuilder csv = new StringBuilder();
        switch (tabela) {
            case "demonstrativo" -> {
                csv.append("Período;Linha;Valor;% da receita líquida\n");
                for (var l : painel.demonstrativo()) {
                    csv.append(p)
                            .append(ExportadorCSV.campo(
                                    (l.operador().isEmpty() ? "" : l.operador() + " ") + l.rotulo()))
                            .append(';')
                            .append(num(l.valor()))
                            .append(';')
                            .append(num(l.percentualDaReceita()))
                            .append('\n');
                }
            }
            case "serie" -> {
                csv.append("Período;Início;Receita líquida;Lucro bruto;Vendas\n");
                for (var x : painel.serie().pontos()) {
                    csv.append(p)
                            .append(x.inicio().format(DIA_HORA))
                            .append(';')
                            .append(num(x.receitaLiquida()))
                            .append(';')
                            .append(num(x.lucroBruto()))
                            .append(';')
                            .append(x.vendas())
                            .append('\n');
                }
            }
            case "produtos" -> {
                csv.append("Período;Posição;Produto;Categoria;Arquivado;Quantidade;Receita líquida;CMV;Lucro bruto;"
                        + "Margem %;Participação %;Curva ABC;Unidades sem custo\n");
                for (var x : painel.produtos()) {
                    var l = x.produto();
                    csv.append(p)
                            .append(l.posicao())
                            .append(';')
                            .append(ExportadorCSV.campo(l.nome()))
                            .append(';')
                            .append(ExportadorCSV.campo(l.categoria()))
                            .append(';')
                            .append(l.arquivado() ? "\"sim\"" : "\"não\"")
                            .append(';')
                            .append(l.quantidade())
                            .append(';')
                            .append(num(l.receitaLiquida()))
                            .append(';')
                            .append(num(l.cmv()))
                            .append(';')
                            .append(num(l.lucroBruto()))
                            .append(';')
                            .append(num(l.margemPercentual()))
                            .append(';')
                            .append(num(l.participacaoPercentual()))
                            .append(';')
                            .append(x.classe() == null ? "" : x.classe().name())
                            .append(';')
                            .append(l.unidadesSemCusto())
                            .append('\n');
                }
            }
            case "categorias" -> {
                csv.append(
                        "Período;Posição;Categoria;Quantidade;Vendas;Receita líquida;CMV;Lucro bruto;Margem %;Participação %\n");
                for (var c : painel.categorias()) {
                    csv.append(p)
                            .append(c.posicao())
                            .append(';')
                            .append(ExportadorCSV.campo(c.categoria()))
                            .append(';')
                            .append(c.quantidade())
                            .append(';')
                            .append(c.vendas())
                            .append(';')
                            .append(num(c.receitaLiquida()))
                            .append(';')
                            .append(num(c.cmv()))
                            .append(';')
                            .append(num(c.lucroBruto()))
                            .append(';')
                            .append(num(c.margemPercentual()))
                            .append(';')
                            .append(num(c.participacaoPercentual()))
                            .append('\n');
                }
            }
            case "formas" -> {
                csv.append("Período;Forma;Valor;Vendas;Taxa estimada;Participação %\n");
                for (var f : painel.formas()) {
                    csv.append(p)
                            .append(ExportadorCSV.campo(f.forma().getRotulo()))
                            .append(';')
                            .append(num(f.valor()))
                            .append(';')
                            .append(f.vendas())
                            .append(';')
                            .append(num(f.taxas()))
                            .append(';')
                            .append(num(f.participacaoPercentual()))
                            .append('\n');
                }
            }
            case "mapa" -> {
                csv.append("Período;Dia da semana;Hora;Vendas;Receita líquida\n");
                for (var l : painel.mapa()) {
                    for (var c : l.horas()) {
                        csv.append(p)
                                .append(ExportadorCSV.campo(l.rotulo()))
                                .append(';')
                                .append(c.hora())
                                .append(';')
                                .append(c.vendas())
                                .append(';')
                                .append(num(c.receita()))
                                .append('\n');
                    }
                }
            }
            case "promocoes" -> {
                csv.append("Período;Promoção;Vendas;Desconto concedido;Receita das vendas\n");
                for (var x : painel.promocoes()) {
                    csv.append(p)
                            .append(ExportadorCSV.campo(x.promocao()))
                            .append(';')
                            .append(x.vendas())
                            .append(';')
                            .append(num(x.descontoTotal()))
                            .append(';')
                            .append(num(x.receitaLiquida()))
                            .append('\n');
                }
            }
            case "giro" -> estoque(csv, p, painel.giro());
            case "parados" -> estoque(csv, p, painel.parados());
            default -> throw new IllegalArgumentException("Tabela desconhecida: " + tabela);
        }
        return csv.toString();
    }

    private static void estoque(StringBuilder csv, String p, List<LinhaGiro> linhas) {
        csv.append(
                "Período;Produto;Categoria;Vendidas;Média por dia;Estoque;Dias até acabar;Valor a custo;Valor a preço\n");
        for (var g : linhas) {
            csv.append(p)
                    .append(ExportadorCSV.campo(g.nome()))
                    .append(';')
                    .append(ExportadorCSV.campo(g.categoria()))
                    .append(';')
                    .append(g.vendidas())
                    .append(';')
                    .append(num(g.mediaPorDia()))
                    .append(';')
                    .append(g.estoque())
                    .append(';')
                    .append(
                            g.semGiro()
                                    ? "\"sem giro\""
                                    : g.diasAteAcabar().toPlainString().replace('.', ','))
                    .append(';')
                    .append(num(g.valorACusto()))
                    .append(';')
                    .append(num(g.valorAPreco()))
                    .append('\n');
        }
    }

    private static String num(BigDecimal valor) {
        return valor == null ? "" : Dinheiro.numero(valor);
    }
}
