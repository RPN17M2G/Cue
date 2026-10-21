# Cue — Implementation Phases

The build is staged so the hardest, riskiest part (the native FFmpeg pipeline)
is proven first with a **walking skeleton**. Each phase ends in something that
runs on a device and can be demonstrated. Later phases layer capability on top of
a working pipeline rather than replacing it.

Each phase lists: goal, deliverables, exit criteria (definition of done).

---

## Phase 0 — Tooling & project setup

**Goal:** a buildable Android project on the agreed stack, with the native build
script ready.

**Deliverables**
- Gradle project: Kotlin 2.x, AGP, `:app` module, Compose, Material 3 Expressive.
- `applicationId io.github.rpn17m2g.cue`, minSdk 31, target/compile 35 (36 deferred until the SDK is installed).
- Package-by-feature skeleton (`ui`, `ocr`, `metadata`, `audio`, `tags`, `data`,
  `settings`).
- `AppContainer` for DI; DataStore wired for settings; Navigation Compose graph
  with placeholder screens (Home, Review, Processing, Result, Error, Settings).
- Native build script (ffmpeg-android-maker style) building a minimal FFmpeg with
  `--enable-chromaprint` for `arm64-v8a` + `x86_64`; `.so` files gitignored;
  `libffmpeg.so` executed via `Process`.
- Dependencies wired: Retrofit/OkHttp/kotlinx.serialization, Media3, mp3agic,
  ML Kit Text v2, Tesseract4Android.
- Manifest permissions declared.

**Exit criteria**
- App builds and installs on the physical phone and the emulator.
- A trivial `ffmpeg -version` invoked from the app succeeds on both ABIs.
- Lint/format tasks run clean on the skeleton.

---

## Phase 1 — Walking skeleton

**Goal:** pick an MP4 and produce a tagged, trimmed MP3 through the real pipeline,
with manual tag entry only.

**Deliverables**
- Home "Add file" → system picker (video) → type probe.
- Review screen with editable fields (manual entry), trim handles over a Media3
  video preview, and quality chips with live size estimate.
- Foreground service: stage checklist + percentage (parsed from FFmpeg stderr),
  cancel, notification.
- FFmpeg: demux + decode + `-ss`/`-to` trim + libmp3lame encode at the chosen
  preset.
- mp3agic: write ID3v2.3 tags; embed artwork if one is set.
- Save to `Music/Cue` via MediaStore (`IS_PENDING`), template naming, duplicate
  `" (2)"` handling.
- Result screen (path, play, Open with / Share, Show in folder, Process another).

**Exit criteria**
- A real screen recording on the phone becomes a playable, correctly tagged MP3
  in `Music/Cue`.
- Trim, quality presets, and size estimates behave correctly on a long recording.
- Backgrounding/locking the screen during conversion completes successfully.
- Duplicate filename produces `" (2)"`.

---

## Phase 2 — Recognition (OCR + online lookup)

**Goal:** fill the tags automatically from the video.

**Deliverables**
- Frame extraction (MediaMetadataRetriever) over the first 15 s at a 2 s interval
  (both configurable).
- ML Kit Text Recognition v2 pass; Tesseract4Android Hebrew second pass when the
  app language is Hebrew or no plausible title emerges.
- Candidate aggregation (frequency/stability, status-bar filtering).
- Metadata resolver: iTunes Search API (primary) → MusicBrainz (secondary),
  behind one interface; compliant User-Agent; timeout/retry/backoff.
- Review-screen enrichment: auto-picked top match, "Other matches" list, manual
  search, per-field source badges, collapsible raw OCR/details panel.
- Offline / no-match behaviour: continue to Review with OCR text + "Look up
  online" retry.

**Exit criteria**
- An English-UI recording and a Hebrew-UI recording both auto-fill the correct
  song on the phone.
- Airplane mode still reaches Review with editable OCR text and a working retry
  when the network returns.
- Multiple-match cases allow correcting the auto-pick.

---

## Phase 3 — Fingerprint fallback

**Goal:** identify songs when OCR fails.

**Deliverables**
- Chromaprint fingerprint from a ~30 s slice starting ~20 s in (best available for
  short clips), emitted via `ffmpeg -f chromaprint`.
- AcoustID match → MusicBrainz/Cover Art Archive resolution.
- AcoustID key handling: Settings field + `local.properties` default; graceful
  skip and hint when no key or network.
- Cascade wiring: OCR → resolver → fingerprint → manual.

**Exit criteria**
- A recording with no readable title is identified via fingerprint on the phone.
- Removing the key disables fingerprinting cleanly with a visible hint.

---

## Phase 4 — MP3 tag editing & trimming

**Goal:** make any MP3 a first-class citizen.

**Deliverables**
- Pick/share an MP3 → mp3agic reads tags.
- Complete title+artist+album → straight to Review; otherwise look up / fingerprint
  / manual.
- In-place tag edits via SAF write access; audio changes (trim) produce a new file
  in `Music/Cue`.
- Audio-only preview with trim handles.
- Artwork pickers: iTunes-based cover grid (search any term) + gallery picker.
- Result-screen "Edit tags" reopens the editor for the just-saved file.

**Exit criteria**
- Editing the tags of an arbitrary MP3 updates the original file in place and the
  changes are visible in another player.
- Trimming an MP3 produces a new, correct file and leaves the original intact.
- Hebrew tags round-trip correctly.

---

## Phase 5 — Bilingual UI, settings & polish

**Goal:** the "beautiful, modern" finish.

**Deliverables**
- Full EN + HE string resources (Hebrew AI-drafted, user-reviewed); in-app
  Language setting (System / English / עברית); complete RTL mirroring.
- Theme setting (System / Light / Dark); Material 3 Expressive dynamic color.
- Complete Settings screen, including advanced options (filename template, OCR
  interval, timeouts).
- Curated motion: shared-element cover art, animated stage checklist, staggered
  lists, RTL-aware transitions, springy buttons.
- Custom waveform/play adaptive icon + monochrome themed icon + splash screen.
- Full-screen error states with expandable technical details.
- Launch-time `POST_NOTIFICATIONS` request.

**Exit criteria**
- Switching to Hebrew mirrors the entire UI correctly, including transitions.
- All settings persist and take effect.
- No untranslated hardcoded strings remain.

---

## Phase 6 — Hardening & documentation

**Goal:** robustness and reproducibility.

**Deliverables**
- Instrumentation tests: pipeline end-to-end and tag-write/in-place-rewrite with
  synthetic fixtures.
- Unit tests for all pure logic (heuristics, sanitizer, size estimator, mapping,
  merge, template).
- Edge cases: video with no audio track, unreadable/corrupt files, very large
  files, unusual codecs, cancel mid-conversion (temp cleanup), storage failures.
- Performance pass (memory, ANRs, progress accuracy).
- README and docs complete; `docs/PLAN.md` reconciled with what was built.

**Exit criteria**
- The agreed test suite passes on the emulator; core flows pass manual regression
  on the phone.
- A fresh clone can build and run the app by following the README (including the
  native build step).

---

## Deferred (post-v1 candidates)
- Loudness normalization (FFmpeg `loudnorm`) and other enhancement DSP.
- Multi-song detection/splitting; waveform editor; fades.
- Processing queue / batch.
- Optional history/library.
- CI pipeline.
- Store release activities (if the undecided distribution question resolves to
  "publish").
