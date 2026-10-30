package br.com.lac.caixa.util;

import br.com.lac.caixa.modelo.Produto;
import br.com.lac.caixa.modelo.Venda;
import br.com.lac.caixa.servico.Fechamento;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gera o conteudo dos arquivos .csv do estoque, do historico e do fechamento.
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
              .append(Dinheiro.numero(p.getPreco())).append(';')
              .append(p.getQuantidade()).append('\n');
        }
        return sb.toString();
    }

    public static String doHistorico(List<Venda> vendas) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID;Dia;Hora;Forma de pagamento;Produtos;Quantidade;Valor total\n");
        for (Venda v : vendas) {
            if (v.isCancelada()) continue;
            sb.append(v.getId()).append(';')
              .append(v.getDataHora().format(DATA)).append(';')
              .append(v.getDataHora().format(HORA)).append(';')
              .append(campo(v.getFormaPagamentoTexto())).append(';')
              .append(campo(v.getResumoProdutos())).append(';')
              .append(v.getQuantidadeTotal()).append(';')
              .append(Dinheiro.numero(v.getValorTotal())).append('\n');
        }
        return sb.toString();
    }

    public static String doFechamento(Fechamento fechamento, LocalDate inicio, LocalDate fim) {
        StringBuilder csv = new StringBuilder("Início;Fim;Forma de pagamento;Quantidade de vendas;Total;Descontos;Ajustes\n");
        String intervalo = campo(inicio == null ? "" : inicio.format(DATA)) + ";" + campo(fim == null ? "" : fim.format(DATA)) + ";";
        for (var linha : fechamento.formas()) {
            csv.append(intervalo).append(campo(linha.forma().getRotulo())).append(';').append(linha.quantidade()).append(';')
               .append(Dinheiro.numero(linha.total())).append(';').append(Dinheiro.numero(linha.descontos())).append(';')
               .append(Dinheiro.numero(linha.ajustes())).append('\n');
        }
        csv.append(intervalo).append("TOTAL;").append(fechamento.quantidade()).append(';')
           .append(Dinheiro.numero(fechamento.total())).append(';').append(Dinheiro.numero(fechamento.descontos())).append(';')
           .append(Dinheiro.numero(fechamento.ajustes())).append('\n');
        return csv.toString();
    }

    private static String campo(String valor) {
        if (valor == null) {
            return "";
        }
        String escapado = valor.replace("\"", "\"\"");
        return "\"" + escapado + "\"";
    }
    public static ResponseEntity<Resource> arquivoCsv(String csv, String nomeArquivo) {
        // BOM UTF-8 para o Excel exibir os acentos corretamente.
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] corpo = csv.getBytes(StandardCharsets.UTF_8);
        byte[] conteudo = new byte[bom.length + corpo.length];
        System.arraycopy(bom, 0, conteudo, 0, bom.length);
        System.arraycopy(corpo, 0, conteudo, bom.length, corpo.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(new ByteArrayResource(conteudo));
    }
}
