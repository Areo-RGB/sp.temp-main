package com.trapwire.racing

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.MicrophoneInfo
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val DATABASE_URL = "https://realtime-db-b2264-default-rtdb.europe-west1.firebasedatabase.app"
private const val DEFAULT_SENSITIVITY = 70
private const val DEFAULT_MIC_SENSITIVITY = 70
private const val START_BEEP_SAMPLE_RATE = 44_100
private const val START_BEEP_FRAME_MS = 10
private const val STARTER_ARM_TO_BEEP_DELAY_MS = 2_000L
private const val START_BEEP_PATTERN_SCORE = 0.58f
private const val START_BEEP_FREQUENCY_TOLERANCE = 0.45f
private const val START_SHOT_PATTERN_MAX_MS = 240L
private const val START_SHOT_PATTERN_MATCH_GRACE_MS = 350L

private val Neutral950 = Color(0xFF0A0A0A)
private val Neutral900 = Color(0xFF171717)
private val Neutral850 = Color(0xFF1F1F1F)
private val Neutral800 = Color(0xFF262626)
private val Neutral500 = Color(0xFF737373)
private val Neutral400 = Color(0xFFA3A3A3)
private val Neutral100 = Color(0xFFF5F5F5)
private val Blue500 = Color(0xFF3B82F6)
private val Blue600 = Color(0xFF2563EB)
private val Green400 = Color(0xFF4ADE80)
private val Green500 = Color(0xFF22C55E)
private val Green600 = Color(0xFF16A34A)
private val Amber400 = Color(0xFFFBBF24)
private val Red500 = Color(0xFFEF4444)

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ensureFirebaseReady()
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            TrapwireTheme {
                TrapwireApp(cameraExecutor)
            }
        }
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun ensureFirebaseReady() {
        if (FirebaseApp.getApps(this).isNotEmpty()) return

        val options = FirebaseOptions.Builder()
            .setApiKey("AIzaSyDPoogZrY20wvrN8ejssfAbHvpCzAIm57Y")
            .setApplicationId("1:80707107460:web:b102f2a3937c5d9e3f20d1")
            .setDatabaseUrl(DATABASE_URL)
            .setProjectId("realtime-db-b2264")
            .setStorageBucket("realtime-db-b2264.firebasestorage.app")
            .build()

        FirebaseApp.initializeApp(this, options)
    }
}

private data class SessionData(
    val status: String = "waiting",
    val createdAt: Long = 0L,
    val startTime: Long? = null,
    val sensitivity: Int = DEFAULT_SENSITIVITY,
    val micSensitivity: Int = DEFAULT_MIC_SENSITIVITY,
)

private data class ClientData(
    val id: String,
    val joinedAt: Long = 0L,
    val elapsedTime: Long? = null,
    val deviceName: String? = null,
)

private data class OfflineSessionSnapshot(
    val session: SessionData?,
    val clients: List<ClientData>,
    val serverTime: Long,
)

private data class StartBeepPattern(
    val sampleRate: Int,
    val samples: ShortArray,
    val durationMs: Long,
    val envelope: FloatArray,
    val zeroCrossHz: Float,
)

private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val CAMERA_SENSOR_TIMESTAMP_MAX_SKEW_NANOS = 5_000_000_000L
private const val TRAPWIRE_STRIP_WIDTH_FRACTION = 0.05f
private const val TRAPWIRE_CONFIRMATION_FRAME_COUNT = 2
private const val TRAPWIRE_BACKGROUND_KEEP_WEIGHT = 0.90f
private const val TRAPWIRE_BACKGROUND_NEW_WEIGHT = 1f - TRAPWIRE_BACKGROUND_KEEP_WEIGHT

private fun monotonicNowNanos(): Long = SystemClock.elapsedRealtimeNanos()
private fun monotonicNowMs(): Long = monotonicNowNanos() / NANOS_PER_MILLISECOND
private fun nanosToMillis(nanos: Long): Long = nanos / NANOS_PER_MILLISECOND
private fun elapsedMillisBetween(startNanos: Long, endNanos: Long): Long = nanosToMillis(endNanos - startNanos)

private class MotionDetectionState {
    @Volatile
    var session: SessionData? = null

    @Volatile
    var serverOffset: Long = 0L

    @Volatile
    var triggered: Boolean = false

    @Volatile
    var localStartTime: Long? = null

    @Volatile
    var backgroundStrip: FloatArray? = null

    @Volatile
    var consecutiveAboveThresholdFrames: Int = 0

    @Volatile
    var firstAboveThresholdElapsed: Long? = null

    fun reset() {
        backgroundStrip = null
        resetConfirmation()
    }

    fun resetConfirmation() {
        consecutiveAboveThresholdFrames = 0
        firstAboveThresholdElapsed = null
    }
}

@Composable
private fun TrapwireTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue500,
            secondary = Green500,
            surface = Neutral900,
            background = Neutral950,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onSurface = Neutral100,
            onBackground = Neutral100,
        ),
        content = content,
    )
}

@Composable
private fun TrapwireApp(cameraExecutor: ExecutorService) {
    val database = remember { FirebaseDatabase.getInstance(DATABASE_URL) }
    val serverOffset by rememberServerOffset(database)
    var screen by rememberSaveable { mutableStateOf("select") }

    BackHandler(screen != "select") {
        screen = when (screen) {
            "controllerFirebase", "controllerOffline" -> "controllerMode"
            "clientFirebase", "clientOffline" -> "clientMode"
            "controllerMode", "clientMode" -> "select"
            else -> "select"
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Neutral950) {
        when (screen) {
            "controllerMode" -> BackendSelectionScreen(
                title = "Controller Sync",
                subtitle = "Choose how racers will connect to this room.",
                onFirebaseSelected = { screen = "controllerFirebase" },
                onOfflineSelected = { screen = "controllerOffline" },
            )

            "clientMode" -> BackendSelectionScreen(
                title = "Client Sync",
                subtitle = "Offline BLE is the only client sync mode.",
                onFirebaseSelected = { screen = "clientFirebase" },
                onOfflineSelected = { screen = "clientOffline" },
            )

            "controllerFirebase" -> ControllerSession(database, serverOffset)
            "controllerOffline" -> OfflineControllerSession()
            "clientFirebase" -> ClientSession(database, serverOffset, cameraExecutor)
            "clientOffline" -> OfflineClientSession(cameraExecutor)
            else -> RoleSelectionScreen(
                onRoleSelected = { role ->
                    screen = if (role == "controller") "controllerMode" else "clientMode"
                },
            )
        }
    }
}

@Composable
private fun rememberServerOffset(database: FirebaseDatabase): androidx.compose.runtime.State<Long> {
    val offset = remember { mutableLongStateOf(0L) }

    DisposableEffect(database) {
        val offsetRef = database.getReference(".info/serverTimeOffset")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                offset.longValue = snapshot.getValue(Long::class.java) ?: 0L
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }

        offsetRef.addValueEventListener(listener)
        onDispose { offsetRef.removeEventListener(listener) }
    }

    return offset
}

@Composable
private fun RoleSelectionScreen(onRoleSelected: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Neutral900)
                    .border(1.dp, Neutral800, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("◎", color = Neutral100, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }

            Text("Trapwire Racing", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Neutral100)
            Text("Virtual motion detection finish line", color = Neutral500, fontSize = 15.sp)

            DebugUpdateCard()

            Spacer(Modifier.height(14.dp))

            RoleButton(
                title = "Controller",
                subtitle = "Create a room and start the race",
                symbol = "▣",
                tint = Blue500,
                onClick = { onRoleSelected("controller") },
            )

            RoleButton(
                title = "Client Camera",
                subtitle = "Join room as a native trapwire sensor",
                symbol = "◎",
                tint = Green500,
                onClick = { onRoleSelected("client") },
            )
        }
    }
}

@Composable
private fun BackendSelectionScreen(
    title: String,
    subtitle: String,
    onFirebaseSelected: () -> Unit,
    onOfflineSelected: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(title, color = Neutral100, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Neutral500, fontSize = 15.sp, textAlign = TextAlign.Center)

            Spacer(Modifier.height(8.dp))


            RoleButton(
                title = "Offline BLE",
                subtitle = "BLE device-to-device, no Firebase",
                symbol = "⌂",
                tint = Amber400,
                onClick = onOfflineSelected,
            )

            TinyText("BLE mode works phone-to-phone; keep devices close during join and race setup.")
        }
    }
}

@Composable
private fun RoleButton(
    title: String,
    subtitle: String,
    symbol: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Neutral900),
        border = BorderStroke(1.dp, Neutral800),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(symbol, color = tint, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, color = Neutral100, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Neutral500, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ControllerSession(database: FirebaseDatabase, serverOffset: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("123") }
    var sessionActive by rememberSaveable { mutableStateOf(false) }
    var sessionData by remember { mutableStateOf<SessionData?>(null) }
    val clients = remember { mutableStateListOf<ClientData>() }

    if (!sessionActive) {
        CreateSessionScreen(
            code = code,
            onCodeChange = { code = it.onlyDigits3() },
            onCreate = {
                scope.launch {
                    runCatching {
                        playBeep(durationMs = 80)
                        val sessionRef = database.getReference("sessions/$code")
                        sessionRef.setValue(
                            mapOf(
                                "status" to "waiting",
                                "createdAt" to System.currentTimeMillis(),
                                "sensitivity" to DEFAULT_SENSITIVITY,
                                "micSensitivity" to DEFAULT_MIC_SENSITIVITY,
                            ),
                        ).await()
                        sessionRef.onDisconnect().removeValue()
                    }.onSuccess {
                        sessionActive = true
                    }.onFailure {
                        toast(context, "Failed to create session: ${it.friendlyMessage()}")
                    }
                }
            },
        )
        return
    }

    DisposableEffect(sessionActive, code) {
        val sessionRef = database.getReference("sessions/$code")
        val clientsRef = database.getReference("sessions/$code/clients")

        val sessionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                sessionData = snapshot.toSessionData()
            }

            override fun onCancelled(error: DatabaseError) {
                toast(context, "Session listener failed: ${error.message}")
            }
        }

        val clientsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                clients.clear()
                snapshot.children
                    .mapNotNull { it.toClientData() }
                    .sortedBy { it.joinedAt }
                    .forEach { clients.add(it) }
            }

            override fun onCancelled(error: DatabaseError) {
                toast(context, "Client listener failed: ${error.message}")
            }
        }

        sessionRef.addValueEventListener(sessionListener)
        clientsRef.addValueEventListener(clientsListener)

        onDispose {
            sessionRef.removeEventListener(sessionListener)
            clientsRef.removeEventListener(clientsListener)
        }
    }

    ControllerDashboard(
        code = code,
        sessionData = sessionData,
        clients = clients,
        serverOffset = serverOffset,
        onSensitivityChange = { sensitivity ->
            scope.launch {
                database.getReference("sessions/$code/sensitivity").setValue(sensitivity).awaitSafely(context)
            }
        },
        onMicSensitivityChange = { sensitivity ->
            scope.launch {
                database.getReference("sessions/$code/micSensitivity").setValue(sensitivity).awaitSafely(context)
            }
        },
        onStartRace = {
            scope.launch {
                database.getReference("sessions/$code").updateChildren(
                    mapOf(
                        "status" to "running",
                        "startTime" to null,
                    ),
                ).awaitSafely(context)
                delay(STARTER_ARM_TO_BEEP_DELAY_MS)
                val statusAfterArm = database.getReference("sessions/$code/status").get().await()
                    .getValue(String::class.java)
                if (statusAfterArm == "running") {
                    playStarterBeep(context)
                }
            }
        },
        onResetRace = {
            scope.launch {
                val updates = mutableMapOf<String, Any?>(
                    "sessions/$code/status" to "waiting",
                    "sessions/$code/startTime" to null,
                )
                clients.forEach { client ->
                    updates["sessions/$code/clients/${client.id}/elapsedTime"] = null
                }
                database.reference.updateChildren(updates).awaitSafely(context)
            }
        },
    )
}

@Composable
private fun OfflineControllerSession() {
    BlePermissionsGate(
        description = "Offline BLE controller mode needs Bluetooth permissions to advertise the race room. Timing is measured locally on each client.",
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var code by rememberSaveable { mutableStateOf("123") }
        var sessionActive by rememberSaveable { mutableStateOf(false) }
        var statusMessage by rememberSaveable { mutableStateOf("Create a BLE room for nearby clients.") }
        var bleHost by remember { mutableStateOf<BleRaceHost?>(null) }
        var sessionData by remember {
            mutableStateOf(
                SessionData(
                    status = "waiting",
                    createdAt = System.currentTimeMillis(),
                    sensitivity = DEFAULT_SENSITIVITY,
                    micSensitivity = DEFAULT_MIC_SENSITIVITY,
                ),
            )
        }
        val clients = remember { mutableStateListOf<ClientData>() }
        val endpointClients = remember { mutableStateMapOf<String, String>() }

        fun updateBleSnapshot() {
            bleHost?.updateSnapshot(
                offlineSnapshotJson(
                    session = sessionData,
                    serverTimeMs = System.currentTimeMillis(),
                ),
            )
        }

        if (!sessionActive) {
            CreateSessionScreen(
                code = code,
                onCodeChange = { code = it.onlyDigits3() },
                onCreate = {
                    playBeep(durationMs = 80)
                    sessionData = SessionData(
                        status = "waiting",
                        createdAt = System.currentTimeMillis(),
                        sensitivity = DEFAULT_SENSITIVITY,
                        micSensitivity = DEFAULT_MIC_SENSITIVITY,
                    )
                    clients.clear()
                    endpointClients.clear()
                    sessionActive = true
                    statusMessage = "Starting BLE advertiser..."
                },
                title = "Create BLE Room",
                buttonText = "Advertise BLE Room",
                accent = Amber400,
                note = "Clients choose Offline BLE and enter this same code.",
            )
            return@BlePermissionsGate
        }

        DisposableEffect(sessionActive, code) {
            val host = BleRaceHost(
                context = context,
                code = code,
                onClientMessage = { endpointId, json ->
                    when (json.optString("type")) {
                        "join" -> {
                            val clientId = json.optString("clientId").ifBlank { endpointId.takeLast(6) }
                            val deviceName = json.optString("deviceName").ifBlank { "BLE Client" }
                            endpointClients[endpointId] = clientId
                            clients.upsert(
                                ClientData(
                                    id = clientId,
                                    joinedAt = System.currentTimeMillis(),
                                    deviceName = deviceName,
                                ),
                            )
                            playBeep(durationMs = 80)
                            updateBleSnapshot()
                        }

                        "finish" -> {
                            val clientId = json.optString("clientId").ifBlank { endpointClients[endpointId] ?: endpointId.takeLast(6) }
                            val elapsed = json.optNullableLong("elapsedTime")
                            if (elapsed != null) {
                                clients.indexOfFirst { it.id == clientId }.takeIf { it >= 0 }?.let { index ->
                                    clients[index] = clients[index].copy(elapsedTime = elapsed)
                                }
                                updateBleSnapshot()
                            }
                        }
                    }
                },
                onDeviceDisconnected = { endpointId ->
                    endpointClients.remove(endpointId)?.let { clientId ->
                        clients.removeAll { it.id == clientId }
                    }
                    updateBleSnapshot()
                },
                onStatus = { statusMessage = it },
                onError = { message ->
                    statusMessage = message
                    toast(context, message)
                },
            )
            bleHost = host
            host.updateSnapshot(offlineSnapshotJson(sessionData, System.currentTimeMillis()))
            host.start()

            onDispose {
                host.stop()
                if (bleHost === host) bleHost = null
                endpointClients.clear()
            }
        }

        LaunchedEffect(sessionData, sessionActive) {
            if (sessionActive) updateBleSnapshot()
        }

        ControllerDashboard(
            code = code,
            sessionData = sessionData,
            clients = clients,
            serverOffset = 0L,
            syncLabel = statusMessage,
            onSensitivityChange = { sensitivity ->
                sessionData = sessionData.copy(sensitivity = sensitivity)
            },
            onMicSensitivityChange = { sensitivity ->
                sessionData = sessionData.copy(micSensitivity = sensitivity)
            },
            onStartRace = {
                scope.launch {
                    sessionData = sessionData.copy(
                        status = "running",
                        startTime = null,
                    )
                    delay(STARTER_ARM_TO_BEEP_DELAY_MS)
                    if (sessionData.status == "running" && sessionData.startTime == null) {
                        playStarterBeep(context)
                    }
                }
            },
            onResetRace = {
                sessionData = sessionData.copy(status = "waiting", startTime = null)
                clients.replaceAll { it.copy(elapsedTime = null) }
            },
        )
    }
}


@Composable
private fun OfflineClientSession(cameraExecutor: ExecutorService) {
    BlePermissionsGate(
        description = "Offline BLE client mode needs Bluetooth permissions to discover the controller. Timing is measured locally on this device.",
    ) {
        val context = LocalContext.current
        val clientId = rememberSaveable { randomClientId() }
        val motionState = remember { MotionDetectionState() }

        var code by rememberSaveable { mutableStateOf("123") }
        var deviceName by rememberSaveable { mutableStateOf("Client ${(10..99).random()}") }
        var discovering by rememberSaveable { mutableStateOf(false) }
        var joined by rememberSaveable { mutableStateOf(false) }
        var statusMessage by rememberSaveable { mutableStateOf("Enter the controller code, then search BLE.") }
        var bleClient by remember { mutableStateOf<BleRaceClient?>(null) }
        var sessionData by remember { mutableStateOf<SessionData?>(null) }
        var triggered by rememberSaveable { mutableStateOf(false) }
        var finalTime by rememberSaveable { mutableStateOf<Long?>(null) }
        var soundStartTime by rememberSaveable { mutableStateOf<Long?>(null) }
        var micLevel by remember { mutableIntStateOf(0) }
        var localTimer by remember { mutableLongStateOf(0L) }
        var aeLocked by rememberSaveable { mutableStateOf(false) }
        var previewEnabled by rememberSaveable { mutableStateOf(true) }
        var cameraActive by rememberSaveable { mutableStateOf(false) }
        var activeCamera by remember { mutableStateOf<Camera?>(null) }

        fun disconnectBle() {
            bleClient?.close()
            bleClient = null
        }

        SideEffect {
            motionState.session = sessionData
            motionState.triggered = triggered
            motionState.localStartTime = soundStartTime
        }

        DisposableEffect(Unit) {
            onDispose { disconnectBle() }
        }

        if (!joined) {
            OfflineJoinRaceScreen(
                code = code,
                deviceName = deviceName,
                searching = discovering,
                statusMessage = statusMessage,
                onCodeChange = { code = it.onlyDigits3() },
                onDeviceNameChange = { deviceName = it },
                onJoin = {
                    playBeep(durationMs = 80)
                    disconnectBle()
                    discovering = true
                    statusMessage = "Searching for BLE room Trapwire-$code..."
                    val client = BleRaceClient(
                        context = context,
                        code = code,
                        joinJson = offlineJoinJson(clientId, deviceName.trim()),
                        onSnapshot = { snapshot ->
                            val parsed = snapshot.toOfflineSessionSnapshot() ?: return@BleRaceClient
                            sessionData = parsed.session
                        },
                        onConnected = {
                            joined = true
                            discovering = false
                            statusMessage = "Connected to BLE controller."
                        },
                        onDisconnected = {
                            joined = false
                            discovering = false
                            sessionData = null
                            statusMessage = "Controller disconnected. Search again to rejoin."
                            bleClient = null
                        },
                        onStatus = { statusMessage = it },
                        onError = { message ->
                            statusMessage = message
                            discovering = false
                            toast(context, message)
                        },
                    )
                    bleClient = client
                    client.start()
                },
                onCancel = {
                    disconnectBle()
                    discovering = false
                    statusMessage = "Search cancelled."
                },
            )
            return@BlePermissionsGate
        }

        LaunchedEffect(sessionData?.status) {
            if (sessionData?.status == "waiting") {
                triggered = false
                finalTime = null
                soundStartTime = null
                micLevel = 0
                localTimer = 0L
                motionState.triggered = false
                motionState.localStartTime = null
                motionState.reset()
            }
        }

        MicrophoneStartDetector(
            armed = sessionData?.status == "running" && soundStartTime == null && !triggered,
            micSensitivity = sessionData?.micSensitivity ?: DEFAULT_MIC_SENSITIVITY,
            nowNanos = { monotonicNowNanos() },
            onLevel = { micLevel = it },
            onStart = { startTime ->
                if (soundStartTime == null && sessionData?.status == "running") {
                    soundStartTime = startTime
                    motionState.localStartTime = startTime
                    micLevel = 0
                }
            },
        )

        LaunchedEffect(sessionData?.status, soundStartTime, triggered) {
            while (sessionData?.status == "running" && !triggered) {
                val startTime = soundStartTime
                val now = monotonicNowNanos()
                localTimer = if (startTime != null && now >= startTime) elapsedMillisBetween(startTime, now) else 0L
                delay(33L)
            }
            if (sessionData?.status != "running") {
                localTimer = 0L
            }
        }

        ClientDashboard(
            code = code,
            sessionData = sessionData,
            localTimer = localTimer,
            timerStarted = soundStartTime != null,
            micLevel = micLevel,
            triggered = triggered,
            finalTime = finalTime,
            cameraActive = cameraActive,
            aeLocked = aeLocked,
            previewEnabled = previewEnabled,
            onPreviewToggle = { previewEnabled = !previewEnabled },
            onAeLockToggle = {
                val nextLocked = !aeLocked
                aeLocked = nextLocked
                setAeAwbLock(activeCamera, nextLocked)
            },
            cameraContent = {
                CameraTrapwireView(
                    motionState = motionState,
                    cameraExecutor = cameraExecutor,
                    aeLocked = aeLocked,
                    previewEnabled = previewEnabled,
                    onCameraReady = { camera ->
                        activeCamera = camera
                        cameraActive = true
                        if (aeLocked) setAeAwbLock(camera, true)
                    },
                    onCameraStopped = {
                        activeCamera = null
                        cameraActive = false
                    },
                    onElapsedDetected = { elapsed ->
                        val client = bleClient
                        if (!triggered && client != null) {
                            triggered = true
                            finalTime = elapsed
                            motionState.triggered = true
                            client.sendJson(offlineFinishJson(clientId, elapsed))
                        }
                    },
                )
            },
        )
    }
}


@Composable
private fun CreateSessionScreen(
    code: String,
    onCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    title: String = "Create Session",
    buttonText: String = "Open Room",
    accent: Color = Blue500,
    note: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 380.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Neutral900),
            border = BorderStroke(1.dp, Neutral800),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("▣", color = accent, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text(title, color = Neutral100, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                CodeField(label = "Enter 3-Digit Code", code = code, onCodeChange = onCodeChange, accent = accent)
                note?.let { TinyText(it) }
                Button(
                    onClick = onCreate,
                    enabled = code.length == 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (accent == Amber400) Amber400 else Blue600),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(buttonText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (accent == Amber400) Color.Black else Color.White)
                }
            }
        }
    }
}

@Composable
private fun ControllerDashboard(
    code: String,
    sessionData: SessionData?,
    clients: List<ClientData>,
    serverOffset: Long,
    syncLabel: String = "Server sync offset",
    onSensitivityChange: (Int) -> Unit,
    onMicSensitivityChange: (Int) -> Unit,
    onStartRace: () -> Unit,
    onResetRace: () -> Unit,
) {
    val sensitivity = sessionData?.sensitivity ?: DEFAULT_SENSITIVITY
    val micSensitivity = sessionData?.micSensitivity ?: DEFAULT_MIC_SENSITIVITY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Neutral900)
                .border(1.dp, Neutral800, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Label("SESSION CODE")
                Text(code, color = Neutral100, fontSize = 38.sp, fontFamily = FontFamily.Monospace, letterSpacing = 5.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Label("STATUS")
                StatusChip(
                    text = if (sessionData?.status == "running") "ARMED" else sessionData?.status?.uppercase(Locale.US) ?: "WAITING",
                    color = if (sessionData?.status == "running") Green400 else Amber400,
                )
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Label("CLIENT MOTION SENSITIVITY")
                    Text(
                        "Higher percentages trigger on smaller changes.",
                        color = Neutral500,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                Text("$sensitivity%", color = Blue500, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(12.dp))
            Slider(
                value = sensitivity.toFloat(),
                onValueChange = { onSensitivityChange((it / 5f).roundToInt() * 5) },
                valueRange = 10f..100f,
                steps = 17,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TinyText("MIN 10%")
                TinyText("DEFAULT 70%")
                TinyText("MAX 100%")
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Label("MICROPHONE START SENSITIVITY")
                    Text(
                        "Higher percentages start the timer on quieter beep sounds.",
                        color = Neutral500,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                Text("$micSensitivity%", color = Amber400, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(12.dp))
            Slider(
                value = micSensitivity.toFloat(),
                onValueChange = { onMicSensitivityChange((it / 5f).roundToInt() * 5) },
                valueRange = 10f..100f,
                steps = 17,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TinyText("LOUD 10%")
                TinyText("DEFAULT 70%")
                TinyText("QUIET 100%")
            }
        }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Label("SCREEN WAKE LOCK")
                    Text("Active — Android keeps the screen on while this app is open.", color = Neutral500, fontSize = 12.sp)
                }
                StatusChip("STAY ON", Green400)
            }
        }

        SectionCard {
            Text("Connected Devices (${clients.size})", color = Neutral100, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            if (clients.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Waiting for racers to join...", color = Neutral500, fontSize = 17.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    clients.forEach { client ->
                        ClientRow(client = client, running = sessionData?.status == "running")
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onStartRace,
                enabled = clients.isNotEmpty() && sessionData?.status != "running",
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Arm + Beep", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onResetRace,
                enabled = sessionData?.status != "waiting",
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Neutral800),
            ) {
                Text("Reset")
            }
        }

        TinyText(if (serverOffset == 0L && syncLabel != "Server sync offset") syncLabel else syncLabel + ": " + serverOffset + "ms")
    }
}

@Composable
private fun ClientRow(client: ClientData, running: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Neutral950)
            .border(1.dp, Neutral800, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Neutral800),
                contentAlignment = Alignment.Center,
            ) {
                Text(client.id.take(2), color = Neutral400, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Text(client.deviceName ?: "Unknown Client", color = Neutral100, fontWeight = FontWeight.Medium)
        }
        Text(
            text = when {
                client.elapsedTime != null -> formatSeconds(client.elapsedTime)
                running -> "Running..."
                else -> "Ready"
            },
            color = when {
                client.elapsedTime != null -> Green400
                running -> Neutral500
                else -> Neutral500
            },
            fontWeight = if (client.elapsedTime != null) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun ClientSession(database: FirebaseDatabase, serverOffset: Long, cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clientId = rememberSaveable { randomClientId() }
    val motionState = remember { MotionDetectionState() }

    var code by rememberSaveable { mutableStateOf("123") }
    var deviceName by rememberSaveable { mutableStateOf("Client ${(10..99).random()}") }
    var joined by rememberSaveable { mutableStateOf(false) }
    var sessionData by remember { mutableStateOf<SessionData?>(null) }
    var triggered by rememberSaveable { mutableStateOf(false) }
    var finalTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var soundStartTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var micLevel by remember { mutableIntStateOf(0) }
    var localTimer by remember { mutableLongStateOf(0L) }
    var aeLocked by rememberSaveable { mutableStateOf(false) }
    var previewEnabled by rememberSaveable { mutableStateOf(true) }
    var cameraActive by rememberSaveable { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    SideEffect {
        motionState.session = sessionData
        motionState.serverOffset = serverOffset
        motionState.triggered = triggered
        motionState.localStartTime = soundStartTime
    }

    if (!joined) {
        JoinRaceScreen(
            code = code,
            deviceName = deviceName,
            onCodeChange = { code = it.onlyDigits3() },
            onDeviceNameChange = { deviceName = it },
            onJoin = {
                scope.launch {
                    runCatching {
                        playBeep(durationMs = 80)
                        val clientRef = database.getReference("sessions/$code/clients/$clientId")
                        clientRef.setValue(
                            mapOf(
                                "joinedAt" to System.currentTimeMillis(),
                                "deviceName" to deviceName.trim(),
                            ),
                        ).await()
                        clientRef.onDisconnect().removeValue()
                    }.onSuccess {
                        joined = true
                    }.onFailure {
                        toast(context, "Failed to join session: ${it.friendlyMessage()}")
                    }
                }
            },
        )
        return
    }

    DisposableEffect(joined, code, clientId) {
        val sessionRef = database.getReference("sessions/$code")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                sessionData = snapshot.toSessionData()
            }

            override fun onCancelled(error: DatabaseError) {
                toast(context, "Session listener failed: ${error.message}")
            }
        }

        sessionRef.addValueEventListener(listener)
        onDispose { sessionRef.removeEventListener(listener) }
    }

    LaunchedEffect(sessionData?.status) {
        if (sessionData?.status == "waiting") {
            triggered = false
            finalTime = null
            soundStartTime = null
            micLevel = 0
            localTimer = 0L
            motionState.triggered = false
            motionState.localStartTime = null
            motionState.reset()
        }
    }

    MicrophoneStartDetector(
        armed = sessionData?.status == "running" && soundStartTime == null && !triggered,
        micSensitivity = sessionData?.micSensitivity ?: DEFAULT_MIC_SENSITIVITY,
        nowNanos = { monotonicNowNanos() },
        onLevel = { micLevel = it },
        onStart = { startTime ->
            if (soundStartTime == null && sessionData?.status == "running") {
                soundStartTime = startTime
                motionState.localStartTime = startTime
                micLevel = 0
            }
        },
    )

    LaunchedEffect(sessionData?.status, soundStartTime, triggered) {
        while (sessionData?.status == "running" && !triggered) {
            val startTime = soundStartTime
            val now = monotonicNowNanos()
            localTimer = if (startTime != null && now >= startTime) elapsedMillisBetween(startTime, now) else 0L
            delay(33L)
        }
        if (sessionData?.status != "running") {
            localTimer = 0L
        }
    }

    ClientDashboard(
        code = code,
        sessionData = sessionData,
        localTimer = localTimer,
        timerStarted = soundStartTime != null,
        micLevel = micLevel,
        triggered = triggered,
        finalTime = finalTime,
        cameraActive = cameraActive,
        aeLocked = aeLocked,
        previewEnabled = previewEnabled,
        onPreviewToggle = { previewEnabled = !previewEnabled },
        onAeLockToggle = {
            val nextLocked = !aeLocked
            aeLocked = nextLocked
            setAeAwbLock(activeCamera, nextLocked)
        },
        cameraContent = {
            CameraTrapwireView(
                motionState = motionState,
                cameraExecutor = cameraExecutor,
                aeLocked = aeLocked,
                previewEnabled = previewEnabled,
                onCameraReady = { camera ->
                    activeCamera = camera
                    cameraActive = true
                    if (aeLocked) setAeAwbLock(camera, true)
                },
                onCameraStopped = {
                    activeCamera = null
                    cameraActive = false
                },
                onElapsedDetected = { elapsed ->
                    if (!triggered) {
                        triggered = true
                        finalTime = elapsed
                        motionState.triggered = true
                        scope.launch {
                            database.getReference("sessions/$code/clients/$clientId/elapsedTime")
                                .setValue(elapsed)
                                .awaitSafely(context)
                        }
                    }
                },
            )
        },
    )
}

@Composable
private fun JoinRaceScreen(
    code: String,
    deviceName: String,
    onCodeChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onJoin: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 380.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Neutral900),
            border = BorderStroke(1.dp, Neutral800),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("◎", color = Green500, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text("Join Race", color = Neutral100, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = onDeviceNameChange,
                    label = { Text("Device Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )

                CodeField(label = "3-Digit Session Code", code = code, onCodeChange = onCodeChange, accent = Green500)

                Button(
                    onClick = onJoin,
                    enabled = code.length == 3 && deviceName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green600),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Connect →", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OfflineJoinRaceScreen(
    code: String,
    deviceName: String,
    searching: Boolean,
    statusMessage: String,
    onCodeChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onJoin: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 380.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Neutral900),
            border = BorderStroke(1.dp, Neutral800),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("⌂", color = Amber400, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text("Join BLE", color = Neutral100, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = onDeviceNameChange,
                    label = { Text("Device Name") },
                    singleLine = true,
                    enabled = !searching,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )

                CodeField(label = "3-Digit Code", code = code, onCodeChange = onCodeChange, accent = Amber400)
                Text(statusMessage, color = Neutral500, fontSize = 13.sp, textAlign = TextAlign.Center)

                Button(
                    onClick = if (searching) onCancel else onJoin,
                    enabled = searching || (code.length == 3 && deviceName.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (searching) Neutral800 else Amber400),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (searching) "Stop" else "Find Controller",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (searching) Neutral100 else Color.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientDashboard(
    code: String,
    sessionData: SessionData?,
    localTimer: Long,
    timerStarted: Boolean,
    micLevel: Int,
    triggered: Boolean,
    finalTime: Long?,
    cameraActive: Boolean,
    aeLocked: Boolean,
    previewEnabled: Boolean,
    onPreviewToggle: () -> Unit,
    onAeLockToggle: () -> Unit,
    cameraContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(1.dp, Neutral800, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            cameraContent()

            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineColor = if (sessionData?.status == "running" && !triggered) Red500.copy(alpha = 0.85f) else Neutral400.copy(alpha = 0.55f)
                drawLine(
                    color = lineColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = (size.width * 0.01f).coerceAtLeast(3f),
                    cap = StrokeCap.Round,
                )
            }

            if (sessionData?.status == "running" && !triggered) {
                Text(
                    if (timerStarted) formatSeconds(localTimer, decimals = 2) else "LISTENING...",
                    color = Neutral100,
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }

            if (triggered && finalTime != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 34.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Green500)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("✓", color = Color.Black, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Column {
                        Text("TRIGGERED", color = Color.Black.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(formatSeconds(finalTime), color = Color.Black, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Text(
            "Align the vertical trapwire with the finish line or path. Motion across the line will stop the timer.",
            color = Neutral500,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            fontSize = 14.sp,
        )

        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Neutral900)
                    .border(1.dp, Neutral800, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Label("CODE")
                    Text(code, color = Neutral100, fontSize = 22.sp, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
                }
                StatusChip(
                    text = when {
                        sessionData?.status != "running" -> "WAITING"
                        timerStarted -> "TIMING"
                        else -> "LISTENING"
                    },
                    color = when {
                        sessionData?.status != "running" -> Green400
                        timerStarted -> Red500
                        else -> Amber400
                    },
                )
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Label("EXPOSURE & FOCUS CONTROL")
                        Text(
                            if (aeLocked) "Camera locked (no brightness shift)" else "Camera auto-adjusting",
                            color = Neutral100,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        TinyText("Native Android CameraX preview + analyzer")
                    }
                    Button(
                        onClick = onAeLockToggle,
                        enabled = cameraActive,
                        colors = ButtonDefaults.buttonColors(containerColor = if (aeLocked) Amber400.copy(alpha = 0.18f) else Green500.copy(alpha = 0.18f)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(if (aeLocked) "Unlock" else "Lock", color = if (aeLocked) Amber400 else Green400, fontSize = 12.sp)
                    }
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Label("CAMERA PREVIEW")
                        Text(
                            if (previewEnabled) "Preview visible — analyzer running" else "Preview hidden — analyzer still running",
                            color = Neutral100,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        TinyText("Turn preview off to reduce screen load while keeping finish detection active.")
                    }
                    Button(
                        onClick = onPreviewToggle,
                        enabled = cameraActive,
                        colors = ButtonDefaults.buttonColors(containerColor = if (previewEnabled) Neutral800 else Blue500.copy(alpha = 0.18f)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(if (previewEnabled) "Hide" else "Show", color = if (previewEnabled) Neutral100 else Blue500, fontSize = 12.sp)
                    }
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Label("MOTION SENSITIVITY")
                        Text(
                            "Set by controller: ${sessionData?.sensitivity ?: DEFAULT_SENSITIVITY}%",
                            color = Neutral100,
                            fontSize = 14.sp,
                        )
                    }
                    Text(
                        "${thresholdForSensitivity(sessionData?.sensitivity ?: DEFAULT_SENSITIVITY)} diff",
                        color = Neutral400,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Neutral950)
                            .border(1.dp, Neutral800, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Label("MICROPHONE START")
                        Text(
                            if (sessionData?.status == "running" && !timerStarted) "Listening for controller beep" else if (timerStarted) "Timer started from sound" else "Waiting for controller start",
                            color = Neutral100,
                            fontSize = 14.sp,
                        )
                        TinyText("Sensitivity ${sessionData?.micSensitivity ?: DEFAULT_MIC_SENSITIVITY}% • level gate ${microphoneThresholdForSensitivity(sessionData?.micSensitivity ?: DEFAULT_MIC_SENSITIVITY)}% • WAV pattern match")
                    }
                    Text(
                        "$micLevel%",
                        color = if (sessionData?.status == "running" && !timerStarted) Amber400 else Neutral400,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Label("SCREEN WAKE LOCK")
                    Text("Active — Android keeps the screen turned on.", color = Neutral100, fontSize = 14.sp)
                }
                StatusChip("STAY ON", Green400)
            }
        }
    }
}

@Composable
private fun MicrophoneStartDetector(
    armed: Boolean,
    micSensitivity: Int,
    nowNanos: () -> Long,
    onLevel: (Int) -> Unit,
    onStart: (Long) -> Unit,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val beepPattern = remember(context) { loadStarterBeepPattern(context) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) toast(context, "Microphone permission is required to start the timer from the beep.")
    }

    LaunchedEffect(armed) {
        if (armed && !hasPermission) launcher.launch(Manifest.permission.RECORD_AUDIO)
        if (!armed) onLevel(0)
    }

    DisposableEffect(armed, hasPermission, micSensitivity) {
        if (!armed || !hasPermission) return@DisposableEffect onDispose { }

        val thread = Thread {
            runCatching {
                listenForStartBeep(
                    context = context,
                    micSensitivity = micSensitivity,
                    beepPattern = beepPattern,
                    onLevel = { level -> mainHandler.post { onLevel(level) } },
                    onStart = { startTime -> mainHandler.post { onStart(startTime) } },
                    nowNanos = nowNanos,
                )
            }.onFailure {
                mainHandler.post { toast(context, "Microphone listener failed: ${it.friendlyMessage()}") }
            }
        }.apply { start() }

        onDispose {
            thread.interrupt()
        }
    }
}

@Suppress("MissingPermission")
private fun createStartBeepAudioRecord(context: Context, sampleRate: Int, bufferSize: Int): AudioRecord? {
    val preferredSources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        intArrayOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
    } else {
        intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
    }
    val preferredMic = preferredPrimaryBuiltInMic(context)
    val preferredDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferredMic != null) {
        arrayOf<AudioDeviceInfo?>(preferredMic, null)
    } else {
        arrayOf<AudioDeviceInfo?>(null)
    }

    for (source in preferredSources) {
        for (preferredDevice in preferredDevices) {
            val recorder = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .build(),
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .build()
                } else {
                    AudioRecord(
                        source,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                    )
                }
            }.getOrNull() ?: continue

            val preferredRouteAccepted = preferredDevice == null ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                recorder.setPreferredDevice(preferredDevice)
            if (recorder.state == AudioRecord.STATE_INITIALIZED && preferredRouteAccepted) return recorder
            recorder.release()
        }
    }

    return null
}

private fun preferredPrimaryBuiltInMic(context: Context): AudioDeviceInfo? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null

    val audioManager = context.getSystemService(AudioManager::class.java) ?: return null
    val builtInInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        .filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    if (builtInInputs.isEmpty()) return null

    val preferredAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        preferredBuiltInMicAddress(audioManager)
    } else {
        null
    }

    val preferredDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && preferredAddress != null) {
        builtInInputs.firstOrNull { audioDeviceAddress(it) == preferredAddress }
    } else {
        null
    }

    return preferredDevice ?: builtInInputs.maxByOrNull { audioDevicePreferenceScore(it) }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun preferredBuiltInMicAddress(audioManager: AudioManager): String? = runCatching { audioManager.microphones }
    .getOrNull()
    ?.filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    ?.maxByOrNull { microphonePreferenceScore(it) }
    ?.address
    ?.takeIf { it.isNotBlank() }

@RequiresApi(Build.VERSION_CODES.P)
private fun microphonePreferenceScore(microphoneInfo: MicrophoneInfo): Int {
    val hint = listOf(microphoneInfo.description, microphoneInfo.address)
        .joinToString(" ")
        .lowercase(Locale.US)
    return audioInputHintScore(hint) + if (microphoneInfo.location == MicrophoneInfo.LOCATION_MAINBODY) 10 else 0
}

private fun audioDevicePreferenceScore(audioDeviceInfo: AudioDeviceInfo): Int {
    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audioDeviceAddress(audioDeviceInfo) else ""
    val hint = listOf(audioDeviceInfo.productName?.toString().orEmpty(), address)
        .joinToString(" ")
        .lowercase(Locale.US)
    return audioInputHintScore(hint)
}

@RequiresApi(Build.VERSION_CODES.P)
private fun audioDeviceAddress(audioDeviceInfo: AudioDeviceInfo): String = audioDeviceInfo.address

private fun audioInputHintScore(hint: String): Int = when {
    "bottom" in hint -> 100
    "primary" in hint -> 90
    "main" in hint -> 80
    "built-in" in hint || "builtin" in hint -> 40
    "mic" in hint -> 20
    else -> 0
}

@Suppress("MissingPermission")
private fun listenForStartBeep(
    context: Context,
    micSensitivity: Int,
    beepPattern: StartBeepPattern?,
    onLevel: (Int) -> Unit,
    onStart: (Long) -> Unit,
    nowNanos: () -> Long,
) {
    val sampleRate = START_BEEP_SAMPLE_RATE
    val minBufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    )
    if (minBufferSize <= 0) return

    val pattern = beepPattern?.takeIf { it.samples.isNotEmpty() && it.sampleRate == sampleRate }
    val bufferSize = maxOf(minBufferSize, sampleRate / 10)
    val audioRecord = createStartBeepAudioRecord(context, sampleRate, bufferSize) ?: return

    val samples = ShortArray(bufferSize / 2)
    val threshold = microphoneThresholdForSensitivity(micSensitivity)
    val rollingAudio = pattern?.let { RollingAudioWindow(it.samples.size) }
    val patternGraceNanos = ((pattern?.durationMs ?: 0L) + START_SHOT_PATTERN_MATCH_GRACE_MS) * NANOS_PER_MILLISECOND
    var candidateStartNanos: Long? = null

    try {
        audioRecord.startRecording()
        while (!Thread.currentThread().isInterrupted) {
            val read = audioRecord.read(samples, 0, samples.size)
            if (read <= 0) continue

            val readEndNanos = nowNanos()
            rollingAudio?.append(samples, read)

            var peak = 0
            for (index in 0 until read) {
                val value = kotlin.math.abs(samples[index].toInt())
                if (value > peak) peak = value
            }
            val level = ((peak * 100L) / Short.MAX_VALUE).toInt().coerceIn(0, 100)
            onLevel(level)

            if (level >= threshold) {
                if (pattern == null || rollingAudio == null) {
                    onStart(readEndNanos)
                    return
                }

                if (candidateStartNanos == null) {
                    val readDurationNanos = (read.toLong() * 1_000_000_000L) / sampleRate
                    candidateStartNanos = readEndNanos - readDurationNanos
                }
            }

            val candidate = candidateStartNanos
            if (pattern != null && rollingAudio != null && candidate != null) {
                if (readEndNanos - candidate > patternGraceNanos) {
                    candidateStartNanos = null
                } else if (rollingAudio.isReady && matchesStarterBeep(rollingAudio.samples, pattern)) {
                    onStart(candidate)
                    return
                }
            }
        }
    } finally {
        runCatching { audioRecord.stop() }
        audioRecord.release()
    }
}

private data class PcmWav(
    val sampleRate: Int,
    val samples: ShortArray,
)

private class RollingAudioWindow(private val capacity: Int) {
    val samples = ShortArray(capacity)
    var filled: Int = 0
        private set

    val isReady: Boolean get() = filled == capacity

    fun append(source: ShortArray, length: Int) {
        val copyLength = length.coerceIn(0, source.size)
        if (copyLength <= 0 || capacity <= 0) return

        if (copyLength >= capacity) {
            System.arraycopy(source, copyLength - capacity, samples, 0, capacity)
            filled = capacity
            return
        }

        val keep = minOf(filled, capacity - copyLength)
        if (keep > 0) {
            System.arraycopy(samples, filled - keep, samples, 0, keep)
        }
        System.arraycopy(source, 0, samples, keep, copyLength)
        filled = keep + copyLength
    }
}

private fun loadStarterBeepPattern(context: Context): StartBeepPattern? {
    return runCatching {
        val wavBytes = context.resources.openRawResource(R.raw.shot).use { it.readBytes() }
        val decoded = decodePcmWav(wavBytes) ?: return@runCatching null
        val resampled = resamplePcm(decoded.samples, decoded.sampleRate, START_BEEP_SAMPLE_RATE)
        val activeSamples = focusStartShotPattern(trimSilence(resampled))
        if (activeSamples.size < START_BEEP_SAMPLE_RATE / 100) return@runCatching null

        StartBeepPattern(
            sampleRate = START_BEEP_SAMPLE_RATE,
            samples = activeSamples,
            durationMs = (activeSamples.size * 1_000L) / START_BEEP_SAMPLE_RATE,
            envelope = computeEnvelope(activeSamples, START_BEEP_SAMPLE_RATE, START_BEEP_FRAME_MS),
            zeroCrossHz = estimateZeroCrossFrequency(activeSamples, START_BEEP_SAMPLE_RATE),
        )
    }.getOrNull()
}

private fun decodePcmWav(bytes: ByteArray): PcmWav? {
    if (bytes.size < 44) return null
    if (chunkName(bytes, 0) != "RIFF" || chunkName(bytes, 8) != "WAVE") return null

    var offset = 12
    var audioFormat = 0
    var channels = 0
    var sampleRate = 0
    var bitsPerSample = 0
    var dataOffset = -1
    var dataSize = 0

    while (offset + 8 <= bytes.size) {
        val name = chunkName(bytes, offset)
        val size = readLeInt(bytes, offset + 4)
        val chunkStart = offset + 8
        if (size < 0 || chunkStart + size > bytes.size) break

        when (name) {
            "fmt " -> {
                if (size >= 16) {
                    audioFormat = readLeShort(bytes, chunkStart).toInt()
                    channels = readLeShort(bytes, chunkStart + 2).toInt()
                    sampleRate = readLeInt(bytes, chunkStart + 4)
                    bitsPerSample = readLeShort(bytes, chunkStart + 14).toInt()
                }
            }

            "data" -> {
                dataOffset = chunkStart
                dataSize = size
            }
        }

        offset = chunkStart + size + (size and 1)
    }

    if (audioFormat != 1 || channels <= 0 || sampleRate <= 0 || dataOffset < 0) return null
    if (bitsPerSample !in intArrayOf(8, 16, 24, 32)) return null

    val bytesPerSample = bitsPerSample / 8
    val frameSize = channels * bytesPerSample
    if (frameSize <= 0) return null

    val frameCount = dataSize / frameSize
    val samples = ShortArray(frameCount)
    var position = dataOffset
    for (frame in 0 until frameCount) {
        var total = 0
        for (channel in 0 until channels) {
            val value = when (bitsPerSample) {
                8 -> ((bytes[position].toInt() and 0xFF) - 128) shl 8
                16 -> readLeShort(bytes, position).toInt()
                24 -> readSigned24Le(bytes, position) shr 8
                32 -> readLeInt(bytes, position) shr 16
                else -> 0
            }
            total += value
            position += bytesPerSample
        }
        samples[frame] = (total / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return PcmWav(sampleRate, samples)
}

private fun chunkName(bytes: ByteArray, offset: Int): String {
    return if (offset + 4 <= bytes.size) String(bytes, offset, 4, StandardCharsets.US_ASCII) else ""
}

private fun readLeShort(bytes: ByteArray, offset: Int): Short {
    val lo = bytes[offset].toInt() and 0xFF
    val hi = bytes[offset + 1].toInt() and 0xFF
    return (lo or (hi shl 8)).toShort()
}

private fun readLeInt(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

private fun readSigned24Le(bytes: ByteArray, offset: Int): Int {
    var value = (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16)
    if ((value and 0x800000) != 0) value = value or -0x1000000
    return value
}

private fun resamplePcm(samples: ShortArray, fromRate: Int, toRate: Int): ShortArray {
    if (samples.isEmpty() || fromRate <= 0 || toRate <= 0) return samples
    if (fromRate == toRate) return samples

    val outputSize = ((samples.size.toLong() * toRate) / fromRate).toInt().coerceAtLeast(1)
    val output = ShortArray(outputSize)
    val ratio = fromRate.toDouble() / toRate.toDouble()
    for (index in output.indices) {
        val sourcePosition = index * ratio
        val left = sourcePosition.toInt().coerceIn(0, samples.lastIndex)
        val right = (left + 1).coerceAtMost(samples.lastIndex)
        val fraction = sourcePosition - left
        output[index] = (samples[left] + ((samples[right] - samples[left]) * fraction))
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
    return output
}

private fun trimSilence(samples: ShortArray): ShortArray {
    val peak = samples.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: return samples
    if (peak <= 0) return samples

    val cutoff = maxOf(120, (peak * 0.06f).roundToInt())
    var first = samples.indexOfFirst { kotlin.math.abs(it.toInt()) >= cutoff }
    var last = samples.indexOfLast { kotlin.math.abs(it.toInt()) >= cutoff }
    if (first < 0 || last < first) return samples

    val padding = START_BEEP_SAMPLE_RATE / 100
    first = (first - padding).coerceAtLeast(0)
    last = (last + padding).coerceAtMost(samples.lastIndex)
    return samples.copyOfRange(first, last + 1)
}

private fun focusStartShotPattern(samples: ShortArray): ShortArray {
    if (samples.isEmpty()) return samples

    val maxSamples = ((START_BEEP_SAMPLE_RATE * START_SHOT_PATTERN_MAX_MS) / 1_000L).toInt().coerceAtLeast(1)
    if (samples.size <= maxSamples) return samples

    var peakIndex = 0
    var peak = 0
    for (index in samples.indices) {
        val value = kotlin.math.abs(samples[index].toInt())
        if (value > peak) {
            peak = value
            peakIndex = index
        }
    }

    val leadInSamples = START_BEEP_SAMPLE_RATE / 200
    val start = (peakIndex - leadInSamples).coerceAtLeast(0)
    val end = (start + maxSamples).coerceAtMost(samples.size)
    return samples.copyOfRange(start, end)
}

private fun matchesStarterBeep(samples: ShortArray, pattern: StartBeepPattern): Boolean {
    val envelopeScore = cosineSimilarity(
        computeEnvelope(samples, pattern.sampleRate, START_BEEP_FRAME_MS),
        pattern.envelope,
    )
    val waveScore = rectifiedWaveSimilarity(samples, pattern.samples)
    val measuredHz = estimateZeroCrossFrequency(samples, pattern.sampleRate)
    val shortTransient = pattern.durationMs <= START_SHOT_PATTERN_MAX_MS
    val frequencyOk = shortTransient || pattern.zeroCrossHz < 80f || measuredHz <= 0f ||
        (kotlin.math.abs(measuredHz - pattern.zeroCrossHz) / pattern.zeroCrossHz) <= START_BEEP_FREQUENCY_TOLERANCE
    val confidence = if (shortTransient) {
        (envelopeScore * 0.70f) + (waveScore * 0.30f)
    } else {
        (envelopeScore * 0.55f) + (waveScore * 0.45f)
    }
    return frequencyOk && confidence >= START_BEEP_PATTERN_SCORE
}

private fun computeEnvelope(samples: ShortArray, sampleRate: Int, frameMs: Int): FloatArray {
    val frameSize = maxOf(1, (sampleRate * frameMs) / 1_000)
    val frameCount = maxOf(1, (samples.size + frameSize - 1) / frameSize)
    return FloatArray(frameCount) { frame ->
        val start = frame * frameSize
        val end = minOf(samples.size, start + frameSize)
        if (start >= end) return@FloatArray 0f

        var sumSquares = 0.0
        for (index in start until end) {
            val sample = samples[index].toDouble()
            sumSquares += sample * sample
        }
        (sqrt(sumSquares / (end - start).toDouble()) / Short.MAX_VALUE.toDouble()).toFloat()
    }
}

private fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
    val size = minOf(left.size, right.size)
    if (size <= 0) return 0f

    var dot = 0.0
    var leftEnergy = 0.0
    var rightEnergy = 0.0
    for (index in 0 until size) {
        val a = left[index].toDouble()
        val b = right[index].toDouble()
        dot += a * b
        leftEnergy += a * a
        rightEnergy += b * b
    }
    if (leftEnergy <= 0.0 || rightEnergy <= 0.0) return 0f
    return (dot / sqrt(leftEnergy * rightEnergy)).toFloat().coerceIn(0f, 1f)
}

private fun rectifiedWaveSimilarity(left: ShortArray, right: ShortArray): Float {
    val size = minOf(left.size, right.size)
    if (size <= 0) return 0f

    val step = 4
    var dot = 0.0
    var leftEnergy = 0.0
    var rightEnergy = 0.0
    var index = 0
    while (index < size) {
        val a = kotlin.math.abs(left[index].toInt()).toDouble()
        val b = kotlin.math.abs(right[index].toInt()).toDouble()
        dot += a * b
        leftEnergy += a * a
        rightEnergy += b * b
        index += step
    }
    if (leftEnergy <= 0.0 || rightEnergy <= 0.0) return 0f
    return (dot / sqrt(leftEnergy * rightEnergy)).toFloat().coerceIn(0f, 1f)
}

private fun estimateZeroCrossFrequency(samples: ShortArray, sampleRate: Int): Float {
    if (samples.size < 2 || sampleRate <= 0) return 0f
    val peak = samples.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: return 0f
    val floor = maxOf(80, (peak * 0.05f).roundToInt())

    var previousSign = 0
    var crossings = 0
    for (sample in samples) {
        val value = sample.toInt()
        if (kotlin.math.abs(value) < floor) continue
        val sign = if (value >= 0) 1 else -1
        if (previousSign != 0 && sign != previousSign) crossings++
        previousSign = sign
    }

    val durationSeconds = samples.size.toFloat() / sampleRate.toFloat()
    return if (durationSeconds > 0f) crossings / (2f * durationSeconds) else 0f
}

private fun microphoneThresholdForSensitivity(sensitivity: Int): Int {
    return (45 - (sensitivity.coerceIn(10, 100) * 0.4f).roundToInt()).coerceIn(4, 45)
}

@Suppress("DEPRECATION")
@Composable
private fun CameraTrapwireView(
    motionState: MotionDetectionState,
    cameraExecutor: ExecutorService,
    aeLocked: Boolean,
    previewEnabled: Boolean,
    onCameraReady: (Camera) -> Unit,
    onCameraStopped: () -> Unit,
    onElapsedDetected: (Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) toast(context, "Camera permission is required for client trapwire mode.")
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Camera permission needed", color = Neutral100, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, shape = RoundedCornerShape(14.dp)) {
                Text("Grant Camera Access")
            }
        }
        return
    }

    LaunchedEffect(previewEnabled) {
        if (!previewEnabled) previewView = null
    }

    if (previewEnabled) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Neutral950),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Preview off", color = Neutral100, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            TinyText("Camera analyzer remains active")
        }
    }

    DisposableEffect(previewView, hasPermission, aeLocked, previewEnabled) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var bound = false

        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            provider = cameraProvider

            val preview = previewView?.let { view ->
                Preview.Builder().build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }
            }

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(320, 240))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(
                        cameraExecutor,
                        TrapwireAnalyzer(motionState) { elapsed ->
                            mainExecutor.execute { onElapsedDetected(elapsed) }
                        },
                    )
                }

            runCatching {
                cameraProvider.unbindAll()
                bindCameraWithFallback(cameraProvider, lifecycleOwner, preview, analysis)
            }.onSuccess { camera ->
                bound = true
                onCameraReady(camera)
                if (aeLocked) setAeAwbLock(camera, true)
            }.onFailure {
                toast(context, "Failed to start camera: ${it.friendlyMessage()}")
                onCameraStopped()
            }
        }, mainExecutor)

        onDispose {
            if (bound) provider?.unbindAll()
            onCameraStopped()
        }
    }
}

private fun bindCameraWithFallback(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    preview: Preview?,
    analysis: ImageAnalysis,
): Camera {
    return runCatching {
        bindCameraUseCases(cameraProvider, lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
    }.getOrElse {
        bindCameraUseCases(cameraProvider, lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
    }
}

private fun bindCameraUseCases(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    selector: CameraSelector,
    preview: Preview?,
    analysis: ImageAnalysis,
): Camera {
    return if (preview != null) {
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
    } else {
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, analysis)
    }
}

private class TrapwireAnalyzer(
    private val state: MotionDetectionState,
    private val onElapsedDetected: (Long) -> Unit,
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            val session = state.session
            if (session?.status != "running" || state.triggered) {
                state.reset()
                return
            }

            val startTime = state.localStartTime ?: run {
                state.reset()
                return
            }
            val frameTimestampNanos = image.frameTimestampNanosOrFallback()
            if (frameTimestampNanos < startTime) {
                state.reset()
                return
            }

            val elapsed = elapsedMillisBetween(startTime, frameTimestampNanos)
            val currentStrip = image.centerLumaStrip()
            val background = state.backgroundStrip

            if (background != null && background.size == currentStrip.size && elapsed > 200L) {
                var diff = 0.0
                for (i in currentStrip.indices) {
                    diff += kotlin.math.abs((currentStrip[i].toInt() and 0xFF) - background[i])
                }
                val avgDiff = diff / currentStrip.size.toDouble()
                if (avgDiff > thresholdForSensitivity(session.sensitivity)) {
                    if (state.consecutiveAboveThresholdFrames == 0) {
                        state.firstAboveThresholdElapsed = elapsed
                    }
                    state.consecutiveAboveThresholdFrames += 1
                    if (state.consecutiveAboveThresholdFrames >= TRAPWIRE_CONFIRMATION_FRAME_COUNT) {
                        state.triggered = true
                        onElapsedDetected(state.firstAboveThresholdElapsed ?: elapsed)
                    }
                } else {
                    state.resetConfirmation()
                }
                background.updateFrom(currentStrip)
            } else {
                state.resetConfirmation()
                state.backgroundStrip = currentStrip.toFloatBackground()
            }
        } finally {
            image.close()
        }
    }
}

private fun ImageProxy.frameTimestampNanosOrFallback(): Long {
    val fallback = monotonicNowNanos()
    val sensorTimestamp = imageInfo.timestamp
    if (sensorTimestamp <= 0L) return fallback

    val skew = kotlin.math.abs(fallback - sensorTimestamp)
    return if (skew <= CAMERA_SENSOR_TIMESTAMP_MAX_SKEW_NANOS) sensorTimestamp else fallback
}
private fun ByteArray.toFloatBackground(): FloatArray {
    return FloatArray(size) { index -> (this[index].toInt() and 0xFF).toFloat() }
}

private fun FloatArray.updateFrom(current: ByteArray) {
    for (i in indices) {
        val currentValue = (current[i].toInt() and 0xFF).toFloat()
        this[i] = TRAPWIRE_BACKGROUND_KEEP_WEIGHT * this[i] + TRAPWIRE_BACKGROUND_NEW_WEIGHT * currentValue
    }
}

private fun verticallyBlurredStrip(raw: ByteArray, stripWidth: Int, stripHeight: Int): ByteArray {
    if (stripWidth <= 0 || stripHeight <= 0) return raw
    val result = ByteArray(raw.size)
    for (row in 0 until stripHeight) {
        val prevRow = maxOf(0, row - 1)
        val nextRow = minOf(stripHeight - 1, row + 1)
        val rowBase = row * stripWidth
        val prevBase = prevRow * stripWidth
        val nextBase = nextRow * stripWidth
        for (col in 0 until stripWidth) {
            val top = raw[prevBase + col].toInt() and 0xFF
            val mid = raw[rowBase + col].toInt() and 0xFF
            val bottom = raw[nextBase + col].toInt() and 0xFF
            result[rowBase + col] = ((top + mid + bottom) / 3).toByte()
        }
    }
    return result
}

private fun ImageProxy.centerLumaStrip(): ByteArray {
    val plane = planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = maxOf(1, plane.pixelStride)
    val safeWidth = width.coerceAtLeast(1)
    val safeHeight = height.coerceAtLeast(1)
    val stripWidth = maxOf(1, (safeWidth * TRAPWIRE_STRIP_WIDTH_FRACTION).roundToInt())
    val stripX = (safeWidth / 2 - stripWidth / 2).coerceIn(0, safeWidth - 1)
    val stripEndExclusive = minOf(safeWidth, stripX + stripWidth)
    val actualStripWidth = maxOf(1, stripEndExclusive - stripX)
    val result = ByteArray(actualStripWidth * safeHeight)
    var out = 0
    val limit = buffer.limit()

    for (row in 0 until safeHeight) {
        val rowStart = row * rowStride
        if (rowStart < 0 || rowStart >= limit) {
            repeat(actualStripWidth) { result[out++] = 0 }
            continue
        }
        for (col in stripX until stripEndExclusive) {
            val index = rowStart + col * pixelStride
            result[out++] = if (index >= 0 && index < limit) buffer.get(index) else 0
        }
    }
    return verticallyBlurredStrip(result, actualStripWidth, safeHeight)
}

@Composable
private fun CodeField(
    label: String,
    code: String,
    onCodeChange: (String) -> Unit,
    accent: Color,
) {
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(
            color = Neutral100,
            fontSize = 34.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 10.sp,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(16.dp),
        prefix = { Spacer(Modifier.width(10.dp)) },
    )
    TinyText("Native app input • ${if (accent == Blue500) "controller" else "client"} mode")
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Neutral900),
        border = BorderStroke(1.dp, Neutral800),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content,
        )
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, color = Neutral500, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

@Composable
private fun TinyText(text: String) {
    Text(text, color = Neutral500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun BlePermissionsGate(
    description: String,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val permissions = remember { requiredBlePermissions() }
    var granted by remember {
        mutableStateOf(permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = permissions.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) toast(context, "BLE permissions are required for offline mode.")
    }

    LaunchedEffect(Unit) {
        if (!granted && permissions.isNotEmpty()) launcher.launch(permissions)
    }

    if (granted || permissions.isEmpty()) {
        content()
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            SectionCard {
                Text("BLE permission needed", color = Neutral100, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(description, color = Neutral500, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launcher.launch(permissions) }, shape = RoundedCornerShape(14.dp)) {
                    Text("Grant BLE Access")
                }
            }
        }
    }
}


private fun offlineJoinJson(clientId: String, deviceName: String): JSONObject {
    return JSONObject()
        .put("type", "join")
        .put("clientId", clientId)
        .put("deviceName", deviceName.ifBlank { "BLE Client" })
}

private fun offlineFinishJson(clientId: String, elapsedTime: Long): JSONObject {
    return JSONObject()
        .put("type", "finish")
        .put("clientId", clientId)
        .put("elapsedTime", elapsedTime)
}

private fun offlineSnapshotJson(session: SessionData, serverTimeMs: Long): JSONObject {
    return JSONObject()
        .put("type", "snapshot")
        .put("serverTime", serverTimeMs)
        .put("session", session.toJson())
}

private fun SessionData.toJson(): JSONObject {
    val json = JSONObject()
    json.put("status", status)
    json.put("createdAt", createdAt)
    json.put("sensitivity", sensitivity)
    json.put("micSensitivity", micSensitivity)
    if (startTime == null) json.put("startTime", JSONObject.NULL) else json.put("startTime", startTime)
    return json
}

private fun ClientData.toJson(): JSONObject {
    val json = JSONObject()
    json.put("id", id)
    json.put("joinedAt", joinedAt)
    json.put("elapsedTime", elapsedTime)
    json.put("deviceName", deviceName)
    return json
}

private fun JSONObject.toOfflineSessionSnapshot(): OfflineSessionSnapshot? {
    if (optString("type") != "snapshot") return null
    val clientsJson = optJSONArray("clients") ?: JSONArray()
    val parsedClients = mutableListOf<ClientData>()
    for (index in 0 until clientsJson.length()) {
        clientsJson.optJSONObject(index)?.toClientDataFromJson()?.let { parsedClients.add(it) }
    }
    return OfflineSessionSnapshot(
        session = optJSONObject("session")?.toSessionDataFromJson(),
        clients = parsedClients,
        serverTime = optNullableLong("serverTime") ?: System.currentTimeMillis(),
    )
}

private fun JSONObject.toSessionDataFromJson(): SessionData {
    return SessionData(
        status = optString("status", "waiting"),
        createdAt = optNullableLong("createdAt") ?: 0L,
        startTime = optNullableLong("startTime"),
        sensitivity = optInt("sensitivity", DEFAULT_SENSITIVITY),
        micSensitivity = optInt("micSensitivity", DEFAULT_MIC_SENSITIVITY),
    )
}

private fun JSONObject.toClientDataFromJson(): ClientData? {
    val id = optString("id")
    if (id.isBlank()) return null
    return ClientData(
        id = id,
        joinedAt = optNullableLong("joinedAt") ?: 0L,
        elapsedTime = optNullableLong("elapsedTime"),
        deviceName = optString("deviceName").ifBlank { null },
    )
}

private fun JSONObject.optNullableLong(name: String): Long? {
    return if (!has(name) || isNull(name)) null else optLong(name)
}

private fun MutableList<ClientData>.upsert(client: ClientData) {
    val index = indexOfFirst { it.id == client.id }
    if (index >= 0) this[index] = client else add(client)
}

private fun DataSnapshot.toSessionData(): SessionData? {
    if (!exists()) return null
    return SessionData(
        status = child("status").getValue(String::class.java) ?: "waiting",
        createdAt = child("createdAt").longValue() ?: 0L,
        startTime = child("startTime").longValue(),
        sensitivity = child("sensitivity").intValue() ?: DEFAULT_SENSITIVITY,
        micSensitivity = child("micSensitivity").intValue() ?: DEFAULT_MIC_SENSITIVITY,
    )
}

private fun DataSnapshot.toClientData(): ClientData? {
    val key = key ?: return null
    return ClientData(
        id = key,
        joinedAt = child("joinedAt").longValue() ?: 0L,
        elapsedTime = child("elapsedTime").longValue(),
        deviceName = child("deviceName").getValue(String::class.java),
    )
}

private fun DataSnapshot.longValue(): Long? {
    return getValue(Long::class.java) ?: getValue(Double::class.java)?.toLong() ?: getValue(Int::class.java)?.toLong()
}

private fun DataSnapshot.intValue(): Int? {
    return getValue(Int::class.java) ?: getValue(Long::class.java)?.toInt() ?: getValue(Double::class.java)?.toInt()
}

private suspend fun com.google.android.gms.tasks.Task<Void>.awaitSafely(context: Context) {
    runCatching { await() }.onFailure { toast(context, "Firebase update failed: ${it.friendlyMessage()}") }
}

private fun String.onlyDigits3(): String = filter { it.isDigit() }.take(3)

private fun randomClientId(): String = List(6) { ('a'..'z').random() }.joinToString("")

private fun thresholdForSensitivity(sensitivity: Int): Int {
    return maxOf(1, (50.0 - (sensitivity.coerceIn(10, 100) * 48.0) / 100.0).roundToInt())
}

private fun formatSeconds(ms: Long, decimals: Int = 3): String {
    return String.format(Locale.US, "%.${decimals}fs", ms / 1000.0)
}

private fun playStarterBeep(context: Context) {
    runCatching {
        val player = MediaPlayer.create(context, R.raw.shot)
        if (player == null) {
            playBeep(durationMs = 300)
            return
        }
        player.setOnCompletionListener { completedPlayer -> completedPlayer.release() }
        player.setOnErrorListener { failedPlayer, _, _ ->
            failedPlayer.release()
            playBeep(durationMs = 300)
            true
        }
        player.start()
    }.onFailure {
        playBeep(durationMs = 300)
    }
}

private fun playBeep(durationMs: Int = 400) {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, durationMs)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, durationMs + 120L)
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun Throwable.friendlyMessage(): String = localizedMessage ?: message ?: javaClass.simpleName

@SuppressLint("UnsafeOptInUsageError")
private fun setAeAwbLock(camera: Camera?, locked: Boolean) {
    val activeCamera = camera ?: return
    val options = CaptureRequestOptions.Builder()
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, locked)
        .apply {
            if (locked) {
                setCaptureRequestOption(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            }
        }
        .build()

    Camera2CameraControl.from(activeCamera.cameraControl).setCaptureRequestOptions(options)
}
