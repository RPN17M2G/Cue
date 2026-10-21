#!/usr/bin/env bash
#
# Build a minimal, LGPL-only FFmpeg command-line executable plus dynamically
# linked FFmpeg/LAME/Chromaprint shared libraries for Android, and stage them
# under app/src/main/jniLibs/<abi>/.
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
LICENSE_DIR="$REPO_ROOT/app/src/main/assets/licenses"
NDK_DIR="${CUE_NATIVE_NDK:-$CACHE_DIR/android-ndk-r25c}"

MIN_SDK=31
SUPPORTED_ABIS=(arm64-v8a x86_64)
JOBS="${JOBS:-8}"

SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1735689600}"
export SOURCE_DATE_EPOCH
export LC_ALL=C
export TZ=UTC
REPRO_CFLAGS="-ffile-prefix-map=$REPO_ROOT=. -ffile-prefix-map=$CACHE_DIR=."

FFMPEG_SHARED_LIBS=(libavcodec.so libavformat.so libavfilter.so libavutil.so libswresample.so)
LGPL_SHARED_LIBS=(libchromaprint.so libmp3lame.so)
SHARED_LIBS=("${FFMPEG_SHARED_LIBS[@]}" "${LGPL_SHARED_LIBS[@]}")

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
NDK_VERSION="25.2.9519653"
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
app/src/main/jniLibs/<abi>/*.so and are never committed. License texts are
written to app/src/main/assets/licenses/ and are committed.
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

    python3 - "$zip" "$tmp" "$NDK_RELEASE" <<'PY'
import os
import stat
import sys
import zipfile

archive, dest, release = sys.argv[1], sys.argv[2], sys.argv[3]
prefix = f"android-ndk-{release}/"

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
    local build="$WORK_DIR/$ABI/lame"
    local log_prefix="$LOG_DIR/$ABI-lame"

    if [ -f "$prefix/lib/libmp3lame.so" ] && [ -f "$prefix/include/lame/lame.h" ] && [ -f "$stamp" ]; then
        log "LAME $LAME_VERSION already built for $ABI"
        return 0
    fi

    section "Building LAME $LAME_VERSION for $ABI"
    rm -rf "$build"
    mkdir -p "$build"
    tar -xf "$SRC_DIR/lame-${LAME_VERSION}.tar.gz" -C "$build" --strip-components=1

    (
        cd "$build"
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
            CFLAGS="-O2 -fPIC $REPRO_CFLAGS -Wno-implicit-function-declaration -Wno-int-conversion"
    ) > "$log_prefix-configure.log" 2>&1 || {
        tail -n 40 "$log_prefix-configure.log" >&2 || true
        die "LAME configure failed (see $log_prefix-configure.log)"
    }

    make -C "$build" -j"$JOBS" > "$log_prefix-build.log" 2>&1 || {
        tail -n 40 "$log_prefix-build.log" >&2 || true
        die "LAME build failed (see $log_prefix-build.log)"
    }
    make -C "$build" install > "$log_prefix-install.log" 2>&1 || {
        tail -n 40 "$log_prefix-install.log" >&2 || true
        die "LAME install failed (see $log_prefix-install.log)"
    }

    "$CC" -shared -o "$prefix/lib/libmp3lame.so" \
        -Wl,-soname,libmp3lame.so \
        -Wl,-z,max-page-size=16384 \
        -Wl,--no-undefined \
        -Wl,--whole-archive "$prefix/lib/libmp3lame.a" \
        -Wl,--no-whole-archive \
        -lm \
        > "$log_prefix-link.log" 2>&1 || {
        tail -n 40 "$log_prefix-link.log" >&2 || true
        die "LAME shared-library link failed (see $log_prefix-link.log)"
    }

    rm -f "$prefix/lib/libmp3lame.a" "$prefix/lib/libmp3lame.la"
    "$STRIP" --strip-unneeded "$prefix/lib/libmp3lame.so"
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
Libs: -L\${libdir} -lchromaprint
Libs.private: -lc++_static -lc++abi -lm
Cflags: -I\${includedir}
EOF
}

build_chromaprint() {
    local prefix="$1"
    local stamp="$prefix/.chromaprint-$CHROMAPRINT_VERSION"
    local build="$WORK_DIR/$ABI/chromaprint"
    local log_prefix="$LOG_DIR/$ABI-chromaprint"

    if [ -f "$prefix/lib/libchromaprint.so" ] && [ -f "$stamp" ]; then
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
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DCMAKE_C_FLAGS="-O2 -fPIC $REPRO_CFLAGS" \
        -DCMAKE_CXX_FLAGS="-O2 -fPIC $REPRO_CFLAGS" \
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

    "$CXX" -shared -o "$prefix/lib/libchromaprint.so" \
        -Wl,-soname,libchromaprint.so \
        -Wl,-z,max-page-size=16384 \
        -Wl,--no-undefined \
        -Wl,--whole-archive "$prefix/lib/libchromaprint.a" \
        -Wl,--no-whole-archive \
        -nostdlib++ -lc++_static -lc++abi -lm -ldl \
        > "$log_prefix-link.log" 2>&1 || {
        tail -n 40 "$log_prefix-link.log" >&2 || true
        die "Chromaprint shared-library link failed (see $log_prefix-link.log)"
    }

    rm -f "$prefix/lib/libchromaprint.a"
    "$STRIP" --strip-unneeded "$prefix/lib/libchromaprint.so"
    write_chromaprint_pc "$prefix"
    : > "$stamp"
}

is_allowed_needed() {
    case "$1" in
        libc.so|libm.so|libdl.so|liblog.so|libandroid.so|libmediandk.so) return 0 ;;
        libavcodec.so|libavformat.so|libavfilter.so|libavutil.so|libswresample.so) return 0 ;;
        libchromaprint.so|libmp3lame.so) return 0 ;;
    esac
    return 1
}

needed_of() {
    "$TOOLCHAIN/bin/llvm-readelf" -d "$1" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p'
}

soname_of() {
    "$TOOLCHAIN/bin/llvm-readelf" -d "$1" | sed -n 's/.*Library soname: \[\(.*\)\]/\1/p'
}

load_alignments_of() {
    "$TOOLCHAIN/bin/llvm-readelf" -lW "$1" | awk '$1 == "LOAD" {print $NF}'
}

verify_artifact() {
    local file="$1" label="$2" needed lib align
    [ -f "$file" ] || die "missing artifact: $file"
    needed="$(needed_of "$file")"
    for lib in $needed; do
        is_allowed_needed "$lib" || die "$label has unexpected NEEDED: $lib"
    done
    while read -r align; do
        [ -n "$align" ] || continue
        [ $((align)) -ge 16384 ] || die "$label LOAD alignment $align is below 0x4000"
    done < <(load_alignments_of "$file")
}

stage_artifacts() {
    local prefix="$1" outdir="$2"
    local ffmpeg_bin="$WORK_DIR/$ABI/ffmpeg/ffmpeg"
    local lib

    [ -x "$ffmpeg_bin" ] || die "FFmpeg build did not produce $ffmpeg_bin"
    mkdir -p "$outdir"
    rm -f "$outdir"/*.so
    cp "$ffmpeg_bin" "$outdir/libffmpeg.so"
    for lib in "${SHARED_LIBS[@]}"; do
        [ -f "$prefix/lib/$lib" ] || die "missing built library: $prefix/lib/$lib"
        cp "$prefix/lib/$lib" "$outdir/$lib"
    done
    chmod 0755 "$outdir"/*.so
    for lib in "$outdir"/*.so; do
        "$STRIP" --strip-unneeded "$lib"
    done
}

verify_abi_artifacts() {
    local outdir="$1"
    local ffmpeg="$outdir/libffmpeg.so"
    local lib needed soname

    verify_artifact "$ffmpeg" "libffmpeg.so"
    needed="$(needed_of "$ffmpeg")"
    for lib in "${SHARED_LIBS[@]}"; do
        grep -Fxq "$lib" <<< "$needed" ||
            die "libffmpeg.so does not NEED $lib"
    done
    if grep -Fxq 'libc++_shared.so' <<< "$needed"; then
        die "libffmpeg.so depends on libc++_shared.so"
    fi

    for lib in "${SHARED_LIBS[@]}"; do
        verify_artifact "$outdir/$lib" "$lib"
        soname="$(soname_of "$outdir/$lib")"
        [ "$soname" = "$lib" ] || die "$lib has soname '$soname', expected '$lib'"
    done
}

write_build_info() {
    local abi="$1" configure_line="$2" outdir="$3"
    local f base size sha needed align ndk_revision
    mkdir -p "$PREBUILT_DIR/build-info"

    ndk_revision="$(sed -n 's/^Pkg.Revision *= *//p' "$NDK_DIR/source.properties" 2>/dev/null | head -n1)"
    [ -n "$ndk_revision" ] || ndk_revision="unknown"

    {
        printf 'abi=%s\n' "$abi"
        printf 'ffmpeg=%s\n' "$FFMPEG_VERSION"
        printf 'chromaprint=%s\n' "$CHROMAPRINT_VERSION"
        printf 'lame=%s\n' "$LAME_VERSION"
        printf 'ndk=%s\n' "$NDK_VERSION"
        printf 'ndk_revision=%s\n' "$ndk_revision"
        printf 'min_sdk=%s\n' "$MIN_SDK"
        printf 'host_cc=%s\n' "$(gcc --version | head -n1)"
        printf 'host_make=%s\n' "$(make --version | head -n1)"
        printf 'host_cmake=%s\n' "$(cmake --version | head -n1)"
        printf 'host_pkg_config=%s\n' "$(pkg-config --version)"
        printf 'source_date_epoch=%s\n' "$SOURCE_DATE_EPOCH"
        printf 'repro_cflags=%s\n' "$REPRO_CFLAGS"
        printf 'configure=%s\n' "$configure_line"
        for f in "$outdir"/*.so; do
            base="$(basename "$f")"
            size="$(stat -c %s "$f")"
            sha="$(sha256sum "$f" | cut -d' ' -f1)"
            needed="$(needed_of "$f" | paste -sd, -)"
            align="$(load_alignments_of "$f" | paste -sd, -)"
            printf '%s_size=%s\n' "$base" "$size"
            printf '%s_sha256=%s\n' "$base" "$sha"
            printf '%s_needed=%s\n' "$base" "$needed"
            printf '%s_load_align=%s\n' "$base" "$align"
        done
    } > "$PREBUILT_DIR/build-info/$abi.txt"
}

build_ffmpeg() {
    local prefix="$1" outdir="$2"
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
        --ld="$CC"
        --ar="$AR"
        --ranlib="$RANLIB"
        --strip="$STRIP"
        --nm="$NM"
        --sysroot="$SYSROOT"
        --host-cc=gcc
        --enable-shared
        --disable-static
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
        --enable-demuxer=mov,mp3,matroska,webm,ogg
        --enable-parser=aac,mpegaudio,opus,vorbis
        --enable-decoder=aac,mp3,opus,vorbis
        --enable-encoder=libmp3lame,pcm_s16le
        --enable-muxer=mp3,chromaprint
        --enable-filter=aresample,anull,atrim,asetpts
        --enable-libmp3lame
        --enable-chromaprint
        --extra-cflags="-O2 $REPRO_CFLAGS -I$prefix/include"
        --extra-ldflags="-L$prefix/lib -Wl,-z,max-page-size=16384"
        --extra-ldsoflags="-Wl,-z,max-page-size=16384"
        --extra-ldexeflags="-pie -Wl,-z,origin -Wl,-rpath,'\\\$\\\$ORIGIN' -Wl,-z,max-page-size=16384 -L$prefix/lib -Wl,--no-as-needed -lchromaprint -lmp3lame"
        --extra-libs="-lm"
        "${extra[@]}"
    )

    local config_hash
    config_hash="$(
        {
            printf 'SOURCE_DATE_EPOCH=%s\n' "$SOURCE_DATE_EPOCH"
            printf '%s\n' "${configure[@]}"
        } | sha256sum | cut -d' ' -f1
    )"

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
    make -C "$build" install > "$log_prefix-install.log" 2>&1 || {
        tail -n 60 "$log_prefix-install.log" >&2 || true
        die "FFmpeg install failed (see $log_prefix-install.log)"
    }

    stage_artifacts "$prefix" "$outdir"
    verify_abi_artifacts "$outdir"
    write_build_info "$ABI" "${configure[*]}" "$outdir"

    local total
    total="$(du -ch "$outdir"/*.so | tail -n1 | cut -f1)"
    log "staged $outdir/libffmpeg.so and ${#SHARED_LIBS[@]} shared libraries ($total total)"
}

clean_abi() {
    local abi="$1"
    rm -rf "$WORK_DIR/$abi" "$PREFIX_ROOT/$abi"
    rm -f "$OUT_ROOT/$abi"/*.so
    rm -f "$PREBUILT_DIR/build-info/$abi.txt"
    rmdir "$OUT_ROOT/$abi" 2>/dev/null || true
    log "cleaned build outputs for $abi"
}

copy_licenses() {
    local kissfft_src="$CHROMAPRINT_SRC/src/3rdparty/kissfft"
    mkdir -p "$LICENSE_DIR"

    cp -f "$FFMPEG_SRC/COPYING.LGPLv2.1" "$LICENSE_DIR/FFmpeg-COPYING.LGPLv2.1.txt"
    cp -f "$FFMPEG_SRC/LICENSE.md" "$LICENSE_DIR/FFmpeg-LICENSE.md"
    cp -f "$LAME_SRC/COPYING" "$LICENSE_DIR/LAME-COPYING.txt"
    cp -f "$CHROMAPRINT_SRC/LICENSE.md" "$LICENSE_DIR/Chromaprint-LICENSE.md"
    {
        cat "$kissfft_src/COPYING"
        printf '\n'
        cat "$kissfft_src/LICENSES/BSD-3-Clause"
    } > "$LICENSE_DIR/KissFFT-BSD-3-Clause.txt"

    cat > "$LICENSE_DIR/NOTICE.txt" <<EOF
Cue third-party notices
=======================

Cue ships the following LGPL components as separate, replaceable shared
libraries in app/src/main/jniLibs/<abi>/. The corresponding notices are in
app/src/main/assets/licenses/:

- FFmpeg $FFMPEG_VERSION (LGPL-2.1-or-later)
  Copyright (c) 2000-2025 the FFmpeg developers.
  Source: $FFMPEG_URL
  License: FFmpeg-COPYING.LGPLv2.1.txt and FFmpeg-LICENSE.md

- LAME $LAME_VERSION (LGPL-2.0-or-later)
  Copyright (c) 1999-2011 by The LAME Project; copyright (c) 1999, 2000, 2001
  by Mark Taylor.
  Source: $LAME_URL
  License: LAME-COPYING.txt

- Chromaprint $CHROMAPRINT_VERSION (LGPL-2.1-or-later; includes MIT-licensed
  Chromaprint code and LGPL-licensed FFmpeg code)
  Copyright (C) 2010-2016 Lukas Lalinsky.
  Source: $CHROMAPRINT_URL
  License: Chromaprint-LICENSE.md and FFmpeg-COPYING.LGPLv2.1.txt

- KissFFT, bundled with Chromaprint $CHROMAPRINT_VERSION (BSD-3-Clause)
  Copyright (c) 2003-2010 Mark Borgerding.
  Source: $CHROMAPRINT_URL
  License: KissFFT-BSD-3-Clause.txt

Compliance statement
--------------------
The LGPL components are dynamically linked. The executable libffmpeg.so and the
FFmpeg shared libraries depend at runtime on separate libchromaprint.so and
libmp3lame.so files shipped in the same APK native library directory. A user can
replace those shared libraries with a compatible build without relinking
libffmpeg.so. The complete corresponding source is available from the pinned
URLs above; the build is reproducible with scripts/native/build-ffmpeg.sh, which
is part of this repository. No GPL or nonfree FFmpeg components are enabled.

These notices are packaged as app assets. The in-app notices screen is a later
integration milestone; this file and the license texts must remain in the APK.
EOF
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
    local outdir="$OUT_ROOT/$abi"

    build_lame "$prefix"
    build_chromaprint "$prefix"
    build_ffmpeg "$prefix" "$outdir"
}

main() {
    parse_args "$@"

    if [ "$CLEAN" -eq 1 ]; then
        for abi in "${ABIS[@]}"; do
            clean_abi "$abi"
        done
        exit 0
    fi

    require_cmd bash curl tar xz python3 cmake make gcc pkg-config sha256sum file strings du

    mkdir -p "$SRC_DIR" "$CACHE_DIR" "$WORK_DIR" "$PREFIX_ROOT" "$LOG_DIR" "$PREBUILT_DIR" "$LICENSE_DIR"

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
        for out in "$OUT_ROOT/$abi"/*.so; do
            [ -f "$out" ] || die "missing artifact: $out"
            ls -lh "$out"
            sha256sum "$out"
            file "$out"
        done
    done
}

main "$@"
