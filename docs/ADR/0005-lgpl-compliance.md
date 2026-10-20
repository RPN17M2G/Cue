# ADR 0005 - LGPL-only native build (distribution kept open)

Status: accepted
Date: 2026-10-19

## Context

Distribution is currently undecided: Cue may stay a personal sideloaded app, or
it may be published (Google Play / F-Droid) later. The design should not
foreclose the store path. The native stack (FFmpeg + Chromaprint, and libmp3lame
for MP3 encoding) carries licensing obligations that differ sharply between a
GPL and an LGPL configuration.

Key facts:

- FFmpeg is LGPL-2.1-or-later by default, but enabling GPL-only components
  (e.g. `--enable-gpl` with certain filters/codecs) makes the whole build GPL.
- libmp3lame is LGPL; the LAME/MP3 patents have expired.
- LGPL permits static or dynamic linking provided the user can relink/replace the
  library (distribution of the object files or a compliant dynamic-linking setup)
  and attribution is preserved.

## Decision

Configure the FFmpeg build **LGPL-only**: do not pass `--enable-gpl`; avoid any
GPL-licensed components; keep only what Cue needs (AAC/MP3/Opus/Vorbis decode,
libmp3lame encode, trim, Chromaprint).

**Revision 2026-10-20:** the original static-link decision was insufficient for
LGPL-2.1 section 6 because the APK contained no relinkable form of FFmpeg, LAME,
or Chromaprint. The build now uses dynamic linking for every LGPL component:

- FFmpeg is built with `--enable-shared --disable-static` and installed as
  `libavcodec.so`, `libavformat.so`, `libavfilter.so`, `libavutil.so`, and
  `libswresample.so`.
- LAME 3.100 is shipped as `libmp3lame.so`; Chromaprint 1.5.1 is shipped as
  `libchromaprint.so`. Chromaprint's bundled KissFFT is inside that shared
  library.
- The `libffmpeg.so` executable dynamically depends on all of those libraries.
  It uses `DT_RUNPATH=$ORIGIN`, so the libraries are resolved from the APK's
  extracted `nativeLibraryDir`. The app may additionally set
  `LD_LIBRARY_PATH` to that directory as a fallback.
- All shared libraries are staged per ABI in `app/src/main/jniLibs/<abi>/` and
  are not committed. The pinned build script rebuilds them from source.
- License texts, copyright notices, and this statement are committed under
  `app/src/main/assets/licenses/` and packaged in the APK. The in-app notices
  screen is a later integration task.

## Compliance statement

The LGPL components are dynamically linked. `libffmpeg.so` and the FFmpeg
shared libraries depend at runtime on separate, replaceable `libchromaprint.so`
and `libmp3lame.so` files shipped in the same APK native library directory. A
user can replace those shared libraries with a compatible build without
relinking `libffmpeg.so`. The complete corresponding source is available from
the pinned upstream URLs in `scripts/native/build-ffmpeg.sh`; the build script
and notices are part of this repository. No GPL or nonfree FFmpeg components
are enabled.

## Consequences

- The sideload path is unconstrained, and a future Play/F-Droid release does not
  require re-architecting the native layer.
- The build configuration is narrower (some filters/codecs unavailable), which is
  fine for audio-only conversion.
- Contributors must not casually add GPL components; the build script is the
  control point and any change to licensing must go through a new ADR.
- The APK contains several `.so` files instead of one executable. Android's
  linker must resolve them from `nativeLibraryDir`; this is verified statically
  with `readelf` and will be exercised by the emulator/device milestone.
- Attribution/license text must ship with the app; it is now committed under
  `app/src/main/assets/licenses/` and checked by the probe script.
- Nothing here is legal advice; if and when publishing, the licensing position
  should be reviewed against the store's current policies.

## Alternatives considered

- **GPL-enabled FFmpeg.** Rejected: would force GPL on the whole app, blocking a
  future store release without a relicense.
- **Ignore licensing until release.** Rejected: retrofitting an LGPL-compliant
  build later is more expensive than choosing it now.
- **Proprietary/closed native codec.** Rejected: licensing cost and complexity,
  and unnecessary since LGPL options suffice.
