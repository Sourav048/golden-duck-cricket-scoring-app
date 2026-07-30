# Walkthrough - Duckworth-Lewis-Stern (DLS) Implementation

I have replaced the manual target revision tool with a professional **Duckworth-Lewis-Stern (DLS)** system. This allows for accurate target adjustments during rain-interrupted matches.

## Changes Made

### 1. DLS Calculation Engine
Created [DLSUtility.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/DLSUtility.kt) which implements:
- **Standard Resource Table**: A lookup table mapping "Overs Remaining" and "Wickets Lost" to resource percentages.
- **T20 Scaling**: Automatically scales the 50-over standard table for T20 matches.
- **Target Formulas**: Implements both DLS scenarios (Team 2 having fewer or more resources than Team 1).

### 2. User Interface Updates
- **Menu Rename**: The "Revise Target/Overs" option in the scoring menu is now **"Apply DLS"**.
- **Simplified Workflow**:
    1. Select "Apply DLS".
    2. Enter the **Revised Total Overs** for the match.
    3. The app automatically calculates the new target based on the current match state.

### 3. Explanation & Confirmation Dialog
When DLS is calculated, a detailed dialog appears:
- **Summary**: Shows Team A's final score and the revised match length.
- **Target Calculation**: Displays the new projected target for the chasing team.
- **Decision**: You can **Accept** the changes or **Reject** them if they were entered by mistake.

### 4. Commentary & Persistence
- **Fact Entry**: Upon accepting DLS, a professional entry is added to the commentary log: `FACT: DLS Applied: Match reduced to 15 overs. New target: 124.`
- **Match State**: The match target and over limits are updated throughout the app (Scoreboard, Target displays, etc.).

## Verification Results

### Logic Test
- **Scenario**: 20 over match, Team A scores 150. Match reduced to 15 overs before Team B starts.
- **Result**: Target correctly adjusted based on resource percentage differences.

### Build
- Ran `app:assembleDebug` - Build successful.
