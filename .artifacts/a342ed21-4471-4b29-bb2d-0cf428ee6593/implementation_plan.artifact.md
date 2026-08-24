# Fix Live Match Indicators and Add Live Tab in Match Details

This plan addresses the user's feedback that live matches still show "IN-PROGRESS" in various places and that the "Live" tab in Match Details is missing or not defaulting correctly.

## Proposed Changes

### [MatchListFragment](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/MatchListFragment.kt)

#### [MODIFY] [MatchListFragment.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/MatchListFragment.kt)
- Update the match card result bar to show "LIVE" instead of "IN-PROGRESS" if the match is currently live.

---

### [MatchDetailsActivity](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/MatchDetailsActivity.kt)

#### [MODIFY] [MatchDetailsActivity.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/MatchDetailsActivity.kt)
- Initialize `ScoringViewModel` to support `LiveScoringFragment`.
- Add references to header views (`scoreText`, `oversText`, `tvCurrentTeamName`, etc.).
- In `loadMatch`, if the match is live or not finished:
    - Show `layoutLiveHeader` (score and overs) instead of hiding it.
    - Set `tvFinalResultBanner` to "LIVE" (with a red color) if live, or hide it.
    - Update the header views with current match data.
- Update `setupTabs` to:
    - Add a "Live" tab at index 0 if the match is live.
    - Default the ViewPager to the "Live" tab if the match is live, otherwise default to "Summary".
- Update `updateUI` to refresh the header views.

## Verification Plan

### Automated Tests
- Build the project to ensure no regressions.

### Manual Verification
- Deploy the app.
- Start a match.
- Go to the "Live" tab in the match list. Verify the card says "LIVE" at the bottom.
- Tap the card to open Match Details.
- Verify that the header shows the live score and overs.
- Verify that the "Live" tab is shown and selected by default, showing the batsman and bowler cards.
- Verify that the "Summary" tab is still available.
- Finish the match and verify that Match Details returns to showing the "Summary" tab and the final result banner.
