param(
    [int]$Port = 5555,
    [int]$DelaySeconds = 3
)
. "$PSScriptRoot\_common.ps1"

$devices = Get-AdbDevices -UsbOnly
if ($devices.Count -eq 0) { Write-Host "No USB devices found." -ForegroundColor Yellow; adb devices; exit 1 }

$targets = @()
foreach ($d in $devices) {
    $model = Get-DeviceProp $d.Serial "ro.product.model"
    Write-Host "Enabling tcpip:$Port on $($d.Serial) $model..." -ForegroundColor Cyan
    & adb -s $d.Serial tcpip $Port
}
Start-Sleep -Seconds $DelaySeconds

foreach ($d in $devices) {
    $ip = Get-DeviceWifiIp $d.Serial
    if ($ip) {
        $target = "$ip`:$Port"
        $targets += $target
        Write-Host "Target: adb connect $target" -ForegroundColor Green
    } else {
        Write-Host "No wlan0 IPv4 found for $($d.Serial). Check Wi-Fi on device." -ForegroundColor Yellow
    }
}

$configDir = Join-Path (Split-Path $PSScriptRoot -Parent) "..\config"
$configDir = Resolve-Path $configDir
$targetFile = Join-Path $configDir "wireless-targets.txt"
if ($targets.Count -gt 0) {
    $existing = @()
    if (Test-Path $targetFile) { $existing = Get-Content $targetFile | Where-Object { $_ -and -not $_.TrimStart().StartsWith('#') } }
    ($existing + $targets | Sort-Object -Unique) | Set-Content $targetFile -Encoding UTF8
    Write-Host "Saved targets to $targetFile" -ForegroundColor Green
}
