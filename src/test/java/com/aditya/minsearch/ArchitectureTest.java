package com.aditya.minsearch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  @Test
  void modulesAreFreeOfCycles() {
    JavaClasses importedClasses = new ClassFileImporter().importPackages("com.aditya.minsearch");

    SlicesRuleDefinition.slices()
        .matching("com.aditya.minsearch.(*)..")
        .should()
        .beFreeOfCycles()
        .check(importedClasses);
  }
}
