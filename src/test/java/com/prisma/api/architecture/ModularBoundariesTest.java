package com.prisma.api.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-21 (SCRUM-33): Verificación automática de límites entre módulos.
 *
 * Criterios de Aceptación Oficiales:
 * 1. Verificación exitosa:
 *    Given que el código respeta los límites de paquete definidos por cada módulo
 *    When se ejecuta la suite de pruebas de ArchUnit en el pipeline de build
 *    Then el build pasa sin violaciones de arquitectura (exit code 0)
 *
 * 2. Violación detectada:
 *    Given que un desarrollador agrega código que accede directamente a una clase interna
 *    de otro módulo sin pasar por su interfaz pública
 *    When se ejecuta la suite de pruebas de ArchUnit
 *    Then el build falla y reporta el paquete y la clase que violan el límite de módulo
 */
@DisplayName("SCRUM-33: Verificación Automática de Límites entre Módulos y Arquitectura Limpia")
public class ModularBoundariesTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    public static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.prisma.api");
    }

    @Test
    @DisplayName("Criterio 1: Ausencia de ciclos entre paquetes/módulos del monolito")
    public void paquetesDebenEstarLibresDeCiclos() {
        ArchRule rule = slices()
                .matching("com.prisma.api.(*)..")
                .should().beFreeOfCycles();

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Criterio 1: Componentes externos solo acceden a servicios a través de sus interfaces públicas")
    public void componentesExternosNoDebenAccederAImplementacionesInternasDeServicios() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..controller..", "..config..", "..security..")
                .should().dependOnClassesThat().resideInAPackage("..service.impl..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Criterio 1: Las entidades de dominio no deben depender de controladores ni de servicios")
    public void entidadesNoDebenDependerDeCapasSuperiores() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..entity..")
                .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Criterio 1: Los repositorios de datos no deben depender de la capa de presentación (controladores)")
    public void repositoriosNoDebenDependerDeControladores() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAPackage("..controller..");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Criterio 2: ArchUnit detecta y reporta violación cuando se rompe una regla de límites")
    public void debeDetectarViolacionDeLimitesCuandoExisteAccesoIndebido() {
        // Regla que evalúa una restricción deliberadamente estricta para comprobar que ArchUnit
        // detecta infracciones y genera un reporte detallado con exit code de fallo
        ArchRule reglaDeteccion = classes()
                .that().resideInAPackage("..controller..")
                .should().resideInAPackage("..repository..");

        EvaluationResult result = reglaDeteccion.evaluate(importedClasses);
        assertTrue(result.hasViolation(), "ArchUnit debe detectar violaciones cuando una clase rompe la regla de arquitectura");
        assertFalse(result.getFailureReport().getDetails().isEmpty(), "El reporte de fallo de ArchUnit debe detallar las clases infractoras");
    }
}
