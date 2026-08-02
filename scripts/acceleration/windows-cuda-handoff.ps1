param(
    [string]$RepoRoot = "."
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path $RepoRoot
Write-Host "CF-336 CUDA continuation root: $root"
Write-Host "Implementation plan: $root\docs\indicator-acceleration-cuda-plan.md"

$required = @(
    "nvcc --version",
    "nvidia-smi",
    ".\mvnw.cmd -B -pl ta4j-accelerator -am test",
    ".\mvnw.cmd -B -pl ta4j-accelerator -am '-Dgroups=integration' '-Dta4j.excludedTestTags=' test",
    "scripts\run-full-build-quiet.ps1"
)

Write-Host "Required preflight and validation commands:"
$required | ForEach-Object { Write-Host "  $_" }

Write-Host ""
Write-Host "TODO checkpoints:"
Write-Host "  1. Freeze the operation ABI, RNG vectors, snapshot contract, and tolerance policy."
Write-Host "  2. Implement the CUDA capability probe without reporting availability before self-test passes."
Write-Host "  3. Add optional JNI/native packaging for Windows x86_64 without affecting CPU-only Maven users."
Write-Host "  4. Implement ta4j.forecast.monte-carlo-price.v1 sampling, projection, and reduction."
Write-Host "  5. Add snapshot, stream/memory, device-loss, and deterministic fallback tests."
Write-Host "  6. Produce paired CPU/Metal/CUDA/HYBRID JSON reports from the same commit."
Write-Host "  7. Leave Linux qualification to scripts/acceleration/linux-cuda-handoff.sh."
