param(
    [Parameter(Mandatory=$true)][string]$Repo,
    [string]$RepoRoot = "",
    [string]$Tag = "debug-latest",
    [int]$VersionCode = 0,
    [string]$VersionName = ""
)
. "$PSScriptRoot\_common.ps1"
Require-Command gh

if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Find-RepoRoot }
$AndroidDir = Get-AndroidDir $RepoRoot
if ($VersionCode -le 0) { $VersionCode = [int][DateTimeOffset]::UtcNow.ToUnixTimeSeconds() }
if ([string]::IsNullOrWhiteSpace($VersionName)) { $VersionName = "debug-$VersionCode" }

Invoke-Gradle -AndroidDir $AndroidDir -Args @(':app:assembleDebug', "-PciVersionCode=$VersionCode", "-PciVersionName=$VersionName", "-PupdateRepo=$Repo")
$apk = Get-ApkPath $AndroidDir

$releaseDir = Join-Path $RepoRoot "release"
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
$apkOut = Join-Path $releaseDir "trapwire-debug.apk"
$jsonOut = Join-Path $releaseDir "trapwire-debug.json"
Copy-Item $apk $apkOut -Force

$meta = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    apkAssetName = "trapwire-debug.apk"
    builtAt = [DateTimeOffset]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
    source = "publish-debug-release.ps1"
}
$meta | ConvertTo-Json -Depth 4 | Set-Content $jsonOut -Encoding UTF8

# Create the release if it does not exist, then overwrite assets.
gh release view $Tag --repo $Repo *> $null
if ($LASTEXITCODE -ne 0) {
    gh release create $Tag --repo $Repo --title "Latest debug APK" --notes "Rolling debug APK for personal devices." --prerelease
    if ($LASTEXITCODE -ne 0) { throw "Failed to create release $Tag" }
}

gh release upload $Tag $apkOut $jsonOut --repo $Repo --clobber
if ($LASTEXITCODE -ne 0) { throw "Failed to upload release assets" }
Write-Host "Published $VersionName to https://github.com/$Repo/releases/tag/$Tag" -ForegroundColor Green
