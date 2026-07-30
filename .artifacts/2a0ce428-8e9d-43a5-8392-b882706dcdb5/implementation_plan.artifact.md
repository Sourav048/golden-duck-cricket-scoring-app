# App Icon & Splash Screen Correction Plan

Fix the app icon to fully cover the available width and ensure the new "Golden Duck" branding appears during the app splash screen.

## User Review Required

> [!IMPORTANT]
> - I will create an **Adaptive Icon** structure that uses a black background to blend perfectly with your new icon.
> - I am removing the **15% inset** that was causing the icon to look small and framed.
> - The splash screen will be updated to show the new "Golden Duck" icon instead of the old one.

## Proposed Changes

### [Component] Branding Assets

#### [NEW] [ic_launcher_app_foreground.xml](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/res/drawable/ic_launcher_app_foreground.xml)
- Create a foreground drawable that directly references the new PNG without any insets, allowing it to take up the maximum possible space.

#### [NEW] [ic_launcher_app.xml](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/res/mipmap-anydpi-v26/ic_launcher_app.xml)
- Define the adaptive icon for the app:
    - **Background**: `@color/black`
    - **Foreground**: `@drawable/ic_launcher_app_foreground`

#### [NEW] [ic_launcher_app_round.xml](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/res/mipmap-anydpi-v26/ic_launcher_app_round.xml)
- Link the round icon to the same adaptive definition for consistency.

### [Component] UI Themes & Styles

#### [MODIFY] [themes.xml](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/res/values/themes.xml) & [themes.xml (night)](file:///C:/Users/Soura/Downloads/ScoringApp/app/src/main/res/values-night/themes.xml)
- Update `windowSplashScreenAnimatedIcon` to use `@mipmap/ic_launcher_app` so the new branding shows up during app load.

## Verification Plan

### Manual Verification
1. **Launcher Check**:
    - Uninstall and reinstall the app (due to previous database reset and to force icon cache refresh).
    - Verify the icon on the home screen is large, fills the width, and doesn't have a white frame.
2. **Splash Screen Check**:
    - Launch the app.
    - Verify the "Golden Duck" icon appears on the splash screen instead of the old icon.
3. **Adaptive Test**:
    - Change the system icon shape (if supported by your launcher) to verify it behaves correctly as an adaptive icon.
