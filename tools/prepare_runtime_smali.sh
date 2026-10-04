#!/usr/bin/env bash
# Purpose: Optional standalone runtime compilation; build_clone.py is the full three-APK workflow.
# Invocation: bash tools/prepare_runtime_smali.sh --compile-authorized [fresh-output-directory]
# Contract: Explicit gate, pinned Gradle distribution, SDK 36 library supplied to D8, isolated output.
# Verification: bash syntax only until the user authorizes Android compilation.
set -euo pipefail
[[ "${1:-}" == "--compile-authorized" ]] || { echo "Compilation authorization required" >&2; exit 2; }
shift
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_root="${1:-$repo_root/work/runtime-smali}"
[[ "$output_root" == /* ]] || output_root="$repo_root/$output_root"
sdk="${ANDROID_HOME:-/opt/android-sdk}"
gradle_runner="${GRADLE_RUNNER:-$repo_root/work/toolchain/gradle-8.11.1/bin/gradle}"
for tool in "$gradle_runner" "$sdk/build-tools/36.0.0/d8" baksmali unzip; do
    command -v "$tool" >/dev/null || { echo "Missing: $tool" >&2; exit 2; }
done
test -s "$sdk/platforms/android-36/android.jar"
[[ ! -e "$output_root" ]] || { echo "Output already exists" >&2; exit 2; }
mkdir -p "$output_root/bytecode" "$output_root/dex" "$output_root/tmp"
export TMPDIR="$output_root/tmp"
export GRADLE_USER_HOME="$repo_root/work/gradle-cache"
export JAVA_TOOL_OPTIONS="-Xmx1024m -Djava.io.tmpdir=$TMPDIR"
"$gradle_runner" --no-daemon --max-workers=1 -p "$repo_root" :runtime:assembleRelease
unzip -p "$repo_root/runtime/build/outputs/aar/runtime-release.aar" classes.jar > "$output_root/bytecode/classes.jar"
test -s "$output_root/bytecode/classes.jar"
"$sdk/build-tools/36.0.0/d8" --release --min-api 24 --lib "$sdk/platforms/android-36/android.jar" \
    --output "$output_root/dex" "$output_root/bytecode/classes.jar"
test -s "$output_root/dex/classes.dex"
baksmali disassemble "$output_root/dex/classes.dex" -o "$output_root/smali"
test -s "$output_root/smali/com/ivanchan/launcher/combined/transitions/CombinedTransitionController.smali"
echo "$output_root/smali"
