# ADR 0001 — Native FFmpeg invoked as a process

Status: accepted
Date: 2026-10-19

## Context

Cue must demux an MP4, decode its audio, optionally trim it, and encode MP3, with
optional fingerprinting. Android has no built-in MP3 encoder (MediaCodec encodes
AAC, not MP3), so a native codec dependency is unavoidable. The previously
convenient wrapper library, `ffmpeg-kit`, was retired in 2025, so its prebuilt
binaries are not a safe foundation.

Two broad options were on the table:

1. Build a minimal FFmpeg ourselves and invoke the resulting binary.
2. Use a JNI library (`libav*` with hand-written bindings) or a MediaCodec +
   libmp3lame combination.

The app also needs Chromaprint for fingerprinting (AcoustID path), which is
available as an FFmpeg integration (`--enable-chromaprint`).

## Decision

Build a **single minimal FFmpeg per ABI** and ship it as `libffmpeg.so`,
executed via `Process`. The build is produced by a committed script
(ffmpeg-android-maker style, run via Docker/WSL with the NDK) for `arm64-v8a` and
`x86_64`. Fingerprints are emitted by the same binary using `-f chromaprint`.
mp3agic (pure Java) handles all ID3 work; FFmpeg never touches tags.

## Consequences

- One native dependency covers conversion, trimming, and fingerprinting.
- No hand-written JNI bindings to maintain, and no dependency on an abandoned
  prebuilt wrapper.
- All FFmpeg invocations are assembled from typed parameters in one command
  builder; user-supplied text is passed as arguments, never through a shell.
- Progress is parsed from stderr by a single progress reporter.
- Builds require a one-time cross-compile setup (Docker/WSL + NDK); compiled
  `.so` files are not committed, so a fresh clone must run the build script.
- Shipping a native executable as `lib…so` and executing it from the app's native
  library directory is a well-established Android technique, but it is a
  non-obvious artifact layout that must be documented for contributors.
- Future enhancement DSP (e.g. `loudnorm`) is available without new dependencies.

## Alternatives considered

- **ffmpeg-kit prebuilt binaries.** Rejected: retired upstream, no security
  maintenance, and its licensing states complicate a future store release.
- **JNI shared libraries with hand-written bindings.** Rejected: substantially
  more native code to write and maintain for no functional gain here.
- **MediaCodec (AAC decode) + libmp3lame JNI (MP3 encode).** Rejected: lighter
  APK, but every future processing feature (normalization, fingerprinting) would
  require more hand-written native code, and Chromaprint would still need its own
  integration.
- **Two separate binaries (ffmpeg + fpcalc).** Rejected: more artifacts to build,
  ship, and keep in sync.
