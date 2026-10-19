# ADR 0004 — One-job foreground service for processing

Status: accepted
Date: 2026-10-19

## Context

Conversion of a screen recording (demux, decode, trim, encode, fingerprint,
tag) can take longer than a moment and may run while the user locks the screen or
briefly leaves the app. The product decision was **one job at a time**, not a
background queue or batch processing.

Options:

1. Run the work in a ViewModel / app-scoped coroutine; the job dies if the process
   is killed or the app is backgrounded aggressively.
2. Run the work in a **foreground service** with a progress notification, so it
   completes regardless of UI state.
3. Persist and schedule jobs with WorkManager (implies queueing/batch).

## Decision

Run processing in a **foreground service**, one job at a time. On Android 15+
(API 35+) use the `mediaProcessing` foreground-service type; on API 31–34 use
`dataSync`. The notification mirrors the on-screen stage checklist and pixel
percentage and offers cancel. `POST_NOTIFICATIONS` is requested at launch.

## Consequences

- Long jobs survive backgrounding and screen-off; the user gets a reliable
  notification with progress and cancel.
- Needs `FOREGROUND_SERVICE` plus the type-specific permissions and a declared
  notification channel.
- Android 14+ enforces foreground-service types; the manifest declares the types
  and the service starts only while the app is legitimately in the foreground for a
  user-initiated action.
- No persistence across process death and no queueing; if the process is killed
  mid-job, the job aborts and temp files are cleaned on next start. This matches
  the "single job" decision and avoids WorkManager's queue semantics.
- Cancellation must be handled explicitly and must delete partial temp output.

## Alternatives considered

- **ViewModel-only coroutine.** Rejected: fragile against backgrounding and
  process death; a long conversion could silently die.
- **WorkManager queue.** Rejected: introduces batch/queue semantics that were
  explicitly out of scope, and constrains interactive progress reporting.
- **No service, keep-screen-on UI only.** Rejected: locking the phone would abort
  the conversion.
