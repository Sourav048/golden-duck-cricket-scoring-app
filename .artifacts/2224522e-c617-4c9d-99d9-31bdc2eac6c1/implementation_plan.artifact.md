# Implementation Plan: Fix Match List Tab Synchronization & Reactive UI

This plan addresses the issue where matches incorrectly appear in the "Live" tab on multiple devices, even when paused. It also ensures the UI is reactive to cloud changes.

## Proposed Changes

### [Database Layer]

#### [MODIFY] [MatchDao.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/MatchDao.kt)
- Add `getMatchesByStatusByGullyLive` returning `LiveData<List<MatchEntity?>?>`.
- Add `getAbandonedMatchesByGullyLive` returning `LiveData<List<MatchEntity?>?>`.

#### [MODIFY] [DraftMatchDao.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/DraftMatchDao.kt)
- Add `getAllDraftsLive` returning `LiveData<List<DraftMatchEntity?>?>`.

### [Logic Layer]

#### [MODIFY] [MatchListViewModel.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/MatchListViewModel.kt)
- Re-implement the reactive logic using `switchMap` and `LiveData`.
- **Tab Logic**:
    - **Live (0)**: `isLive == true` && `isFinished == false` && `isAbandoned == false`.
    - **Completed (1)**: `isFinished == true`.
    - **In Progress (2)**: `isLive == false` && `isFinished == false` && `isAbandoned == false` (plus drafts).
    - **Abandoned (3)**: `isAbandoned == true`.
- Use `Transformations.map` to ensure the lists are non-null and filtered correctly.

### [UI Layer]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/MainActivity.kt)
- **Lifecycle Protection**:
    - In `onResume`, only mark the match as `isLive = true` if `isScorer == true`.
    - In `onPause`, only mark the match as `isLive = false` if `isScorer == true`.
    - Add `onDestroy` safety to reset `isLive = false` if `isScorer == true`.
- **Sync Guard**:
    - In `saveMatchToDatabase`, ensure `GullySyncManager.syncMatchToCloud` is only called if `isScorer == true`. This prevents spectators from accidentally hijacking the match status in the cloud.

#### [MODIFY] [MatchListFragment.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/MatchListFragment.kt)
- Update `onViewCreated` to observe `viewModel.matches`.
- Remove manual `refresh()` calls in favor of LiveData observation.

## Verification Plan

### Automated Tests
- Run existing `androidTest` to ensure basic scoring functionality is intact.

### Manual Verification
- **Device A (Scorer)**: Open a match. Verify it appears in "Live" on Device B.
- **Device B (Spectator)**: Open the same match. Verify it **STAYS** in "Live" on Device A (no hijacking).
- **Device A (Scorer)**: Pause/Exit the match. Verify it moves to "In Progress" on Device B within seconds.
- **Device B (Spectator)**: Open the paused match. Verify it **STAYS** in "In Progress" (no accidental reactivation).
