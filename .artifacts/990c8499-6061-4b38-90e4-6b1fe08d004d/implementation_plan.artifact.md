# Implementation Plan - Dynamic No-Ball Penalty Logic

This plan addresses a bug where No-Balls were always assigned a 1-run penalty, even when the \"Runs on Wides/No-Balls\" rule was disabled. It ensures that No-Balls follow the same dynamic penalty logic as Wides.

## User Review Required

> [!IMPORTANT]
> The \"Runs on Wides/No-Balls\" toggle in the match configuration will now correctly control the penalty for both delivery types. If disabled, a No-Ball with 0 runs will result in 0 runs added to the team total.

## Proposed Changes

### [MainActivity.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/MainActivity.kt)

#### [MODIFY] Dynamic Penalty Logic
I will replace all hardcoded `1` penalties for No-Balls with a dynamic `nbPenalty` value calculated as:
`val nbPenalty = if (isRuleRunsOnWide) 1 else 0`

Specifically, I will update:
1.  **`showNoBallRunsInput`**: Use `nbPenalty + r` for the total runs passed to the wicket dialog.
2.  **`playBallWithPlayer`**:
    - Update the narrative description (`desc`) for No-Balls. If the penalty is 0 and 1 run was hit, it will correctly say \"1 Run NO BALL\" instead of \"NO BALL\".
    - Update the badge text (`badgeText`) and over summary text (`event`) to correctly calculate the extra runs ran by subtracting the dynamic `nbPenalty`.
3.  **`reconstructCurrentOverState`**: Update the over history display logic to use `nbPenalty` when calculating the label for No-Balls (e.g., `1NB` vs `NB`).

## Verification Plan

### Automated Tests
- Build the project (`app:assembleDebug`) to ensure logic consistency.

### Manual Verification
1.  **Rule Disabled (OFF)**:
    - Start a match with \"Runs on Wides/No-Balls\" turned **OFF**.
    - Record a No-Ball with 0 runs.
    - **Expect**: Team score remains 0/0. Icon shows \"NB\".
    - Record a No-Ball with 1 run (off bat).
    - **Expect**: Team score becomes 1/0. Icon shows \"1NB\".
2.  **Rule Enabled (ON)**:
    - Start a match with \"Runs on Wides/No-Balls\" turned **ON**.
    - Record a No-Ball with 0 runs.
    - **Expect**: Team score becomes 1/0. Icon shows \"NB\".
    - Record a No-Ball with 1 run (off bat).
    - **Expect**: Team score becomes 2/0. Icon shows \"1NB\".
