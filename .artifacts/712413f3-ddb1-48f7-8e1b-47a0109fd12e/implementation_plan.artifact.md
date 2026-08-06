# Implementation Plan - Fix Serialization Error 'Serializer for class Any not found'

The error occurs in `SyncRepositoryImpl.kt` because the Supabase `upsert()` function is called with a variable of type `Any?`. Kotlinx Serialization requires the concrete serializable type to be known at compile time to find the correct serializer.

## Proposed Changes

### Repository Layer

#### [MODIFY] [SyncRepositoryImpl.kt](file:///C:/Users/Nadeem/Desktop/vaccine_manager_app/app/src/main/java/com/neochildclinic/data/repository/SyncRepositoryImpl.kt)
- Update `uploadEntity` to explicitly cast `localData` to its concrete type before calling `postgrest.from(table).upsert()`.
- This ensures the `reified` type parameter of `upsert` receives the correct serializable class instead of `Any`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the build.
- Trigger a sync operation in the app to ensure data is successfully uploaded to Supabase without serialization errors.
