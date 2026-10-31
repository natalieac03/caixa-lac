package br.com.lac.caixa.controle.csv;

import static br.com.lac.caixa.controle.csv.LinhaCsv.cru;

import br.com.lac.caixa.servico.financas.Financas.LinhaGiro;
import br.com.lac.caixa.servico.financas.PainelFinancas.Painel;
import br.com.lac.caixa.servico.financas.PainelFinancas.Parte;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/** As tabelas da tela de Finanças que viram CSV: nome na URL, parte do painel, cabeçalho e linhas. */
public enum TabelaCsv {
    DEMONSTRATIVO(
            "demonstrativo",
            Parte.RESUMO,
            "Período;Linha;Valor;% da receita líquida",
            p -> p.demonstrativo().stream().map(l -> new Object[] {
                (l.operador().isEmpty() ? "" : l.operador() + " ") + l.rotulo(), l.valor(), l.percentualDaReceita()
            })),
    SERIE(
            "serie",
            Parte.SERIE,
            "Período;Início;Receita líquida;Lucro bruto;Vendas",
            p -> p.serie().pontos().stream().map(x -> new Object[] {
                cru(x.inicio().format(Formato.DIA_HORA)), x.receitaLiquida(), x.lucroBruto(), x.vendas()
            })),
    PRODUTOS(
            "produtos",
            Parte.PRODUTOS,
            "Período;Posição;Produto;Categoria;Arquivado;Quantidade;Receita líquida;CMV;Lucro bruto;"
                    + "Margem %;Participação %;Curva ABC;Unidades sem custo",
            p -> p.produtos().stream().map(x -> {
                var l = x.produto();
                return new Object[] {
                    l.posicao(),
                    l.nome(),
                    l.categoria(),
                    l.arquivado() ? "sim" : "não",
                    l.quantidade(),
                    l.receitaLiquida(),
                    l.cmv(),
                    l.lucroBruto(),
                    l.margemPercentual(),
                    l.participacaoPercentual(),
                    cru(x.classe() == null ? "" : x.classe().name()),
                    l.unidadesSemCusto()
                };
            })),
    CATEGORIAS(
            "categorias",
            Parte.CATEGORIAS,
            "Período;Posição;Categoria;Quantidade;Vendas;Receita líquida;CMV;Lucro bruto;Margem %;Participação %",
            p -> p.categorias().stream().map(c -> new Object[] {
                c.posicao(),
                c.categoria(),
                c.quantidade(),
                c.vendas(),
                c.receitaLiquida(),
                c.cmv(),
                c.lucroBruto(),
                c.margemPercentual(),
                c.participacaoPercentual()
            })),
    FORMAS(
            "formas",
            Parte.FORMAS,
            "Período;Forma;Valor;Vendas;Taxa estimada;Participação %",
            p -> p.formas().stream()
                    .map(f -> new Object[] {
                        f.forma().getRotulo(), f.valor(), f.vendas(), f.taxas(), f.participacaoPercentual()
                    })),
    MAPA(
            "mapa",
            Parte.MAPA,
            "Período;Dia da semana;Hora;Vendas;Receita líquida",
            p -> p.mapa().stream()
                    .flatMap(l ->
                            l.horas().stream().map(c -> new Object[] {l.rotulo(), c.hora(), c.vendas(), c.receita()}))),
    PROMOCOES(
            "promocoes",
            Parte.PROMOCOES,
            "Período;Promoção;Vendas;Desconto concedido;Receita das vendas",
            p -> p.promocoes().stream()
                    .map(x -> new Object[] {x.promocao(), x.vendas(), x.descontoTotal(), x.receitaLiquida()})),
    GIRO("giro", Parte.GIRO, TabelaCsv.CABECALHO_ESTOQUE, p -> estoque(p.giro())),
    PARADOS("parados", Parte.PARADOS, TabelaCsv.CABECALHO_ESTOQUE, p -> estoque(p.parados()));

    private static final String CABECALHO_ESTOQUE =
            "Período;Produto;Categoria;Vendidas;Média por dia;Estoque;Dias até acabar;Valor a custo;Valor a preço";

    private static final class Formato {
        static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    }

    private final String nome;
    private final Parte parte;
    private final String cabecalho;
    private final Function<Painel, Stream<Object[]>> linhas;

    TabelaCsv(String nome, Parte parte, String cabecalho, Function<Painel, Stream<Object[]>> linhas) {
        this.nome = nome;
        this.parte = parte;
        this.cabecalho = cabecalho;
        this.linhas = linhas;
    }

    public static Optional<TabelaCsv> porNome(String nome) {
        return Arrays.stream(values()).filter(t -> t.nome.equals(nome)).findFirst();
    }

    public String nome() {
        return nome;
    }

    public Parte parte() {
        return parte;
    }

    String cabecalho() {
        return cabecalho;
    }

    Stream<Object[]> linhas(Painel painel) {
        return linhas.apply(painel);
    }

    private static Stream<Object[]> estoque(List<LinhaGiro> linhas) {
        return linhas.stream().map(g -> new Object[] {
            g.nome(),
            g.categoria(),
            g.vendidas(),
            g.mediaPorDia(),
            g.estoque(),
            g.semGiro() ? "sem giro" : cru(g.diasAteAcabar().toPlainString().replace('.', ',')),
            g.valorACusto(),
            g.valorAPreco()
        });
    }
}
