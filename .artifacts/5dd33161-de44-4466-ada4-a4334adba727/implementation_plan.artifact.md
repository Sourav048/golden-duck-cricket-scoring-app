# Fix Room Database Migration Crash

The app is crashing with `java.lang.IllegalStateException: A migration from 4 to 8 was required but not found`. Even though `fallbackToDestructiveMigration()` is present in `AppDatabase.kt`, it seems it's either not working correctly or the build is stale.

## Proposed Changes

### [Database]

#### [MODIFY] [AppDatabase.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/AppDatabase.kt)
- Update the `getInstance` method to ensure `fallbackToDestructiveMigration()` is correctly applied.
- Remove `exportSchema = true` as it might be causing issues if schema files are missing.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/build.gradle.kts)
- Downgrade Room to a more widely tested stable version (2.6.1) to rule out bugs in 2.8.4.

## Verification Plan

### Automated Tests
- Run `gradle_build` to ensure the project compiles.
- Deploy the app and navigate to "Matches" and "Players" to verify the crash is gone.

### Manual Verification
- Verify that the database is reset (or migrated) without crashing.
