param([string]$RepoRoot = "")
. "$PSScriptRoot\_common.ps1"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Find-RepoRoot }
$manifest = Join-Path $RepoRoot "android\app\src\main\AndroidManifest.xml"
$pathsDir = Join-Path $RepoRoot "android\app\src\main\res\xml"
$pathsFile = Join-Path $pathsDir "trapwire_file_paths.xml"
$updaterFile = Join-Path $RepoRoot "android\app\src\main\java\com\trapwire\racing\DebugUpdater.kt"

if (-not (Test-Path $manifest)) { throw "Manifest not found: $manifest" }
New-Item -ItemType Directory -Force -Path $pathsDir | Out-Null

@'
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="updates" path="updates/" />
</paths>
'@ | Set-Content $pathsFile -Encoding UTF8

$manifestText = Get-Content $manifest -Raw
if ($manifestText -notmatch 'androidx\.core\.content\.FileProvider') {
    $provider = @'
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/trapwire_file_paths" />
        </provider>
'@
    $manifestText = $manifestText -replace "\s*</application>", "`n$provider    </application>"
    Set-Content $manifest -Value $manifestText -Encoding UTF8
    Write-Host "Added FileProvider to manifest." -ForegroundColor Green
} else {
    Write-Host "Manifest already contains FileProvider." -ForegroundColor Yellow
}

@'
package com.trapwire.racing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class DebugUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkName: String,
)

@Composable
fun DebugUpdateCard(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<DebugUpdateInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val found = DebugUpdater.findUpdate()
        checked = true
        update = found
        if (found != null) message = "New debug build is ready."
    }

    val target = update
    if (!checked || target == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Debug update ready", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(
                "${BuildConfig.VERSION_NAME} → ${target.versionName}. Downloads inside the app, then Android asks to confirm install.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            message = DebugUpdater.downloadAndInstall(context, target)
                            busy = false
                        }
                    },
                ) { Text(if (busy) "Working..." else "Update") }
                OutlinedButton(enabled = !busy, onClick = { update = null }) { Text("Later") }
            }
        }
    }
}

object DebugUpdater {
    suspend fun findUpdate(): DebugUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val repo = BuildConfig.UPDATE_REPO.trim()
            if (!repo.contains('/')) return@runCatching null
            val releaseUrl = "https://api.github.com/repos/$repo/releases/tags/${BuildConfig.UPDATE_RELEASE_TAG}"
            val release = JSONObject(fetchString(releaseUrl))
            val assets = release.getJSONArray("assets")
            val metadataAsset = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == BuildConfig.UPDATE_METADATA_ASSET }
                ?: return@runCatching null
            val metadata = JSONObject(fetchString(metadataAsset.getString("browser_download_url")))
            val versionCode = metadata.optInt("versionCode", 0)
            if (versionCode <= BuildConfig.VERSION_CODE) return@runCatching null
            val apkName = metadata.optString("apkAssetName", BuildConfig.UPDATE_APK_ASSET)
            val apkAsset = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == apkName }
                ?: return@runCatching null
            DebugUpdateInfo(
                versionCode = versionCode,
                versionName = metadata.optString("versionName", "debug-$versionCode"),
                apkUrl = apkAsset.getString("browser_download_url"),
                apkName = apkName,
            )
        }.getOrNull()
    }

    suspend fun downloadAndInstall(context: Context, update: DebugUpdateInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return "Allow installs from Trapwire once, then tap Update again."
        }

        val apkFile = withContext(Dispatchers.IO) { downloadApk(context, update) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        return "Opening Android installer..."
    }

    private fun downloadApk(context: Context, update: DebugUpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, update.apkName)
        openConnection(update.apkUrl).inputStream.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    fun handlePackageInstallerResult(activity: android.app.Activity, intent: android.content.Intent?) = Unit

    private fun fetchString(url: String): String = openConnection(url).inputStream.use { input ->
        input.bufferedReader().readText()
    }

    private fun openConnection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 60_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "TrapwireRacing/${BuildConfig.VERSION_NAME}".lowercase(Locale.US))
        if (responseCode !in 200..299) {
            val error = errorStream?.bufferedReader()?.readText().orEmpty()
            disconnect()
            throw IllegalStateException("HTTP $responseCode ${error.take(120)}")
        }
    }
}
'@ | Set-Content $updaterFile -Encoding UTF8

Write-Host "Wrote lower-prompt DebugUpdater.kt + manifest provider + file paths." -ForegroundColor Green
Write-Host "Now run: .\trapwire-ops\scripts\windows\build-debug.ps1" -ForegroundColor Cyan
