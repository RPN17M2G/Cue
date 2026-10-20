# Native FFmpeg build

This directory contains the reproducible build for Cue's single native
dependency: a minimal **LGPL-only** FFmpeg CLI with libmp3lame and Chromaprint,
cross-compiled for Android `arm64-v8a` and `x86_64` and staged as
`app/src/main/jniLibs/<abi>/libffmpeg.so`.

Compiled binaries are not committed. A fresh clone runs the build once.

## What gets built

| Component | Version | License | Role |
|---|---|---|---|
| FFmpeg | 7.1.5 | LGPL-2.1-or-later | demux/decode/trim/encode CLI |
| LAME | 3.100 | LGPL-2.0-or-later | MP3 encoder (`libmp3lame`) |
| Chromaprint | 1.5.1 | LGPL-2.1-or-later | audio fingerprinting |
| KissFFT | bundled with Chromaprint | BSD-3-Clause | Chromaprint's FFT |
| Android NDK | r25c (`25.1.8937393`) | Apache-2.0 / LLVM | cross toolchain |

Both libraries are linked **statically into the FFmpeg executable**, so the
staged artifact is a single file. `readelf -d` shows only bionic system
dependencies (`libc.so`, `libm.so`, `libdl.so`).

Pinned archives and SHA-256 values are declared at the top of
`build-ffmpeg.sh`; the script refuses to use a mismatched download.

## Prerequisites

The build runs inside **WSL2/Linux**, not Windows. On this machine:

```bash
wsl -d Ubuntu
```

Required host tools (already present in the project's WSL environment):

- `bash`, `curl`, `tar`, `xz`
- `python3` (used to unpack the NDK zip with its symlinks intact)
- `cmake` (Chromaprint is CMake-only)
- `make`, `gcc`, `pkg-config`
- `sha256sum`, `file`, `readelf`, `strings`

No `sudo` is required. The Linux NDK is downloaded into the user's cache if it
is not already present.

> The Windows NDK at
> `C:\Users\omerg\AppData\Local\Android\Sdk\ndk\25.1.8937393` is **not** usable
> from WSL: its `clang` wrappers invoke `clang.exe`, which cannot consume WSL
> (`/mnt/c/...`) paths. The script therefore provisions the matching Linux NDK
> (`r25c`, the same 25.1.8937393 release) automatically. If `ANDROID_NDK_HOME`
> points at the Windows NDK, unset it or pass a Linux NDK with `--ndk`.

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
dependency prefixes, and only run the incremental FFmpeg make.

### Cache layout

| Path | Contents |
|---|---|
| `.prebuilt/src/` | pinned source archives and extracted trees (gitignored) |
| `.prebuilt/build-info/` | exact configure line, size, SHA-256 per ABI |
| `.prebuilt/licenses/` | FFmpeg/LAME/Chromaprint license texts, staged for packaging (ADR 0005) |
| `~/.cache/cue-native/` | Linux NDK, build trees, per-ABI prefixes, logs |
| `app/src/main/jniLibs/<abi>/libffmpeg.so` | staged artifact (gitignored by `*.so`) |

Override the cache with `CUE_NATIVE_CACHE=/path` and the Linux NDK with
`CUE_NATIVE_NDK=/path`. `--clean` removes only the selected ABI's build tree,
prefix, and staged artifact; downloads and extracted sources are kept.

The license texts under `.prebuilt/licenses/` satisfy the attribution
requirement in `docs/ADR/0005-lgpl-compliance.md`; they are not embedded in the
executable and must be shipped with the app.

## FFmpeg configuration

This is the effective configuration (the script substitutes the NDK and prefix
paths; `--disable-x86asm` is added for `x86_64` because the NDK ships no NASM):

```text
--target-os=android --arch=aarch64|x86_64 --cpu=armv8-a|x86-64
--enable-cross-compile --enable-static --disable-shared --enable-pic
--disable-everything
--enable-ffmpeg --disable-ffplay --disable-ffprobe
--disable-doc --disable-debug --disable-network --disable-autodetect
--enable-small --disable-avdevice --disable-postproc --disable-swscale
--enable-protocol=file,pipe
--enable-demuxer=mov,mp3
--enable-parser=aac,mpegaudio
--enable-decoder=aac,mp3
--enable-encoder=libmp3lame
--enable-muxer=mp3,chromaprint
--enable-filter=aresample,anull,atrim,asetpts
--enable-libmp3lame
--enable-chromaprint
```

`pipe` is enabled alongside `file` so `-f chromaprint -` can write a fingerprint
to stdout. `avdevice`, `postproc`, and `swscale` are disabled because Cue does
audio only. **No** `--enable-gpl`, no `--enable-nonfree`, and no GPL-only
filters/codecs are used. See `docs/ADR/0005-lgpl-compliance.md`.

The exact configure line for each build is embedded in the binary and recorded
in `.prebuilt/build-info/<abi>.txt`.

## Verification

Run:

```bash
scripts/native/probe-ffmpeg.sh
scripts/native/probe-ffmpeg.sh --abi x86_64
```

The probe does not execute the binaries. It checks:

- `file(1)`: ELF 64-bit, PIE, `ARM aarch64` / `x86-64`
- `readelf -h`: `DYN` type and the expected ELF machine
- `readelf -l`: interpreter is `/system/bin/linker64`
- `readelf -d`: only allowed bionic dependencies
- embedded configure line: Android target, required encoder/decoder/demuxer
  components, Chromaprint and libmp3lame, and no GPL/nonfree flags
- component strings: `libmp3lame encoder`, `chromaprint muxer`, `LAME3.100`,
  `Lavf`, AAC

Android binaries link against bionic (`libc.so`, linker), so they cannot be run
on the Linux host. The project does **not** start an emulator as part of this
check; on-device instrumentation tests exercise the executable.

## How the app loads and executes it

Gradle's `packaging.jniLibs.useLegacyPackaging = true` (already set in
`app/build.gradle.kts`) makes the APK extract native libraries to disk instead
of loading them directly from the APK. The executable then lives at
`context.applicationInfo.nativeLibraryDir/libffmpeg.so` and can be launched with
`ProcessBuilder` (never a shell):

```kotlin
val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

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
).redirectErrorStream(false).start()
```

The command builder owns argument assembly and passes user text as separate
arguments, never through a shell. Parse progress from `stderr` only in the
progress reporter. Fingerprints use the same executable:

```text
libffmpeg.so -hide_banner -nostdin -i input.mp4 -vn -ss 20 -t 30 -f chromaprint -
```

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
regenerates `lib/pkgconfig/libchromaprint.pc` with the NDK C++ runtime flags.
Check `~/.cache/cue-native/logs/<abi>-chromaprint-*.log`, then rebuild with
`--clean`.

**`nasm` is missing**
Expected. arm64 uses clang's integrated assembler; x86_64 is configured with
`--disable-x86asm`, which is sufficient for audio. Do not add a NASM dependency
just for this build.

**Build fails after a version or configure change**
Use `scripts/native/build-ffmpeg.sh --clean --abi <abi>` and rerun. Configure
hashes are cached per ABI and automatically invalidate a stale build directory.

**`Permission denied` when starting FFmpeg on device**
Confirm `useLegacyPackaging = true`, reinstall the APK, and check
`File(nativeLibraryDir, "libffmpeg.so").canExecute()`. Android does not allow
executing arbitrary files from app data, but `nativeLibraryDir` is executable.

**APK install fails or the file is missing**
The artifact must be packaged as `lib/<abi>/libffmpeg.so`. It is intentionally
named `libffmpeg.so` (not `ffmpeg`) so Android's packaging and extraction rules
keep it. Do not rename it after staging.

**`libc++_shared.so` not found**
This build should never require it: Chromaprint is linked statically with the
NDK C++ runtime. Run `probe-ffmpeg.sh`; `NEEDED` must only list bionic system
libraries. If it lists `libc++_shared.so`, the FFmpeg link did not use the
generated `libchromaprint.pc`.

**Slow builds on `/mnt/c`**
The repository may live on OneDrive. Sources stay in `.prebuilt/src`, but build
trees and the NDK live in the WSL filesystem cache (`~/.cache/cue-native`) for
speed. You can also set `CUE_NATIVE_CACHE` to another ext4 path.
