# Cue — Backlog

Deferred work that is agreed but not scheduled. The design itself lives in
`docs/PLAN.md`; architectural decisions are in `docs/ADR/`. An item moves out of
here when it is picked up, and its outcome is recorded in the ADR or the design
plan as usual.

## Edit an existing MP3 in place instead of always creating a new file

**Status:** deferred.

The video-to-MP3 flow always writes a new file, and trimming an existing MP3
also writes a new file. The ask is for an option to change an existing MP3 — its
tags, trims or metadata — and save over that same file, rather than producing a
second copy the user then has to manage.

Notes:

- Needs a safe write: the tag/trim edit happens on a temp copy that is atomically
  renamed over the original only on success, so a cancelled or failed job never
  destroys the source MP3.
- The delete-source guardrail that protects the recording must not be reused
  blindly here: an in-place MP3 edit has no source to delete, and the original
  must never be removed mid-write.
- Trim-in-place is destructive and cannot be undone, so it needs an explicit
  confirmation that names the file being overwritten.
- The gallery's "MP3 music" tab reads MediaStore; an in-place rewrite may need a
  media scan/refresh so the row updates.

## Synced (timed) lyrics

**Status:** deferred; OCR identification takes priority.

Today lyrics are fetched from LRCLIB and written as an ID3v2 `USLT`
(unsynchronized) frame, as multi-line plain text. The client prefers
`plainLyrics`; when only `syncedLyrics` (LRC) exists the `[mm:ss.xx]` timestamps
are stripped, and `Lyrics.synced` is never set. So the MP3 carries the words but
no timing — no karaoke-style highlighting in any player.

Goal: when LRCLIB returns `syncedLyrics`, keep the timing and write an ID3v2
`SYLT` (synchronised lyrics) frame alongside `USLT`, so players that support it
can highlight the current line. Notes for whoever picks this up:

- `Lyrics.synced` already exists as a flag; make `toLyrics()` set it and keep
  the raw LRC instead of only the stripped text.
- jaudiotagger can build a `SYLT` frame (`FrameBodySYLT` with text-encoding,
  language, timestamp format and a `SynchronisedText[]`), but it needs
  `java.util.Locale` language and millisecond timestamps parsed from the LRC.
- Keep `USLT` as the plain-text fallback: most players still read only that, and
  a file with `SYLT` alone would show no lyrics at all in them.
- `TagDraft` / `ProcessingJobCodec` / `MetadataMerger` must carry the timed
  payload, or it will be dropped between review and the foreground service
  (this already happened once with the enrichment fields — see ADR 0009).
- A trimmed MP3 shifts every timestamp; either rewrite the offsets for the
  chosen start, or only write `SYLT` when the trim is a no-op.
