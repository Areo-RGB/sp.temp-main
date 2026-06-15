param([string]$RepoRoot = "", [switch]$UsbOnly)
. "$PSScriptRoot\_common.ps1"
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Find-RepoRoot }
$pkg = Get-PackageName $RepoRoot
$devices = Get-AdbDevices -UsbOnly:$UsbOnly

foreach ($d in $devices) {
    Write-Host "Opening unknown-apps permission screen for $pkg on $($d.Serial)..." -ForegroundColor Cyan
    & adb -s $d.Serial shell am start -a android.settings.MANAGE_UNKNOWN_APP_SOURCES -d "package:$pkg"
}
Write-Host "On each device, allow installs from Trapwire once. Android may not allow this to be granted by adb for normal apps." -ForegroundColor Yellow
