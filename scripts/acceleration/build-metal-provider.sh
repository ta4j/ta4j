#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source_file="$repo_root/scripts/acceleration/metal-provider-smoke.m"
output_dir="$repo_root/build/native"
output_file="$output_dir/libta4j-metal-accelerator.dylib"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Metal smoke build requires macOS" >&2
  exit 2
fi

mkdir -p "$output_dir"
java_home="$(/usr/libexec/java_home)"
clang -dynamiclib -fobjc-arc \
  -I"$java_home/include" \
  -I"$java_home/include/darwin" \
  -framework Foundation \
  -framework Metal \
  "$source_file" \
  -o "$output_file"
echo "$output_file"
