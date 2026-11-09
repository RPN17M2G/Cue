# ADR 0007 — Fixed palette themes

Status: accepted
Date: 2026-11-01

## Context

The first UI pass used one cheerful skin (cream background, coral accent) and
leaned on Material You dynamic colour for the rest. The review rejected the warm
cream/coral palette and asked for green/blue instead, then asked for the review
options to be selectable inside the app rather than baked in.

A palette is more than a handful of colours here: the round, glowing cheerful
screens and the tight, flat professional screens differ in corner radius, shadow
and title weight too. Dynamic colour cannot supply any of that, and it would
override whatever the user picked with wallpaper-derived hues.

## Decision

Ship four complete, hand-specified themes and let the user choose one in
**Settings → Appearance**. Each theme is a `CuePalette` (Mint, Lagoon, Navy,
Deep teal) that resolves to a light **and** a dark `ColorScheme`, plus shape,
shadow and type tokens. Mint and Lagoon are cheerful; Navy and Deep teal are
professional. Mint is the default.

- Palette and light/dark stay independent axes: `AppTheme` (system/light/dark)
  is the mode, `CuePalette` is the colour. All 4 × 3 combinations are supported.
- The choice persists as a string key in the existing Preferences DataStore
  (`cue_settings`); an unknown or missing value falls back to Mint, so no
  migration is needed.
- Dynamic colour (Material You) is removed. The dynamic entry points stay behind
  one function so the feature can return later.
- A palette change applies instantly through the settings flow — no restart and
  no activity recreation.
- The home-screen icon follows the palette through four activity aliases. They
  forward through a transparent `LauncherActivity` instead of targeting
  `MainActivity`: an alias that targets the real screen becomes the task's base,
  and disabling it to swap the icon would tear the running task down.
- All colour reaches screens through `MaterialTheme.colorScheme` and a small
  `LocalCueTokens`; no colour literals live outside `ui/theme/`.
- Dark schemes are written by hand per palette. Material's defaults desaturate
  greens and blues badly, and the dark background is tinted toward each
  palette's hue.

The full specification (hex tables, token values, screen-by-screen application
and the phased delivery plan) lives in `docs/UI-REDESIGN-PLAN.md`.

## Consequences

- Eight colour schemes and two shape/type personalities must be kept consistent.
  One `PaletteSpec` per palette, a contrast unit test over all eight schemes, and
  previews for every palette × mode keep drift bounded.
- Any future screen must be built from theme tokens; adding a colour literal to
  a screen is now a review failure.
- Wallpaper-based theming is gone until someone deliberately reintroduces it.
- The Settings picker renders real components in miniature, so the preview
  cannot diverge from the app.
- Users who preferred the old cream/coral look lose it; the trade is four
  coherent palettes in the requested green/blue family.
