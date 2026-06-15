param([switch]$UsbOnly)
. "$PSScriptRoot\_common.ps1"

$devices = Get-AdbDevices -UsbOnly:$UsbOnly
if ($devices.Count -eq 0) {
    Write-Host "No authorized ADB devices found. Raw adb devices:" -ForegroundColor Yellow
    adb devices
    exit 0
}

$rows = foreach ($d in $devices) {
    [PSCustomObject]@{
        Serial = $d.Serial
        Model = Get-DeviceProp $d.Serial "ro.product.model"
        Brand = Get-DeviceProp $d.Serial "ro.product.brand"
        Android = Get-DeviceProp $d.Serial "ro.build.version.release"
        Sdk = Get-DeviceProp $d.Serial "ro.build.version.sdk"
        WifiIp = Get-DeviceWifiIp $d.Serial
        Transport = if ($d.Serial -match ":\d+$") { "wireless" } else { "usb" }
    }
}

$rows | Format-Table -AutoSize
