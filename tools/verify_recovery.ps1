param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [ValidateSet('CrashAndRestart', 'SeedReboot', 'VerifyReboot')][string]$Mode = 'CrashAndRestart',
    [string]$Adb = 'C:\Users\yelem\AppData\Local\Android\Sdk\platform-tools\adb.exe'
)
$ErrorActionPreference = 'Stop'
$fixturePath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../app/build/recovery_fixture.json'))

function Invoke-Probe([string]$ProbeMode, [string]$Store, [string]$Identity, [string]$Boundary = '', [string]$Expected = 'UNKNOWN') {
    if ($Store -notmatch '^test_recovery_[a-zA-Z0-9_-]+\.db$') { throw 'Invalid synthetic fixture name' }
    $probeArgs = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-e', 'mode', $ProbeMode, '-e', 'store', $Store, '-e', 'identity', $Identity, '-e', 'expected', $Expected)
    if ($Boundary) { $probeArgs += @('-e', 'boundary', $Boundary) }
    $probeArgs += 'com.festpay.app/.RecoveryProbeInstrumentation'
    $probeOutput = & $Adb @probeArgs 2>&1
    return ($probeOutput -join "`n")
}
function Assert-Probe([string]$Output, [string]$ProbeMode) {
    if ($Output -notmatch ('RECOVERY_PROBE=PASS:' + [regex]::Escape($ProbeMode))) { throw "Synthetic recovery verification failed: $Output" }
}

if ($Mode -eq 'CrashAndRestart') {
    $boundaries = @('BEFORE_MUTATION', 'AFTER_MUTATION', 'AFTER_SEAL_BEFORE_COMMIT', 'AFTER_COMMIT_BEFORE_FINAL_SEAL', 'AFTER_FINAL_SEAL')
    foreach ($boundary in $boundaries) {
        $store = 'test_recovery_' + [guid]::NewGuid().ToString('N') + '.db'
        $identity = [guid]::NewGuid().ToString()
        $output = Invoke-Probe 'crash' $store $identity $boundary
        if ($output -notmatch ('CRASH_BOUNDARY_REACHED:' + $boundary) -or $output -notmatch 'Process crashed') { throw "Expected process termination was not established: $output" }
        $expected = if ($boundary -in @('AFTER_SEAL_BEFORE_COMMIT', 'AFTER_COMMIT_BEFORE_FINAL_SEAL')) { 'UNAVAILABLE' } else { 'UNKNOWN' }
        Assert-Probe (Invoke-Probe 'verify' $store $identity '' $expected) 'verify'
        Write-Output "$boundary -> ${expected}: PASS (new process, no resubmission)"
    }
    foreach ($seedMode in @('seed', 'seed_unknown')) {
        $store = 'test_recovery_' + [guid]::NewGuid().ToString('N') + '.db'
        $identity = [guid]::NewGuid().ToString()
        Assert-Probe (Invoke-Probe $seedMode $store $identity) $seedMode
        & $Adb -s $Serial shell am force-stop com.festpay.app
        if ($LASTEXITCODE -ne 0) { throw 'Force-stop failed' }
        Assert-Probe (Invoke-Probe 'verify' $store $identity) 'verify'
        Write-Output "FORCE_STOP_${seedMode} -> UNKNOWN: PASS (reservation retained)"
    }
} elseif ($Mode -eq 'SeedReboot') {
    if (Test-Path -LiteralPath $fixturePath) { throw 'A reboot fixture already exists; verify it before creating another' }
    $store = 'test_recovery_' + [guid]::NewGuid().ToString('N') + '.db'
    $identity = [guid]::NewGuid().ToString()
    Assert-Probe (Invoke-Probe 'seed_unknown' $store $identity) 'seed_unknown'
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($fixturePath)) | Out-Null
    @{ store = $store; identity = $identity; serial = $Serial } | ConvertTo-Json | Set-Content -LiteralPath $fixturePath -Encoding utf8
    & $Adb -s $Serial reboot
    if ($LASTEXITCODE -ne 0) { throw 'Reboot request failed; synthetic fixture retained for verification' }
    Write-Output 'REBOOT_REQUESTED: unlock the device manually after boot, then run VerifyReboot.'
} else {
    $fixture = Get-Content -LiteralPath $fixturePath -Raw | ConvertFrom-Json
    if ($fixture.serial -ne $Serial) { throw 'Fixture belongs to a different test device' }
    Assert-Probe (Invoke-Probe 'verify' $fixture.store $fixture.identity) 'verify'
    Remove-Item -LiteralPath $fixturePath
    Write-Output 'DEVICE_REBOOT -> UNKNOWN: PASS (reservation retained, no resubmission)'
}
