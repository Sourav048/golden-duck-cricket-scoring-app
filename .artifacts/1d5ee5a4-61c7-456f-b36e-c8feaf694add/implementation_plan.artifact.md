# Implementation Plan - Final Fix for Player Comparison Visibility

The previous "nuclear option" broke Dark Mode because it hardcoded white text on backgrounds that become white in Dark Mode. Additionally, the `RelativeLayout` might have been causing clipping or overlapping issues.

I will now implement a clean, theme-aware layout that handles both modes correctly by using the system's own "On" colors (`colorOnPrimary`, `colorOnSecondary`).

## Proposed Changes

### [Component: UI Layout]

#### [MODIFY] [activity_player_compare.xml](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/res/layout/activity_player_compare.xml)
- **Simplify Layout**: Revert from `RelativeLayout` to a vertical `LinearLayout` inside the cards. This is more reliable for simple vertical stacking of an icon and a label.
- **Fixed Height**: Keep the `100dp` height to ensure the cards don't collapse.
- **Theme-Aware Colors**:
    - Slot 1 TextView: `android:textColor="?attr/colorOnPrimary"`
    - Slot 2 TextView: `android:textColor="?attr/colorOnSecondary"`
- This ensures the system automatically picks White for Light Mode (Teal cards) and Black for Dark Mode (White cards).

### [Component: Player Comparison Logic]

#### [MODIFY] [PlayerCompareActivity.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/PlayerCompareActivity.kt)
- **Remove Hardcoding**: Strip out all manual `Color.WHITE` or `Color.BLACK` overrides.
- **Dynamic Resolution**: Programmatically resolve the correct `colorOnPrimary` or `colorOnSecondary` based on the slot.
- **Clean Reset**: When a player is null, explicitly set the text to "Select Player" and the color to the resolved theme color.
- **Call on Launch**: Re-enable the initial placeholder update in `onCreate`.

## Verification Plan

### Manual Verification
1. **Light Mode**: Verify "Select Player" is White on the Teal card.
2. **Dark Mode**: Verify "Select Player" is Black on the White card.
3. **Player Selection**: Select a normal player and verify their name contrast is correct in both modes.
4. **Prestige Check**: Select a ranked player and verify they keep their Gold/Orange/Purple rank colors.
