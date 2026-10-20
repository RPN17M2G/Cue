# Native FFmpeg build

This directory contains the reproducible build for Cue's native audio stack: a
minimal **LGPL-only** FFmpeg CLI with libmp3lame and Chromaprint, cross-compiled
for Android `arm64-v8a` and `x86_64`.

The LGPL components are built as **shared libraries** so they remain
replaceable, as required for the distribution path described in
`docs/ADR/0005-lgpl-compliance.md`. The executable and its dependencies are
staged per ABI in `app/src/main/jniLibs/<abi>/`. Compiled binaries are not
committed.

## What gets built

| Component | Version | License | Artifact |
|---|---|---|---|
| FFmpeg | 7.1.5 | LGPL-2.1-or-later | `libffmpeg.so` + `libav*.so` |
| LAME | 3.100 | LGPL-2.0-or-later | `libmp3lame.so` |
| Chromaprint | 1.5.1 | LGPL-2.1-or-later (MIT + LGPL components) | `libchromaprint.so` |
| KissFFT | bundled with Chromaprint | BSD-3-Clause | inside `libchromaprint.so` |
| Android NDK | r25c (`25.2.9519653`) | Apache-2.0 / LLVM | cross toolchain |

Per ABI, the staged set is:

```text
libffmpeg.so          # executable, run via ProcessBuilder
libavcodec.so
libavformat.so
libavfilter.so
libavutil.so
libswresample.so
libchromaprint.so
libmp3lame.so
```

`libffmpeg.so` has `DT_RUNPATH=$ORIGIN` and `DT_NEEDED` entries for every
library above. `libavformat.so` NEEDs `libchromaprint.so`; `libavcodec.so` NEEDs
`libmp3lame.so`; the executable also NEEDs both directly so the replaceable
LGPL libraries are explicit APK dependencies. The only non-FFmpeg system
dependencies are bionic/platform libraries (`libc.so`, `libm.so`, `libdl.so`,
`libandroid.so`, `libmediandk.so`). `libc++_shared.so` is not required:
Chromaprint's C++ runtime is linked statically into `libchromaprint.so`.

The arm64 build is about 2.7 MB and the x86_64 build about 2.9 MB across all
eight files; the previous single-file static executable was about 2.1 MB. The
extra size buys LGPL replaceability. The exact per-file sizes and SHA-256 values
are recorded in `.prebuilt/build-info/<abi>.txt`.

## Prerequisites

The build runs inside **WSL2/Linux**, not Windows. On this machine:

```powershell
wsl -d Ubuntu
```

Required host tools (already present in the project's WSL environment):

- `bash`, `curl`, `tar`, `xz`
- `python3` (used to unpack the NDK zip with its symlinks intact)
- `cmake` (Chromaprint is CMake-only)
- `make`, `gcc`, `pkg-config`
- `sha256sum`, `file`, `readelf`, `strings`, `du`

No `sudo` is required. The Linux NDK is downloaded into the user's cache if it
is not already present.

> The Windows NDK at
> `C:\Users\omerg\AppData\Local\Android\Sdk\ndk\25.1.8937393` is **not** usable
> from WSL: its `clang` wrappers invoke `clang.exe`, which cannot consume WSL
> (`/mnt/c/...`) paths. The script therefore provisions the Linux NDK `r25c`
> (`25.2.9519653`) automatically. If `ANDROID_NDK_HOME` points at the Windows
> NDK, unset it or pass a Linux NDK with `--ndk`.

## One-command build

From the repository root:

```powershell
wsl -d Ubuntu -e bash -lc \
  'cd /mnt/c/path/to/Cue && bash scripts/native/build-ffmpeg.sh'
```

Or from inside WSL, from the repository root:

```bash
bash scripts/native/build-ffmpeg.sh
```

Translate a Windows path with `wslpath 'C:\path\to\Cue'` if needed.

Useful options:

```bash
scripts/native/build-ffmpeg.sh --abi arm64-v8a   # one ABI
scripts/native/build-ffmpeg.sh --abi x86_64
scripts/native/build-ffmpeg.sh --abi all --jobs 12
scripts/native/build-ffmpeg.sh --clean           # remove build dirs + staged .so
scripts/native/build-ffmpeg.sh --clean --abi x86_64
scripts/native/build-ffmpeg.sh --ndk /opt/android-ndk-r25c
```

The default job count is 8. The first build can take 15–60 minutes depending on
the machine; subsequent builds reuse downloaded sources, the NDK, and existing
dependency prefixes, and only run the incremental FFmpeg make. Whenever LAME is
rebuilt, it is extracted fresh into a per-ABI build tree, so no object files are
shared between ABIs.

### Cache and output layout

| Path | Contents |
|---|---|
| `.prebuilt/src/` | pinned source archives and extracted trees (gitignored) |
| `.prebuilt/build-info/` | host/toolchain versions, configure line, per-file size, SHA-256, NEEDED, alignment (gitignored) |
| `~/.cache/cue-native/` | Linux NDK, per-ABI build trees, prefixes, logs |
| `app/src/main/jniLibs/<abi>/*.so` | staged artifacts (gitignored by `*.so`) |
| `app/src/main/assets/licenses/` | committed license texts and NOTICE index (packaged in the APK) |

Override the cache with `CUE_NATIVE_CACHE=/path` and the Linux NDK with
`CUE_NATIVE_NDK=/path`. `--clean` removes only the selected ABI's build tree,
prefix, and staged `.so` files; downloads and extracted sources are kept.

## FFmpeg configuration

This is the effective configuration (the script substitutes the NDK and prefix
paths; `--disable-x86asm` is added for `x86_64` because the NDK ships no NASM):

```text
--target-os=android --arch=aarch64|x86_64 --cpu=armv8-a|x86-64
--enable-cross-compile --enable-shared --disable-static --enable-pic
--disable-everything
--enable-ffmpeg --disable-ffplay --disable-ffprobe
--disable-doc --disable-debug --disable-network --disable-autodetect
--enable-small --disable-avdevice --disable-postproc --disable-swscale
--enable-protocol=file,pipe
--enable-demuxer=mov,mp3,matroska,webm,ogg
--enable-parser=aac,mpegaudio,opus,vorbis
--enable-decoder=aac,mp3,opus,vorbis
--enable-encoder=libmp3lame,pcm_s16le
--enable-muxer=mp3,chromaprint
--enable-filter=aresample,anull,atrim,asetpts
--enable-libmp3lame
--enable-chromaprint
```

`pcm_s16le` is required by the Chromaprint muxer, which consumes 16-bit PCM.
`pipe` is enabled alongside `file` so `-f chromaprint -` can write a fingerprint
to stdout. `avdevice`, `postproc`, and `swscale` are disabled because Cue does
audio only. WebM files use FFmpeg's Matroska demuxer. **No** `--enable-gpl`, no
`--enable-nonfree`, and no GPL-only filters/codecs are used. See
`docs/ADR/0005-lgpl-compliance.md`.

The exact configure line for each build is embedded in `libffmpeg.so` and
recorded in `.prebuilt/build-info/<abi>.txt`.

## Verification

Run:

```bash
scripts/native/probe-ffmpeg.sh
scripts/native/probe-ffmpeg.sh --abi x86_64
```

The probe does not execute the binaries. It checks:

- `file(1)`: ELF 64-bit, PIE executable for `libffmpeg.so`, shared objects for
  the libraries, and the expected architecture
- `readelf -h`: `DYN` type and the expected ELF machine
- `readelf -l`: interpreter `/system/bin/linker64` and **16 KB** LOAD alignment
  (`0x4000`) on every shipped library
- `readelf -d`: expected `NEEDED` libraries (including `libchromaprint.so` and
  `libmp3lame.so` for `libffmpeg.so`), unversioned SONAMEs, and
  `DT_RUNPATH=$ORIGIN`
- exported symbols: `lame_init`/`lame_encode_buffer`/`lame_close` and
  `chromaprint_new`/`chromaprint_feed`/`chromaprint_get_fingerprint`
- embedded configure line: Android target, shared build, libmp3lame +
  pcm_s16le encoders, mp3/chromaprint muxers, mov/mp3/matroska/ogg demuxers,
  aac/mp3/opus/vorbis decoders, and no GPL/nonfree flags
- component strings: `libmp3lame`, `pcm_s16le`, `chromaprint`, `mp3`, Matroska,
  Ogg, Opus, Vorbis, and `LAME3.100`
- packaged license assets: FFmpeg LGPL-2.1 text and license overview, LAME
  LGPL, Chromaprint MIT/LGPL notice, KissFFT BSD-3-Clause, and the NOTICE index

Android binaries link against bionic and cannot run on the Linux host. The
project deliberately does not start an emulator in this check. **Runtime
execution, `ffmpeg -version`, and the real Chromaprint fingerprint test are
deferred to the emulator/device milestone**; Phase 0's on-device
`ffmpeg -version` check is still pending. The probe's static assertions are the
gate until then.

## Reproducibility

The build exports `LC_ALL=C`, `TZ=UTC`, and a fixed `SOURCE_DATE_EPOCH`
(default `1735689600`, overrideable). Compilation uses
`-ffile-prefix-map=$REPO_ROOT=.` and
`-ffile-prefix-map=$CUE_NATIVE_CACHE=.` so build paths do not leak into
objects.

`.prebuilt/build-info/<abi>.txt` records the host `gcc`, `make`, `cmake`, and
`pkg-config` versions, the NDK revision, the source date epoch, the exact
configure line, and per-artifact size/SHA-256/NEEDED/alignment.

Bit-for-bit reproducibility is scoped to the **same host and toolchain**
(same WSL distribution, host GCC/CMake/make/pkg-config, and NDK r25c). A clean
arm64 rebuild was verified byte-identical on this host. A different host or
toolchain may produce different bytes; the pinned sources and configuration
still reproduce functionally equivalent binaries.

## How the app loads and executes it

Gradle's `packaging.jniLibs.useLegacyPackaging = true` (already set in
`app/build.gradle.kts`) makes the APK extract all native libraries to disk
instead of loading them directly from the APK. The executable and its
dependencies then live in `context.applicationInfo.nativeLibraryDir`.
`libffmpeg.so` is launched with `ProcessBuilder` (never a shell):

```kotlin
val nativeDir = context.applicationInfo.nativeLibraryDir
val ffmpeg = File(nativeDir, "libffmpeg.so")

val process = ProcessBuilder(
    ffmpeg.absolutePath,
    "-hide_banner",
    "-nostdin",
    "-i", inputFile.absolutePath,
    "-vn",
    "-ss", "00:00:10",
    "-to", "00:03:10",
    "-c:a", "libmp3lame",
    "-q:a", "0",
    outputFile.absolutePath,
).redirectErrorStream(false)

// DT_RUNPATH=$ORIGIN is the primary lookup path. Keep this fallback for
// devices/linker versions that do not honor $ORIGIN for executables.
process.environment()["LD_LIBRARY_PATH"] = nativeDir
process.start()
```

The command builder owns argument assembly and passes user text as separate
arguments, never through a shell. Parse progress from `stderr` only in the
progress reporter. Fingerprints use the same executable:

```text
libffmpeg.so -hide_banner -nostdin -i input.mp4 -vn -ss 20 -t 30 -f chromaprint -
```

All eight `.so` files must be packaged for the ABI. Do not rename
`libffmpeg.so`; Android's packaging and extraction rules expect the `lib*.so`
form.

## Licensing

The LGPL components are dynamically linked and shipped as separate,
replaceable shared libraries. The executable and FFmpeg libraries depend at
runtime on `libchromaprint.so` and `libmp3lame.so` in the same native library
directory. A user can replace those libraries with a compatible build without
relinking `libffmpeg.so`. The complete corresponding source is available from
the pinned upstream URLs in `build-ffmpeg.sh`; the build script and notices are
part of this repository. No GPL or nonfree FFmpeg components are enabled.

The committed assets in `app/src/main/assets/licenses/` are:

- `FFmpeg-COPYING.LGPLv2.1.txt` and `FFmpeg-LICENSE.md`
- `LAME-COPYING.txt`
- `Chromaprint-LICENSE.md`
- `KissFFT-BSD-3-Clause.txt`
- `NOTICE.txt` (component index, copyrights, source URLs, compliance statement)

The in-app notices screen is a later integration task, but these files are
already packaged in the APK. This is not legal advice; see
`docs/ADR/0005-lgpl-compliance.md`.

## Troubleshooting

**`clang.exe: not found`, `No such file or directory` while compiling**
The Windows NDK is being used from WSL. Unset `ANDROID_NDK_HOME`/`--ndk`, or
point it at a Linux NDK. The script's auto-provisioned NDK is under
`~/.cache/cue-native/android-ndk-r25c`.

**`cmake: not found`**
Install CMake in the WSL distribution (`sudo apt install cmake`); on the project
machine it is already installed and no sudo is available, so the script does not
try to install packages.

**`pkg-config` cannot find `libchromaprint`**
The script sets `PKG_CONFIG_PATH`/`PKG_CONFIG_LIBDIR` to the per-ABI prefix and
regenerates `lib/pkgconfig/libchromaprint.pc` with dynamic-link flags. Check
`~/.cache/cue-native/logs/<abi>-chromaprint-*.log`, then rebuild with
`--clean`.

**`nasm` is missing**
Expected. arm64 uses clang's integrated assembler; x86_64 is configured with
`--disable-x86asm`, which is sufficient for audio. Do not add a NASM dependency
just for this build.

**`libc++_shared.so` is listed by `readelf -d`**
The build must not require it. Chromaprint links `libc++_static`/`libc++abi`
with `-nostdlib++`. Re-run `probe-ffmpeg.sh`; if it appears, the Chromaprint
shared-link step is wrong and `--clean` is required.

**`dlopen failed: library "libchromaprint.so" not found` on device**
All eight `.so` files must be staged for the same ABI and extracted by
`useLegacyPackaging`. Check `readelf -d libffmpeg.so` for `$ORIGIN` and set
`LD_LIBRARY_PATH` to `nativeLibraryDir` as a fallback. This runtime path is
exercised by the emulator milestone.

**Build fails after a version or configure change**
Use `scripts/native/build-ffmpeg.sh --clean --abi <abi>` and rerun. Configure
hashes are cached per ABI and automatically invalidate a stale build directory.

**`Permission denied` when starting FFmpeg on device**
Confirm `useLegacyPackaging = true`, reinstall the APK, and check
`File(nativeLibraryDir, "libffmpeg.so").canExecute()`. Android does not allow
executing arbitrary files from app data, but `nativeLibraryDir` is executable.

**APK install fails or a file is missing**
Every artifact must be packaged as `lib/<abi>/<name>.so`. They are intentionally
named `lib*.so` so Android's packaging and extraction rules keep them. Do not
rename them after staging.

**Slow builds on `/mnt/c`**
The repository may live on OneDrive. Sources stay in `.prebuilt/src`, but build
trees and the NDK live in the WSL filesystem cache (`~/.cache/cue-native`) for
speed. You can also set `CUE_NATIVE_CACHE` to another ext4 path.
