package br.com.lac.caixa.financas;

import org.springframework.boot.test.context.SpringBootTest;

/** A massa fixa das finanças no H2. */
@SpringBootTest(
        properties = {"spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:financas;DB_CLOSE_DELAY=-1"})
class FinancasH2Test extends MassaFinanceira {}
