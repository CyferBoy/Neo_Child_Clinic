# Walkthrough - Kotlin SourceSets Fix

I have resolved the Gradle sync error related to `kotlin.sourceSets` DSL usage in AGP 9.3.2.

## Changes

### Build Configuration

#### [gradle.properties](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/gradle.properties)

I added the following property to the `gradle.properties` file:
```properties
android.disallowKotlinSourceSets=false
```
This flag allows the project to continue using the legacy Kotlin source sets DSL, which is required for compatibility with the current version of the KSP plugin in this project.

## Verification Results

### Gradle Sync
I ran a Gradle Sync, and it completed successfully:
> **Status:** Sync finished successfully.
