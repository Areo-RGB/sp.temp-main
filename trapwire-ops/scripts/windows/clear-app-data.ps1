param([string]$RepoRoot = "", [switch]$UsbOnly)
. "$PSScriptRoot\_common.ps1"
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Find-RepoRoot }
$pkg = Get-PackageName $RepoRoot
$devices = Get-AdbDevices -UsbOnly:$UsbOnly
foreach ($d in $devices) {
    Write-Host "Clearing $pkg on $($d.Serial)..." -ForegroundColor Yellow
    & adb -s $d.Serial shell pm clear $pkg
}
