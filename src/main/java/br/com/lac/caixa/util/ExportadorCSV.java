package br.com.lac.caixa.util;

import br.com.lac.caixa.modelo.FormaPagamento;
import br.com.lac.caixa.modelo.PagamentoVenda;
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
import java.util.stream.Collectors;

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

    /**
     * Uma coluna por forma com o valor pago nela (venda dividida preenche
     * várias) e a descrição do Outro. Canceladas ficam fora.
     */
    public static String doHistorico(List<Venda> vendas) {
        StringBuilder sb = new StringBuilder("ID;Dia;Hora;Produtos;Quantidade;Valor total");
        for (FormaPagamento forma : FormaPagamento.values()) sb.append(';').append(forma.getRotulo());
        sb.append(";Descrição do Outro\n");
        for (Venda v : vendas) {
            if (v.isCancelada()) continue;
            sb.append(v.getId()).append(';')
              .append(v.getDataHora().format(DATA)).append(';')
              .append(v.getDataHora().format(HORA)).append(';')
              .append(campo(v.getResumoProdutos())).append(';')
              .append(v.getQuantidadeTotal()).append(';')
              .append(Dinheiro.numero(v.getValorTotal()));
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
        String intervalo = campo(inicio == null ? "" : inicio.format(DATA)) + ";" + campo(fim == null ? "" : fim.format(DATA)) + ";";
        for (var linha : fechamento.formas()) {
            csv.append(intervalo).append(campo(linha.forma().getRotulo())).append(';').append(linha.vendas()).append(';')
               .append(Dinheiro.numero(linha.total())).append('\n');
        }
        csv.append(intervalo).append("\"Soma das formas\";;").append(Dinheiro.numero(fechamento.totalFormas())).append('\n');
        csv.append(intervalo).append("TOTAL;").append(fechamento.quantidade()).append(';')
           .append(Dinheiro.numero(fechamento.total())).append('\n');
        csv.append(intervalo).append("\"Descontos promocionais\";;").append(Dinheiro.numero(fechamento.descontos())).append('\n');
        csv.append(intervalo).append("\"Ajustes manuais\";;").append(Dinheiro.numero(fechamento.ajustes())).append('\n');
        csv.append(intervalo).append("\"Vendas com pagamento dividido\";").append(fechamento.divididas()).append(";\n");
        csv.append(intervalo).append("\"Cortesias\";").append(fechamento.cortesias()).append(";\n");
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
