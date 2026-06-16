param(
    [string]$RepoRoot = "",
    [string]$Repo = "Areo-RGB/sp.temp-main",
    [string]$Tag = "debug-latest",
    [long]$VersionCode = 0,
    [string]$VersionName = "",
    [string]$AppPackage = "com.trapwire.racing",
    [switch]$SkipBuild,
    [switch]$SkipPublish,
    [switch]$SkipInstall,
    [switch]$SkipLaunch,
    [switch]$UsbOnly,
    [switch]$FailOnInstallError
)

. "$PSScriptRoot\_common.ps1"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

function Invoke-NativeChecked {
    param(
        [Parameter(Mandatory=$true)][string]$FilePath,
        [Parameter(Mandatory=$true)][string[]]$Arguments,
        [Parameter(Mandatory=$true)][string]$ErrorMessage
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$ErrorMessage (exit $LASTEXITCODE)" }
}
function Get-ShortGitSha {
    param([string]$Root)
    $sha = "unknown"
    try {
        $value = git -C $Root rev-parse --short HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($value)) {
            $sha = ($value -join "").Trim()
        }
    } catch { }
    return $sha
}

if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Find-RepoRoot }
$RepoRoot = (Resolve-Path $RepoRoot).Path
$AndroidDir = Get-AndroidDir $RepoRoot

if ($VersionCode -le 0) { $VersionCode = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() }
if ([string]::IsNullOrWhiteSpace($VersionName)) { $VersionName = "debug-$VersionCode" }

Write-Host "Trapwire debug all-in-one" -ForegroundColor Cyan
Write-Host "Repo root:   $RepoRoot"
Write-Host "GitHub repo: $Repo"
Write-Host "App package: $AppPackage"
Write-Host "Version:     $VersionName ($VersionCode)"
if (-not $SkipBuild) {
    Write-Host "Building debug APK..." -ForegroundColor Cyan
    Invoke-Gradle -AndroidDir $AndroidDir -Args @(
        ':app:assembleDebug',
        "-PciVersionCode=$VersionCode",
        "-PciVersionName=$VersionName",
        "-PupdateRepo=$Repo"
    )
} else {
    Write-Host "Skipping build; using existing debug APK." -ForegroundColor Yellow
}

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
    commit = Get-ShortGitSha $RepoRoot
    source = "build-bump-publish-install-debug.ps1"
}
$meta | ConvertTo-Json -Depth 4 | Set-Content $jsonOut -Encoding UTF8

Write-Host "Prepared release assets:" -ForegroundColor Green
Write-Host "  $apkOut"
Write-Host "  $jsonOut"

if (-not $SkipPublish) {
    Require-Command gh
    Write-Host "Publishing $Tag to $Repo..." -ForegroundColor Cyan
    gh release view $Tag --repo $Repo *> $null
    if ($LASTEXITCODE -ne 0) {
        Invoke-NativeChecked -FilePath gh -Arguments @(
            'release', 'create', $Tag,
            '--repo', $Repo,
            '--title', 'Latest debug APK',
            '--notes', 'Rolling debug APK for personal devices.',
            '--prerelease'
        ) -ErrorMessage "Failed to create release $Tag"
    }

    Invoke-NativeChecked -FilePath gh -Arguments @(
        'release', 'upload', $Tag, $apkOut, $jsonOut,
        '--repo', $Repo,
        '--clobber'
    ) -ErrorMessage "Failed to upload debug release assets"
    Write-Host "Published $VersionName to https://github.com/$Repo/releases/tag/$Tag" -ForegroundColor Green
} else {
    Write-Host "Skipping GitHub release upload." -ForegroundColor Yellow
}

if ($SkipInstall) {
    Write-Host "Skipping device install and launch." -ForegroundColor Yellow
    exit 0
}

Write-Host "Trying ADB install on connected devices..." -ForegroundColor Cyan
try {
    Require-Command adb
} catch {
    Write-Host "ADB not found; publish/build succeeded but install was skipped." -ForegroundColor Yellow
    exit 0
}

$devices = Get-AdbDevices -UsbOnly:$UsbOnly
if ($devices.Count -eq 0) {
    Write-Host "No authorized ADB devices found; publish/build succeeded." -ForegroundColor Yellow
    adb devices
    exit 0
}

$results = @()
foreach ($d in $devices) {
    $model = ""
    try { $model = Get-DeviceProp $d.Serial "ro.product.model" } catch { }
    Write-Host "Installing on $($d.Serial) $model..." -ForegroundColor Cyan

    $installArgs = @('-s', $d.Serial, 'install', '-r', '-d', $apkOut)
    & adb @installArgs
    $ok = ($LASTEXITCODE -eq 0)
    if (-not $ok) {
        Write-Host "Install failed on $($d.Serial); retrying once..." -ForegroundColor Yellow
        Start-Sleep -Seconds 2
        & adb @installArgs
        $ok = ($LASTEXITCODE -eq 0)
    }
    $launched = $false
    if ($ok -and -not $SkipLaunch) {
        Write-Host "Launching $AppPackage on $($d.Serial)..." -ForegroundColor Cyan
        & adb -s $d.Serial shell monkey -p $AppPackage -c android.intent.category.LAUNCHER 1
        $launched = ($LASTEXITCODE -eq 0)
        if (-not $launched) {
            Write-Host "Launch failed on $($d.Serial); install still succeeded." -ForegroundColor Yellow
        }
    } elseif ($SkipLaunch) {
        Write-Host "Skipping app launch on $($d.Serial)." -ForegroundColor Yellow
    }

    $results += [PSCustomObject]@{
        Serial = $d.Serial
        Model = $model
        Installed = $ok
        Launched = $launched
    }
    if (-not $ok) {
        Write-Host "Install failed on $($d.Serial) after retry; launch skipped." -ForegroundColor Yellow
    }
}
Write-Host "Install summary:" -ForegroundColor Cyan
$results | Format-Table -AutoSize

$failed = @($results | Where-Object { -not $_.Installed })
if ($failed.Count -gt 0 -and $FailOnInstallError) {
    throw "$($failed.Count) install(s) failed."
}

Write-Host "Done: $VersionName" -ForegroundColor Green
