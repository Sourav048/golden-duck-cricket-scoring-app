# Allow 1 vs 1 Matches

This plan addresses the requirement to allow matches where each team can have as few as 1 player. Currently, the app requires at least one team to have 2 players.

## User Review Required

> [!IMPORTANT]
> In 1-player teams, the app will function in "Single Player" mode for that team:
> - There will be no Non-Striker.
> - Strike rotation will be disabled (runs won't change who is facing).
> - The innings will end as soon as the single player is out (unless "Every Player Bats" rule is used, but even then, with only 1 player, there's no one else to bat).

## Proposed Changes

### [Component: Player Entry]

#### [MODIFY] [PlayerEntryActivity.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/PlayerEntryActivity.kt)
- Remove the validation check that requires at least one team to have more than 1 player.
- Ensure the "Start Match" button is enabled as long as both teams have at least 1 player.

### [Component: Toss & Initialization]

#### [MODIFY] [TossActivity.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/TossActivity.kt)
- Verify that the transition to `MainActivity` correctly handles cases where only 1 player is available (passing `null` for the non-striker).

## Verification Plan

### Automated Tests
- I will verify the logic by examining the `Match` and `Innings` state transitions for a 1-vs-1 scenario.

### Manual Verification
- Start a match with 1 player in Team A and 1 player in Team B.
- Verify that the match starts correctly without the "One team must have 2 Players" toast.
- Verify that in the scoring screen, the Non-Striker card shows "-" or is empty.
- Verify that scoring runs does not rotate strike (since there is no non-striker).
- Verify that taking a wicket ends the innings immediately.
