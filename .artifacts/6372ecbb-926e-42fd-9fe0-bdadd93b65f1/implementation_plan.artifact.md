# Implementation Plan - Fix Live Footer (Recent Balls) Latency & Resumption

This plan addresses a critical "off-by-one" latency issue in the Live Footer (Recent Balls) where ball events appear one ball late (e.g., the 1st ball shows nothing, the 2nd ball shows the 1st). It also refines the reconstruction logic for match resumption and undo operations.

## User Review Required

> [!IMPORTANT]
> I have identified the root cause of the "one-ball-late" display:
> The `LiveScoringFragment` is observing `overBalls` from the `ScoringViewModel`, but `MainActivity` was only updating this list *after* certain logic blocks, and sometimes missed the immediate update for the very first ball of an over or after an undo.
>
> I will fix this by ensuring `recalculateMatchState()` (which pushes data to the ViewModel) is called consistently and immediately after every ball event or state change.

## Proposed Changes

### [Component] UI State & ViewModel Synchronization

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/MainActivity.kt)

- **Immediate State Push**: Ensure `recalculateMatchState()` is called at the end of `playBallWithPlayer` (the core ball recording method) and `undoBall`.
- **`recalculateMatchState` Update**: Explicitly include `viewModel.updateOverBalls(overBallsList)` in this method to ensure the footer is always in sync with the activity's local list.
- **`reconstructCurrentOverState` Refinement**:
    - Fix the logic to correctly identify "extras" at the start of an over.
    - Prevent previous over's balls from appearing in the current over summary.
- **`updateUI` Integration**: Ensure `overBallsList` is pushed during the general `updateUI()` call to handle cases like tab switching or app resumption.

#### [MODIFY] [LiveScoringFragment.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/LiveScoringFragment.kt)

- **Observer Optimization**: Ensure the `overBalls` observer in `onViewCreated` handles empty/null lists gracefully and clears the UI immediately.

## Verification Plan

### Manual Verification
1.  **Latency Test (The "One-Ball-Late" Fix)**:
    - Bowl the 1st ball (e.g., "0").
    - **Verify**: A circle with "•" appears in the footer *immediately*.
    - Bowl the 2nd ball (e.g., "4").
    - **Verify**: A second circle with "4" appears *immediately*.
2.  **Undo Sync Test**:
    - Bowl 3 balls.
    - Press Undo.
    - **Verify**: The 3rd ball disappears from the footer *immediately*.
3.  **Resumption Test**:
    - Bowl 2 balls. Exit match.
    - Resume match.
    - **Verify**: Both balls appear in the footer upon opening.
4.  **New Over Boundary Test**:
    - Complete an over.
    - **Verify**: Footer clears (or shows the next over's balls as they happen).
    - Bowl a Wide at the start of the next over.
    - **Verify**: "WD" appears immediately.
