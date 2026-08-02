param(
    [string]$RepoRoot = ".",
    [string]$LibraryPath = "",
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path $RepoRoot).Path
if ([string]::IsNullOrWhiteSpace($LibraryPath)) {
    $LibraryPath = Join-Path $root "ta4j-cli\target\native\cuda\package\META-INF\native\windows-x86_64\ta4j-cuda-accelerator.dll"
}
$library = (Resolve-Path $LibraryPath).Path
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $runId = [Guid]::NewGuid().ToString("N")
    $OutputPath = Join-Path $root ".agents\reports\cuda\windows-rtx5090-$stamp-$runId.json"
}
$output = [System.IO.Path]::GetFullPath($OutputPath)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($output)) | Out-Null

$sourceStatus = & git -C $root status --porcelain 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "git status failed with exit code $LASTEXITCODE`: $($sourceStatus -join [Environment]::NewLine)"
}
if ($sourceStatus) {
    throw "CUDA benchmark requires a clean source worktree"
}
$sourceHead = & git -C $root rev-parse HEAD 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse failed with exit code $LASTEXITCODE`: $($sourceHead -join [Environment]::NewLine)"
}
$sourceTree = & git -C $root write-tree 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "git write-tree failed with exit code $LASTEXITCODE`: $($sourceTree -join [Environment]::NewLine)"
}
$gpu = & nvidia-smi --query-gpu=name,driver_version,compute_cap,memory.total --format=csv,noheader 2>&1 |
    Select-Object -First 1
if ($LASTEXITCODE -ne 0) {
    throw "nvidia-smi failed with exit code $LASTEXITCODE"
}
$nvcc = & nvcc --version 2>&1 | Select-Object -Last 1
if ($LASTEXITCODE -ne 0) {
    throw "nvcc failed with exit code $LASTEXITCODE"
}
$javaOutput = & java -version 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "java -version failed with exit code $LASTEXITCODE"
}
$javaVersion = ($javaOutput | Select-Object -First 1).ToString()

$workloads = @(
    @{ decisions = 1; paths = 1024; horizon = 8 },
    @{ decisions = 8; paths = 4096; horizon = 8 },
    @{ decisions = 32; paths = 8192; horizon = 8 }
)
$measurements = @()
foreach ($workload in $workloads) {
    for ($process = 1; $process -le 5; $process++) {
        $arguments = @(
            "-B", "-pl", "ta4j-cli", "-am",
            "-Dtest=CudaBenchmarkTest", "-Dsurefire.failIfNoSpecifiedTests=false",
            "-Dgroups=requires-cuda", "-Dta4j.excludedTestTags=requires-metal",
            "-Dta4j.runBenchmarks=true",
            "-Dta4j.acceleration.cuda.library=$library",
            "-Dta4j.cuda.benchmark.decisions=$($workload.decisions)",
            "-Dta4j.cuda.benchmark.paths=$($workload.paths)",
            "-Dta4j.cuda.benchmark.horizon=$($workload.horizon)",
            "-Dta4j.cuda.benchmark.repetitions=3", "test"
        )
        $lines = & (Join-Path $root "mvnw.cmd") @arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            $lines | ForEach-Object { Write-Host $_ }
            throw "CUDA benchmark Maven process failed with exit code $LASTEXITCODE"
        }
        $prefix = "CUDA_BENCHMARK "
        $resultLine = $lines | Where-Object { $_ -like "$prefix*" } | Select-Object -Last 1
        if ($null -eq $resultLine) {
            throw "CUDA benchmark process did not emit a result"
        }
        $measurement = ($resultLine.Substring($prefix.Length) | ConvertFrom-Json)
        $measurement | Add-Member -NotePropertyName process -NotePropertyValue $process
        $measurements += $measurement
        Write-Host "work=$($measurement.work) process=$process speedup=$($measurement.speedup)x"
    }
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    sourceHead = $sourceHead
    sourceTree = $sourceTree
    operatingSystem = [System.Environment]::OSVersion.VersionString
    java = $javaVersion
    gpu = $gpu
    cudaCompiler = $nvcc
    librarySha256 = (Get-FileHash -Algorithm SHA256 $library).Hash.ToLowerInvariant()
    processesPerWorkload = 5
    repetitionsPerProcess = 3
    measurements = $measurements
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($output, ($report | ConvertTo-Json -Depth 8), $utf8NoBom)
Write-Host $output
