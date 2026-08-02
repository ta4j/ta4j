#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source_file="$repo_root/ta4j-cli/src/main/native/metal/ta4j_metal_jni.m"
output_dir="${1:-$repo_root/ta4j-cli/target/native/metal/package/META-INF/native/macos-aarch64}"
output_file="$output_dir/libta4j-metal-accelerator.dylib"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Metal native build requires macOS" >&2
  exit 2
fi

mkdir -p "$output_dir"
java_home="${JAVA_HOME:-$(/usr/libexec/java_home)}"
xcrun clang -O3 -dynamiclib -fobjc-arc \
  -I"$java_home/include" \
  -I"$java_home/include/darwin" \
  -framework Foundation \
  -framework Metal \
  "$source_file" \
  -o "$output_file"
shasum -a 256 "$output_file" | awk '{print $1}' > "$output_file.sha256"
echo "$output_file"
