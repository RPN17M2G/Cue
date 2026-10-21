# ADR 0002 — ID3 read/write with mp3agic, audio with FFmpeg

Status: accepted
Date: 2026-10-19

## Context

Cue writes ID3 tags, including embedded cover art, and — a first-class feature —
edits the tags of an arbitrary existing MP3 **in place**, without re-encoding the
audio. The native FFmpeg (ADR 0001) can write basic metadata, but significant
concerns apply to in-place, artwork-bearing edits:

- FFmpeg does not edit a file in place; it writes a new output. Doing "in-place"
  would mean writing a temp file and swapping it, which is risky on Android's
  scoped storage and with user-selected SAF URIs.
- Artwork (APIC) embedding with FFmpeg is error-prone and poorly suited to
  round-tripping an existing file's frames.
- FFmpeg's metadata flags are not a general-purpose ID3 library.

A Java ID3 library can read, mutate, and rewrite the tag portion of a file
directly while leaving the audio frames untouched.

## Decision

Use **mp3agic** (pure Java, no JNI) for **all** ID3 read and write operations,
including in-place edits and APIC artwork. FFmpeg remains audio-only.

New tags and tags without a version are written as **ID3v2.3** for maximum player
compatibility. Existing **ID3v2.4** tags are edited in place and stay v2.4. ID3v2.2
is rejected with `UnsupportedTagVersionException` rather than being silently
converted. Text fields are written as UTF-16; existing UTF-8 v2.4 frames are read
correctly and are only rewritten when their field is edited.

Cover art is fetched at 600×600, downscaled to at most 1000 px JPEG, and embedded
as a front-cover APIC frame. The stored MIME type is detected from the actual bytes,
so PNG data is never labelled `image/jpeg`. Replacing artwork replaces the front
cover and keeps other APIC pictures (for example a back cover). Text-only edits
never touch APIC frames.

ID3v1 values are used as a fallback when the v2 tag leaves a core field blank. On
the first write, those values are copied into the v2 tag and the ID3v1 tag is
removed, so it cannot go stale.

## Crash safety

A tag write never edits the audio in place. The implementation:

1. Hashes the existing MPEG frames (excluding ID3 tags and pre-audio junk).
2. Writes the new tag and copied audio to a hidden same-directory
   `.cue-tag-<name>-*.tmp` file, then fsyncs it.
3. Verifies that the temp file's audio hash matches the original.
4. Copies the original to `.cue-tag-<name>.bak` and verifies that backup.
5. Replaces the target with `ATOMIC_MOVE` plus `REPLACE_EXISTING` **only**. If the
   filesystem does not support an atomic move, the write fails; there is no
   silent non-atomic fallback.
6. Verifies the target's audio hash again before deleting the backup.

Temporary files are removed on every failure. If post-swap verification fails, the
backup is restored atomically. If that restore also fails, the backup is kept and
the exception names it. Stale temp files for the same target are removed before a
new write. A single writer per target is assumed.

`Mp3TagStore` accepts a local `File` only; `TagStore` does not pretend to support
SAF. An Android SAF adapter must copy the document into the app cache, edit and
verify the copy, then write it back through `ContentResolver`. That final provider
write cannot be assumed atomic, so the adapter must keep its own backup and define
a partial-write policy.

## Consequences

- In-place tag edits are natural: read the existing tag, change fields, save.
- No dependency on FFmpeg's tag handling; the two tools have clean boundaries.
- mp3agic rewrites the tag; ID3 frames it does not model may be dropped on
  rewrite. This is an accepted limitation for v1 and is documented behaviour.
- Unknown frames that mp3agic parsed and retained are preserved on text updates.
- The write path is more expensive because the audio is hashed several times and
  copied once. That is deliberate: losing audio is worse than a slower tag save.
- Adding other formats (M4A, FLAC, OGG) later would require separate libraries,
  since mp3agic is MP3-specific. That matches the current scope (MP3 only).

## Alternatives considered

- **FFmpeg for tags too.** Rejected: no true in-place edit; clunky with SAF;
  fragile artwork embedding.
- **jaudiotagger.** Rejected: richer ID3v2.4 support but heavier and known to have
  Android quirks; not needed for v2.3 + APIC.
- **Hand-rolled ID3 parser.** Rejected: unnecessary risk and effort.
