param([string]$RepoRoot = "", [switch]$UsbOnly)
. "$PSScriptRoot\_common.ps1"
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Find-RepoRoot }
$pkg = Get-PackageName $RepoRoot
$devices = Get-AdbDevices -UsbOnly:$UsbOnly
foreach ($d in $devices) {
    Write-Host "Starting $pkg on $($d.Serial)..." -ForegroundColor Cyan
    & adb -s $d.Serial shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
}
