package poc.a2a.investimentos.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import poc.a2a.investimentos.BaseIntegrationTest;

// Mesma regra do analizza-auction. Sem Kafka e sem @McpTool neste agente (ele e cliente MCP, nao servidor) -> checa apenas @RestController e @Scheduled.
@Tag("integration")
class EntrypointHasIntegrationTestRuleIT {

    private final JavaClasses importedClasses = new ClassFileImporter()
            .importPackages("poc.a2a.investimentos");

    private final DescribedPredicate<JavaClass> isEntrypoint = DescribedPredicate.describe(
            "are entrypoints (@RestController or @Scheduled)",
            javaClass -> javaClass.isAnnotatedWith(RestController.class)
                    || javaClass.getMethods().stream().anyMatch(m -> m.isAnnotatedWith(Scheduled.class))
    );

    private ArchCondition<JavaClass> haveCorrespondingIntegrationTest(JavaClasses allClasses) {
        return new ArchCondition<>(
                "have a corresponding integration test (<Name>IT extending BaseIntegrationTest)") {

            @Override
            public void check(JavaClass entrypoint, ConditionEvents events) {
                String expectedTestName = entrypoint.getSimpleName() + "IT";

                boolean testClassFound = false;
                for (JavaClass testClass : allClasses) {
                    if (testClass.getSimpleName().equals(expectedTestName)
                            && testClass.isAssignableTo(BaseIntegrationTest.class)) {
                        testClassFound = true;
                        break;
                    }
                }

                if (!testClassFound) {
                    events.add(SimpleConditionEvent.violated(
                            entrypoint,
                            "Entrypoint " + entrypoint.getName() + " does not have a corresponding "
                                    + "integration test '" + expectedTestName + "' extending BaseIntegrationTest"
                    ));
                } else {
                    events.add(SimpleConditionEvent.satisfied(
                            entrypoint,
                            "Entrypoint " + entrypoint.getName() + " has corresponding integration test '"
                                    + expectedTestName + "'"
                    ));
                }
            }
        };
    }

    @Test
    void entrypointMustHaveIntegrationTest() {
        ArchRule rule = FreezingArchRule.freeze(
                classes()
                        .that(isEntrypoint)
                        .and().areNotAnnotatedWith(RestControllerAdvice.class)
                        .should(haveCorrespondingIntegrationTest(importedClasses))
                        .because("entrypoints exigem IT (BaseIntegrationTest)")
                        .allowEmptyShould(true)
        );

        rule.check(importedClasses);
    }

}
