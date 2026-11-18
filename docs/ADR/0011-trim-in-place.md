# ADR 0011 — Trim a writable MP3 in place

Status: accepted
Date: 2026-11-18

## Context

Tag-only edits already rewrite the selected MP3 through its own document URI:
the cache copy is patched, verified, and written back with the untouched copy
kept as a restore point. A trim, however, always produced a second file in
`Music/Cue`, even when the user opened an MP3 that the app can write to. The
backlog asked for an option to trim such a file and save over it, so a session
of tidying a library does not leave copies behind.

`docs/STANDARDS.md` §5 said "any audio change produces a new file. Never
overwrite a source." That rule exists to protect the user's audio, so changing
it needed an explicit, narrow decision rather than a silent behaviour change.

## Decision

Add `ProcessingMode.TRIM_IN_PLACE`, used only when **all** of these hold:

- the input is an MP3,
- the source granted write access (`canEditInPlace`),
- a trim window is set, and
- the "Replace the original file" switch is on (on by default in Review).

The job re-encodes the trim into a cache temp file, tags it as a whole new file
(every field, plus `USLT`/`SYLT`), then publishes it through the same
`DocumentEditor` path as a tag-only edit: write, read back and compare, and
restore the untouched cache copy if either step fails. The user confirms once
more in a dialog that names the file, because the write cannot be undone.

After a successful write the MediaStore row's `DURATION` is refreshed when the
URI is a MediaStore row; other SAF documents have no row to update.

## Consequences

- The source MP3 is genuinely replaced, not duplicated. There is no undo: the
  restore path only covers a failed write or a failed read-back, not a change of
  mind after success.
- The trim is a lossy mp3→mp3 re-encode, exactly like the new-file path; the
  quality chips and the estimated size still apply.
- Verification covers the tag frames and the provider-reported size, not the
  audio contents. The bytes written are the bytes FFmpeg produced from the
  original, and nothing deletes the cache copy until the write is verified.
- Video input and read-only MP3s are unchanged: they still publish a new file.
- Toggling the switch off restores the old behaviour for that job.
- `docs/STANDARDS.md` §5 changes from "never overwrite a source" to the narrower
  rule described here.

## Alternatives considered

- **Always write a new file** (the previous behaviour). Safe, but leaves the
  duplicate the backlog item was opened to avoid.
- **Atomic rename over the document.** Content providers do not expose one, and
  a rename is impossible across a SAF URI; the backup-and-restore path is the
  strongest guarantee available.
- **Move the original to a trash folder, then write.** Adds a second destructive
  step (and a retention policy) to protect against a change of mind; rejected
  as more machinery than the confirmed opt-in needs.
- **Trim into the app's private storage only.** Does not update the user's
  library, which is the point of editing an existing MP3.
