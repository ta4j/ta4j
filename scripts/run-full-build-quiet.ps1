#!/usr/bin/env pwsh
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Show-Usage {
    @"
Usage: scripts/run-full-build-quiet.ps1 [--preflight-only] [--goals "goal..."] [--] [maven-args...]

The default invocation runs the same repository-owned checks and Maven gate as
hosted PR CI. Maven output is filtered and the complete log is written to
.agents/logs/full-build-*.log. Explicit --goals invocations remain focused and
skip repository preflight checks.

Examples:
  scripts/run-full-build-quiet.ps1
  scripts/run-full-build-quiet.ps1 --preflight-only
  scripts/run-full-build-quiet.ps1 -- -pl ta4j-core
  scripts/run-full-build-quiet.ps1 --goals "test jacoco:report jacoco:check" -- -pl ta4j-core -am
  scripts/run-full-build-quiet.ps1 --goals test -- -Dgroups=integration -Dta4j.excludedTestTags=analysis-demo
"@
}

function Positive-Int-OrDefault {
    param(
        [string] $Value,
        [int] $DefaultValue,
        [int] $MinimumValue = 0
    )
    $parsed = 0
    if ([int]::TryParse($Value, [ref] $parsed) -and $parsed -ge $MinimumValue) {
        return $parsed
    }
    return $DefaultValue
}

function Split-Goals {
    param([string] $Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "At least one Maven goal is required"
    }
    return @($Value -split '\s+' | Where-Object { $_ -ne "" })
}

function Format-Elapsed {
    param([TimeSpan] $Elapsed)
    if ($Elapsed.TotalHours -ge 1) {
        return "{0}h{1:00}m{2:00}s" -f [int]$Elapsed.TotalHours, $Elapsed.Minutes, $Elapsed.Seconds
    }
    if ($Elapsed.TotalMinutes -ge 1) {
        return "{0}m{1:00}s" -f [int]$Elapsed.TotalMinutes, $Elapsed.Seconds
    }
    return "{0}s" -f [int]$Elapsed.TotalSeconds
}

function Extract-TestSummary {
    param([string] $LogFile)
    $totalRun = 0
    $totalFailures = 0
    $totalErrors = 0
    $totalSkipped = 0
    $hasAggregated = $false
    $fallbackSummary = $null

    foreach ($line in Get-Content -LiteralPath $LogFile) {
        if ($line -match '^\[(INFO|WARNING)\]\s+Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+), Skipped: ([0-9]+)\s*$') {
            $hasAggregated = $true
            $totalRun += [int]$Matches[2]
            $totalFailures += [int]$Matches[3]
            $totalErrors += [int]$Matches[4]
            $totalSkipped += [int]$Matches[5]
        } elseif ($line -match 'Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+), Skipped: ([0-9]+)') {
            $fallbackSummary = "Tests run: $($Matches[1]), Failures: $($Matches[2]), Errors: $($Matches[3]), Skipped: $($Matches[4])"
        }
    }

    if ($hasAggregated) {
        return "Tests run: $totalRun, Failures: $totalFailures, Errors: $totalErrors, Skipped: $totalSkipped"
    }
    return $fallbackSummary
}

function Normalize-BuildLine {
    param([string] $Line)
    return ($Line -replace '^\[(INFO|WARNING|WARN|ERROR)\]\s*', '').Trim()
}

function Normalize-WarningLine {
    param([string] $Line)
    return ($Line -replace '^\[(WARNING|WARN)\]\s*', '').Trim()
}

function Test-WarningLine {
    param([string] $Line)
    return $Line -match '\[(WARNING|WARN)\]' `
        -or $Line -match '(^|\s)WARN(\s|:|$)' `
        -or $Line -match '(^|\s)WARNING:'
}

function Test-StackOrExceptionLine {
    param([string] $Line)
    return $Line -match '(Exception|exception|Throwable|Caused by:|Suppressed:|OutOfMemoryError|StackOverflowError)' `
        -or $Line -match '^\s+at\s+\S+\([^)]*\)$' `
        -or $Line -match '^\s*\.\.\. [0-9]+ more$'
}

function Test-UnexpectedLine {
    param([string] $Line)
    $lower = $Line.ToLowerInvariant()
    return $Line -match '\[ERROR\]' `
        -or $Line -match '(^|\s)ERROR(\s|:|$)' `
        -or $lower.Contains("unexpected") `
        -or $lower.Contains("fatal") `
        -or $lower.Contains("timed out") `
        -or $lower.Contains("permission denied") `
        -or $lower.Contains("no such file") `
        -or $Line.Contains("BUILD FAILURE") `
        -or $Line.Contains("Failed to execute goal") `
        -or $Line.Contains("There are test failures") `
        -or $Line.Contains("failed tests") `
        -or (Test-StackOrExceptionLine $Line)
}

function Add-UniqueValue {
    param(
        [System.Collections.Generic.List[string]] $List,
        [string] $Value
    )
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -match '^-+$') {
        return
    }
    if (-not $List.Contains($Value)) {
        $List.Add($Value) | Out-Null
    }
}

function Add-CountedValue {
    param(
        [System.Collections.Specialized.OrderedDictionary] $Entries,
        [string] $Value
    )
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -match '^-+$') {
        return
    }
    if ($Entries.Contains($Value)) {
        $Entries[$Value] = [int]$Entries[$Value] + 1
    } else {
        $Entries[$Value] = 1
    }
}

function Write-CountedSummary {
    param(
        [string] $Title,
        [System.Collections.Specialized.OrderedDictionary] $Entries,
        [int] $Limit,
        [string] $OverflowLabel
    )
    if ($Entries.Count -eq 0) {
        return
    }
    Write-Output $Title
    $index = 0
    foreach ($key in $Entries.Keys) {
        if ($index -ge $Limit) {
            Write-Output ("  ... {0} more {1}; see full log" -f ($Entries.Count - $Limit), $OverflowLabel)
            break
        }
        $count = [int]$Entries[$key]
        $suffix = if ($count -gt 1) { " (${count}x)" } else { "" }
        Write-Output ("  {0}{1}" -f $key, $suffix)
        $index++
    }
}

function Write-BoundedRows {
    param(
        [string] $Title,
        [System.Collections.Generic.List[string]] $Rows,
        [int] $Limit
    )
    if ($Rows.Count -eq 0) {
        return
    }
    Write-Output ("{0}:" -f $Title)
    for ($i = 0; $i -lt $Rows.Count -and $i -lt $Limit; $i++) {
        Write-Output ("  {0}" -f $Rows[$i])
    }
    if ($Rows.Count -gt $Limit) {
        Write-Output ("  ... {0} more; see full log" -f ($Rows.Count - $Limit))
    }
}

function Write-ReactorSummary {
    param([string] $LogFile)
    $rows = [System.Collections.Generic.List[string]]::new()
    $capturing = $false
    foreach ($line in Get-Content -LiteralPath $LogFile) {
        if ($line.Contains("Reactor Summary")) {
            $capturing = $true
            continue
        }
        if ($capturing -and ($line.Contains("BUILD SUCCESS") -or $line.Contains("BUILD FAILURE"))) {
            break
        }
        if ($capturing) {
            Add-UniqueValue $rows (Normalize-BuildLine $line)
        }
    }
    Write-BoundedRows "Reactor summary" $rows ([int]::MaxValue)
}

function Write-WarningSummary {
    param(
        [string] $LogFile,
        [int] $Limit = 12
    )
    $warnings = [ordered]@{}
    foreach ($line in Get-Content -LiteralPath $LogFile) {
        if (Test-WarningLine $line) {
            $text = Normalize-WarningLine $line
            if ($text -notmatch 'Tests run:') {
                Add-CountedValue $warnings $text
            }
        }
    }
    Write-CountedSummary "Warnings summary:" $warnings $Limit "unique warning(s)"
}

function Write-UnexpectedSummary {
    param(
        [string] $LogFile,
        [int] $Limit = 12
    )
    $unexpected = [ordered]@{}
    foreach ($line in Get-Content -LiteralPath $LogFile) {
        if ((Test-UnexpectedLine $line) -and -not (Test-WarningLine $line)) {
            $text = Normalize-BuildLine $line
            if ($text -and $text -ne "BUILD SUCCESS") {
                Add-CountedValue $unexpected $text
            }
        }
    }
    Write-CountedSummary "Unexpected output summary:" $unexpected $Limit "unique unexpected line(s)"
}

function Write-FailureDigest {
    param([string] $LogFile)
    $failedModules = [System.Collections.Generic.List[string]]::new()
    $failedGoals = [System.Collections.Generic.List[string]]::new()
    $testHints = [System.Collections.Generic.List[string]]::new()
    $qualityHints = [System.Collections.Generic.List[string]]::new()
    $exceptionHints = [System.Collections.Generic.List[string]]::new()
    $errorTail = [System.Collections.Generic.List[string]]::new()
    $capturingReactor = $false

    foreach ($line in Get-Content -LiteralPath $LogFile) {
        $text = Normalize-BuildLine $line
        $lower = $line.ToLowerInvariant()
        if ($line.Contains("Reactor Summary")) {
            $capturingReactor = $true
            continue
        }
        if ($capturingReactor -and ($line.Contains("BUILD SUCCESS") -or $line.Contains("BUILD FAILURE"))) {
            $capturingReactor = $false
        }
        if ($capturingReactor -and $line.Contains(" FAILURE ")) {
            Add-UniqueValue $failedModules $text
        }
        if ($line.Contains("Failed to execute goal")) {
            Add-UniqueValue $failedGoals $text
        }
        if ($line -match 'Tests run:\s*[0-9]+,\s*Failures:\s*([1-9][0-9]*)' `
            -or $line -match 'Tests run:\s*[0-9]+,\s*Failures:\s*[0-9]+,\s*Errors:\s*([1-9][0-9]*)' `
            -or $lower -match 'surefire-reports|failsafe-reports|there are test failures|failed tests') {
            Add-UniqueValue $testHints $text
        }
        if ($lower -match '(pmd|spotbugs|jacoco)' -and $lower -match '(fail|violat|coverage|error|check)') {
            Add-UniqueValue $qualityHints $text
        }
        if (Test-StackOrExceptionLine $line) {
            Add-UniqueValue $exceptionHints $text
        }
        if ($line.StartsWith("[ERROR]") -and -not [string]::IsNullOrWhiteSpace($text) -and $text -notmatch '^-+$') {
            $errorTail.Add($text) | Out-Null
        }
    }

    Write-Output "Failure digest:"
    Write-BoundedRows "Failed modules" $failedModules 12
    Write-BoundedRows "Failed goals" $failedGoals 8
    Write-BoundedRows "Test/report hints" $testHints 12
    Write-BoundedRows "Quality gate hints" $qualityHints 12
    Write-BoundedRows "Exception/stack-trace hints" $exceptionHints 12
    if ($errorTail.Count -gt 0) {
        Write-Output "Maven error tail:"
        $start = [Math]::Max(0, $errorTail.Count - 25)
        for ($i = $start; $i -lt $errorTail.Count; $i++) {
            Write-Output ("  {0}" -f $errorTail[$i])
        }
    }
}

$goals = @("clean", "license:check", "formatter:validate", "verify")
$mavenArgs = @()
$defaultGate = $true
$preflightOnly = $false
$index = 0
while ($index -lt $args.Count) {
    $arg = $args[$index]
    switch -Regex ($arg) {
        '^-h$|^--help$' {
            Show-Usage
            exit 0
        }
        '^--goals$' {
            $index++
            if ($index -ge $args.Count) {
                throw "Missing value for --goals"
            }
            $goals = Split-Goals $args[$index]
            $defaultGate = $false
        }
        '^--goals=' {
            $goals = Split-Goals $arg.Substring("--goals=".Length)
            $defaultGate = $false
        }
        '^--preflight-only$' {
            $preflightOnly = $true
        }
        '^--$' {
            $index++
            while ($index -lt $args.Count) {
                $mavenArgs += $args[$index]
                $index++
            }
            break
        }
        default {
            $mavenArgs += $arg
        }
    }
    $index++
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
Set-Location $repoRoot

if ($defaultGate -or $preflightOnly) {
    $bash = Get-Command bash -ErrorAction SilentlyContinue
    if (-not $bash) {
        throw "bash is required to run the hosted CI parity preflight checks"
    }
    & $bash.Source (Join-Path $repoRoot "scripts/run-full-build-quiet.sh") --preflight-only
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if ($preflightOnly) {
    exit 0
}

if ($defaultGate) {
    $mavenArgs = @("-Dta4j.excludedTestTags=analysis-demo") + $mavenArgs
}

$logDir = Join-Path $repoRoot ".agents/logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
Get-ChildItem -LiteralPath $logDir -Filter "full-build-*.log" -File -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending |
    Select-Object -Skip 10 |
    Remove-Item -Force

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logFile = Join-Path $logDir "full-build-$timestamp.log"
$stdoutFile = [System.IO.Path]::GetTempFileName()
$stderrFile = [System.IO.Path]::GetTempFileName()

try {
    $timeoutSeconds = Positive-Int-OrDefault $env:QUIET_BUILD_TIMEOUT_SECONDS 180 0
    $heartbeatSeconds = Positive-Int-OrDefault $env:QUIET_BUILD_HEARTBEAT_SECONDS 60 1

    $mavenCommand = "mvn"
    $isWindowsPlatform = [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
    if ($isWindowsPlatform -and (Test-Path -LiteralPath (Join-Path $repoRoot "mvnw.cmd"))) {
        $mavenCommand = Join-Path $repoRoot "mvnw.cmd"
        Write-Output "Using Maven Wrapper: mvnw.cmd"
    } elseif (Test-Path -LiteralPath (Join-Path $repoRoot "mvnw")) {
        $mavenCommand = Join-Path $repoRoot "mvnw"
        Write-Output "Using Maven Wrapper: ./mvnw"
    } else {
        Write-Output "Using system Maven from PATH: mvn"
    }

    $mavenFlags = @(
        "-B",
        "-ntp",
        "-Dstyle.color=never",
        "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=WARN"
    )
    $commandArgs = @($mavenFlags + $mavenArgs + $goals)

    if ($mavenArgs.Count -gt 0) {
        Write-Output ("Forwarding extra Maven args: {0}" -f ($mavenArgs -join " "))
    }
    Write-Output ("Maven goals: {0}" -f ($goals -join " "))
    Write-Output "Running ta4j Maven build quietly..."
    Write-Output "Full log: $logFile"
    Write-Output ""

    $process = Start-Process -FilePath $mavenCommand `
        -ArgumentList $commandArgs `
        -WorkingDirectory $repoRoot `
        -NoNewWindow `
        -PassThru `
        -RedirectStandardOutput $stdoutFile `
        -RedirectStandardError $stderrFile

    $start = Get-Date
    $lastHeartbeat = $start
    $timedOut = $false
    while (-not $process.HasExited) {
        Start-Sleep -Seconds 1
        $now = Get-Date
        if ($timeoutSeconds -gt 0 -and ($now - $start).TotalSeconds -ge $timeoutSeconds) {
            $timedOut = $true
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            break
        }
        if (($now - $lastHeartbeat).TotalSeconds -ge $heartbeatSeconds) {
            Write-Output ("[quiet-build] still running... ({0})" -f (Format-Elapsed ($now - $start)))
            $lastHeartbeat = $now
        }
    }

    $stdout = if (Test-Path -LiteralPath $stdoutFile) { Get-Content -LiteralPath $stdoutFile } else { @() }
    $stderr = if (Test-Path -LiteralPath $stderrFile) { Get-Content -LiteralPath $stderrFile } else { @() }
    Set-Content -LiteralPath $logFile -Value @($stdout + $stderr)

    foreach ($line in Get-Content -LiteralPath $logFile) {
        if ($line.Contains("BUILD SUCCESS") -or $line.Contains("BUILD FAILURE")) {
            Write-Output $line
        }
    }

    if ($timedOut) {
        Write-Output ""
        Write-Output "Maven build timed out after ${timeoutSeconds}s. Inspect the full log at: $logFile"
        Write-Output "Full build log saved to: $logFile"
        exit 124
    }

    if ($process.ExitCode -ne 0) {
        Write-Output ""
        Write-Output "Maven build failed (mvn=$($process.ExitCode))."
        Write-FailureDigest $logFile
        Write-WarningSummary $logFile 12
        Write-UnexpectedSummary $logFile 12
        Write-Output "Full build log saved to: $logFile"
        exit $process.ExitCode
    }

    $summary = Extract-TestSummary $logFile
    Write-Output ""
    Write-ReactorSummary $logFile
    if ($summary) {
        Write-Output $summary
    } else {
        Write-Output "Tests run summary not found in log; see $logFile"
    }
    Write-WarningSummary $logFile 12
    Write-UnexpectedSummary $logFile 12
    Write-Output "Full build log saved to: $logFile"
} finally {
    Remove-Item -LiteralPath $stdoutFile, $stderrFile -Force -ErrorAction SilentlyContinue
}
