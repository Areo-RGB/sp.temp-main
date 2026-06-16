param(
    [string]$Repo = "Areo-RGB/sp.temp-main",
    [string]$Workflow = "debug-apk.yml",
    [string]$Ref = "",
    [switch]$Wait
)
. "$PSScriptRoot\_common.ps1"
Require-Command gh

if ([string]::IsNullOrWhiteSpace($Ref)) {
    $Ref = (git branch --show-current 2>$null).Trim()
    if ([string]::IsNullOrWhiteSpace($Ref)) { $Ref = "main" }
}

Write-Host "Triggering $Workflow on $Repo@$Ref" -ForegroundColor Cyan
Write-Host "Version bump note: debug-apk.yml uses epoch seconds for VERSION_CODE/VERSION_NAME, so every workflow run bumps the in-app update version." -ForegroundColor DarkGray

gh workflow run $Workflow --repo $Repo --ref $Ref
if ($LASTEXITCODE -ne 0) { throw "Failed to trigger workflow $Workflow for $Repo@$Ref" }

Write-Host "Workflow dispatched. Release will update at https://github.com/$Repo/releases/tag/debug-latest when the run finishes." -ForegroundColor Green

if ($Wait) {
    Start-Sleep -Seconds 5
    $runId = gh run list --repo $Repo --workflow $Workflow --branch $Ref --limit 1 --json databaseId --jq '.[0].databaseId'
    if ([string]::IsNullOrWhiteSpace($runId)) { throw "Could not find the dispatched workflow run." }
    Write-Host "Watching run $runId..." -ForegroundColor Cyan
    gh run watch $runId --repo $Repo --exit-status
    if ($LASTEXITCODE -ne 0) { throw "Workflow run failed: $runId" }
    Write-Host "Workflow finished successfully. Devices can now check the Debug updates card." -ForegroundColor Green
}
