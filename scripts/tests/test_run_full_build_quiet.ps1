param([string] $Scenario, [string] $FixtureRoot)
$ErrorActionPreference = 'Stop'

if (-not $Scenario) {
    $root = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString())
    try {
        New-Item -ItemType Directory -Path "$root/scripts" -Force | Out-Null
        Copy-Item "$PSScriptRoot/../run-full-build-quiet.ps1" "$root/scripts/run-full-build-quiet.ps1"
        $shell = (Get-Process -Id $PID).Path
        foreach ($case in @('wsl', 'fallback', 'no-program-files', 'preflight-failure', 'maven-failure', 'timeout')) {
            $output = & $shell -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath -Scenario $case -FixtureRoot $root 2>&1
            $code = $LASTEXITCODE
            $expected = switch ($case) { 'preflight-failure' { 9 }; 'maven-failure' { 7 }; 'timeout' { 124 }; default { 0 } }
            if ($code -ne $expected) { throw "$case returned $code instead of ${expected}: $output" }
            if ($case -eq 'timeout' -and "$output" -notmatch 'Maven build timed out') { throw 'Missing timeout diagnostic' }
            if ($case -eq 'maven-failure' -and "$output" -notmatch 'Maven build failed') { throw 'Missing Maven failure diagnostic' }
            if ($case -in @('wsl', 'fallback', 'no-program-files', 'preflight-failure')) {
                $calls = @(Get-Content "$root/calls.txt")
                $windows = [Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT
                $expectedCalls = if ($windows -and $case -in @('wsl', 'preflight-failure')) { @('probe', 'wsl') } elseif ($windows -and $case -eq 'fallback') { @('probe', 'bash') } else { @('bash') }
                if (($calls -join ',') -ne ($expectedCalls -join ',')) { throw "Wrong preflight selection for ${case}: $calls" }
            }
            Write-Output "[PASS] PowerShell $case"
        }
    } finally {
        Remove-Item $root -Recurse -Force -ErrorAction SilentlyContinue
    }
    exit 0
}

$env:QUIET_BUILD_TIMEOUT_SECONDS = '1'
$env:ProgramFiles = if ($Scenario -eq 'no-program-files') { '' } else { $FixtureRoot }
Set-Content "$FixtureRoot/calls.txt" -Value @()
$env:FIXTURE_ROOT = $FixtureRoot
$env:FIXTURE_SCENARIO = $Scenario
@'
if ($args[0] -ne '--cd' -or $args[1] -ne $env:FIXTURE_ROOT -or
    $args[2] -ne 'bash' -or $args.Count -ne 5) { throw 'Invalid WSL arguments' }
if ($args[3] -eq '-c' -and $args[4] -eq 'test -r scripts/run-full-build-quiet.sh') {
    Add-Content "$env:FIXTURE_ROOT/calls.txt" 'probe'
    $global:LASTEXITCODE = if ($env:FIXTURE_SCENARIO -eq 'fallback') { 1 } else { 0 }
} else {
    if ($args[3] -ne 'scripts/run-full-build-quiet.sh' -or $args[4] -ne '--preflight-only') { throw 'Invalid preflight arguments' }
    Add-Content "$env:FIXTURE_ROOT/calls.txt" 'wsl'
    $global:LASTEXITCODE = if ($env:FIXTURE_SCENARIO -eq 'preflight-failure') { 9 } else { 0 }
}
'@ | Set-Content "$FixtureRoot/wsl.ps1"
@'
if ($args[0] -ne (Join-Path $env:FIXTURE_ROOT 'scripts/run-full-build-quiet.sh') -or
    $args[1] -ne '--preflight-only' -or $args.Count -ne 2) { throw 'Invalid Bash arguments' }
Add-Content "$env:FIXTURE_ROOT/calls.txt" 'bash'
$global:LASTEXITCODE = if ($env:FIXTURE_SCENARIO -eq 'preflight-failure') { 9 } else { 0 }
'@ | Set-Content "$FixtureRoot/bash.ps1"

function global:Get-Command {
    param($Name, $ErrorAction)
    if ($Name -eq 'wsl.exe' -and $env:FIXTURE_SCENARIO -ne 'no-program-files') {
        return [pscustomobject]@{ Source = "$env:FIXTURE_ROOT/wsl.ps1" }
    }
    if ($Name -eq 'bash') { return [pscustomobject]@{ Source = "$env:FIXTURE_ROOT/bash.ps1" } }
}
function global:Start-Process {
    param($FilePath, $ArgumentList, $WorkingDirectory, [switch]$NoNewWindow, [switch]$PassThru,
          $RedirectStandardOutput, $RedirectStandardError)
    if ($ArgumentList -notcontains 'verify') { throw 'Missing verify goal' }
    Set-Content $RedirectStandardOutput '[INFO] BUILD SUCCESS'
    Set-Content $RedirectStandardError ''
    $process = [pscustomobject]@{ Id = 999999; Stopped = $false }
    $process | Add-Member ScriptMethod WaitForExit {
        if ($env:FIXTURE_SCENARIO -eq 'timeout' -and $args.Count -eq 0) { throw 'Timeout must not wait indefinitely' }
        if ($env:FIXTURE_SCENARIO -eq 'timeout' -and -not $this.Stopped) {
            Start-Sleep -Milliseconds 1100
            return $false
        }
        return $true
    }
    $process | Add-Member ScriptMethod Refresh { }
    $process | Add-Member ScriptProperty ExitCode {
        if ($env:FIXTURE_SCENARIO -eq 'timeout') { throw 'Timeout must not read ExitCode' }
        if ($env:FIXTURE_SCENARIO -eq 'maven-failure') { return 7 }
        return $null
    }
    $global:FixtureProcess = $process
    return $process
}
function global:Stop-Process {
    param($Id, [switch]$Force, $ErrorAction)
    $global:FixtureProcess.Stopped = $true
}
& "$FixtureRoot/scripts/run-full-build-quiet.ps1"
exit $LASTEXITCODE
