# Cue — UI redesign & palette plan

Status: implemented. Phases A–E are merged on `dev` and verified on the SM-S911B;
Phase F (docs, screenshots, artefact cleanup) is in progress. A visual polish
pass (Rubik type scale, card gallery rows, hero glow, saved-song hero) is on
`feat/ui-polish`, verified on the API 31 emulator in light and dark.
Owner: Omer Gindi (`RPN17M2G`).
Related: `docs/PLAN.md` (design source of truth), `docs/ADR/0007-fixed-palette-themes.md`.

---

## 0. In one paragraph

The gallery becomes a three-tab **Timeline** list (Recent / Recordings / Music).
The song-details screen keeps the **compact field list (F1)** but opens with the
**large cover hero (F2)**, and every value on it can be fixed in place. The cream
and coral are gone: the app ships with **four green/blue palettes** — Mint,
Lagoon, Navy and Deep teal — selectable in **Settings → Appearance** and applied
to every screen, dialog and sheet, in both light and dark. Recognition quality
(OCR stitching, confidence tiers, artwork auto-apply) and delete-source land in
the same push, before the visual pass is declared done.

---

## 1. What was decided, and what changed

| Area | Decision |
|---|---|
| Home | Timeline (C): segmented pill tabs, date-grouped rows, wide thumbnails, source chip, extended Add FAB. |
| Song details | F1 structure (compact inline fields, collapsible clip/quality, sticky Create) with the F2 cover hero on top. |
| Skin | No longer one "cheerful" and one "professional". **Four complete themes** carry the personality. |
| Colour | Green/blue only. Cream, coral and warm beige are removed everywhere. |
| Theme scope | Every screen, sheet, dialog and empty state; light + dark. |
| Selection | User picks the palette in Settings. Default is **Mint**. |
| Dynamic colour | Removed (see D5). |

The two review artefacts stay in the repo as reference, not as code:
`design/ui-options.html` (the layout review) and `design/cue-palettes.html`
(the palette review), plus `design/cue-demo.html` (the interaction prototype).

---

## 2. The four themes

Each theme is a complete personality: colour **plus** corner radius, shadow
character and title weight. Two are "cheerful" in shape, two are "professional";
all four are green or blue.

### 2.1 Light

| | Mint | Lagoon | Navy | Deep teal |
|---|---|---|---|---|
| Character | cheerful | cheerful | professional | professional |
| background | `#F1FBF6` | `#EFF9FC` | `#F5F7FB` | `#F4F9F8` |
| surface | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| ink | `#0E2A20` | `#0C2530` | `#0D1A2B` | `#0A2024` |
| muted | `#567568` | `#547683` | `#5A6A80` | `#557078` |
| line | `#D8F0E4` | `#D5EBF2` | `#E3E9F1` | `#DCEAEA` |
| primary | `#0A7F5C` | `#067A8B` | `#1E3A8A` | `#0F766E` |
| on primary | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| primary container | `#E2F8EE` | `#DFF4FA` | `#E8EEFB` | `#E4F2F1` |
| accent | `#22D3EE` | `#6366F1` | `#2563EB` | `#0E7490` |
| hero gradient | `#5EEAD4 → #22C55E 48% → #0E7490` | `#7DD3FC → #22D3EE 46% → #4338CA` | `#1E3A8A → #2563EB 55% → #0B1220` | `#0F766E → #0E7490 50% → #082F49` |

Two light values were darkened from the review page after measuring them: the
muted ink and the cheerful primaries were below 4.5:1 as text. The hero
gradients keep the original bright stops, so the palette still reads the same;
only text and button fills use the darker primary. `PaletteContrastTest` locks
all eight schemes to WCAG AA.

### 2.2 Dark

| | Mint | Lagoon | Navy | Deep teal |
|---|---|---|---|---|
| background | `#0B1512` | `#081318` | `#0A101C` | `#07110F` |
| surface | `#12201B` | `#0F1F26` | `#111A29` | `#0E1B19` |
| ink | `#E6F5EE` | `#E3F3F9` | `#EAF0FA` | `#E6F3F1` |
| muted | `#8AA79B` | `#86A3AE` | `#8B9AB2` | `#86A29D` |
| line | `#1E312A` | `#1A2E36` | `#1E2A3C` | `#1A2B28` |
| primary | `#34D399` | `#22D3EE` | `#60A5FA` | `#2DD4BF` |
| on primary | `#04231A` | `#04222B` | `#061426` | `#03211D` |
| primary container | `#12302A` | `#0E2B33` | `#16233A` | `#102B27` |
| accent | `#38BDF8` | `#818CF8` | `#93C5FD` | `#38BDF8` |

Dark is a designed variant of each palette, not `darkColorScheme()` defaults:
the primary is lightened for contrast, the background is tinted toward the
palette's hue, and surfaces stay one step above the background.

### 2.3 Shape and type

| Token | Mint / Lagoon | Navy / Deep teal |
|---|---|---|
| card radius | 22 dp | 12 dp |
| thumbnail radius | 18 dp | 10 dp |
| button | pill (50%) | 12 dp |
| sheet top radius | 28 dp | 16 dp |
| elevation | tinted glow, 8–24 dp | flat, 0–2 dp + hairline |
| title weight | W800, tracking −0.03 em | W600, tracking −0.02 em |
| body weight | W500 | W400 |
| tab strip | filled pill with gradient | segmented, outlined |

These map onto Material 3 `Shapes` and a small `CueTokens` object; screens never
branch on the palette themselves.

---

## 3. Theme architecture

### 3.1 Types

```kotlin
// settings/SettingsRepository.kt
enum class CuePalette { MINT, LAGOON, NAVY, DEEP_TEAL }

data class AppSettings(
    …
    val theme: AppTheme = AppTheme.SYSTEM,      // light / dark / system (unchanged)
    val palette: CuePalette = CuePalette.MINT,  // new
)
```

`AppTheme` stays the **mode** axis (system/light/dark). `CuePalette` is the
**colour** axis. The two are independent: 4 × 3 = 12 combinations, all supported.

### 3.2 New files

| File | Contents |
|---|---|
| `ui/theme/Palettes.kt` | `data class PaletteSpec` (light + dark `ColorScheme`, hero gradient, glow colour) and the four instances. |
| `ui/theme/CueShapes.kt` | `cheerfulShapes` / `professionalShapes` as `Shapes`, plus `CueTokens` (thumbnail radius, glow elevation, title weight, hero gradient). |
| `ui/theme/Theme.kt` | `CueTheme(palette, mode)` — resolves dark, builds `MaterialTheme` + `CompositionLocalProvider(LocalCueTokens)`, keeps the existing system-bar icon logic. |

`Theme.kt` loses `dynamicLightColorScheme` / `dynamicDarkColorScheme`.

### 3.3 Mapping to Material 3

| Material slot | Source |
|---|---|
| `primary`, `onPrimary` | palette primary |
| `primaryContainer`, `onPrimaryContainer` | palette container / ink |
| `secondary`, `tertiary` | palette accent |
| `background`, `onBackground` | bg / ink |
| `surface`, `onSurface` | surface / ink |
| `surfaceVariant`, `onSurfaceVariant` | container / muted |
| `outlineVariant` | line |
| `error` | one shared red, tuned per mode |

Everything else (elevation tint, disabled alpha) comes from Material defaults.

### 3.4 Applying it

`MainActivity` already collects `settings`; it passes `palette` alongside `theme`
into `CueTheme`. Because the whole tree re-composes on the settings flow, a
palette change is **instant** — no restart, no activity recreation.

The splash screen keeps one neutral brand colour for every palette. Deriving it
per theme would need a static resource and would flash on change; a neutral ink
splash reads as intentional (D14).

### 3.5 Guardrails

- No `Color(0x…)` outside `ui/theme/`. The UI layer today already only uses
  `MaterialTheme.colorScheme`, so this stays true.
- `@Preview` for each palette × light/dark on the three key screens.
- A unit test asserts body-text contrast ≥ 4.5:1 and primary-on-background
  ≥ 3:1 for all 8 schemes.

---

## 4. Settings → Appearance

```
Appearance
  Palette
    [ Mint     ]  [ Lagoon   ]
    [ Navy     ]  [ Deep teal]
    Applies to every screen. Tap to preview instantly.
  Mode
    [ System ] [ Light ] [ Dark ]
```

- The four palette cards are a 2 × 2 grid. Each card is a miniature of the real
  details screen (hero gradient + three field bars + a pill), rendered with the
  **actual** components at 40 % scale, so the preview cannot drift from the app.
- Selected card: 2 dp primary ring + check badge. No separate "apply" button.
- Cards carry a content description (`"Mint theme"`), and the grid is focusable
  in reading order for TalkBack.
- New strings in `values/strings.xml` and `values-iw/strings.xml`:
  `settings_appearance`, `settings_palette`, `settings_palette_hint`,
  `palette_mint`, `palette_lagoon`, `palette_navy`, `palette_teal`.

The existing "Theme" section (system/light/dark chips) moves under Appearance and
keeps its strings.

---

## 5. Screen-by-screen application

### 5.1 Home — Timeline

- Header: `Cue` wordmark (weight per theme) + search + settings.
- Segmented pill tabs: `Recent` / `Recordings` / `Music`, with counts.
- Content: date labels (`Today`, `Yesterday`, `Earlier`), then rows.
- Row: 58 dp thumbnail (cover for MP3, first frame for video), title, `artist ·
  duration`, source chip (`From screen text`, `Fingerprint match`, `Not
  converted`), chevron.
- FAB: extended, `+ Add`, primary → opens the picker.
- Empty state per tab: short line + the Add action.
- Permission gate: if `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` are missing, the
  tab shows a single "Allow access" card instead of an empty list.

### 5.2 Song details

Top to bottom:

1. **Cover hero** (4:3, `heroGradient` when no artwork): title and artist over a
   scrim, `Change cover` pill top-right.
2. **Check-this banner** — only when a field is below the confidence bar:
   `Artist was read with low confidence. [Fix it]`.
3. **Fields card** — Title / Artist / Album / Year, each a row with label, value,
   pencil. A flagged field also shows a `Check this` chip. Tapping a row expands
   it into an inline editor (input, Save, Cancel, `Enter` / `Esc`).
4. **Match card** — `Matched 98% · iTunes`, the matched title, `Other matches`
   and `Search manually`.
5. **Clip & quality** — collapsed summary row (`0:12–3:41 · 3:29 · Best`) that
   expands to the trim slider, typed start/end fields and quality chips.
6. **Delete the source recording** toggle with its guardrail subtitle.
7. **Create MP3** sticky at the bottom, with a one-line consequence note.

### 5.3 Sheets

- **Cover**: four suggestions + `Search online` + `Pick from this device` +
  `Remove artwork`.
- **Other matches**: three candidates with cover, artist, album/year and a
  confidence badge coloured by tier (green ≥ 0.75, amber 0.55–0.75, muted
  below; decision D15 supersedes the earlier 80/60 split).
- **Manual search**: query field + result list, results update as you type.

All sheets: scrim, 28/16 dp top radius, grabber, max 90 % height, scrollable.

### 5.4 Processing / Saved / Error / Settings

- Processing: conic ring, percentage, stage steps (Prepare → Convert → Tag →
  Save), cancel.
- Saved: cover, title/artist/duration, `Music/Cue` path chip, Play / Share /
  Show folder / Edit tags, and the outcome line — `Source recording deleted` or
  `Source recording kept`.
- Error: icon, message, one retry action, "Open log" if available.
- Settings: Appearance, After processing, Identification, Advanced — each a
  card with the same radius and line tokens.

---

## 6. Manual fixing — the interaction contract

Derived from `design/cue-demo.html`, which is the reference behaviour.

| Trigger | Result |
|---|---|
| Tap a field row | Row expands into an editor; text selected; hero title/artist update live as you type. |
| `Enter` / `Save` | Value committed, flag cleared for that field, small confirmation. |
| `Esc` / `Cancel` | Editor collapses, nothing changes. |
| Tap `Fix it` on the banner | Opens the flagged field's editor, scrolls it to the centre, keeps the banner until save. |
| Tap the cover / `Change cover` | Cover sheet. |
| Pick artwork | Hero and thumbnails update immediately. |
| `Other matches` | Sheet with alternatives; choosing one overwrites title/artist/album/year and confidence. |
| `Search manually` | Sheet with a live-filtered result list; picking applies it. |
| Toggle delete-source | Consequence note under the Create button updates. |
| `Create MP3` | Processing → Saved, with the delete outcome stated. |

Confidence rules that drive the banner (implemented in Phase E):

- `≥ 0.75` — accepted silently.
- `0.55 – 0.75` — accepted **and** flagged with `Check this`.
- `< 0.55` — ignored; the field falls through to the lookup or stays empty.

---

## 7. Functional work folded into this push

These are the outstanding items from the earlier plan. They are sequenced with
the visual work because they touch the same screens.

1. **OCR temporal stitching** — replace the `frameCount / totalFrames` stability
   score (which penalises scrolling titles) with a per-candidate time-weighted
   vote across frames; keep the script-purity gate.
2. **Confidence tiers** — plumb a 0–1 score per field through
   `IdentificationCascade` into the details screen; drive the banner and chips.
3. **Artwork auto-apply** — on a confident match, fetch artwork through
   `ArtworkFetcher` and set it, unless the user already picked one.
4. **Delete-source** — after a successful save **and** tag read-back, delete the
   source video behind a system confirm dialog; videos only; drop the cached copy
   first; default ON; report "kept" when dismissed.
5. **Consolidated form** — one set of inputs, no duplicated search boxes,
   human-readable quality labels, typed trim values (`mm:ss` in, `mm:ss` out).
6. **Three-tab gallery** — MediaStore-backed lists, Cue output folder first in
   Music.
7. **Phase 6 hardening** — unit tests, README, docs, device verification.

---

## 8. Delivery plan

Ordered so each screen is written **once**, against tokens that already exist.

### Phase A — Theme foundation
- `CuePalette`, `PaletteSpec`, 8 schemes, `CueShapes`, `CueTokens`.
- `CueTheme(palette, mode)`; dynamic colour removed.
- DataStore key `palette` (Preferences, default `MINT` — no migration needed).
- Settings → Appearance with the 2 × 2 picker.
- Previews for 4 palettes × light/dark.

**Done when:** switching palette recolours every screen instantly, survives a
relaunch, and the contrast test passes.

### Phase B — Gallery home
- MediaStore queries for recordings and audio, Cue folder first.
- Timeline scaffold, tabs with counts, rows, chips, FAB, empty + permission states.
- Row tap navigates into the existing flow.

**Done when:** real files appear, tabs filter, tapping opens details.

### Phase C — Song details
- Cover hero + compact fields + inline editors + banner.
- Cover sheet, matches sheet, manual search sheet.
- Collapsible clip/quality with typed trim; quality labels.
- Delete-source toggle in the form.

**Done when:** the manual-fix contract in §6 works end to end on device.

### Phase D — Processing, Saved, delete-source
- Ring/steps/cancel, Saved screen, outcome line.
- Delete-source guardrails + Settings section.

**Done when:** a real conversion deletes the source exactly once, behind a
confirmation, and reports the outcome.

### Phase E — Identification quality
- OCR stitching, confidence tiers, artwork auto-apply.
- Fingerprint duration + centred slice (already written, uncommitted) and the
  rejected-key UX.

**Done when:** a text-free clip is identified by fingerprint, a scrolling title
is read correctly, and gibberish no longer reaches the form.

### Phase F — Hardening
- Unit tests: palettes/contrast, trim parsing, stitching, delete-source rules.
- README + docs + screenshots; `scripts/device-restore.ps1` run; test artefacts
  removed.

**Done when:** `testDebugUnitTest lintDebug assembleDebug` is green and the phone
is back to normal settings.

---

## 9. Testing & verification

| Layer | What |
|---|---|
| Unit | Palette → `ColorScheme` mapping and contrast; `CuePalette` persistence; trim parse/format; OCR stitching votes; delete-source decision table. |
| Lint | `lintDebug` clean; no `Color(0x…)` outside `ui/theme`. |
| Device | 4 palettes × light/dark spot-checked on the SM-S911B; Hebrew + RTL pass on at least Mint and Navy. |
| Manual | The §6 table, executed once per theme on the details screen. |

Screenshots for the README are captured from the device, not from the HTML
mockups.

---

## 10. Risks

| Risk | Mitigation |
|---|---|
| Muted text fails contrast on the tinted cheerful backgrounds. | Contrast unit test over all 8 schemes; darken `muted` until ≥ 4.5:1. |
| Status/nav bar icons become unreadable. | Already derived from the resolved dark flag; verify per theme. |
| Eight schemes drift apart over time. | One `PaletteSpec` per palette, no per-screen colour literals. |
| Cheerful gradients look muddy on OLED. | Hero gradient is a token; check on device in dark mode. |
| Theme switch flickers mid-navigation. | Palette is read from the same settings flow as mode; no local state. |
| Scope creep across four palettes. | Shape/type differences are tokens, not branches; no per-palette layouts. |

---

## 11. Decisions

**D1 — Timeline (C) is the home.**
Rows scan faster than a grid, the source chip explains where a title came from,
and it leaves room for the three tabs. Rejected: 3-column grid (A, denser but
weaker provenance), hero + cards (B, least information).

**D2 — Details = F1 structure + F2 hero.**
The compact field list keeps the whole form within one screen; the large cover
gives the screen its identity. Rejected: pure F2 (too much scrolling), pure F1
(small cover, weaker first impression).

**D3 — Four palettes ship as themes, not one fixed palette.**
The user asked for the choice to live in the app. It also lets the app suit
different moods without a rebuild. Cost: eight schemes to maintain.

**D4 — Palette and light/dark are independent axes.**
`AppTheme` (system/light/dark) already exists and is orthogonal; folding them
into one enum would double the list and forbid "Mint in dark".

**D5 — Dynamic colour (Material You) is removed.**
The redesign's identity is the palette; wallpaper-derived colour would override
it and would not supply the shape/type tokens. Rejected: keep it as a fifth
option (breaks the shape story), keep it only in light mode (inconsistent).
The dynamic path stays behind a single function so it can return later.

**D6 — Shape, shadow and type weight are part of the palette.**
Mint/Lagoon are round and heavy; Navy/Deep teal are tight and flat. Two palettes
therefore read as "cheerful" and two as "professional" without a separate
setting. Rejected: a separate "style" toggle (12 more combinations, more drift).

**D7 — Default palette is Mint.**
Closest to the approved cheerful direction, best contrast of the four.

**D8 — Theme changes apply instantly.**
Settings flow → `CueTheme`; no restart, no activity recreation. Rejected: apply
on next launch (cheap, but feels broken).

**D9 — Palette lives in Preferences DataStore as a string key.**
`cue_settings` already uses Preferences; a new key with a default needs no
migration and no proto change. Unknown values fall back to `MINT`.

**D10 — All colour comes from theme tokens.**
Screens read `MaterialTheme.colorScheme` and `LocalCueTokens`; no literals in
the UI layer. This is already true today and must stay true.

**D11 — The hero gradient is a theme token, not per-song art.**
Placeholder artwork should look like the app, not like random art. Real artwork,
when found, replaces it.

**D12 — The Settings picker previews with real components.**
A miniature of the details screen built from the same composables, so the
preview cannot lie. Rejected: static swatch images (drift, maintenance).

**D13 — Dark schemes are hand-specified per palette.**
Material's defaults desaturate greens and blues badly. Each dark scheme is tuned
for contrast and hue.

**D14 — One neutral splash for all palettes.**
A per-theme splash needs a static drawable and flashes on change; a neutral ink
splash reads as intentional and costs nothing.

**D15 — Confidence tiers drive the UI, not the reverse.**
`0.75 / 0.55` thresholds live in the domain; the screen only renders the flag.
Rejected: thresholds in the UI layer (untestable, duplicated).

**D16 — Delete-source is ON by default, with guardrails.**
Only after a successful save and a tag read-back, videos only, always behind the
system confirm dialog, cached copy dropped first, and a "kept" message when
dismissed.

---

## 12. Open questions

1. Order of the four cards — Mint, Lagoon, Navy, Deep teal (cheerful first) or
   alphabetical? *Proposed: cheerful first.*
2. Should the picker sit at the top of Appearance with a live sample screen
   behind a "Preview" tap, or is the 2 × 2 card grid enough? *Proposed: grid
   only; the cards already preview.*
3. Do we want the timeline to remember the last used tab across launches?
   *Proposed: no, always start on Recent.*
4. Is a fifth "system accent" option wanted later? *Deferred, see D5.*
