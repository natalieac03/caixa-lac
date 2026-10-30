package br.com.lac.caixa.migracao;

import org.flywaydb.core.Flyway;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MigracoesTest {
    String banco() { return "jdbc:h2:mem:migracao_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"; }
    Flyway flyway(String url) { return Flyway.configure().dataSource(url,"sa","").baselineOnMigrate(true).load(); }
    void executar(String url, String sql) throws Exception {
        try (var c=DriverManager.getConnection(url,"sa",""); var s=c.createStatement()) { s.execute(sql); }
    }
    long numero(String url, String sql) throws Exception {
        try (var c=DriverManager.getConnection(url,"sa",""); var s=c.createStatement(); var r=s.executeQuery(sql)) { r.next(); return r.getLong(1); }
    }
    @Test void bancoNovoMigraEImpedeValoresInvalidos() throws Exception {
        String url=banco(); flyway(url).migrate();
        assertEquals(5,numero(url,"select count(*) from \"flyway_schema_history\" where \"success\"=true and \"type\"='SQL'"));
        assertThrows(SQLException.class,()->executar(url,"insert into produto(nome,ativo,preco,quantidade) values ('P',true,1,-1)"));
        assertThrows(SQLException.class,()->executar(url,"insert into produto(nome,ativo,preco,quantidade) values ('P',true,0,1)"));
        assertThrows(SQLException.class,()->executar(url,"insert into produto(ativo,preco,quantidade) values (true,1,1)"));
        assertThrows(SQLException.class,()->executar(url,"insert into venda(data_hora,forma_pagamento,valor_cobrado) values (current_timestamp,'PIX',-1)"));
        assertThrows(SQLException.class,()->executar(url,"insert into venda(forma_pagamento) values ('PIX')"));
        assertThrows(SQLException.class,()->executar(url,"insert into venda(data_hora) values (current_timestamp)"));
        assertThrows(SQLException.class,()->executar(url,"insert into promocao(ativo,preco) values (true,0)"));
        assertThrows(SQLException.class,()->executar(url,"insert into promocao_item(quantidade,promocao_id) values (0,1)"));
        assertThrows(SQLException.class,()->executar(url,"insert into item_venda(quantidade,venda_id) values (1,1)"));
    }
    @Test void copiaDoH2LocalRecebeBaselineEPreservaRelacionamentos() throws Exception {
        String url=banco();
        try (var c=DriverManager.getConnection(url,"sa",""); var leitor=new InputStreamReader(getClass().getResourceAsStream("/db/esquema-h2-legado.sql"), StandardCharsets.UTF_8)) { RunScript.execute(c,leitor); }
        executar(url,"insert into venda(id,data_hora,forma_pagamento,valor_total) values (10,current_timestamp,'PIX',12)");
        executar(url,"insert into item_venda(id,produto_id,quantidade,preco_unitario) values (10,1,2,6)");
        executar(url,"insert into venda_itens values (10,10)");
        executar(url,"insert into promocao(id,ativo,preco,tipo) values (10,true,5,'A_PARTIR_DE')");
        executar(url,"insert into promocao_item(id,categoria_id,quantidade) values (10,1,2)");
        executar(url,"insert into promocao_itens values (10,10)");
        flyway(url).migrate();
        assertEquals(3,numero(url,"select count(*) from produto"));
        assertThrows(SQLException.class,()->executar(url,"update venda set forma_pagamento='INVALIDA' where id=10"));
        assertThrows(SQLException.class,()->executar(url,"update promocao set tipo='INVALIDO' where id=10"));
        assertEquals(10,numero(url,"select venda_id from item_venda where id=10"));
        assertEquals(10,numero(url,"select promocao_id from promocao_item where id=10"));
        assertEquals(12,numero(url,"select valor_total from venda where id=10 and valor_cobrado is null"));
        assertEquals(0,numero(url,"select count(*) from information_schema.tables where table_name in ('VENDA_ITENS','PROMOCAO_ITENS')"));
    }
    @Test void dadosInvalidosParamMigracaoSemInventarPreco() throws Exception {
        String url=banco(); Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
        executar(url,"insert into produto(nome,ativo,preco,quantidade) values ('Inválido',true,-1,2)");
        var erro=assertThrows(org.flywaydb.core.api.FlywayException.class,()->flyway(url).migrate());
        assertTrue(erro.getMessage().toLowerCase().contains("ck_produto_preco"));
        assertEquals(-1,numero(url,"select preco from produto"));
    }
}
