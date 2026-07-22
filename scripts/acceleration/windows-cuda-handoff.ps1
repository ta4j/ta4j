param(
    [string]$RepoRoot = "."
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path $RepoRoot
Write-Host "CF-336 CUDA continuation root: $root"

$required = @(
    "nvcc --version",
    "nvidia-smi",
    "mvnw.cmd -B -pl ta4j-accelerator -am test",
    "mvnw.cmd -B -pl ta4j-accelerator -Dgroups=integration -Dta4j.excludedTestTags= test",
    "scripts\run-full-build-quiet.ps1"
)

Write-Host "Required preflight and validation commands:"
$required | ForEach-Object { Write-Host "  $_" }

Write-Host ""
Write-Host "TODO checkpoints:"
Write-Host "  1. Implement CUDA capability probe without reporting availability before self-test passes."
Write-Host "  2. Add JNI/native packaging for Windows x86_64 without affecting CPU-only Maven users."
Write-Host "  3. Implement forecast operation against ta4j.forecast.monte-carlo-price.v1 golden fixtures."
Write-Host "  4. Add stream/memory failure tests and deterministic fallback tests."
Write-Host "  5. Produce paired CPU/Metal/CUDA/HYBRID JSON reports from the same commit."
