package br.com.lac.caixa;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Regras de dependência entre pacotes, sem ciclos. */
@AnalyzeClasses(packages = "br.com.lac.caixa", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule dominioIsolado = noClasses()
            .that()
            .resideInAnyPackage("..caixa.modelo..", "..caixa.dominio..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..caixa.servico..", "..caixa.repositorio..", "..caixa.controle..", "org.springframework.web..")
            .because("modelo e dominio são o miolo POO: não dependem de serviço, banco nem web");

    @ArchTest
    static final ArchRule repositorioSemServicoNemControle = noClasses()
            .that()
            .resideInAPackage("..caixa.repositorio..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..caixa.servico..", "..caixa.controle..")
            .because("o repositório só conhece o modelo; quem orquestra é o serviço");

    @ArchTest
    static final ArchRule servicoSemControle = noClasses()
            .that()
            .resideInAPackage("..caixa.servico..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..caixa.controle..")
            .because("o serviço não sabe de tela; o controle é que chama o serviço");

    @ArchTest
    static final ArchRule utilEFolha = noClasses()
            .that()
            .resideInAPackage("..caixa.util..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..caixa.servico..", "..caixa.repositorio..", "..caixa.controle..", "..caixa.modelo..")
            .because("util são ferramentas soltas (dinheiro, rateio, gráfico); quem monta CSV de tela não é util");

    @ArchTest
    static final ArchRule semCiclos = slices().matching("br.com.lac.caixa.(*)..")
            .should()
            .beFreeOfCycles()
            .because("um pacote que depende de outro que depende dele vira um pacote só");
}
