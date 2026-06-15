param(
    [Parameter(Mandatory=$true)][string]$Repo,
    [string]$DebugKeystore = "$env:USERPROFILE\.android\debug.keystore"
)
. "$PSScriptRoot\_common.ps1"
Require-Command gh

if (-not (Test-Path $DebugKeystore)) {
    throw "Debug keystore not found: $DebugKeystore. Build/install locally once or create a debug keystore first."
}

Write-Host "Encoding $DebugKeystore..." -ForegroundColor Cyan
$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $DebugKeystore))
$b64 = [Convert]::ToBase64String($bytes)

# Default Android debug keystore values. If you use a custom keystore, change these in GitHub after running this.
$secrets = @{
    ANDROID_DEBUG_KEYSTORE_B64 = $b64
    ANDROID_DEBUG_KEYSTORE_PASSWORD = "android"
    ANDROID_DEBUG_KEY_ALIAS = "androiddebugkey"
    ANDROID_DEBUG_KEY_PASSWORD = "android"
}

foreach ($name in $secrets.Keys) {
    Write-Host "Setting GitHub secret $name on $Repo" -ForegroundColor Cyan
    $secrets[$name] | gh secret set $name --repo $Repo
    if ($LASTEXITCODE -ne 0) { throw "Failed to set $name" }
}

Write-Host "Done. Now add the restore step from patches/debug-workflow-keystore-step.yml before the Gradle build step." -ForegroundColor Green
