# Implementation Plan - Fix Bowling Prestige Color (Purple)

I will fix the issue where the Purple color for top bowlers is being overwritten by the theme engine, making it invisible or incorrect in the high-contrast white theme.

## User Review Required

> [!IMPORTANT]
> **Prestige Protection**: I am updating the app's theme engine to "recognize" prestige colors (Gold, Orange, Purple). Once a player name is highlighted as a top performer, the theme engine will no longer attempt to change its color, ensuring it stays vibrant on both white and dark cards.

## Proposed Changes

### 1. Protect Prestige Colors in Theme Engine
#### [MODIFY] [RankingRegistry.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/RankingRegistry.kt)
- Expose the cached prestige colors (`cachedGold`, `cachedOrange`, `cachedPurple`).
- Add a helper method `isPrestigeColor(color: Int): Boolean` to allow other parts of the app to identify these special colors.

#### [MODIFY] [ThemeManager.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/ThemeManager.kt)
- Update the `TextView` coloring logic.
- Before forcing a text color to Black or White, check if it's already a prestige color using `RankingRegistry.isPrestigeColor()`.
- If it is, skip the automatic coloring to preserve the player's rank highlight.

### 2. Vibrancy & Mapping Fixes
#### [MODIFY] [res/values/themes.xml](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/res/values/themes.xml)
- Fix the `prestigeOrange` mapping in Light Mode (currently mistakenly points to Deep Teal).

#### [MODIFY] [res/values-night/colors.xml](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/res/values-night/colors.xml)
- Update `prestige_purple` to a more vibrant and prestigious shade (`#E040FB`) to ensure it stands out clearly against the high-contrast white UI.

## Verification Plan

### Manual Verification
- **Bowling Leaderboard**:
    - Go to **Stats** -> **Most Wickets** (or any bowling stat).
    - In Dark Mode (White theme), verify the #1 player's name is a vibrant **Purple**.
- **Batting Leaderboard**:
    - Verify the #1 player remains **Orange**.
- **Overall Leaderboard**:
    - Verify the #1 player remains **Gold**.
- **Consistency**:
    - Check regular player names (ranks 4-10) to ensure they are still **Black** and readable on the white cards.
