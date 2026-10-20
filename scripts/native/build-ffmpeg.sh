#!/usr/bin/env bash
#
# Build a minimal, LGPL-only FFmpeg command-line executable for Android and
# stage it as app/src/main/jniLibs/<abi>/libffmpeg.so.
#
# Run this from WSL/Linux. The Windows NDK wrappers call clang.exe and cannot
# consume WSL paths, so a Linux NDK is provisioned automatically.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREBUILT_DIR="$REPO_ROOT/.prebuilt"
SRC_DIR="$PREBUILT_DIR/src"
CACHE_DIR="${CUE_NATIVE_CACHE:-$HOME/.cache/cue-native}"
WORK_DIR="$CACHE_DIR/work"
PREFIX_ROOT="$CACHE_DIR/prefix"
LOG_DIR="$CACHE_DIR/logs"
OUT_ROOT="$REPO_ROOT/app/src/main/jniLibs"
NDK_DIR="${CUE_NATIVE_NDK:-$CACHE_DIR/android-ndk-r25c}"

MIN_SDK=31
SUPPORTED_ABIS=(arm64-v8a x86_64)
JOBS="${JOBS:-8}"

FFMPEG_VERSION="7.1.5"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.xz"
FFMPEG_SHA256="de668509caf9e35e3cd162473441fdb29538c6d96ed080292b3cf9e6fc5d558f"

CHROMAPRINT_VERSION="1.5.1"
CHROMAPRINT_URL="https://github.com/acoustid/chromaprint/releases/download/v${CHROMAPRINT_VERSION}/chromaprint-${CHROMAPRINT_VERSION}.tar.gz"
CHROMAPRINT_SHA256="a1aad8fa3b8b18b78d3755b3767faff9abb67242e01b478ec9a64e190f335e1c"

LAME_VERSION="3.100"
LAME_URL="https://downloads.sourceforge.net/project/lame/lame/${LAME_VERSION}/lame-${LAME_VERSION}.tar.gz"
LAME_SHA256="ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e"

NDK_RELEASE="r25c"
NDK_VERSION="25.1.8937393"
NDK_ZIP="android-ndk-${NDK_RELEASE}-linux.zip"
NDK_URL="https://dl.google.com/android/repository/${NDK_ZIP}"
NDK_SHA256="769ee342ea75f80619d985c2da990c48b3d8eaf45f48783a2d48870d04b46108"

FFMPEG_SRC="$SRC_DIR/ffmpeg-${FFMPEG_VERSION}"
CHROMAPRINT_SRC="$SRC_DIR/chromaprint-${CHROMAPRINT_VERSION}"
LAME_SRC="$SRC_DIR/lame-${LAME_VERSION}"

ABIS=()
CLEAN=0
NDK_ARG=""

log() {
    printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"
}

section() {
    printf '\n===== %s =====\n' "$*"
}

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

usage() {
    cat <<'EOF'
Usage: scripts/native/build-ffmpeg.sh [options]

Options:
  --abi <arm64-v8a|x86_64|all>  ABI to build (repeatable; default: all)
  --clean                       Remove build dirs and staged .so for the selected ABI(s)
  --jobs <n>                    Parallel make/CMake jobs (default: 8)
  --ndk <path>                  Linux Android NDK root (default: auto-provisioned r25c)
  -h, --help                    Show this help

Environment:
  CUE_NATIVE_CACHE   Cache root for NDK, build trees, prefixes (default: ~/.cache/cue-native)
  CUE_NATIVE_NDK     Linux NDK root, same as --ndk
  ANDROID_NDK_HOME   Used when CUE_NATIVE_NDK/--ndk is not set

Sources are downloaded into .prebuilt/src. Compiled artifacts go to
app/src/main/jniLibs/<abi>/libffmpeg.so and are never committed.
EOF
}

add_abi() {
    local abi="$1" existing
    for existing in "${ABIS[@]:-}"; do
        [ "$existing" = "$abi" ] && return 0
    done
    ABIS+=("$abi")
}

parse_args() {
    while [ "$#" -gt 0 ]; do
        case "$1" in
            --abi)
                [ "$#" -ge 2 ] || die "--abi requires a value"
                case "$2" in
                    all) ABIS=("${SUPPORTED_ABIS[@]}") ;;
                    arm64-v8a|x86_64) add_abi "$2" ;;
                    *) die "unsupported ABI: $2" ;;
                esac
                shift 2
                ;;
            --abi=*)
                case "${1#*=}" in
                    all) ABIS=("${SUPPORTED_ABIS[@]}") ;;
                    arm64-v8a|x86_64) add_abi "${1#*=}" ;;
                    *) die "unsupported ABI: ${1#*=}" ;;
                esac
                shift
                ;;
            --clean)
                CLEAN=1
                shift
                ;;
            --jobs)
                [ "$#" -ge 2 ] || die "--jobs requires a value"
                JOBS="$2"
                shift 2
                ;;
            --jobs=*)
                JOBS="${1#*=}"
                shift
                ;;
            --ndk)
                [ "$#" -ge 2 ] || die "--ndk requires a value"
                NDK_ARG="$2"
                shift 2
                ;;
            --ndk=*)
                NDK_ARG="${1#*=}"
                shift
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                die "unknown option: $1 (try --help)"
                ;;
        esac
    done

    if [ "${#ABIS[@]}" -eq 0 ]; then
        ABIS=("${SUPPORTED_ABIS[@]}")
    fi

    case "$JOBS" in
        ''|*[!0-9]*) die "--jobs must be a positive integer" ;;
    esac
    [ "$JOBS" -gt 0 ] || die "--jobs must be a positive integer"
}

require_cmd() {
    local cmd
    for cmd in "$@"; do
        command -v "$cmd" >/dev/null 2>&1 || die "required command not found: $cmd"
    done
}

fetch() {
    local url="$1" sha256="$2" dest="$3"
    mkdir -p "$(dirname "$dest")"

    if [ -f "$dest" ] && printf '%s  %s\n' "$sha256" "$dest" | sha256sum -c - >/dev/null 2>&1; then
        log "cached $(basename "$dest")"
        return 0
    fi

    log "downloading $(basename "$dest")"
    rm -f "$dest.part"
    curl --fail --location --retry 3 --retry-delay 2 --connect-timeout 30 \
        --output "$dest.part" "$url"
    if ! printf '%s  %s\n' "$sha256" "$dest.part" | sha256sum -c - >/dev/null 2>&1; then
        rm -f "$dest.part"
        die "SHA-256 mismatch for $url"
    fi
    mv "$dest.part" "$dest"
}

extract_source() {
    local archive="$1" dest="$2" version="$3"
    local marker="$dest/.cue-source-$version"
    if [ -f "$marker" ]; then
        log "using extracted $(basename "$dest")"
        return 0
    fi
    log "extracting $(basename "$archive")"
    rm -rf "$dest"
    mkdir -p "$dest"
    tar -xf "$archive" -C "$dest" --strip-components=1
    : > "$marker"
}

ndk_is_valid() {
    local root="$1"
    [ -x "$root/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ] &&
        [ -x "$root/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android31-clang" ] &&
        [ -d "$root/toolchains/llvm/prebuilt/linux-x86_64/sysroot" ]
}

extract_ndk() {
    local zip="$1" dest="$2" tmp="$CACHE_DIR/.ndk-extract"

    require_cmd python3
    rm -rf "$tmp"
    mkdir -p "$tmp"

    python3 - "$zip" "$tmp" <<'PY'
import os
import stat
import sys
import zipfile

archive, dest = sys.argv[1], sys.argv[2]
prefix = "android-ndk-r25c/"

with zipfile.ZipFile(archive) as zf:
    for info in zf.infolist():
        if not info.filename.startswith(prefix):
            continue
        rel = info.filename[len(prefix):]
        if not rel:
            continue
        out = os.path.join(dest, rel)
        mode = (info.external_attr >> 16) & 0xFFFF

        if stat.S_ISLNK(mode):
            target = zf.read(info).decode("utf-8")
            os.makedirs(os.path.dirname(out), exist_ok=True)
            if os.path.lexists(out):
                os.remove(out)
            os.symlink(target, out)
        elif info.is_dir():
            os.makedirs(out, exist_ok=True)
        else:
            os.makedirs(os.path.dirname(out), exist_ok=True)
            with zf.open(info) as src, open(out, "wb") as dst:
                while True:
                    chunk = src.read(1024 * 1024)
                    if not chunk:
                        break
                    dst.write(chunk)
            perms = stat.S_IMODE(mode)
            if perms:
                os.chmod(out, perms)
PY

    rm -rf "$dest"
    mv "$tmp" "$dest"
}

resolve_ndk() {
    local candidate="${NDK_ARG:-${CUE_NATIVE_NDK:-${ANDROID_NDK_HOME:-}}}"

    if [ -n "$candidate" ]; then
        if ndk_is_valid "$candidate"; then
            NDK_DIR="$candidate"
            log "using NDK at $NDK_DIR"
            return 0
        fi
        die "NDK at '$candidate' has no linux-x86_64 host toolchain. The Windows NDK wrappers invoke clang.exe and cannot read WSL paths. Unset ANDROID_NDK_HOME/--ndk or point it at a Linux NDK."
    fi

    if ndk_is_valid "$NDK_DIR"; then
        log "using NDK at $NDK_DIR"
        return 0
    fi

    log "Linux NDK ${NDK_RELEASE} (${NDK_VERSION}) not found; downloading to $CACHE_DIR"
    fetch "$NDK_URL" "$NDK_SHA256" "$CACHE_DIR/$NDK_ZIP"
    log "extracting $NDK_ZIP"
    extract_ndk "$CACHE_DIR/$NDK_ZIP" "$NDK_DIR"
    ndk_is_valid "$NDK_DIR" || die "NDK extraction did not produce a usable Linux toolchain"
    log "using NDK at $NDK_DIR"
}

common_cmake_flags() {
    local prefix="$1"
    printf '%s\n' \
        "-DCMAKE_TOOLCHAIN_FILE=$NDK_DIR/build/cmake/android.toolchain.cmake" \
        "-DANDROID_ABI=$ANDROID_ABI" \
        "-DANDROID_PLATFORM=android-$MIN_SDK" \
        "-DCMAKE_INSTALL_PREFIX=$prefix" \
        "-DCMAKE_BUILD_TYPE=Release"
}

build_lame() {
    local prefix="$1"
    local stamp="$prefix/.lame-$LAME_VERSION"
    local log_prefix="$LOG_DIR/$ABI-lame"

    if [ -f "$prefix/lib/libmp3lame.a" ] && [ -f "$prefix/include/lame/lame.h" ] && [ -f "$stamp" ]; then
        log "LAME $LAME_VERSION already built for $ABI"
        return 0
    fi

    section "Building LAME $LAME_VERSION for $ABI"
    (
        cd "$LAME_SRC"
        if [ -f Makefile ]; then
            make distclean >/dev/null 2>&1 || true
        fi
        sh ./configure \
            --prefix="$prefix" \
            --host="$TARGET_TRIPLE" \
            --build="$(gcc -dumpmachine)" \
            --disable-shared \
            --enable-static \
            --disable-frontend \
            CC="$CC" \
            AR="$AR" \
            RANLIB="$RANLIB" \
            CFLAGS="-O2 -fPIC -Wno-implicit-function-declaration -Wno-int-conversion"
    ) > "$log_prefix-configure.log" 2>&1 || {
        tail -n 40 "$log_prefix-configure.log" >&2 || true
        die "LAME configure failed (see $log_prefix-configure.log)"
    }

    make -C "$LAME_SRC" -j"$JOBS" > "$log_prefix-build.log" 2>&1 || {
        tail -n 40 "$log_prefix-build.log" >&2 || true
        die "LAME build failed (see $log_prefix-build.log)"
    }
    make -C "$LAME_SRC" install > "$log_prefix-install.log" 2>&1 || {
        tail -n 40 "$log_prefix-install.log" >&2 || true
        die "LAME install failed (see $log_prefix-install.log)"
    }

    : > "$stamp"
}

write_chromaprint_pc() {
    local prefix="$1"
    mkdir -p "$prefix/lib/pkgconfig"
    cat > "$prefix/lib/pkgconfig/libchromaprint.pc" <<EOF
prefix=$prefix
exec_prefix=\${prefix}
libdir=\${prefix}/lib
includedir=\${prefix}/include

Name: libchromaprint
Description: Audio fingerprint library
Version: $CHROMAPRINT_VERSION
Libs: -L\${libdir} -lchromaprint -lc++_static -lc++abi
Libs.private: -lm
Cflags: -I\${includedir}
EOF
}

build_chromaprint() {
    local prefix="$1"
    local stamp="$prefix/.chromaprint-$CHROMAPRINT_VERSION"
    local build="$WORK_DIR/$ABI/chromaprint"
    local log_prefix="$LOG_DIR/$ABI-chromaprint"

    if [ -f "$prefix/lib/libchromaprint.a" ] && [ -f "$stamp" ]; then
        log "Chromaprint $CHROMAPRINT_VERSION already built for $ABI"
        return 0
    fi

    section "Building Chromaprint $CHROMAPRINT_VERSION for $ABI"
    rm -rf "$build"

    cmake -S "$CHROMAPRINT_SRC" -B "$build" \
        $(common_cmake_flags "$prefix") \
        -DBUILD_SHARED_LIBS=OFF \
        -DBUILD_TOOLS=OFF \
        -DBUILD_TESTS=OFF \
        -DFFT_LIB=kissfft \
        -DKISSFFT_SOURCE_DIR="$CHROMAPRINT_SRC/src/3rdparty/kissfft" \
        -DCMAKE_DISABLE_FIND_PACKAGE_FFmpeg=ON \
        -DCMAKE_DISABLE_FIND_PACKAGE_FFTW3=ON \
        -DCMAKE_C_FLAGS="-O2 -fPIC" \
        -DCMAKE_CXX_FLAGS="-O2 -fPIC" \
        > "$log_prefix-configure.log" 2>&1 || {
        tail -n 40 "$log_prefix-configure.log" >&2 || true
        die "Chromaprint configure failed (see $log_prefix-configure.log)"
    }

    cmake --build "$build" --parallel "$JOBS" > "$log_prefix-build.log" 2>&1 || {
        tail -n 40 "$log_prefix-build.log" >&2 || true
        die "Chromaprint build failed (see $log_prefix-build.log)"
    }
    cmake --install "$build" > "$log_prefix-install.log" 2>&1 || {
        tail -n 40 "$log_prefix-install.log" >&2 || true
        die "Chromaprint install failed (see $log_prefix-install.log)"
    }

    write_chromaprint_pc "$prefix"
    : > "$stamp"
}

build_ffmpeg() {
    local prefix="$1" out="$2"
    local build="$WORK_DIR/$ABI/ffmpeg"
    local config_stamp="$WORK_DIR/$ABI/ffmpeg.configure"
    local log_prefix="$LOG_DIR/$ABI-ffmpeg"
    local extra=()

    case "$ABI" in
        x86_64) extra+=(--disable-x86asm) ;;
    esac

    local configure=(
        bash "$FFMPEG_SRC/configure"
        --prefix="$prefix"
        --target-os=android
        --arch="$FF_ARCH"
        --cpu="$FF_CPU"
        --enable-cross-compile
        --pkg-config=pkg-config
        --cc="$CC"
        --cxx="$CXX"
        --ld="$CXX"
        --ar="$AR"
        --ranlib="$RANLIB"
        --strip="$STRIP"
        --nm="$NM"
        --sysroot="$SYSROOT"
        --host-cc=gcc
        --disable-shared
        --enable-static
        --enable-pic
        --disable-everything
        --enable-ffmpeg
        --disable-ffplay
        --disable-ffprobe
        --disable-doc
        --disable-debug
        --disable-network
        --disable-autodetect
        --enable-small
        --disable-avdevice
        --disable-postproc
        --disable-swscale
        --enable-protocol=file,pipe
        --enable-demuxer=mov,mp3
        --enable-parser=aac,mpegaudio
        --enable-decoder=aac,mp3
        --enable-encoder=libmp3lame
        --enable-muxer=mp3,chromaprint
        --enable-filter=aresample,anull,atrim,asetpts
        --enable-libmp3lame
        --enable-chromaprint
        --extra-cflags="-O2 -fPIE -I$prefix/include"
        --extra-ldflags="-pie -L$prefix/lib -static-libstdc++"
        --extra-libs="-lm"
        "${extra[@]}"
    )

    local config_hash
    config_hash="$(printf '%s\n' "${configure[@]}" | sha256sum | cut -d' ' -f1)"

    section "Building FFmpeg $FFMPEG_VERSION for $ABI"
    if [ -f "$build/ffmpeg" ] && [ -f "$config_stamp" ] && [ "$(cat "$config_stamp")" = "$config_hash" ]; then
        log "reusing FFmpeg configure for $ABI"
    else
        rm -rf "$build"
        mkdir -p "$build"
        (
            cd "$build"
            "${configure[@]}"
        ) > "$log_prefix-configure.log" 2>&1 || {
            tail -n 60 "$log_prefix-configure.log" >&2 || true
            die "FFmpeg configure failed (see $log_prefix-configure.log)"
        }
        printf '%s\n' "$config_hash" > "$config_stamp"
    fi

    make -C "$build" -j"$JOBS" > "$log_prefix-build.log" 2>&1 || {
        tail -n 60 "$log_prefix-build.log" >&2 || true
        die "FFmpeg build failed (see $log_prefix-build.log)"
    }

    [ -x "$build/ffmpeg" ] || die "FFmpeg build did not produce $build/ffmpeg"
    mkdir -p "$(dirname "$out")"
    cp "$build/ffmpeg" "$out"
    "$STRIP" --strip-unneeded "$out"
    chmod 0755 "$out"

    local needed lib
    needed="$("$TOOLCHAIN/bin/llvm-readelf" -d "$out" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p')"
    for lib in $needed; do
        case "$lib" in
            libc.so|libm.so|libdl.so|liblog.so) ;;
            *) die "$ABI artifact has unexpected shared dependency: $lib" ;;
        esac
    done

    local strings_out
    strings_out="$(strings -a "$out")"
    case "$strings_out" in
        *--enable-chromaprint*) ;;
        *) die "$ABI artifact does not contain the expected chromaprint configuration" ;;
    esac
    case "$strings_out" in
        *--enable-libmp3lame*) ;;
        *) die "$ABI artifact does not contain the expected libmp3lame configuration" ;;
    esac
    if grep -Eq -- '--enable-(gpl|nonfree)' <<< "$strings_out"; then
        die "$ABI artifact was built with GPL/nonfree code"
    fi

    local sha256 size
    sha256="$(sha256sum "$out" | cut -d' ' -f1)"
    size="$(stat -c %s "$out")"

    mkdir -p "$PREBUILT_DIR/build-info"
    {
        printf 'abi=%s\n' "$ABI"
        printf 'ffmpeg=%s\n' "$FFMPEG_VERSION"
        printf 'chromaprint=%s\n' "$CHROMAPRINT_VERSION"
        printf 'lame=%s\n' "$LAME_VERSION"
        printf 'ndk=%s\n' "$NDK_VERSION"
        printf 'min_sdk=%s\n' "$MIN_SDK"
        printf 'configure=%s\n' "${configure[*]}"
        printf 'size=%s\n' "$size"
        printf 'sha256=%s\n' "$sha256"
    } > "$PREBUILT_DIR/build-info/$ABI.txt"

    log "staged $out ($(numfmt --to=iec "$size" 2>/dev/null || echo "$size bytes"), sha256 $sha256)"
}

clean_abi() {
    local abi="$1"
    rm -rf "$WORK_DIR/$abi" "$PREFIX_ROOT/$abi" "$OUT_ROOT/$abi/libffmpeg.so"
    rm -f "$PREBUILT_DIR/build-info/$abi.txt"
    rmdir "$OUT_ROOT/$abi" 2>/dev/null || true
    log "cleaned build outputs for $abi"
}

copy_licenses() {
    local chromaprint_license
    mkdir -p "$PREBUILT_DIR/licenses"
    cp -f "$FFMPEG_SRC/COPYING.LGPLv2.1" "$PREBUILT_DIR/licenses/FFmpeg-COPYING.LGPLv2.1.txt"
    cp -f "$FFMPEG_SRC/COPYING.LGPLv3" "$PREBUILT_DIR/licenses/FFmpeg-COPYING.LGPLv3.txt"
    chromaprint_license="$CHROMAPRINT_SRC/COPYING"
    [ -f "$chromaprint_license" ] || chromaprint_license="$CHROMAPRINT_SRC/LICENSE.md"
    cp -f "$chromaprint_license" "$PREBUILT_DIR/licenses/Chromaprint-COPYING.txt"
    cp -f "$LAME_SRC/COPYING" "$PREBUILT_DIR/licenses/LAME-COPYING.txt"
}

setup_toolchain() {
    case "$ABI" in
        arm64-v8a)
            ANDROID_ABI="arm64-v8a"
            FF_ARCH="aarch64"
            FF_CPU="armv8-a"
            TARGET_TRIPLE="aarch64-linux-android"
            ;;
        x86_64)
            ANDROID_ABI="x86_64"
            FF_ARCH="x86_64"
            FF_CPU="x86-64"
            TARGET_TRIPLE="x86_64-linux-android"
            ;;
        *)
            die "unsupported ABI: $ABI"
            ;;
    esac

    TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
    SYSROOT="$TOOLCHAIN/sysroot"
    CC="$TOOLCHAIN/bin/${TARGET_TRIPLE}${MIN_SDK}-clang"
    CXX="$TOOLCHAIN/bin/${TARGET_TRIPLE}${MIN_SDK}-clang++"
    AR="$TOOLCHAIN/bin/llvm-ar"
    RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
    STRIP="$TOOLCHAIN/bin/llvm-strip"
    NM="$TOOLCHAIN/bin/llvm-nm"

    [ -x "$CC" ] || die "missing compiler: $CC"
    [ -x "$CXX" ] || die "missing C++ compiler: $CXX"

    PREFIX="$PREFIX_ROOT/$ABI"
    mkdir -p "$PREFIX" "$WORK_DIR/$ABI" "$LOG_DIR"
    export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig"
    export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig"
}

build_abi() {
    local abi="$1"
    ABI="$abi"
    setup_toolchain

    local prefix="$PREFIX_ROOT/$abi"
    local out="$OUT_ROOT/$abi/libffmpeg.so"

    build_lame "$prefix"
    build_chromaprint "$prefix"
    build_ffmpeg "$prefix" "$out"
}

main() {
    parse_args "$@"

    if [ "$CLEAN" -eq 1 ]; then
        for abi in "${ABIS[@]}"; do
            clean_abi "$abi"
        done
        exit 0
    fi

    require_cmd bash curl tar xz python3 cmake make gcc pkg-config sha256sum file strings

    mkdir -p "$SRC_DIR" "$CACHE_DIR" "$WORK_DIR" "$PREFIX_ROOT" "$LOG_DIR" "$PREBUILT_DIR"

    section "Fetching pinned sources"
    fetch "$FFMPEG_URL" "$FFMPEG_SHA256" "$SRC_DIR/ffmpeg-${FFMPEG_VERSION}.tar.xz"
    fetch "$CHROMAPRINT_URL" "$CHROMAPRINT_SHA256" "$SRC_DIR/chromaprint-${CHROMAPRINT_VERSION}.tar.gz"
    fetch "$LAME_URL" "$LAME_SHA256" "$SRC_DIR/lame-${LAME_VERSION}.tar.gz"

    extract_source "$SRC_DIR/ffmpeg-${FFMPEG_VERSION}.tar.xz" "$FFMPEG_SRC" "$FFMPEG_VERSION"
    extract_source "$SRC_DIR/chromaprint-${CHROMAPRINT_VERSION}.tar.gz" "$CHROMAPRINT_SRC" "$CHROMAPRINT_VERSION"
    extract_source "$SRC_DIR/lame-${LAME_VERSION}.tar.gz" "$LAME_SRC" "$LAME_VERSION"
    copy_licenses

    resolve_ndk

    for abi in "${ABIS[@]}"; do
        build_abi "$abi"
    done

    section "Artifacts"
    for abi in "${ABIS[@]}"; do
        out="$OUT_ROOT/$abi/libffmpeg.so"
        [ -f "$out" ] || die "missing artifact: $out"
        ls -lh "$out"
        sha256sum "$out"
        file "$out"
    done
}

main "$@"
