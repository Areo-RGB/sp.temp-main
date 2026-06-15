param(
    [string[]]$Targets = @(),
    [string]$TargetsFile = ""
)
. "$PSScriptRoot\_common.ps1"
Require-Command adb

if ([string]::IsNullOrWhiteSpace($TargetsFile)) {
    $TargetsFile = Join-Path (Split-Path $PSScriptRoot -Parent) "..\config\wireless-targets.txt"
}

if ($Targets.Count -eq 0 -and (Test-Path $TargetsFile)) {
    $Targets = Get-Content $TargetsFile | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') }
}

if ($Targets.Count -eq 0) {
    Write-Host "No wireless targets provided. Try:" -ForegroundColor Yellow
    Write-Host ".\connect-wireless.ps1 -Targets 192.168.178.30:5555,192.168.178.99:5555"
    Write-Host "\nCurrent adb mdns services:" -ForegroundColor Cyan
    adb mdns services
    exit 1
}

foreach ($target in $Targets) {
    Write-Host "adb connect $target" -ForegroundColor Cyan
    & adb connect $target
}

Write-Host "\nCurrent devices:" -ForegroundColor Green
adb devices
