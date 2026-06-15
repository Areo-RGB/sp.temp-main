Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Find-RepoRoot {
    param([string]$Start = (Get-Location).Path)
    $dir = Resolve-Path $Start
    while ($true) {
        if ((Test-Path (Join-Path $dir "android\settings.gradle.kts")) -or (Test-Path (Join-Path $dir "settings.gradle.kts"))) {
            return $dir.Path
        }
        $parent = Split-Path $dir -Parent
        if ([string]::IsNullOrWhiteSpace($parent) -or $parent -eq $dir) {
            throw "Could not find repo root. Run from the sp.temp-main repo or pass -RepoRoot."
        }
        $dir = Resolve-Path $parent
    }
}

function Get-AndroidDir {
    param([string]$RepoRoot)
    if (Test-Path (Join-Path $RepoRoot "android\settings.gradle.kts")) {
        return (Join-Path $RepoRoot "android")
    }
    if (Test-Path (Join-Path $RepoRoot "settings.gradle.kts")) {
        return $RepoRoot
    }
    throw "No Android Gradle project found below $RepoRoot"
}

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command '$Name'. Add it to PATH and retry."
    }
}

function Invoke-Gradle {
    param(
        [Parameter(Mandatory=$true)][string]$AndroidDir,
        [Parameter(Mandatory=$true)][string[]]$Args
    )
    Push-Location $AndroidDir
    try {
        if (Test-Path ".\gradlew.bat") {
            & .\gradlew.bat @Args
        } elseif (Test-Path ".\gradlew") {
            & .\gradlew @Args
        } else {
            Require-Command gradle
            & gradle @Args
        }
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

function Get-AdbRows {
    Require-Command adb
    $lines = & adb devices | Select-Object -Skip 1
    foreach ($line in $lines) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line -split "\s+"
        if ($parts.Count -lt 2) { continue }
        [PSCustomObject]@{ Serial = $parts[0]; State = $parts[1] }
    }
}

function Get-AdbDevices {
    param([switch]$UsbOnly)
    $devices = Get-AdbRows | Where-Object { $_.State -eq "device" }
    if ($UsbOnly) {
        $devices = $devices | Where-Object { $_.Serial -notmatch ":\d+$" }
    }
    return @($devices)
}

function Get-ApkPath {
    param([string]$AndroidDir)
    $apk = Join-Path $AndroidDir "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "APK not found: $apk. Run build-debug.ps1 first." }
    return $apk
}

function Get-DeviceProp {
    param([string]$Serial, [string]$Prop)
    $value = & adb -s $Serial shell getprop $Prop 2>$null
    return ($value -join "").Trim()
}

function Get-DeviceWifiIp {
    param([string]$Serial)
    $output = & adb -s $Serial shell "ip -f inet addr show wlan0 2>/dev/null | grep -oE 'inet [0-9.]+' | awk '{print `$2}'" 2>$null
    $ip = ($output -join "").Trim()
    if ($ip) { return $ip }

    $output = & adb -s $Serial shell "ifconfig wlan0 2>/dev/null | grep -oE 'inet (addr:)?[0-9.]+' | head -1 | sed 's/inet addr://;s/inet //'" 2>$null
    return (($output -join "").Trim())
}

function Get-PackageName {
    param([string]$RepoRoot)
    $gradleFile = Join-Path $RepoRoot "android\app\build.gradle.kts"
    if (-not (Test-Path $gradleFile)) { return "com.trapwire.racing" }
    $text = Get-Content $gradleFile -Raw
    if ($text -match 'applicationId\s*=\s*"([^"]+)"') { return $Matches[1] }
    return "com.trapwire.racing"
}
