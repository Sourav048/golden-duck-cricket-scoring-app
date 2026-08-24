# Project Research: Scoring App ("Golden Duck")

This project is a comprehensive cricket scoring application for Android. It handles match setup, live ball-by-ball scoring, player statistics, and match history.

## Architecture & Tech Stack

- **UI Framework**: Native Android (XML ViewBinding).
- **Language**: Kotlin.
- **Database**: Room (Local storage) and Firebase (Cloud sync/backup).
- **Core Components**:
    - `HomeActivity`: Launcher, handles `.cricket` file imports.
    - `MainActivity`: The "God Activity" handling live scoring logic. Implements `ScoringProvider`.
    - `ScoringViewModel`: Manages live match data using `LiveData`.
    - `AppDatabase`: Room database for `Match`, `Player`, `Stats`, and `DraftMatch`.
- **Key Libraries**:
    - **Firebase**: Auth, Firestore (syncing), Storage (photos), Crashlytics.
    - **UI**: Material Components, Lottie (animations), Confetti (celebrations), Glide (image loading), Shimmer (loading states).
    - **Room**: Local persistence with complex entities stored via TypeConverters/JSON serialization.

## Data Model

- `Match` / `MatchEntity`: Stores match metadata, settings, and serialized innings data.
- `Innings`: Tracks runs, wickets, overs, and lists of balls for a single innings.
- `Player` / `PlayerEntity`: Player profiles and career stats.
- `Ball`: Individual ball data (runs, type, extras, wicket info).
- `CommentaryEntry`: Auto-generated or manual commentary for each ball.

## Key Features

- **Match Setup**: `SetupActivity`, `PlayerEntryActivity`, `TossActivity`.
- **Live Scoring**: Ball-by-ball entry with support for various extras (Wide, No Ball, Bye, Leg Bye, Penalty) and wicket types.
- **DLS Support**: `DLSUtility` for Duckworth-Lewis-Stern calculations in rain-interrupted matches.
- **Match Management**: Draft matches, resume match capability, and historical match view (`MatchHistoryActivity`, `MatchDetailsActivity`).
- **Statistics**: Leaderboards (`LeaderboardActivity`), detailed player stats (`PlayerDetailsActivity`), and player comparison (`PlayerCompareActivity`).
- **Backup/Export**: `BackupManager` for encrypted data export/import and `.cricket` file handling.

## Observations & Potential Areas for Improvement

- **`MainActivity` Complexity**: At nearly 4,000 lines, `MainActivity` is highly coupled with business logic. Refactoring this into more granular components or a more robust ViewModel/Repository pattern would improve maintainability.
- **Data Persistence**: Storing large lists (balls, commentary) as JSON within Room entities might lead to performance issues as data grows.
- **Testing**: The project has basic JUnit and Espresso setups, but unit tests for core scoring logic (e.g., `MainActivity`'s `playBall` method) seem sparse given the complexity.
