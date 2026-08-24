# Implementation Plan - Newest Commentary at Top for Live Viewer

This plan addresses the issue where the commentary in the "Commentary" tab for live viewers is displayed from oldest to newest. We will flip this so that the most recent actions appear at the top, consistent with the scorer's view.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MatchDetailsActivity.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/MatchDetailsActivity.kt)

- In `loadMatch`, remove `.asReversed()` when adding commentary entries to the `commentary` list.
- In `startLiveObserver`, remove `.asReversed()` when rebuilding the commentary list from the updated match entity.

## Verification Plan

### Manual Verification
- **Spectator Mode**: Open a live match as a spectator.
- **Check Commentary Tab**: Verify that the latest ball appears at the top of the list.
- **Live Update**: Wait for a new ball to be bowled by the scorer and verify it appears at the top of the commentary feed without scrolling.
