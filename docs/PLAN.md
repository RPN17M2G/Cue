# Cue — Design Plan

Status: agreed design; Phase 1 walking skeleton, Phase 2 recognition, Phase 3
fingerprint fallback, Phase 4 MP3 tag editing & trimming, and Phase 5 bilingual
UI, settings & polish implemented.
Owner: Omer Gindi (`RPN17M2G`).
App id: `io.github.rpn17m2g.cue`.

Cue turns an MP4 screen recording of the YouTube Music interface (or any other UI)
into a metadata-tagged MP3. It extracts the song details from the video, does a
best-effort online lookup to fill the ID3 tags, converts the audio, lets you trim
the track, and lets you edit the details. It can also edit the tags of any
existing MP3 file.

This document is the single source of truth for the design. Decisions are
recorded here; the notable architectural choices are expanded in `docs/ADR/`.

---

## 1. Scope

### In scope (v1)
- Pick a video (any container FFmpeg can demux) via the system file picker or the
  Android share sheet.
- Extract song details from the video's frames (OCR) and/or its audio
  (fingerprint), enrich them with online lookups, and let the user review/edit.
- Convert the video's audio to MP3 at a user-chosen quality, with start/end
  trimming in a single pass.
- Write ID3v2.3 tags, including embedded cover art.
- Edit the tags of any existing MP3, in place.
- Trim an existing MP3 to a new file.
- Bilingual UI (English + Hebrew) with full RTL support.

### Out of scope (v1)
- Audio "enhancement" (loudness normalization, noise reduction, EQ, AI upscaling).
  Deferred; the FFmpeg build keeps the door open.
- Multi-song recordings (one input = one song; trim to the song you want).
- Full waveform editor, fade in/out, multi-segment splitting.
- Processing queue / batch processing (one job at a time).
- In-app screen recorder.
- Persistent library/history of processed files.
- Cloud recognition services (ACRCloud, AudD).
- Self-hosted backend.
- Localization beyond English and Hebrew.
- CI pipeline.

### Distribution
Undecided. The design keeps both a personal (sideload) path and a future Google
Play path open: the native FFmpeg build is LGPL-only (no GPL components), and no
proprietary SDKs are used.

---

## 2. Platform & identity

| Item | Value |
|---|---|
| Min SDK | 31 (Android 12) |
| Compile / target SDK | 35 (36 deferred until the SDK is installed) |
| Language | Kotlin 2.x |
| UI | Jetpack Compose, Material 3 Expressive |
| Module layout | Single `:app` module, package-by-feature |
| Application id | `io.github.rpn17m2g.cue` |
| Display name | Cue |
| Native ABIs | `arm64-v8a`, `x86_64` |

---

## 3. Architecture at a glance

```
Input (picker / share)
        │
        ▼
   Type probe  ── video ──►  Frame extraction (first 15 s)
        │                        │
        │                        ▼
        │                   OCR: ML Kit (Latin) → Tesseract (Hebrew)
        │                        │
        │                        ▼
        │            Metadata resolver: iTunes → MusicBrainz
        │                        │  (on failure)
        │                        ▼
        │            Fingerprint: Chromaprint → AcoustID → MusicBrainz/CAA
        │                        │  (on failure)
        │                        ▼
        │                  Manual entry / search
        │
        └──── audio (MP3) ──► mp3agic read tags
                                 │ (complete) ──► Review
                                 │ (incomplete) ► resolver / fingerprint / manual
        ▼
   Review screen (editable fields, artwork, trim, quality)
        ▼
   Processing (foreground service)
     ├─ FFmpeg: demux + decode + trim + encode MP3 (+ chromaprint on demand)
     └─ mp3agic: write ID3v2.3 tags + cover art
        ▼
   Save to Music/Cue via MediaStore  →  Result screen
```

No history database is kept. There is no Room. Transient state lives in the
foreground service and the UI layer; settings live in DataStore.

---

## 4. Input handling

- **Entry point:** one "Add file" action on Home, plus the Android share sheet.
  The share filter accepts `video/*` and `audio/*`.
- **Type detection:** MIME type plus a probe. Videos go to the conversion flow;
  MP3 files go to the tag-editing flow. Unsupported files produce a clear
  unsupported-format error.
- **Video containers:** MP4/MOV, Matroska/WebM, and OGG (Opus/Vorbis). The
  minimal native build enables exactly these demuxers/decoders; other containers
  require a build change. Screen recordings are normally MP4. A video with no
  audio track is an error state.
- **Audio:** MP3 only for tag editing.
- **Sources are never modified** by the conversion flow.
- **One job at a time.** Picking a new file while processing is not allowed.

---

## 5. Identification

The cascade, in order. Each stage may be skipped when it cannot help.

1. **Embedded tags (MP3 only).** Read with mp3agic. If title, artist, and album
   are all present, skip the online lookup and go straight to Review (the online
   search stays one tap away). Otherwise the tags seed the lookup.
2. **OCR (video only).** Extract frames from the **first 15 seconds** (configurable
   5–120 s) at one frame every **2 seconds** (configurable). Run **ML Kit Text
   Recognition v2** (Latin). If the app language is Hebrew, or no plausible title
   emerges, also run a **Tesseract4Android** pass with Hebrew traineddata on the
   same frames. Aggregate candidates across frames by frequency and stability,
   filtering status-bar/clock noise.
3. **Online lookup.** Search **iTunes Search API** first (no key, 600×600 artwork,
   strong mainstream coverage), then **MusicBrainz** (User-Agent required, no
   artwork; artwork via Cover Art Archive). Auto-select the top match, list the
   alternatives, and allow manual search. Requires network and the network toggle.
4. **Fingerprint fallback.** Compute a Chromaprint fingerprint from a **~30 s
   slice starting ~20 s in** (or the best available slice for short clips), match
   via **AcoustID** (free API key), then resolve metadata via MusicBrainz/Cover
   Art Archive. Requires network, the fingerprint toggle, and a configured key.
5. **Manual entry.** Always available; never blocked.

**Provider pacing.** Every request to MusicBrainz is spaced at least 1 s apart
(their published limit). AcoustID publishes a 3-requests-per-second limit, but
Cue keeps a full 20 s between fingerprint lookups — one lookup happens per
processed file, so three requests per minute is plenty and leaves a wide margin.
Both limits are enforced by an OkHttp interceptor, so retries count too;
AcoustID lookups are POSTs and are never replayed automatically.

ML Kit v2 does **not** support the Hebrew script; that is why Tesseract is the
second pass. See `docs/ADR/0003-dual-ocr-mlkit-tesseract.md`.

**Offline / no-match behaviour:** the flow never blocks. The Review screen shows
the raw OCR text, every field is editable, and a "Look up online" button retries
the lookup later.

**No auto-split.** One recording is treated as one song; the user trims to the
song they want.

---

## 6. Review screen

- Editable fields: title, artist, album, album artist, year, genre, track number.
- **Source badges** per field (OCR / iTunes / MusicBrainz / fingerprint / manual)
  and a collapsible "details" panel exposing the raw OCR text and the chosen
  match.
- **Multiple matches:** the top match is pre-filled; an expandable "Other
  matches" list and a manual search field allow correction.
- **Artwork:** automatic (iTunes → Cover Art Archive) with a manual fallback —
  an iTunes-based cover grid (search any term, pick a result) and a gallery
  picker for local images.
- **Trim:** start/end handles. Over a **video preview** (Media3) for recordings,
  over an **audio preview** for MP3 inputs. Trim only; no waveform, no fades.
- **Quality:** preset chips — **V0 (~245 kbps)**, **V2 (~190 kbps)**,
  **CBR 256**, **CBR 192** — each annotated with a live estimated output size
  computed from the selected trim duration. Hidden for tag-only edits (no
  re-encode). The default chip comes from Settings.
- Hebrew/Unicode text is fully supported in every field.

---

## 7. Processing

- Runs in a **foreground service** so it survives backgrounding and screen-off.
  Service type `mediaProcessing` on API 35+, `dataSync` on 31–34.
- Screen shows a **stage checklist** (Convert → Tag → Save) with a determinate
  percentage during FFmpeg conversion (parsed from FFmpeg stderr), plus a cancel
  action mirrored in the notification.
- Temp output is written to the app cache; the final file is published to
  `MediaStore` with `IS_PENDING` set, then cleared.
- **Output:** `Music/Cue/`, filename from the template (default
  `{artist} - {title}`; tokens `artist`, `title`, `album`, `track`, `year`,
  sanitized for illegal filename characters). On a name collision, the app
  appends `" (2)"` and mentions the rename on the result screen. Originals are
  untouched.

### Tag writing
- **mp3agic** handles all ID3 reads and writes (text and artwork). New tags are
  ID3v2.3; existing ID3v2.4 tags stay v2.4 and ID3v2.2 is rejected. See
  `docs/ADR/0002-id3-writing-with-mp3agic.md`.
- Text fields (including Hebrew) are written as **UTF-16**.
- Cover art is fetched at 600×600, downscaled to ≤1000 px JPEG, embedded as a
  front-cover APIC frame. Replacing it preserves non-front APIC pictures.
- **Tag-only edits** are written **in place** to the selected MP3. The SAF adapter
  copies the document to the cache, edits and verifies that local copy, then
  writes it back through `ContentResolver`. Any change to the audio (trim,
  conversion) produces a **new file** in `Music/Cue`.

---

## 8. Screens

| Screen | Contents |
|---|---|
| **Launch** | Requests `POST_NOTIFICATIONS` immediately (no onboarding screen). |
| **Home** | One "Add file" action + Settings. |
| **Review** | Metadata fields, source badges + details, artwork, trim handles, quality chips. |
| **Processing** | Stage checklist + percentage, cancel. |
| **Result** | Saved path, play, Open with / Share, Show in folder, Edit tags, Process another. |
| **Error** | Full-screen, plain-language reason, Retry / Back, expandable technical details. |
| **Settings** | See below. |
| **Artwork picker** | iTunes cover grid + gallery picker. |

### Settings
- OCR scan-window length (default 15 s; range 5–120 s)
- Default MP3 quality (default V0)
- Output folder (default `Music/Cue`)
- Network lookups (default on)
- Fingerprint matching (default on when a key is present)
- AcoustID API key (default from the `acoustid.apiKey` property in
  `local.properties`; overridable here)
- Language (System / English / עברית)
- Theme (System / Light / Dark)
- Advanced: filename template, OCR frame interval (default 2 s), HTTP timeout
  (default 10 s) and retries (default 2)

---

## 9. Native components

- A **minimal FFmpeg** build (AAC/MP3/Opus/Vorbis decode, MP3 encode via
  libmp3lame, trim, metadata) with **`--enable-chromaprint`**, produced per ABI
  by a committed build script (ffmpeg-android-maker style, run via WSL with the
  NDK). FFmpeg, LAME, and Chromaprint are built as **shared libraries** so the
  LGPL components remain replaceable. Compiled `.so` files are **not** committed.
- Shipped as `libffmpeg.so` plus its `libav*.so`, `libchromaprint.so`, and
  `libmp3lame.so` dependencies, executed via `Process`; fingerprints are emitted
  with `-f chromaprint`. mp3agic is pure Java. See
  `docs/ADR/0001-native-ffmpeg-via-process.md`.
- LGPL-only configuration; GPL components disabled. License notices ship in
  `app/src/main/assets/licenses/`. See `docs/ADR/0005-lgpl-compliance.md`.

### Permissions
`INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PROCESSING`, `FOREGROUND_SERVICE_DATA_SYNC`.
No storage permissions (SAF + MediaStore).

---

## 10. Libraries

| Concern | Choice |
|---|---|
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| Settings | DataStore (Preferences) |
| Media preview / playback | Media3 (ExoPlayer) |
| Navigation | Navigation Compose |
| DI | Hand-rolled `AppContainer` (swappable with fakes in tests) |
| ID3 read/write | mp3agic |
| OCR (Latin) | ML Kit Text Recognition v2 |
| OCR (Hebrew) | Tesseract4Android + `heb.traineddata` |
| Fingerprint | Chromaprint (inside the FFmpeg build) + AcoustID HTTP |

---

## 11. Testing

- **Unit tests** for pure logic: OCR text heuristics, filename sanitizer, size
  estimator, tag field mapping, metadata merge, template rendering.
- **Instrumentation tests** on an x86_64 emulator for the end-to-end pipeline
  (convert + tag write + in-place tag rewrite) using tiny **synthetic fixtures**
  generated for tests, so runs stay fast.
- **Real samples:** you record 2–3 short YouTube Music clips (one English UI, one
  Hebrew UI) into a **gitignored** `samples/` folder for local OCR tuning.
- **Devices:** a physical Android 12+ phone (real codec/OCR performance, Hebrew
  UI checks) plus an x86_64 emulator (instrumentation).
- **CI:** local only for now.

---

## 12. Risks & mitigations

| Risk | Mitigation |
|---|---|
| FFmpeg + chromaprint cross-compile is fiddly on Windows | Committed script + Docker/WSL; one-time setup |
| ML Kit cannot read Hebrew | Tesseract second pass; fingerprint/manual fallbacks |
| OCR heuristics need real data | Real samples + frequency/stability aggregation |
| iTunes rate limits / MusicBrainz User-Agent rules | Backoff, retries, compliant User-Agent, 1 req/s MusicBrainz pacing |
| AcoustID rate limit (3 req/s published) | 20 s between lookups (3/min); POSTs are never auto-replayed |
| AcoustID needs a key | Settings field + `local.properties` default; feature disabled gracefully without it |
| Emulator differs from hardware | Final validation on the physical phone |
| Transcode generation loss | Quality presets default high (V0); never re-encode for tag-only edits |

---

## 13. Related documents
- `docs/PHASES.md` — implementation phases.
- `docs/STANDARDS.md` — coding and project standards.
- `docs/ADR/` — architecture decision records.
