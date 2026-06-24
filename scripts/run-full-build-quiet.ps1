#!/usr/bin/env pwsh
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Show-Usage {
    @"
Usage: scripts/run-full-build-quiet.ps1 [--goals "goal..."] [--] [maven-args...]

Runs Maven with filtered terminal output and writes the complete log to
.agents/logs/full-build-*.log.

Examples:
  scripts/run-full-build-quiet.ps1
  scripts/run-full-build-quiet.ps1 -- -pl ta4j-core
  scripts/run-full-build-quiet.ps1 --goals "test jacoco:report jacoco:check" -- -pl ta4j-core -am
  scripts/run-full-build-quiet.ps1 --goals test -- -Dgroups=integration -Dta4j.excludedTestTags=analysis-demo,elliott-macro-cycle-replay
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

function Should-PrintLine {
    param([string] $Text)
    return $Text.Contains("[ERROR]") `
        -or $Text.Contains("[WARNING]") `
        -or $Text.Contains("BUILD SUCCESS") `
        -or $Text.Contains("BUILD FAILURE") `
        -or $Text.Contains("Failed to execute goal") `
        -or $Text.Contains("Reactor Summary")
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

$goals = @("verify")
$mavenArgs = @()
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
        }
        '^--goals=' {
            $goals = Split-Goals $arg.Substring("--goals=".Length)
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
    $isWindows = [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
    if ($isWindows -and (Test-Path -LiteralPath (Join-Path $repoRoot "mvnw.cmd"))) {
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

    $seen = @{}
    $suppressed = @{}
    foreach ($line in Get-Content -LiteralPath $logFile) {
        if (-not (Should-PrintLine $line)) {
            continue
        }
        if ($line.Contains("Tests run:") -and $line.Contains("Time elapsed")) {
            continue
        }
        if ($seen.ContainsKey($line)) {
            if (-not $suppressed.ContainsKey($line)) {
                $suppressed[$line] = 0
            }
            $suppressed[$line]++
        } else {
            $seen[$line] = $true
            Write-Output $line
        }
    }

    if ($suppressed.Count -gt 0) {
        Write-Output ""
        Write-Output "[quiet-build] Suppressed duplicate warnings:"
        foreach ($line in $suppressed.Keys) {
            Write-Output ("[quiet-build]   ({0} more) {1}" -f $suppressed[$line], $line.Trim())
        }
    }

    if ($timedOut) {
        Write-Output ""
        Write-Output "Maven build timed out after ${timeoutSeconds}s. Inspect the full log at: $logFile"
        exit 1
    }

    if ($process.ExitCode -ne 0) {
        Write-Output ""
        Write-Output "Maven build failed (mvn=$($process.ExitCode)). Inspect the full log at: $logFile"
        exit 1
    }

    $summary = Extract-TestSummary $logFile
    Write-Output ""
    if ($summary) {
        Write-Output $summary
    } else {
        Write-Output "Tests run summary not found in log; see $logFile"
    }
    Write-Output "Full build log saved to: $logFile"
} finally {
    Remove-Item -LiteralPath $stdoutFile, $stderrFile -Force -ErrorAction SilentlyContinue
}
