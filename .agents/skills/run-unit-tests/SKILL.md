---
name: run-unit-tests
description: Instructions for running the JVM unit test suite (344 tests) in Neo Child Clinic using Gradle. Use when testing code changes, verifying bug fixes, or assessing release readiness.
---

# Run Unit Tests Skill

This skill documents how to execute JVM unit tests for the Neo Child Clinic project.

## Running Tests on Workstation (Linux Mint Desktop)

Execute unit tests using the Gradle wrapper:

```bash
# Run all JVM unit tests
./gradlew testDebugUnitTest

# Run a specific feature's test class
./gradlew testDebugUnitTest --tests "com.neochildclinic.feature.patient.*"

# Run a single targeted test method
./gradlew testDebugUnitTest --tests "com.neochildclinic.feature.vaccination.presentation.AddVaccinationViewModelTest.testStockDeductionSuccess"
```

## Running Tests on CI / Verification Environment

In continuous integration or Docker/headless environments:

```bash
./gradlew testDebugUnitTest --continue --no-daemon --stacktrace
```

## Interpreting Test Results
- HTML Test Reports: `app/build/reports/tests/testDebugUnitTest/index.html`
- XML Test Results: `app/build/test-results/testDebugUnitTest/`

## Mobile CLI (Android Termux) Note
Termux does not natively support running `./gradlew testDebugUnitTest` due to JVM/SDK architecture limitations. All test runs must be delegated to the Desktop workstation or GitHub Actions CI.
