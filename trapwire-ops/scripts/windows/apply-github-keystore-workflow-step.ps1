param([string]$RepoRoot = "")
. "$PSScriptRoot\_common.ps1"
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Find-RepoRoot }
$workflow = Join-Path $RepoRoot ".github\workflows\debug-apk.yml"
if (-not (Test-Path $workflow)) { throw "Workflow not found: $workflow" }

$text = Get-Content $workflow -Raw
if ($text -match 'ANDROID_DEBUG_KEYSTORE_B64') {
    Write-Host "Workflow already appears to restore debug keystore." -ForegroundColor Yellow
    exit 0
}

$needle = @'
      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 9.4.1

'@
$insert = @'
      - name: Restore local debug keystore
        if: ${{ secrets.ANDROID_DEBUG_KEYSTORE_B64 != '' }}
        shell: bash
        run: |
          mkdir -p "$HOME/.android"
          echo "${{ secrets.ANDROID_DEBUG_KEYSTORE_B64 }}" | base64 --decode > "$HOME/.android/debug.keystore"

'@
if ($text -notlike "*$needle*") {
    throw "Could not find Gradle setup block. Open patches/debug-workflow-keystore-step.yml and insert it manually before Build debug APK."
}
$text = $text.Replace($needle, $needle + $insert)
Set-Content $workflow -Value $text -Encoding UTF8
Write-Host "Inserted debug keystore restore step into $workflow" -ForegroundColor Green
