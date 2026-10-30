package br.com.lac.caixa.migracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MigracoesTest {
    String banco() {
        return "jdbc:h2:mem:migracao_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    Flyway flyway(String url) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .baselineOnMigrate(true)
                .load();
    }

    void executar(String url, String sql) throws Exception {
        try (var c = DriverManager.getConnection(url, "sa", "");
                var s = c.createStatement()) {
            s.execute(sql);
        }
    }

    long numero(String url, String sql) throws Exception {
        try (var c = DriverManager.getConnection(url, "sa", "");
                var s = c.createStatement();
                var r = s.executeQuery(sql)) {
            r.next();
            return r.getLong(1);
        }
    }

    /** O banco recusa o comando (constraint, NOT NULL, FK ou CHECK). */
    void assertRecusa(String url, String sql) {
        assertThrows(SQLException.class, () -> executar(url, sql), sql);
    }

    Flyway flywayAte(String url, String versao) {
        return Flyway.configure().dataSource(url, "sa", "").target(versao).load();
    }

    @Test
    @DisplayName("Banco vazio migra V1-V6 e o banco recusa valores inválidos")
    void bancoNovoMigraEImpedeValoresInvalidos() throws Exception {
        // Arrange
        String url = banco();

        // Act
        flyway(url).migrate();

        // Assert: seis migrações SQL aplicadas
        assertEquals(
                6,
                numero(
                        url,
                        "select count(*) from \"flyway_schema_history\" where \"success\"=true and \"type\"='SQL'"));
        // Assert: produto e venda
        assertRecusa(url, "insert into produto(nome,ativo,preco,quantidade) values ('P',true,1,-1)");
        assertRecusa(url, "insert into produto(nome,ativo,preco,quantidade) values ('P',true,0,1)");
        assertRecusa(url, "insert into produto(ativo,preco,quantidade) values (true,1,1)");
        assertRecusa(
                url, "insert into venda(data_hora,forma_pagamento,valor_cobrado) values (current_timestamp,'PIX',-1)");
        assertRecusa(url, "insert into venda(forma_pagamento) values ('PIX')");
        // Assert: desde a V6 a forma fica em pagamento_venda; venda.forma_pagamento aceita null
        executar(url, "insert into venda(id,data_hora,valor_cobrado) values (500,current_timestamp,10)");
        assertRecusa(url, "insert into pagamento_venda(venda_id,valor) values (500,10)");
        assertRecusa(url, "insert into pagamento_venda(venda_id,forma,valor) values (500,'PIX',0)");
        assertRecusa(url, "insert into pagamento_venda(venda_id,forma,valor) values (500,'CHEQUE',10)");
        assertRecusa(url, "insert into pagamento_venda(forma,valor) values ('PIX',10)");
        assertRecusa(url, "insert into pagamento_venda(venda_id,forma,valor) values (999,'PIX',10)");
        assertRecusa(url, "insert into pagamento_venda(venda_id,forma,valor,valor_recebido) values (500,'PIX',10,20)");
        assertRecusa(
                url, "insert into pagamento_venda(venda_id,forma,valor,valor_recebido) values (500,'DINHEIRO',10,9)");
        executar(url, "insert into pagamento_venda(venda_id,forma,valor,valor_recebido) values (500,'DINHEIRO',10,20)");
        // Assert: promoção e itens
        assertRecusa(url, "insert into promocao(ativo,preco) values (true,0)");
        assertRecusa(url, "insert into promocao_item(quantidade,promocao_id) values (0,1)");
        assertRecusa(url, "insert into item_venda(quantidade,venda_id) values (1,1)");
    }

    @Test
    @DisplayName("Cópia do H2 local antigo recebe baseline e mantém vendas, itens e promoções ligados")
    void copiaDoH2LocalRecebeBaselineEPreservaRelacionamentos() throws Exception {
        // Arrange: esquema do H2 local antigo com uma venda e uma promoção nas tabelas de junção
        String url = banco();
        try (var c = DriverManager.getConnection(url, "sa", "");
                var leitor = new InputStreamReader(
                        getClass().getResourceAsStream("/db/esquema-h2-legado.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(c, leitor);
        }
        executar(
                url,
                "insert into venda(id,data_hora,forma_pagamento,valor_total) values (10,current_timestamp,'PIX',12)");
        executar(url, "insert into item_venda(id,produto_id,quantidade,preco_unitario) values (10,1,2,6)");
        executar(url, "insert into venda_itens values (10,10)");
        executar(url, "insert into promocao(id,ativo,preco,tipo) values (10,true,5,'A_PARTIR_DE')");
        executar(url, "insert into promocao_item(id,categoria_id,quantidade) values (10,1,2)");
        executar(url, "insert into promocao_itens values (10,10)");

        // Act
        flyway(url).migrate();

        // Assert
        assertEquals(3, numero(url, "select count(*) from produto"));
        assertRecusa(url, "update venda set forma_pagamento='INVALIDA' where id=10");
        assertRecusa(url, "update promocao set tipo='INVALIDO' where id=10");
        assertEquals(10, numero(url, "select venda_id from item_venda where id=10"));
        assertEquals(10, numero(url, "select promocao_id from promocao_item where id=10"));
        assertEquals(12, numero(url, "select valor_total from venda where id=10 and valor_cobrado is null"));
        assertEquals(
                0,
                numero(
                        url,
                        "select count(*) from information_schema.tables where table_name in ('VENDA_ITENS','PROMOCAO_ITENS')"));
    }

    @Test
    @DisplayName("V6 cria uma linha de pagamento por venda antiga (inclusive cancelada) e pula a de valor zero")
    void v6CopiaUmaLinhaPorVendaEPulaCortesia() throws Exception {
        // Arrange: banco em V5 com vendas Pix, Outro ajustada, cortesia e cancelada
        String url = banco();
        flywayAte(url, "5").migrate();
        executar(
                url,
                "insert into venda(id,data_hora,forma_pagamento,valor_total) values (1,current_timestamp,'PIX',12)");
        executar(
                url,
                "insert into venda(id,data_hora,forma_pagamento,forma_pagamento_outro,valor_total,valor_cobrado) values (2,current_timestamp,'OUTRO','Vale',9,7.50)");
        executar(
                url,
                "insert into venda(id,data_hora,forma_pagamento,valor_total,valor_cobrado,motivo_ajuste) values (3,current_timestamp,'DINHEIRO',0,0,'Cortesia')");
        executar(
                url,
                "insert into venda(id,data_hora,forma_pagamento,valor_total,cancelada,data_cancelamento,motivo_cancelamento) values (4,current_timestamp,'DEBITO',5,true,current_timestamp,'Teste')");

        // Act
        flyway(url).migrate();

        // Assert
        assertEquals(3, numero(url, "select count(*) from pagamento_venda"));
        assertEquals(
                1, numero(url, "select count(*) from pagamento_venda where venda_id=1 and forma='PIX' and valor=12"));
        assertEquals(
                1,
                numero(
                        url,
                        "select count(*) from pagamento_venda where venda_id=2 and forma='OUTRO' and valor=7.50 and descricao='Vale'"));
        assertEquals(0, numero(url, "select count(*) from pagamento_venda where venda_id=3"));
        assertEquals(1, numero(url, "select count(*) from pagamento_venda where venda_id=4"));
        executar(url, "insert into venda(id,data_hora,valor_cobrado) values (10,current_timestamp,1)");
    }

    @Test
    @DisplayName("V6 para com mensagem clara se houver venda sem valor, sem criar a tabela")
    void v6ParaComMensagemClaraSeVendaNaoTemValor() throws Exception {
        // Arrange
        String url = banco();
        flywayAte(url, "5").migrate();
        executar(url, "insert into venda(id,data_hora,forma_pagamento) values (77,current_timestamp,'PIX')");

        // Act
        var erro = assertThrows(FlywayException.class, () -> flyway(url).migrate());

        // Assert
        assertTrue(
                erro.getMessage().contains("V6 parada: a venda 77 não tem valor_cobrado nem valor_total"),
                erro.getMessage());
        assertEquals(
                0, numero(url, "select count(*) from information_schema.tables where table_name='PAGAMENTO_VENDA'"));
    }

    @Test
    @DisplayName("Dado inválido para a migração (V2) em vez de inventar um preço")
    void dadosInvalidosParamMigracaoSemInventarPreco() throws Exception {
        // Arrange
        String url = banco();
        flywayAte(url, "1").migrate();
        executar(url, "insert into produto(nome,ativo,preco,quantidade) values ('Inválido',true,-1,2)");

        // Act
        var erro = assertThrows(FlywayException.class, () -> flyway(url).migrate());

        // Assert
        assertTrue(erro.getMessage().toLowerCase().contains("ck_produto_preco"));
        assertEquals(-1, numero(url, "select preco from produto"));
    }
}
