#!/usr/bin/env bash
#
# Verify the staged libffmpeg.so artifacts without executing them.
#
# Android binaries are linked against bionic and cannot run on the Linux host,
# and the project deliberately does not start an emulator. This script checks
# the ELF metadata, dynamic dependencies, required FFmpeg components, and the
# embedded configure line instead.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_ROOT="$REPO_ROOT/app/src/main/jniLibs"
SUPPORTED_ABIS=(arm64-v8a x86_64)
ABIS=()
FAILURES=0

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

usage() {
    cat <<'EOF'
Usage: scripts/native/probe-ffmpeg.sh [--abi <arm64-v8a|x86_64|all>]

Checks file type, ELF machine and type, Android interpreter, NEEDED libraries,
the embedded LGPL configure line, and required encoder/muxer/decoder strings.
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

check_abi() {
    local abi="$1"
    local out="$OUT_ROOT/$abi/libffmpeg.so"
    local file_out header type machine interp needed lib config config_checks strings_out
    local expected_machine expected_arch

    printf '\n===== %s =====\n' "$abi"
    if [ ! -f "$out" ]; then
        fail "missing artifact: $out"
        return 0
    fi
    if [ ! -s "$out" ]; then
        fail "artifact is empty: $out"
        return 0
    fi
    if [ ! -x "$out" ]; then
        fail "artifact is not executable: $out"
    else
        pass "artifact is executable"
    fi

    file_out="$(file -b "$out")"
    printf '  file: %s\n' "$file_out"
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

    has "$file_out" 'ELF 64-bit LSB' ||
        fail "not a 64-bit ELF file"
    has "$file_out" "$expected_arch" ||
        fail "file(1) does not report $expected_arch"
    grep -Eq 'pie executable|shared object' <<< "$file_out" ||
        fail "file(1) does not report a PIE executable"

    header="$(readelf -h "$out")"
    type="$(printf '%s\n' "$header" | awk -F: '/Type:/{sub(/^[ \t]+/, "", $2); print $2}')"
    machine="$(printf '%s\n' "$header" | awk -F: '/Machine:/{sub(/^[ \t]+/, "", $2); print $2}')"
    printf '  ELF type: %s\n' "$type"
    printf '  ELF machine: %s\n' "$machine"

    has "$type" 'DYN' ||
        fail "ELF type is not DYN/PIE"
    [ "$machine" = "$expected_machine" ] ||
        fail "ELF machine is '$machine', expected '$expected_machine'"

    interp="$(readelf -l "$out" | sed -n 's/.*Requesting program interpreter: \(.*\)]/\1/p')"
    printf '  interpreter: %s\n' "${interp:-<none>}"
    [ "$interp" = "/system/bin/linker64" ] ||
        fail "interpreter is not /system/bin/linker64"

    needed="$(readelf -d "$out" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p')"
    printf '  NEEDED:\n'
    if [ -z "$needed" ]; then
        printf '    <none>\n'
    else
        printf '    %s\n' $needed
    fi
    for lib in $needed; do
        case "$lib" in
            libc.so|libm.so|libdl.so|liblog.so) pass "allowed dependency: $lib" ;;
            *) fail "unexpected shared dependency: $lib" ;;
        esac
    done

    strings_out="$(strings -a "$out")"
    config="$(grep -m1 -- '--target-os=android' <<< "$strings_out" || true)"
    config="${config#*configuration: }"
    config_checks="${config//\'/}"
    if [ -z "$config" ]; then
        fail "embedded FFmpeg configure line not found"
    else
        printf '  configure: %s\n' "$config"
        has "$config_checks" '--enable-libmp3lame' ||
            fail "configure line does not enable libmp3lame"
        has "$config_checks" '--enable-chromaprint' ||
            fail "configure line does not enable chromaprint"
        has "$config_checks" '--enable-decoder=aac,mp3' ||
            fail "configure line does not enable the aac/mp3 decoders"
        has "$config_checks" '--enable-demuxer=mov,mp3' ||
            fail "configure line does not enable the mov/mp3 demuxers"
        has "$config_checks" '--target-os=android' ||
            fail "configure line is not targeting Android"
        if grep -Eq -- '--enable-(gpl|nonfree)' <<< "$config"; then
            fail "configure line enables GPL/nonfree code"
        else
            pass "LGPL-only configure line"
        fi
    fi

    if has "$strings_out" 'libmp3lame encoder'; then
        pass "libmp3lame encoder present"
    else
        fail "libmp3lame encoder string missing"
    fi
    if has "$strings_out" 'chromaprint muxer'; then
        pass "chromaprint muxer present"
    else
        fail "chromaprint muxer string missing"
    fi
    if has "$strings_out" 'LAME3.100'; then
        pass "LAME 3.100 runtime present"
    else
        fail "LAME 3.100 string missing"
    fi
    if has "$strings_out" 'Lavf'; then
        pass "libavformat version string present"
    else
        fail "libavformat version string missing"
    fi
    if grep -Fiq 'aac' <<< "$strings_out"; then
        pass "AAC support string present"
    else
        fail "AAC support string missing"
    fi

    local size sha
    size="$(stat -c %s "$out")"
    sha="$(sha256sum "$out" | cut -d' ' -f1)"
    printf '  size: %s bytes\n' "$size"
    printf '  sha256: %s\n' "$sha"
}

main() {
    command -v file >/dev/null 2>&1 || { printf 'error: file not found\n' >&2; exit 2; }
    command -v readelf >/dev/null 2>&1 || { printf 'error: readelf not found\n' >&2; exit 2; }
    command -v strings >/dev/null 2>&1 || { printf 'error: strings not found\n' >&2; exit 2; }

    parse_args "$@"

    for abi in "${ABIS[@]}"; do
        check_abi "$abi"
    done

    printf '\n'
    if [ "$FAILURES" -ne 0 ]; then
        printf 'probe failed: %d issue(s)\n' "$FAILURES" >&2
        exit 1
    fi
    log "all checks passed"
}

main "$@"
