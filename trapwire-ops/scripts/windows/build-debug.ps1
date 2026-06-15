param(
    [string]$RepoRoot = "",
    [int]$VersionCode = 0,
    [string]$VersionName = "",
    [string]$UpdateRepo = ""
)
. "$PSScriptRoot\_common.ps1"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Find-RepoRoot }
$AndroidDir = Get-AndroidDir $RepoRoot

$args = @(':app:assembleDebug')
if ($VersionCode -gt 0) { $args += "-PciVersionCode=$VersionCode" }
if (-not [string]::IsNullOrWhiteSpace($VersionName)) { $args += "-PciVersionName=$VersionName" }
if (-not [string]::IsNullOrWhiteSpace($UpdateRepo)) { $args += "-PupdateRepo=$UpdateRepo" }

Invoke-Gradle -AndroidDir $AndroidDir -Args $args
$apk = Get-ApkPath $AndroidDir
Write-Host "Built APK:" -ForegroundColor Green
Write-Host $apk
