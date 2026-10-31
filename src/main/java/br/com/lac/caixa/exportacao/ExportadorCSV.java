package br.com.lac.caixa.exportacao;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.servico.Fechamento;
import br.com.lac.caixa.util.Dinheiro;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** Gera o texto dos CSVs do estoque, do histórico e do fechamento. */
public final class ExportadorCSV {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private ExportadorCSV() {}

    public static String doEstoque(List<Produto> produtos) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nome;Categoria;Preço;Quantidade\n");
        for (Produto p : produtos) {
            sb.append(campo(p.getNome()))
                    .append(';')
                    .append(campo(p.getNomeCategoria()))
                    .append(';')
                    .append(Dinheiro.numero(p.getPreco()))
                    .append(';')
                    .append(p.getQuantidade())
                    .append('\n');
        }
        return sb.toString();
    }

    public static String doHistorico(List<Venda> vendas) {
        StringBuilder sb = new StringBuilder("ID;Dia;Hora;Produtos;Quantidade;Valor total");
        for (FormaPagamento forma : FormaPagamento.values()) sb.append(';').append(forma.getRotulo());
        sb.append(";Descrição do Outro\n");
        for (Venda v : vendas) {
            if (v.isCancelada()) continue;
            sb.append(v.getId())
                    .append(';')
                    .append(v.getDataHora().format(DATA))
                    .append(';')
                    .append(v.getDataHora().format(HORA))
                    .append(';')
                    .append(campo(v.getResumoProdutos()))
                    .append(';')
                    .append(v.getQuantidadeTotal())
                    .append(';')
                    .append(Dinheiro.numero(v.getValorCobrado()));
            for (FormaPagamento forma : FormaPagamento.values()) {
                sb.append(';').append(Dinheiro.numero(v.valorPago(forma)));
            }
            String outros = v.getPagamentos().stream()
                    .filter(p -> p.getForma() == FormaPagamento.OUTRO)
                    .map(PagamentoVenda::getNomeForma)
                    .collect(Collectors.joining(" / "));
            sb.append(';').append(campo(outros)).append('\n');
        }
        return sb.toString();
    }

    public static String doFechamento(Fechamento fechamento, LocalDate inicio, LocalDate fim) {
        StringBuilder csv = new StringBuilder("Início;Fim;Item;Quantidade de vendas;Valor\n");
        String intervalo = campo(inicio == null ? "" : inicio.format(DATA)) + ";"
                + campo(fim == null ? "" : fim.format(DATA)) + ";";
        for (var linha : fechamento.formas()) {
            csv.append(intervalo)
                    .append(campo(linha.forma().getRotulo()))
                    .append(';')
                    .append(linha.vendas())
                    .append(';')
                    .append(Dinheiro.numero(linha.total()))
                    .append('\n');
        }
        csv.append(intervalo)
                .append("\"Pix pelo Mercado Pago\";;")
                .append(Dinheiro.numero(fechamento.pixMercadoPago()))
                .append('\n');
        csv.append(intervalo)
                .append("\"Pix manual\";;")
                .append(Dinheiro.numero(fechamento.pixManual()))
                .append('\n');
        csv.append(intervalo)
                .append("\"Soma das formas\";;")
                .append(Dinheiro.numero(fechamento.totalFormas()))
                .append('\n');
        csv.append(intervalo)
                .append("TOTAL;")
                .append(fechamento.quantidade())
                .append(';')
                .append(Dinheiro.numero(fechamento.total()))
                .append('\n');
        csv.append(intervalo)
                .append("\"Descontos promocionais\";;")
                .append(Dinheiro.numero(fechamento.descontos()))
                .append('\n');
        csv.append(intervalo)
                .append("\"Ajustes manuais\";;")
                .append(Dinheiro.numero(fechamento.ajustes()))
                .append('\n');
        csv.append(intervalo)
                .append("\"Vendas com pagamento dividido\";")
                .append(fechamento.divididas())
                .append(";\n");
        csv.append(intervalo)
                .append("\"Cortesias\";")
                .append(fechamento.cortesias())
                .append(";\n");
        return csv.toString();
    }

    static String campo(String valor) {
        if (valor == null) {
            return "";
        }
        String texto = !valor.isEmpty() && "=+-@\t\r".indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;
        return "\"" + texto.replace("\"", "\"\"") + "\"";
    }
}
