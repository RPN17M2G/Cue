# Cue — Standards

Coding, project, and process standards for this repository. These are
conventions, not suggestions; deviations should be justified in the PR/commit or
in an ADR.

---

## 1. Kotlin style

- Follow the official Kotlin coding conventions (4-space indent, trailing commas,
  120-column soft limit).
- Formatting is enforced by **ktlint** (`./gradlew ktlintFormat`); static analysis
  by **detekt** where useful.
- Prefer `val` over `var`; prefer immutable data; avoid platform types leaking
  from Java interop (mp3agic, FFmpeg output).
- No wildcard imports.
- **No comments that restate the code.** Comments only for non-obvious intent,
  external-format quirks, or workarounds, and they must explain *why*.
- Public API surfaces (interfaces, data classes representing domain concepts)
  should be meaningful and small.

## 2. Architecture

- **Package-by-feature** inside the single `:app` module:
  `ui`, `ocr`, `metadata`, `audio`, `tags`, `data`, `settings`, `di`.
- **Unidirectional data flow:** Composables render state and emit events; state
  lives in `ViewModel`s exposing `StateFlow<UiState>`; UI never touches services
  directly.
- **Layered boundaries:** UI → use cases / view models → repositories/services →
  platform (Process, MediaStore, mp3agic). No FFmpeg or HTTP calls from UI code.
- **Dependency injection:** hand-rolled `AppContainer` interface, constructed at
  app start; tests substitute fakes. No Hilt.
- **Error handling:** model failures with sealed result types (e.g.
  `sealed interface Result<out T>` with typed error cases). Do not throw across
  layer boundaries for expected failures (network, no match, decode failure).
- **Concurrency:** coroutines + Flow. Long work runs in
  `Dispatchers.IO`/`Dispatchers.Default`; the foreground service owns the job
  lifecycle. Cancellation must clean up temp files.
- **One job at a time.** Any code path that would start a second conversion must
  be prevented structurally, not by UI polish alone.

## 3. Compose & UI

- Material 3 Expressive; use the project theme (`CueTheme`), never hardcode colors
  or type scales in components.
- **All user-facing strings in `strings.xml`** — no hardcoded text in Composables.
  Every string must exist in both `values/` (English) and `values-he/` (Hebrew).
- **RTL:** never use `padding(start=…)` style directional APIs blindly; use
  `start`/`end` aware modifiers and let `LocalLayoutDirection` flow from the
  locale. Directional icons must mirror. Layouts must be verified in Hebrew.
- Prefer stateless Composables plus a screen-level stateful wrapper; keep preview
  functions for reusable components.
- Motion: curated and purposeful (shared elements, chart/list transitions,
  springy button feedback). No motion that delays the user's action.

## 4. Native / FFmpeg

- FFmpeg is invoked as an **executable** (`libffmpeg.so`) via `Process`, never via
  hand-written JNI. (See `docs/ADR/0001-native-ffmpeg-via-process.md`.)
- Command construction is centralized in one builder class; every invocation is
  assembled from typed parameters, never ad-hoc string concatenation in business
  code. User-supplied text (tags) must be passed safely (arg array, no shell).
- Parse progress from FFmpeg stderr only in the progress reporter; never assume a
  fixed number of lines.
- The build script is committed; compiled `.so` files are **gitignored**. ABI set
  is `arm64-v8a` + `x86_64`. The LGPL components (FFmpeg libraries, LAME,
  Chromaprint) are separate shared libraries, not statically linked into the
  executable; keep them replaceable and keep the notices in
  `app/src/main/assets/licenses/` in sync.
- Keep the FFmpeg configuration **LGPL-only** (no GPL components). See
  `docs/ADR/0005-lgpl-compliance.md`.
- Native artifacts must have 16 KB LOAD alignment for Android 15+.

## 5. Tags & files

- All ID3 read/write goes through **mp3agic** (see
  `docs/ADR/0002-id3-writing-with-mp3agic.md`).
- Text frames are written as **UTF-16** so Hebrew/Unicode round-trips.
- Tag-only edits are in place; any audio change produces a new file. Never
  overwrite a source.
- Output goes to `Music/Cue` through `MediaStore` with `IS_PENDING` set and
  cleared. Filenames are sanitized (illegal chars, control chars, length).
- Temp artifacts live in the app cache and are removed on success, failure, and
  cancellation.

## 6. Networking

- HTTPS only. No cleartext traffic (`usesCleartextTraffic=false`).
- One HTTP client (OkHttp) configured with timeouts (default 10 s) and
  interceptors; retries/backoff (default 2 retries) live in the repository layer.
- Every third-party service must comply with its terms: a descriptive
  `User-Agent` for MusicBrainz, respect for iTunes rate limits, an AcoustID key
  registered to this application.
- **Secrets never committed.** The AcoustID key defaults from `local.properties`
  (gitignored) and is injectable via BuildConfig; it may also be set in Settings.
- Network access is user-controllable (Settings toggle) and every request that
  sends song data to a third party must be able to be turned off.

## 7. Testing standards

- Pure logic must have JVM unit tests (heuristics, sanitizer, size estimator, tag
  mapping, metadata merge, template rendering). No Android framework dependency in
  these classes without an abstraction.
- The native pipeline and tag writing are covered by instrumentation tests on the
  x86_64 emulator using synthetic fixtures.
- New behaviour ships with tests at the appropriate level; bug fixes that can be
  captured as a test must be.
- Test names state the behaviour, not the method (`x when y does z`).

## 8. Git & commits

- Commit messages: short imperative subject line; body explains *why* when
  non-obvious.
- Keep commits scoped to one logical change.
- **Never commit:** `local.properties`, any API keys, keystores, `samples/`,
  compiled native libraries, build outputs.
- `.gitignore` is authoritative for artifacts; if a new artifact type appears,
  add it there, not in a commit bypass.
- Do not commit generated code or binaries without an ADR justifying it.

## 9. Documentation

- `docs/PLAN.md` is the design source of truth; keep it in sync with reality.
- Architectural choices get an ADR in `docs/ADR/NNNN-title.md` (Context →
  Decision → Consequences → Alternatives). Superseding an ADR means adding a new
  one that references the old, not editing history.
- `docs/PHASES.md` tracks the agreed staging and exit criteria.
- README explains how to build (including the native build step) and run.

## 10. Security & privacy

- No analytics, no tracking, no advertising SDKs.
- The only network destinations are iTunes Search, MusicBrainz, Cover Art
  Archive, and AcoustID, and only when the user enables them.
- Request the minimum permissions: `INTERNET`, `POST_NOTIFICATIONS`,
  `FOREGROUND_SERVICE` (+ types). No storage permissions — use SAF and
  MediaStore.
- Validate and sanitize everything that reaches a file path or a native argument.
