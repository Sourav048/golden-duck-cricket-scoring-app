# Implementation Plan - Automatic Innings Completion on Retirement

The goal is to ensure that if a user retires out players until no one is left to bat, the app automatically recognizes the innings/match as finished and shows the appropriate completion dialog (First Innings Complete or Match Finished) instead of a simple "No more batsman" Toast.

## User Review Required

> [!IMPORTANT]
> This change ensures that "Retired Out" actions correctly trigger the end of an innings if they result in the last possible wicket.

## Proposed Changes

### MainActivity Logic

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/MainActivity.kt)
- **checkInningsCompletion**: Add a `force: Boolean = false` parameter to allow triggering the completion flow even if the internal `innings.isComplete` check hasn't caught up yet (useful for retirement edge cases).
- **showBatsmanSelectionDialog**:
    - When `available.isEmpty()` and no "Last Man Stand" transition is possible, call `checkInningsCompletion(innings, force = true)`.
    - This will trigger the "First Innings Complete" or "Match Finished" dialog as requested.
- **commitRetired**: Ensure `updateUI()` is called before opening the selection dialog so the background scorecard is accurate when the completion dialog appears.

## Verification Plan

### Manual Verification
1. **Scenario: Consecutive Retired Out (1st Innings)**
    - Start a match with 3 players per side.
    - Retire Out the first two players.
    - Retire Out the 3rd player (the last one).
    - **Verify**: The "FIRST INNINGS COMPLETE" dialog appears immediately.
2. **Scenario: Consecutive Retired Out (2nd Innings)**
    - Start the 2nd innings.
    - Retire Out everyone.
    - **Verify**: The "MATCH FINISHED" dialog appears immediately.
3. **Scenario: Last Man Stand Transition**
    - Retire Out the striker when a non-striker is available and there's no one in the dugout.
    - **Verify**: The non-striker is promoted to striker, and then the app checks for a new non-striker (which will be empty and then trigger completion).
