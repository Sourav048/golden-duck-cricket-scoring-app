# Implementation Plan - Optimize Gully Joining and Fix UI Hang

The user reports a 7-second delay when joining a "Gully" (league) and that the app feels "hanged" during this period. This plan addresses the perceived hang through better UI feedback and optimizes the joining logic to be more responsive.

## User Review Required

> [!NOTE]
> The 7-second delay is primarily due to Firestore network latency during the document fetch for passcode verification. While we can't control the network, we can eliminate the "hang" sensation.

## Proposed Changes

### 1. GullySyncManager Optimization
- Move database initialization out of the main thread in `startSync`.
- Ensure all heavy operations in `joinGully` and `startSync` are properly off-loaded.

### 2. GullyManagementActivity UI Improvements
- Add a "Joining..." / "Creating..." state to buttons.
- Disable input fields and buttons during the network call to prevent duplicate requests and provide visual feedback.
- Automatically hide the keyboard when the user initiates a join/create action.
- Clean up redundant/duplicate methods in the activity.

### 3. BaseActivity Utility
- Add a `hideKeyboard()` helper method for common use.

## Proposed Changes

### [Component: Gully System]

#### [MODIFY] [GullySyncManager.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/GullySyncManager.kt)
- Wrap `AppDatabase.getInstance` in an `ioExecutor` or move it to the background threads where it's actually used.
- Add a small delay/timeout or check for cache to improve responsiveness.

#### [MODIFY] [GullyManagementActivity.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/GullyManagementActivity.kt)
- Implement loading states in `performJoin` and `performCreate`.
- Use `hideKeyboard()` utility.
- Remove redundant `performJoin()` and `performCreate()` methods.

#### [MODIFY] [BaseActivity.kt](file:///C:/Users/Soura/OneDrive/Documents/ScoringApp/app/src/main/java/com/example/scoring/BaseActivity.kt)
- Add `hideKeyboard()` method.

## Verification Plan

### Automated Tests
- N/A (UI and Network interaction)

### Manual Verification
1. Open Gully Management.
2. Enter a gully ID and passcode.
3. Tap "Join Gully".
4. Verify:
   - Keyboard disappears immediately.
   - Button text changes to "Joining...".
   - Button and input fields are disabled.
   - App remains responsive (can tap "Back" if allowed, or see that the UI is not frozen).
   - Once joined, the toast appears and the activity finishes correctly.
