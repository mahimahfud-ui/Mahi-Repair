# Implementation Guide — Mahi Repair Premium UI

This guide explains how to apply the design in the existing Mahi Repair application **without changing its backend, APIs, authentication, Supabase configuration, signing or repair logic.**

Scope: visual layer and customer-facing UI structure only.

## 1. Source context

The public repository (`mahimahfud-ui/Mahi-Repair`) describes:

- An Android-oriented repair toolkit with an allowlisted native action engine.
- Five bundled repair tools: YouTube Quick Repair, Facebook Troubleshooter, DNS Repair & Diagnostics, Wi-Fi Troubleshooter, Play Store Repair.
- Signed repair recipes, verified in the client.
- Invited-member email OTP sign-in and an offline emergency mode.
- Real-time verdicts and an execution audit history.

The repository also contains an `admin-panel/` directory and an owner Test Lab. **Both are excluded from this design.** Do not add admin entry points to the customer app.

Note: this design was prepared from the repository README and public metadata. Verify component names and data shapes against the source before implementing.

## 2. Prototype-only mock behaviour (do NOT port)

The following exist only in the prototype and must be replaced by real logic:

| Prototype behaviour | Real app equivalent to keep |
|---|---|
| `DEVICE` object (sample "Pixel 8") | Real device info from the app's existing device-info source |
| `TOOLS` array with hard-coded steps | Tool and recipe definitions from the existing signed recipe source |
| `HISTORY` and `LOGS` | Existing audit-history and execution-log data |
| `simulate()` timed step animation | The real repair engine's step events (verdicts, progress) |
| Confirmation sheet that only shows a demo message | The app's existing `ASK_USER_CONFIRMATION` flow |
| Email validation with a demo toast | Existing invited-email OTP request and verification |
| Offline mode badge "Ready" | Actual offline/cache state from existing logic |
| Settings toggles held in memory | Persist using the app's existing preference storage |

## 3. Design tokens

Source: `design-system/tokens.css`.

- Map `--bg-base`, `--surface-*` and `--text-*` to the app's theme provider. The base is deep navy (`#060A14`) with navy surfaces; the app should stay dark-first.
- Primary interactive colour is blue (`--accent` `#5B8CFF`). Use the blue-to-red brand gradient only on the brand mark and at most one hero highlight per screen.
- Red (`--red` `#FF4D5E`) is reserved for failures, destructive actions and emergency signal. Do not use red for decoration.
- Status colours: success `#4FD69C`, warning `#F2B866`, danger `#FF5A6A`, running uses the blue accent.
- Type: Inter (or the system font fallback). Use tabular numerals for all device and diagnostic values.
- Spacing: 4pt grid (4, 8, 12, 16, 20, 24, 32, 40).
- Radius: 10 (small), 16 (medium), 22 (large), pill for badges.
- Motion (iOS-style): screen push 480 ms with `cubic-bezier(.32,.72,0,1)`, content rises with a spring curve `cubic-bezier(.22,1,.36,1)` 60 ms after the push, sheets 560 ms on the iOS curve, button press release on the spring curve. Animate only on navigation, never on progress updates. Respect the OS reduced-motion setting in every animated component.

## 4. Breakpoints and layout

- Mobile: below 640 px. Single column, bottom navigation with four items (Home, Tools, History, Settings).
- Tablet: 640–1023 px. Two-column tool grid, bottom navigation retained.
- Desktop: 1024 px and above. Fixed left sidebar navigation, two-column dashboard, three-column tool grid.
- Touch targets: minimum 44 × 44 pt on mobile.
- Respect safe-area insets on the bottom navigation and sheets.

## 5. Screen inventory

1. **Home dashboard**: connected-device hero (identity, connection status, battery and storage), recommended action card, three featured tools, recent activity.
2. **Tools library**: list of all bundled tools as cards with description and step count.
3. **Tool detail**: overview, step timeline with pending, active and completed states, status panel, primary action.
4. **Confirmation sheet**: bottom sheet on mobile, centred dialog on desktop. Confirm and cancel actions.
5. **Run progress**: step timeline advancing, linear progress bar, live status text.
6. **Result**: success state with verdict and log excerpt. Failure and attention-required variants use the same structure with warning or danger colours.
7. **History**: segmented filter (All, Passed, Attention, Failed), list of runs, empty state.
8. **Settings**: notification and motion preferences, offline mode status, invited-email sign-in entry.

Not designed here (add later if the real app supports them): splash screen, onboarding carousel, device connection wizard, system permission explainers. Validate each against the existing app before adding.

## 6. Component specifications

- **Button**: primary (accent fill, dark text), ghost (surface fill, border), small variant for inline actions. Heights 48 (default) and 36 (small). Disabled at 40% opacity. Press feedback scales to 98%.
- **Card**: gradient surface (surface-2 to surface-1), 1 px subtle border, 22 px radius, 18 px padding. Interactive cards lift 1 px on hover and scale on press.
- **Badge**: pill with dot. Variants: idle, ok, warn, bad, run.
- **Progress ring and bar**: bar for linear progress; ring for battery or health summaries.
- **Step timeline**: numbered circles connected by a vertical line. States: pending, active (accent halo), done (success check).
- **Segmented control**: 36 px tall, selected segment raised on surface-3.
- **Text field**: 48 px height, accent focus ring, inline error text in danger colour below the field.
- **Sheet**: 26 px top radius, grab handle, scrim with blur. Dismiss on scrim tap or Escape.
- **Toast**: bottom-centred, auto-dismisses after 2.2 s, `role="status"`.
- **Empty state**: icon orb, heading, one-line explanation.
- **Skeleton**: shimmer gradient, use for loading lists and device info.

## 7. Interaction rules

- Every repair action requires explicit confirmation through the existing confirmation flow before it runs.
- Show failure and "needs your attention" states with clear next steps, not only a red badge.
- Never hide the primary action below the fold on mobile.
- Disabled buttons must include a reason in nearby text where it is not obvious.
- Honour reduced motion: remove step-transition animation and shimmer.

## 8. Accessibility

- Contrast: primary text on surfaces exceeds WCAG AA. Verify secondary text (`--text-tertiary`) on the actual app background before shipping, and lighten it if needed.
- Focus: visible 2 px accent focus ring on all interactive elements.
- Semantics: use `role="dialog"` and `aria-modal` for sheets; `aria-live` for status updates; `aria-current` for active navigation.
- Do not convey status by colour alone; always pair with text or icon.

## 9. Assets

- Icons: outline style, 24 px grid, 1.8 stroke. Use the app's existing icon set if one exists; otherwise add the navigation icons used in the prototype (home, tools, history, settings).
- Fonts: Inter (or equivalent), JetBrains Mono (or equivalent) for numeric and log text. Confirm licensing before bundling.
- Tool badges: two-to-three letter monogram placeholders (YT, FB, DNS, Wi, PS). Replace with official brand marks only if permitted.
- Do not add third-party brand logos without confirming usage rights.

## 10. Implementation order

1. Apply tokens to the theme.
2. Build shared components (button, card, badge, step timeline, sheet, toast).
3. Implement navigation shell (bottom bar on mobile, sidebar on desktop).
4. Implement Home and Tools screens using live data.
5. Implement Tool detail, confirmation and run progress wired to the real engine events.
6. Implement History and Settings.
7. Run accessibility and reduced-motion checks, then compare against the prototype at mobile, tablet and desktop widths.

## 11. Verification checklist

- [ ] No admin route, link or entry point exists in the customer app.
- [ ] No backend, auth, Supabase or signing code was modified.
- [ ] Every repair action goes through the existing confirmation step.
- [ ] Mock data is replaced by live sources.
- [ ] No horizontal overflow at 360 px width.
- [ ] Reduced-motion setting is respected.
- [ ] Text contrast meets WCAG AA.
