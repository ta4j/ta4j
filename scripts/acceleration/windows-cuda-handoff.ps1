param(
    [string]$RepoRoot = ".",
    [ValidateSet("Preflight", "Build", "Integration", "Benchmark", "All")]
    [string]$Action = "Preflight"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path $RepoRoot).Path
$maven = Join-Path $root "mvnw.cmd"
$library = Join-Path $root "ta4j-cli\target\native\cuda\package\META-INF\native\windows-x86_64\ta4j-cuda-accelerator.dll"

Write-Host "CF-336 Windows CUDA root: $root"
Write-Host "Operation ABI: ta4j.forecast.monte-carlo-price.v1"
Write-Host "Implementation record: https://github.com/ta4j/ta4j-wiki/wiki/Indicator-Acceleration-CUDA-Plan"

if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
    throw "The cuda-windows-x86_64 profile requires Windows"
}
foreach ($command in @("nvcc", "nvidia-smi", "cmake", "java")) {
    if ($null -eq (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command is unavailable: $command"
    }
}
nvcc --version | Select-Object -Last 1
nvidia-smi --query-gpu=name,driver_version,compute_cap,memory.total --format=csv,noheader | Select-Object -First 1
cmake --version | Select-Object -First 1
java -version

if ($Action -eq "Preflight") {
    return
}

if ($Action -in @("Build", "All")) {
    & $maven -B -pl ta4j-cli -am -Pcuda-windows-x86_64 -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw "CUDA classifier build failed with exit code $LASTEXITCODE"
    }
}
if (-not (Test-Path -LiteralPath $library -PathType Leaf)) {
    throw "CUDA DLL is absent; run this script with -Action Build or -Action All first: $library"
}

if ($Action -in @("Integration", "All")) {
    & $maven -B -pl ta4j-cli -am "-Dtest=CudaNativeIntegrationTest" `
        "-Dsurefire.failIfNoSpecifiedTests=false" "-Dgroups=requires-cuda" `
        "-Dta4j.excludedTestTags=requires-metal" `
        "-Dta4j.acceleration.cuda.library=$library" test
    if ($LASTEXITCODE -ne 0) {
        throw "CUDA integration tests failed with exit code $LASTEXITCODE"
    }
}

if ($Action -in @("Benchmark", "All")) {
    & (Join-Path $root "scripts\acceleration\benchmark-cuda-provider.ps1") -RepoRoot $root -LibraryPath $library
}

if ($Action -eq "All") {
    & (Join-Path $root "scripts\run-full-build-quiet.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "Full Windows build failed with exit code $LASTEXITCODE"
    }
}
