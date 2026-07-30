# Implementation Plan - Final Resumption Verification & Fixes

This plan addresses a specific timing issue discovered during the verification of the "Resumption-Proof" system. While most states are persisted, the **Live Footer** (Recent Balls) fails to restore immediately upon resumption because the UI synchronization happens before the over state is fully reconstructed.

## User Review Required

> [!IMPORTANT]
> I have identified a small but critical timing bug:
> When resuming a match, the app refreshes the UI *before* it finished reading the previous balls from the database. This causes the Live Footer to start empty.
> I will fix this by forcing a final state recalculation at the very end of the resumption process.

## Proposed Changes

### [Component] Resumption Logic

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/MainActivity.kt)

- **`resumeMatch` Timing Fix**:
    - Move `recalculateMatchState()` call to the very end of the `resumeMatch` block, AFTER `reconstructCurrentOverState()`.
    - This ensures the reconstructed `overBallsList` is pushed to the ViewModel and observed by the Live fragment immediately.
- **Robustness Check**:
    - Ensure `isLiveScoringActive` is maintained during the transition to ensure background timers (player clocks) resume correctly.

## Verification Plan

### Manual Verification
1.  **Footer Restoration (Final Confirmation)**:
    - Bowl 3 balls.
    - Exit the match.
    - Resume from Match History.
    - **Verify**: The 3 colored squares appear in the footer the moment the match opens, without needing to bowl a new ball.
2.  **State Atomic Sync**:
    - Verify that the Score, Overs, and Recent Balls all update in a single frame upon resumption.
