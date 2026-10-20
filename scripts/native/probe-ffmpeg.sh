#!/usr/bin/env bash
#
# Verify the staged native artifacts without executing them.
#
# Android binaries are linked against bionic and cannot run on the Linux host.
# The runtime fingerprint/ffmpeg checks require an Android emulator or device
# and are deferred to the emulator milestone. This script checks ELF metadata,
# dynamic dependencies, 16 KB LOAD alignment, exported symbols, embedded
# configuration, and the packaged license notices instead.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_ROOT="$REPO_ROOT/app/src/main/jniLibs"
LICENSE_DIR="$REPO_ROOT/app/src/main/assets/licenses"
SUPPORTED_ABIS=(arm64-v8a x86_64)
ABIS=()
FAILURES=0

FFMPEG_SHARED_LIBS=(libavcodec.so libavformat.so libavfilter.so libavutil.so libswresample.so)
LGPL_SHARED_LIBS=(libchromaprint.so libmp3lame.so)
SHARED_LIBS=("${FFMPEG_SHARED_LIBS[@]}" "${LGPL_SHARED_LIBS[@]}")
LICENSE_FILES=(
    FFmpeg-COPYING.LGPLv2.1.txt
    FFmpeg-LICENSE.md
    LAME-COPYING.txt
    Chromaprint-LICENSE.md
    KissFFT-BSD-3-Clause.txt
    NOTICE.txt
)

log() {
    printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"
}

pass() {
    printf '  ok: %s\n' "$*"
}

fail() {
    printf '  FAIL: %s\n' "$*" >&2
    FAILURES=$((FAILURES + 1))
}

has() {
    case "$1" in
        *"$2"*) return 0 ;;
        *) return 1 ;;
    esac
}

expect_has() {
    if has "$1" "$2"; then
        pass "$3"
    else
        fail "$3"
    fi
}

usage() {
    cat <<'EOF'
Usage: scripts/native/probe-ffmpeg.sh [--abi <arm64-v8a|x86_64|all>]

Checks the executable and its dynamically linked FFmpeg/LGPL libraries:
file type, ELF machine/type, SONAME, Android interpreter, NEEDED libraries,
16 KB LOAD alignment, exported symbols, embedded configure line, component
strings, and the packaged license notices. Runtime execution is deferred.
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
                [ "$#" -ge 2 ] || { usage >&2; exit 2; }
                case "$2" in
                    all) ABIS=("${SUPPORTED_ABIS[@]}") ;;
                    arm64-v8a|x86_64) add_abi "$2" ;;
                    *) printf 'error: unsupported ABI: %s\n' "$2" >&2; exit 2 ;;
                esac
                shift 2
                ;;
            --abi=*)
                case "${1#*=}" in
                    all) ABIS=("${SUPPORTED_ABIS[@]}") ;;
                    arm64-v8a|x86_64) add_abi "${1#*=}" ;;
                    *) printf 'error: unsupported ABI: %s\n' "${1#*=}" >&2; exit 2 ;;
                esac
                shift
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                printf 'error: unknown option: %s (try --help)\n' "$1" >&2
                exit 2
                ;;
        esac
    done

    if [ "${#ABIS[@]}" -eq 0 ]; then
        ABIS=("${SUPPORTED_ABIS[@]}")
    fi
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
    readelf -d "$1" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p'
}

soname_of() {
    readelf -d "$1" | sed -n 's/.*Library soname: \[\(.*\)\]/\1/p'
}

runpath_of() {
    readelf -d "$1" | sed -n 's/.*Library runpath: \[\(.*\)\]/\1/p'
}

alignments_of() {
    readelf -lW "$1" | awk '$1 == "LOAD" {print $NF}'
}

check_file_header() {
    local file="$1" arch="$2" type_re="$3" label="$4"
    local file_out

    file_out="$(file -b "$file")"
    printf '  file: %s\n' "$file_out"
    has "$file_out" 'ELF 64-bit LSB' ||
        fail "$label is not a 64-bit ELF file"
    has "$file_out" "$arch" ||
        fail "$label file(1) does not report $arch"
    grep -Eq "$type_re" <<< "$file_out" ||
        fail "$label file(1) does not report the expected ELF type"
}

check_alignments() {
    local file="$1" label="$2" align
    while read -r align; do
        [ -n "$align" ] || continue
        if [ $((align)) -lt 16384 ]; then
            fail "$label LOAD alignment $align is below 0x4000"
            return 0
        fi
    done < <(alignments_of "$file")
    pass "$label LOAD segments are 16 KB aligned"
}

check_needed() {
    local file="$1" label="$2" needed lib
    needed="$(needed_of "$file")"
    printf '  NEEDED (%s):\n' "$label"
    if [ -z "$needed" ]; then
        printf '    <none>\n'
    else
        printf '    %s\n' $needed
    fi
    for lib in $needed; do
        if is_allowed_needed "$lib"; then
            pass "$label allowed dependency: $lib"
        else
            fail "$label unexpected dependency: $lib"
        fi
    done
}

check_required_needed() {
    local file="$1" label="$2" needed lib
    shift 2
    needed="$(needed_of "$file")"
    for lib in "$@"; do
        if grep -Fxq "$lib" <<< "$needed"; then
            pass "$label NEEDs $lib"
        else
            fail "$label does not NEED $lib"
        fi
    done
}

check_soname() {
    local file="$1" label="$2" expected="$3" soname
    soname="$(soname_of "$file")"
    if [ "$soname" = "$expected" ]; then
        pass "$label SONAME is $expected"
    else
        fail "$label SONAME is '$soname', expected '$expected'"
    fi
}

check_symbols() {
    local file="$1" label="$2" symbols symbol
    shift 2
    symbols="$(
        readelf -Ws "$file" |
            awk '$4 == "FUNC" && $7 != "UND" {print $8}' |
            sed 's/@.*//' |
            sort -u
    )"
    for symbol in "$@"; do
        if grep -Fxq "$symbol" <<< "$symbols"; then
            pass "$label exports $symbol"
        else
            fail "$label is missing symbol $symbol"
        fi
    done
}

check_abi() {
    local abi="$1"
    local outdir="$OUT_ROOT/$abi"
    local ffmpeg="$outdir/libffmpeg.so"
    local expected_machine expected_arch
    local lib strings_out config config_checks interp runpath
    local codec_strings format_strings chromaprint_strings lame_strings

    printf '\n===== %s =====\n' "$abi"
    if [ ! -f "$ffmpeg" ]; then
        fail "missing artifact: $ffmpeg"
        return 0
    fi
    for lib in "${SHARED_LIBS[@]}"; do
        [ -f "$outdir/$lib" ] || fail "missing artifact: $outdir/$lib"
    done

    case "$abi" in
        arm64-v8a)
            expected_machine="AArch64"
            expected_arch="ARM aarch64"
            ;;
        x86_64)
            expected_machine="Advanced Micro Devices X86-64"
            expected_arch="x86-64"
            ;;
    esac

    check_file_header "$ffmpeg" "$expected_arch" 'pie executable|shared object' "$abi libffmpeg.so"
    interp="$(readelf -l "$ffmpeg" | sed -n 's/.*Requesting program interpreter: \(.*\)]/\1/p')"
    printf '  interpreter: %s\n' "${interp:-<none>}"
    [ "$interp" = "/system/bin/linker64" ] ||
        fail "$abi libffmpeg.so interpreter is not /system/bin/linker64"

    check_needed "$ffmpeg" "$abi libffmpeg.so"
    check_required_needed "$ffmpeg" "$abi libffmpeg.so" "${LGPL_SHARED_LIBS[@]}" "${FFMPEG_SHARED_LIBS[@]}"
    check_alignments "$ffmpeg" "$abi libffmpeg.so"
    runpath="$(runpath_of "$ffmpeg")"
    printf '  RUNPATH: %s\n' "${runpath:-<none>}"
    [ "$runpath" = '$ORIGIN' ] ||
        fail "$abi libffmpeg.so RUNPATH is '$runpath', expected '\$ORIGIN'"

    for lib in "${SHARED_LIBS[@]}"; do
        check_file_header "$outdir/$lib" "$expected_arch" 'shared object' "$abi $lib"
        check_needed "$outdir/$lib" "$abi $lib"
        check_soname "$outdir/$lib" "$abi $lib" "$lib"
        check_alignments "$outdir/$lib" "$abi $lib"
        [ "$(readelf -h "$outdir/$lib" | awk -F: '/Machine:/{sub(/^[ \t]+/, "", $2); print $2}')" = "$expected_machine" ] ||
            fail "$abi $lib ELF machine does not match $expected_machine"
    done

    check_symbols "$outdir/libmp3lame.so" "$abi libmp3lame.so" \
        lame_init lame_encode_buffer lame_close
    check_symbols "$outdir/libchromaprint.so" "$abi libchromaprint.so" \
        chromaprint_new chromaprint_feed chromaprint_get_fingerprint

    strings_out="$(strings -a "$ffmpeg")"
    config="$(grep -m1 -- '--target-os=android' <<< "$strings_out" || true)"
    config="${config#*configuration: }"
    config_checks="${config//\'/}"
    if [ -z "$config" ]; then
        fail "$abi embedded FFmpeg configure line not found"
    else
        printf '  configure: %s\n' "$config"
        expect_has "$config_checks" '--enable-shared' "$abi configure enables shared libraries"
        expect_has "$config_checks" '--enable-encoder=libmp3lame,pcm_s16le' \
            "$abi configure enables libmp3lame and pcm_s16le encoders"
        expect_has "$config_checks" '--enable-muxer=mp3,chromaprint' \
            "$abi configure enables mp3 and chromaprint muxers"
        expect_has "$config_checks" '--enable-demuxer=mov,mp3,matroska,webm,ogg' \
            "$abi configure enables mov/mp3/matroska/webm/ogg demuxers"
        expect_has "$config_checks" '--enable-decoder=aac,mp3,opus,vorbis' \
            "$abi configure enables aac/mp3/opus/vorbis decoders"
        expect_has "$config_checks" '--enable-parser=aac,mpegaudio,opus,vorbis' \
            "$abi configure enables aac/mpegaudio/opus/vorbis parsers"
        expect_has "$config_checks" '--target-os=android' "$abi configure targets Android"
        if grep -Eq -- '--enable-(gpl|nonfree)' <<< "$config"; then
            fail "$abi configure enables GPL/nonfree code"
        else
            pass "$abi configure is LGPL-only"
        fi
    fi

    expect_has "$strings_out" 'libmp3lame' "$abi executable references libmp3lame"
    expect_has "$strings_out" 'pcm_s16le' "$abi executable references pcm_s16le"
    expect_has "$strings_out" 'chromaprint' "$abi executable references chromaprint"
    expect_has "$strings_out" 'mp3' "$abi executable references mp3"

    codec_strings="$(strings -a "$outdir/libavcodec.so")"
    format_strings="$(strings -a "$outdir/libavformat.so")"
    chromaprint_strings="$(strings -a "$outdir/libchromaprint.so")"
    lame_strings="$(strings -a "$outdir/libmp3lame.so")"

    expect_has "$codec_strings" 'pcm_s16le' "$abi libavcodec contains pcm_s16le"
    expect_has "$codec_strings" 'libmp3lame' "$abi libavcodec contains libmp3lame"
    expect_has "$codec_strings" 'opus' "$abi libavcodec contains opus"
    expect_has "$codec_strings" 'vorbis' "$abi libavcodec contains vorbis"
    expect_has "$format_strings" 'chromaprint' "$abi libavformat contains chromaprint muxer"
    expect_has "$format_strings" 'matroska' "$abi libavformat contains matroska demuxer"
    expect_has "$format_strings" 'mov' "$abi libavformat contains mov demuxer"
    expect_has "$format_strings" 'mp3' "$abi libavformat contains mp3 demuxer/muxer"
    expect_has "$format_strings" 'ogg' "$abi libavformat contains ogg demuxer"
    expect_has "$chromaprint_strings" 'chromaprint' "$abi libchromaprint contains chromaprint"
    expect_has "$lame_strings" 'LAME3.100' "$abi libmp3lame contains LAME3.100"

    local file size sha total
    total=0
    printf '  artifacts:\n'
    for file in "$outdir"/*.so; do
        size="$(stat -c %s "$file")"
        sha="$(sha256sum "$file" | cut -d' ' -f1)"
        total=$((total + size))
        printf '    %-24s %8d bytes  %s\n' "$(basename "$file")" "$size" "$sha"
    done
    printf '  total size: %d bytes\n' "$total"
}

check_license_assets() {
    local file
    printf '\n===== license assets =====\n'
    for file in "${LICENSE_FILES[@]}"; do
        if [ -s "$LICENSE_DIR/$file" ]; then
            pass "packaged $file"
        else
            fail "missing or empty license asset: app/src/main/assets/licenses/$file"
        fi
    done
    if [ -s "$LICENSE_DIR/NOTICE.txt" ] && grep -Fq 'dynamically linked' "$LICENSE_DIR/NOTICE.txt"; then
        pass "NOTICE records dynamic linking and source availability"
    else
        fail "NOTICE.txt does not record dynamic linking/source availability"
    fi
}

main() {
    command -v file >/dev/null 2>&1 || { printf 'error: file not found\n' >&2; exit 2; }
    command -v readelf >/dev/null 2>&1 || { printf 'error: readelf not found\n' >&2; exit 2; }
    command -v strings >/dev/null 2>&1 || { printf 'error: strings not found\n' >&2; exit 2; }
    command -v sha256sum >/dev/null 2>&1 || { printf 'error: sha256sum not found\n' >&2; exit 2; }

    parse_args "$@"
    check_license_assets
    for abi in "${ABIS[@]}"; do
        check_abi "$abi"
    done

    printf '\n'
    if [ "$FAILURES" -ne 0 ]; then
        printf 'probe failed: %d issue(s)\n' "$FAILURES" >&2
        exit 1
    fi
    log "all checks passed"
    log "runtime execution and fingerprint tests require an Android emulator/device; deferred to the emulator milestone"
}

main "$@"
