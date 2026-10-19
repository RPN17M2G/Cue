# ADR 0002 — ID3 read/write with mp3agic, audio with FFmpeg

Status: accepted
Date: 2026-10-19

## Context

Cue writes ID3v2.3 tags, including embedded cover art, and — a first-class
feature — edits the tags of an arbitrary existing MP3 **in place**, without
re-encoding the audio. The native FFmpeg (ADR 0001) can write basic metadata, but
significant concerns apply to in-place, artwork-bearing edits:

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

Text fields, including Hebrew and other Unicode, are written as **UTF-16** to be
safe within ID3v2.3. Cover art is fetched at 600×600, downscaled to at most
1000 px JPEG, and embedded as a front-cover APIC frame.

## Consequences

- In-place tag edits are natural: read the existing tag, change fields, save.
- No dependency on FFmpeg's tag handling; the two tools have clean boundaries.
- mp3agic rewrites the tag; ID3 frames it does not model may be dropped on
  rewrite. This is an accepted limitation for v1 and is documented behaviour.
- ID3 version is pinned to v2.3 for maximum player compatibility (chosen over
  v2.4, which some older players mishandle).
- Adding other formats (M4A, FLAC, OGG) later would require separate libraries,
  since mp3agic is MP3-specific. That matches the current scope (MP3 only).

## Alternatives considered

- **FFmpeg for tags too.** Rejected: no true in-place edit; clunky with SAF;
  fragile artwork embedding.
- **jaudiotagger.** Rejected: richer ID3v2.4 support but heavier and known to have
  Android quirks; not needed for v2.3 + APIC.
- **Hand-rolled ID3 parser.** Rejected: unnecessary risk and effort.
