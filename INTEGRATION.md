# Mahi Repair design-system integration guide

These assets are presentation-layer building blocks. They do not change authentication, Supabase schemas or policies, API contracts, signature verification, or the native repair execution engine. Existing screens and workflows are left intact until they are deliberately wired to these components.

## Android (Jetpack Compose)

**Requirements:** this repository currently uses Compose BOM 2024.09.00 and Material 3. The new code is kept in the standalone package `com.mahi.repair.designsystem`.

1. The design-system sources are already placed under `app/src/main/java/com/mahi/repair/designsystem/` (theme and components). They coexist with the app's existing `com.example.ui.theme` package.
2. The app currently calls `MahiRepairTheme` from `com.example.ui.theme`. The added `MahiTheme` is not automatically enabled; migrate a screen intentionally by wrapping that screen's content in `com.mahi.repair.designsystem.theme.MahiTheme { ... }` after reviewing it.
3. **Fonts (optional):** add `.ttf` files to `app/src/main/res/font` and change `MahiFontFamily` in `MahiTypography.kt`. It is the only place the typeface is defined.
4. **Home tool cards:** keep the existing ViewModel and action callbacks. Map existing state to `ToolCardState` and pass the real callback:
   ```kotlin
   RepairToolCard(
       name = tool.name, description = tool.description, category = tool.category,
       state = tool.toCardState(),
       onAction = { viewModel.onRunTool(tool.id) },
   )
   ```
5. Map real tool status to `ToolCardState` (Available, New, Updating, Running, RequiresPermission, WaitingForUser, Completed, Failed, Disabled, Offline). Updating, Disabled and Offline render disabled actions.
6. **Search:** use `MahiSearchField` with the existing ViewModel's filter state. It does not perform network searches.
7. **First load:** render `RepairToolCardSkeleton()` only while the real initial sync is pending.
8. **Execution screen:** use `RepairExecutionHeader` and `RepairStepList`. Convert real engine events to `RepairStep(id, title, StepStatus, detail)`. Pass `progress = null` unless the engine reports measurable progress.
9. **Results:** call `RepairSuccessScreen` only after final verification succeeds. Use `RepairPartialScreen` or `RepairFailedScreen` with the engine's actual reason and safe next step.
10. **Sync chip:** place `SyncStatusChip` in the header and wire `onClick` to the existing retry-sync or sign-in callback when appropriate.
11. **Preview gallery:** `DesignSystemGallery.kt` is under `app/src/debug/java/` and contains sample-only previews. It is excluded from release source sets; never use the sample text as live data.
12. Run `./gradlew :app:assembleDebug` and tests in your Android development environment. Also check 1.3x and 2.0x font scale, TalkBack, and system reduced-motion settings.

**Not included:** bottom sheets (use Material 3 `ModalBottomSheet` with the theme tokens), History and Settings screens, and a complete home header/hero layout. The supplied design system is not yet connected to app navigation or ViewModel state.

## Admin Panel (web)

The web gallery is plain HTML and CSS with custom properties, so it can be adapted to any frontend stack.

1. The design-system gallery lives in `admin/design-system-gallery/`. Open `index.html` to inspect the component markup. `components.css` imports `tokens.css`; link only `components.css` from a production app.
2. **Fonts:** the gallery loads Instrument Sans and JetBrains Mono from Google Fonts. Self-host them when required; fallbacks are defined in `--font-sans` and `--font-mono`.
3. **Markup:** use the class names as the basis for framework components, such as `<Badge tone="success">Published</Badge>` rendering `<span class="m-badge m-badge--success">Published</span>`.
4. **Behaviour in the gallery:** dialogs use native `<dialog>`, the toast demo uses an `aria-live` region, and the JSON editor validates with `JSON.parse`. The remaining controls are presentation examples. Connect buttons to real server-backed handlers before production use.
5. **Publishing workflow:** the four steps (Draft, Validate, Test, Publish) are sample labels only. Map them to the actual publishing pipeline.
6. **Code editor:** Script Studio uses a styled `<pre>`. For real editing, mount CodeMirror or Monaco inside `.m-editor` and theme it with `--surface`, `--text` and `--font-mono`.
7. **Responsive:** the sidebar becomes an off-canvas drawer under 1024px (toggle `data-nav-open` on `<body>`); tables scroll inside `.m-table-wrap`. Reduced-motion preferences are respected.
8. **Verify:** keyboard-tab through all controls and check at 375px, 768px and 1440px widths.

## Cloud admin prototype

`admin/cloud-prototype/` contains a separate clickable concept for the Mahi Repair Cloud owner panel and a redesign brief for Claude Code. It is a standalone prototype with sample data, not a production admin console and not connected to Supabase or the Android app. Do not treat its sample success/publish interactions as real publishing operations.

## Brand link

The Android components and web tokens share the red accent (`#E5484D` for text and icons, `#D7383F` behind white button text), matching success/warning/danger hues, and step-state vocabulary. This keeps the phone UI and admin panel visually consistent without forcing either to share runtime code.
