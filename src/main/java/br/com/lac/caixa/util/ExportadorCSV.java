package br.com.lac.caixa.util;

import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Venda;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gera o conteudo dos arquivos .csv do estoque e do historico.
 * Usa ';' como separador (o Excel em pt-br abre direto) e escapa aspas.
 */
public final class ExportadorCSV {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private ExportadorCSV() {
    }

    public static String doEstoque(List<Produto> produtos) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nome;Categoria;Preço;Quantidade\n");
        for (Produto p : produtos) {
            sb.append(campo(p.getNome())).append(';')
              .append(campo(p.getNomeCategoria())).append(';')
              .append(p.getPreco()).append(';')
              .append(p.getQuantidade()).append('\n');
        }
        return sb.toString();
    }

    public static String doHistorico(List<Venda> vendas) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID;Dia;Hora;Forma de pagamento;Produtos;Quantidade;Valor total\n");
        for (Venda v : vendas) {
            sb.append(v.getId()).append(';')
              .append(v.getDataHora().format(DATA)).append(';')
              .append(v.getDataHora().format(HORA)).append(';')
              .append(campo(v.getFormaPagamentoTexto())).append(';')
              .append(campo(v.getResumoProdutos())).append(';')
              .append(v.getQuantidadeTotal()).append(';')
              .append(v.getValorTotal()).append('\n');
        }
        return sb.toString();
    }

    private static String campo(String valor) {
        if (valor == null) {
            return "";
        }
        String escapado = valor.replace("\"", "\"\"");
        return "\"" + escapado + "\"";
    }
}
