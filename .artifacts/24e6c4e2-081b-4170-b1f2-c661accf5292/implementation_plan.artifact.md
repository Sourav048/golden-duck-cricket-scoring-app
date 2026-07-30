# Implementation Plan - Hattrick Fixes & Technical Debt Reduction

This plan focuses on rectifying the cricket scoring engine's hattrick logic and improving project robustness by addressing risky null-handling and swallowed exceptions.

## Proposed Changes

### 1. Hattrick & Scoring Logic Improvements

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/MainActivity.kt)
- **Hattrick Detection**:
    - Update `playBallWithPlayer` to filter out "Run Out" from the consecutive wicket streak.
    - Include bowler-credited wickets taken on Wides (e.g., Stumpings) in the hattrick count.
    - Implement a "milestone check" to prevent a 4th consecutive wicket from triggering a second hattrick notification.

#### [MODIFY] [StatsRecalculator.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/StatsRecalculator.kt)
- Mirror the hattrick logic fixes in the stats rebuild process to ensure consistency between live scoring and historical data.

---

### 2. Technical Debt & Robustness

#### [MODIFY] Multiple Files
- **Null Safety Audit**: Systematically replace risky `!!` assertions in core logic (especially `MainActivity.kt` and `RankingRegistry.kt`) with safe calls (`?.`), Elvis operators (`?:`), or `requireNotNull()` with descriptive error messages.
- **Exception Visibility**: Locate empty `catch` blocks in `PlayerDetailsActivity.kt`, `PlayerListActivity.kt`, and `BackupManager.kt` and replace them with structured `Log.e` reporting to aid in debugging production issues.

## Verification Plan

### Automated Tests
- Create `HattrickLogicTest.kt` to verify:
    - A 3-wicket streak correctly awards 1 hattrick.
    - A 4-wicket streak correctly awards 1 hattrick (not 2).
    - A streak interrupted by a "Run Out" or a non-wicket delivery correctly resets.
    - A stumping on a Wide delivery is correctly counted in the streak.

### Manual Verification
- **Scoring Simulation**: Take 3 consecutive wickets (including one on a wide) in the live scorer and verify the "HATTRICK" announcement appears correctly.
- **Stats Rebuild**: Rebuild stats for a match with a hattrick and verify the player's hattrick count in the database is correct.
