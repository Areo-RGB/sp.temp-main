param(
    [string]$RepoRoot = "",
    [switch]$NoBuild,
    [switch]$AllowDowngrade = $true
)
. "$PSScriptRoot\_common.ps1"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Find-RepoRoot }
$AndroidDir = Get-AndroidDir $RepoRoot

if (-not $NoBuild) {
    Invoke-Gradle -AndroidDir $AndroidDir -Args @(':app:assembleDebug')
}
$apk = Get-ApkPath $AndroidDir
$devices = Get-AdbDevices -UsbOnly
if ($devices.Count -eq 0) {
    Write-Host "No USB-authorized ADB devices found." -ForegroundColor Yellow
    adb devices
    exit 1
}

foreach ($d in $devices) {
    $model = Get-DeviceProp $d.Serial "ro.product.model"
    Write-Host "Installing on $($d.Serial) $model..." -ForegroundColor Cyan
    $installArgs = @('-s', $d.Serial, 'install', '-r')
    if ($AllowDowngrade) { $installArgs += '-d' }
    $installArgs += $apk
    & adb @installArgs
    if ($LASTEXITCODE -ne 0) { throw "Install failed on $($d.Serial)" }
}
Write-Host "Installed on $($devices.Count) USB device(s)." -ForegroundColor Green
