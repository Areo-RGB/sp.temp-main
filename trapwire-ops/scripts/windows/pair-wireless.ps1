param(
    [Parameter(Mandatory=$true)][string]$PairingEndpoint,
    [Parameter(Mandatory=$true)][string]$PairingCode
)
. "$PSScriptRoot\_common.ps1"
Require-Command adb

Write-Host "Pairing with $PairingEndpoint..." -ForegroundColor Cyan
& adb pair $PairingEndpoint $PairingCode
if ($LASTEXITCODE -ne 0) { throw "adb pair failed" }
Write-Host "Pairing done. Now use connect-wireless.ps1 with the device IP & port shown on Wireless debugging screen." -ForegroundColor Green
