package br.com.lac.caixa.controle.csv;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.PagamentoVenda;
import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.servico.Fechamento;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Gera o texto dos CSVs do estoque, do histórico e do fechamento. */
public final class ExportadorCSV {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private ExportadorCSV() {}

    public static String doEstoque(List<Produto> produtos) {
        StringBuilder sb = new StringBuilder("Nome;Categoria;Preço;Quantidade\n");
        for (Produto p : produtos) {
            sb.append(LinhaCsv.de(p.getNome(), p.getNomeCategoria(), p.getPreco(), p.getQuantidade()));
        }
        return sb.toString();
    }

    public static String doHistorico(List<Venda> vendas) {
        StringBuilder sb = new StringBuilder("ID;Dia;Hora;Produtos;Quantidade;Valor total");
        for (FormaPagamento forma : FormaPagamento.values()) sb.append(';').append(forma.getRotulo());
        sb.append(";Descrição do Outro\n");
        for (Venda v : vendas) {
            if (v.isCancelada()) continue;
            List<Object> campos = new ArrayList<>(Arrays.asList(
                    v.getId(),
                    LinhaCsv.cru(v.getDataHora().format(DATA)),
                    LinhaCsv.cru(v.getDataHora().format(HORA)),
                    v.getResumoProdutos(),
                    v.getQuantidadeTotal(),
                    v.getValorCobrado()));
            for (FormaPagamento forma : FormaPagamento.values()) campos.add(v.valorPago(forma));
            campos.add(v.getPagamentos().stream()
                    .filter(p -> p.getForma() == FormaPagamento.OUTRO)
                    .map(PagamentoVenda::getNomeForma)
                    .collect(Collectors.joining(" / ")));
            sb.append(LinhaCsv.de(campos.toArray()));
        }
        return sb.toString();
    }

    public static String doFechamento(Fechamento fechamento, LocalDate inicio, LocalDate fim) {
        StringBuilder csv = new StringBuilder("Início;Fim;Item;Quantidade de vendas;Valor\n");
        String de = inicio == null ? "" : inicio.format(DATA);
        String ate = fim == null ? "" : fim.format(DATA);
        for (var linha : fechamento.formas()) {
            csv.append(LinhaCsv.de(de, ate, linha.forma().getRotulo(), linha.vendas(), linha.total()));
        }
        csv.append(LinhaCsv.de(de, ate, "Pix pelo Mercado Pago", null, fechamento.pixMercadoPago()));
        csv.append(LinhaCsv.de(de, ate, "Pix manual", null, fechamento.pixManual()));
        csv.append(LinhaCsv.de(de, ate, "Soma das formas", null, fechamento.totalFormas()));
        csv.append(LinhaCsv.de(de, ate, LinhaCsv.cru("TOTAL"), fechamento.quantidade(), fechamento.total()));
        csv.append(LinhaCsv.de(de, ate, "Descontos promocionais", null, fechamento.descontos()));
        csv.append(LinhaCsv.de(de, ate, "Ajustes manuais", null, fechamento.ajustes()));
        csv.append(LinhaCsv.de(de, ate, "Vendas com pagamento dividido", fechamento.divididas(), null));
        csv.append(LinhaCsv.de(de, ate, "Cortesias", fechamento.cortesias(), null));
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
