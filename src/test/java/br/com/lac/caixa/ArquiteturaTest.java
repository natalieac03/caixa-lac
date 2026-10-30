package br.com.lac.caixa;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Camadas: o domínio não conhece serviço, banco nem web; o repositório não conhece serviço nem controle. */
@AnalyzeClasses(packages = "br.com.lac.caixa", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule dominioIsolado = noClasses()
            .that().resideInAnyPackage("..caixa.modelo..", "..caixa.dominio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..caixa.servico..", "..caixa.repositorio..", "..caixa.controle..", "org.springframework.web..")
            .because("modelo e dominio são o miolo POO: não dependem de serviço, banco nem web");

    @ArchTest
    static final ArchRule repositorioSemServicoNemControle = noClasses()
            .that().resideInAPackage("..caixa.repositorio..")
            .should().dependOnClassesThat().resideInAnyPackage("..caixa.servico..", "..caixa.controle..")
            .because("o repositório só conhece o modelo; quem orquestra é o serviço");
}
