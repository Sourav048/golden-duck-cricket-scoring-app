# Implementation Plan - "Certified Maiden" Seal Enhancement

This plan refines the "MAIDEN" indicator for both the Commentary Summary cards and the Over Summary Dialog to a high-impact, professional "Legend Seal" look.

## Proposed Changes

### [Layouts] Enhanced Legend Style

#### [MODIFY] [item_commentary_over_summary.xml](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/res/layout/item_commentary_over_summary.xml)
- Update `tvSummaryMaidenBadge` to use:
    - **Solid Red Background**: `#B71C1C`
    - **White Text**: `#FFFFFF`
    - **Rounded Corners**: Using a small radius or padding.
    - **Positioning**: Perfectly centered on the top border.

#### [MODIFY] [dialog_over_summary.xml](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/res/layout/dialog_over_summary.xml)
- Update `tvMaidenBadge` to match the new "Certified Seal" style exactly (White text on Red background, centered on border).

### [MainActivity.kt](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/java/com/example/scoring/MainActivity.kt)

#### [MODIFY] `handleOverCompletion`
- Ensure the dialog's over label (`tvOverLabel`) turns Red if it's a maiden, matching the commentary card's behavior.

#### [MODIFY] `CommentaryAdapter.bindSummary`
- Ensure the over number (`tvOverNum`) turns Red when the maiden seal is visible.

## Verification Plan

### Manual Verification
1.  **Maiden Over**:
    - Bowl 6 dot balls.
    - **Dialog**: Verify a solid red "MAIDEN" badge with white text sits on the top border. The over text should be red.
    - **Commentary**: Verify the same high-impact red seal appears on the commentary card.
2.  **Theme Consistency**: Verify the seal looks professional in both Light and Dark modes (since it's solid red, it should pop against both).
3.  **Regular Over**: Verify the seal is completely hidden and the layout remains aligned for overs with runs.
