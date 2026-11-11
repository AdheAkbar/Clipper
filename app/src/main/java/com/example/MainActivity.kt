package com.example

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.example.data.AppSettings
import com.example.data.SettingsManager
import com.example.model.CaptionTemplate
import com.example.model.ContentPreset
import com.example.model.VideoClip
import com.example.model.parseMmSsToMs
import com.example.network.LanScanner
import com.example.network.VideoAnalysisService
import com.example.ui.SettingsDialog
import com.example.ui.ShortEditorDialog
import com.example.ui.ShortVideoOverlayRenderer
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AutoClipperApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoClipperApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    val analysisService = remember { VideoAnalysisService() }

    var appSettings by remember { mutableStateOf(settingsManager.getSettings()) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val deviceIps = remember { LanScanner.getDeviceLanIps() }
    val subnetPrefix = remember(deviceIps) {
        val first = deviceIps.firstOrNull()
        if (first != null && first.count { it == '.' } == 3) {
            first.substringBeforeLast(".") + "."
        } else {
            "192.168.1."
        }
    }
    var quickUrlInput by remember(appSettings.baseUrl) { mutableStateOf(appSettings.baseUrl) }
    var isScanningLanMain by remember { mutableStateOf(false) }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var videoDurationMs by remember { mutableStateOf(0L) }

    var selectedPreset by remember { mutableStateOf(ContentPreset.GENERAL) }
    var clipCount by remember { mutableStateOf(4f) }
    var exportAspect by remember { mutableStateOf(appSettings.defaultAspect) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Pilih video lalu klik Analyze.") }
    var clipResults by remember { mutableStateOf<List<VideoClip>>(emptyList()) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }

    var editingClipIndex by remember { mutableStateOf<Int?>(null) }
    var exportingIndex by remember { mutableStateOf<Int?>(null) }
    var isExportingAll by remember { mutableStateOf(false) }

    // Automatically scan Wi-Fi LAN for 9Router (port 20128) on startup if on a physical phone and URL is still 10.0.2.2
    LaunchedEffect(Unit) {
        if (!LanScanner.isEmulator() && appSettings.baseUrl.contains("10.0.2.2")) {
            isScanningLanMain = true
            statusText = "Mencari otomatis server 9Router (port 20128) di Wi-Fi Anda..."
            val discovered = LanScanner.discover9RouterOnLan()
            isScanningLanMain = false
            if (discovered != null) {
                val updated = appSettings.copy(baseUrl = discovered)
                appSettings = updated
                quickUrlInput = discovered
                settingsManager.saveSettings(
                    apiKey = updated.apiKey,
                    baseUrl = updated.baseUrl,
                    modelName = updated.modelName,
                    provider = updated.provider,
                    defaultAspect = updated.defaultAspect
                )
                statusText = "✅ Terhubung otomatis ke 9Router PC: $discovered"
            } else {
                statusText = "⚠️ Anda di HP Fisik. Ganti '10.0.2.2' dengan IP LAN PC Anda (contoh: http://${subnetPrefix}X:20128/v1) pada kotak di bawah."
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            clipResults = emptyList()
            statusText = "Loading video metadata..."

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val dur = durStr?.toLongOrNull() ?: 0L
                    val frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()

                    withContext(Dispatchers.Main) {
                        videoThumbnail = frame
                        videoDurationMs = dur
                        val sec = dur / 1000
                        val min = sec / 60
                        val remSec = sec % 60
                        statusText = "Video selected (${String.format("%02d:%02d", min, remSec)}). Choose preset and analyze."
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText = "Error reading video: ${e.message}"
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Auto Clipper Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "${appSettings.modelName.substringAfterLast("/")} • ${appSettings.provider}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingVals ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            // Video Selector Hero Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable {
                            videoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (videoThumbnail != null) {
                            Image(
                                bitmap = videoThumbnail!!.asImageBitmap(),
                                contentDescription = "Video Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f))
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                val sec = videoDurationMs / 1000
                                Text(
                                    "Duration: ${String.format("%02d:%02d", sec / 60, sec % 60)} • Tap to Change",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Select Video",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Import Video to Analyze",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Supports MP4, MKV, MOV files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Preset Selector
            item {
                Column {
                    Text(
                        "Content Style Preset",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ContentPreset.values()) { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset },
                                label = {
                                    Text(preset.title, fontWeight = FontWeight.Medium)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        selectedPreset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Export & Clip Options
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Target Highlights: ${clipCount.toInt()} clips",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Slider(
                                value = clipCount,
                                onValueChange = { clipCount = it },
                                valueRange = 1f..8f,
                                steps = 6,
                                modifier = Modifier.weight(1.3f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Export Format:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = exportAspect == SettingsManager.ASPECT_VERTICAL,
                                    onClick = { exportAspect = SettingsManager.ASPECT_VERTICAL },
                                    label = { Text("📱 9:16 Shorts") }
                                )
                                FilterChip(
                                    selected = exportAspect == SettingsManager.ASPECT_ORIGINAL,
                                    onClick = { exportAspect = SettingsManager.ASPECT_ORIGINAL },
                                    label = { Text("📺 Original") }
                                )
                            }
                        }
                    }
                }
            }

            // Inline 9Router Server Card (so user on HP Fisik can see/change IP or Auto-Scan without opening Settings)
            if (appSettings.provider == SettingsManager.PROVIDER_9ROUTER) {
                item {
                    val isUsingEmulatorIpOnPhone = !LanScanner.isEmulator() && appSettings.baseUrl.contains("10.0.2.2")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isUsingEmulatorIpOnPhone) 1.5.dp else 0.dp,
                                color = if (isUsingEmulatorIpOnPhone) MaterialTheme.colorScheme.error else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "🌐 Server 9Router (${appSettings.modelName})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isUsingEmulatorIpOnPhone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                if (deviceIps.isNotEmpty()) {
                                    Text(
                                        "IP HP: ${deviceIps.first()}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isUsingEmulatorIpOnPhone) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "⚠️ '10.0.2.2' hanya untuk Emulator! Untuk HP fisik, klik 'Cari Otomatis IP PC' atau masukkan IP LAN PC Anda (contoh: http://${subnetPrefix}5:20128/v1):",
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = quickUrlInput,
                                    onValueChange = { quickUrlInput = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Alamat 9Router (IP PC / URL)", fontSize = 11.sp) },
                                    placeholder = { Text("http://${subnetPrefix}10:20128/v1", fontSize = 11.sp) }
                                )
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        var formatted = quickUrlInput.trim()
                                        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                                            formatted = if (formatted.contains(":")) {
                                                "http://$formatted/v1"
                                            } else {
                                                "http://$formatted:20128/v1"
                                            }
                                        }
                                        val updated = appSettings.copy(baseUrl = formatted)
                                        appSettings = updated
                                        quickUrlInput = formatted
                                        settingsManager.saveSettings(
                                            apiKey = updated.apiKey,
                                            baseUrl = updated.baseUrl,
                                            modelName = updated.modelName,
                                            provider = updated.provider,
                                            defaultAspect = updated.defaultAspect
                                        )
                                        coroutineScope.launch {
                                            statusText = "Menguji koneksi ke $formatted..."
                                            try {
                                                val res = analysisService.testConnection(updated)
                                                statusText = "✅ Terhubung! $res"
                                                Toast.makeText(context, "9Router Terhubung!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                statusText = "❌ ${e.message}"
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Simpan & Tes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isScanningLanMain = true
                                        statusText = "Memindai Wi-Fi (${subnetPrefix}1-254) mencari port 20128..."
                                        coroutineScope.launch {
                                            val found = LanScanner.discover9RouterOnLan { msg ->
                                                coroutineScope.launch(Dispatchers.Main) { statusText = msg }
                                            }
                                            isScanningLanMain = false
                                            if (found != null) {
                                                val updated = appSettings.copy(baseUrl = found)
                                                appSettings = updated
                                                quickUrlInput = found
                                                settingsManager.saveSettings(
                                                    apiKey = updated.apiKey,
                                                    baseUrl = updated.baseUrl,
                                                    modelName = updated.modelName,
                                                    provider = updated.provider,
                                                    defaultAspect = updated.defaultAspect
                                                )
                                                statusText = "✅ Ditemukan otomatis 9Router PC di: $found"
                                                Toast.makeText(context, "Ditemukan: $found", Toast.LENGTH_LONG).show()
                                            } else {
                                                quickUrlInput = "http://${subnetPrefix}"
                                                statusText = "⚠️ Port 20128 belum ditemukan di ${subnetPrefix}1-254. Pastikan di PC 9Router jalan di 0.0.0.0:20128 (cek Firewall PC) atau lengkapi IP PC di atas."
                                            }
                                        }
                                    },
                                    enabled = !isScanningLanMain && !isAnalyzing,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isScanningLanMain) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Memindai Wi-Fi...", fontSize = 11.sp)
                                    } else {
                                        Text("🔍 Cari Otomatis IP PC (9Router) di Wi-Fi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Analyze Action Button
            item {
                Button(
                    onClick = {
                        if (isAnalyzing) {
                            analysisJob?.cancel()
                            isAnalyzing = false
                            statusText = "Analysis cancelled."
                            return@Button
                        }
                        if (selectedVideoUri == null) {
                            Toast.makeText(context, "Please import a video first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (appSettings.apiKey.isBlank() && appSettings.provider == SettingsManager.PROVIDER_GEMINI_DIRECT) {
                            Toast.makeText(context, "Configure your Gemini API Key in Settings first!", Toast.LENGTH_LONG).show()
                            showSettingsDialog = true
                            return@Button
                        }

                        // Sync any typed URL in quickUrlInput if user edited it without clicking Save
                        if (appSettings.provider == SettingsManager.PROVIDER_9ROUTER &&
                            quickUrlInput.isNotBlank() &&
                            quickUrlInput != appSettings.baseUrl
                        ) {
                            var formatted = quickUrlInput.trim()
                            if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                                formatted = if (formatted.contains(":")) {
                                    "http://$formatted/v1"
                                } else {
                                    "http://$formatted:20128/v1"
                                }
                            }
                            val updated = appSettings.copy(baseUrl = formatted)
                            appSettings = updated
                            quickUrlInput = formatted
                            settingsManager.saveSettings(
                                apiKey = updated.apiKey,
                                baseUrl = updated.baseUrl,
                                modelName = updated.modelName,
                                provider = updated.provider,
                                defaultAspect = updated.defaultAspect
                            )
                        }

                        isAnalyzing = true
                        clipResults = emptyList()

                        analysisJob = coroutineScope.launch {
                            try {
                                statusText = "1/3 Extracting key video frames..."
                                val frames = extractVideoFrames(context, selectedVideoUri!!)
                                if (frames.isEmpty()) {
                                    statusText = "Failed to extract frames from video."
                                    isAnalyzing = false
                                    return@launch
                                }

                                statusText = "2/3 AI Vision analyzing ${frames.size} frames (${selectedPreset.title} mode)..."
                                val clips = analysisService.analyzeFrames(
                                    frames = frames,
                                    maxClips = clipCount.toInt(),
                                    preset = selectedPreset,
                                    settings = appSettings,
                                    onStatusUpdate = { msg ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            statusText = msg
                                        }
                                    },
                                    onEndpointAutoUpdated = { newUrl ->
                                        coroutineScope.launch(Dispatchers.Main) {
                                            val updated = appSettings.copy(baseUrl = newUrl)
                                            appSettings = updated
                                            quickUrlInput = newUrl
                                            settingsManager.saveSettings(
                                                apiKey = updated.apiKey,
                                                baseUrl = updated.baseUrl,
                                                modelName = updated.modelName,
                                                provider = updated.provider,
                                                defaultAspect = updated.defaultAspect
                                            )
                                        }
                                    }
                                )

                                statusText = "3/3 Found ${clips.size} viral highlight clips!"
                                clipResults = clips
                                if (clips.isEmpty()) {
                                    statusText = "No clear highlights found. Try increasing clip count or changing preset."
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) {
                                    statusText = "Analysis cancelled by user."
                                } else {
                                    statusText = "Error: ${e.message}"
                                    e.printStackTrace()
                                }
                            } finally {
                                isAnalyzing = false
                                analysisJob = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAnalyzing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Cancel Analysis", fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            "✨ Analyze & Generate Highlights",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Results Header & Batch Export
            if (clipResults.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Recommended Clips (${clipResults.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Tipografi Editorial Bersih & Tone Sinematik",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = {
                                    isExportingAll = true
                                    coroutineScope.launch {
                                        var count = 0
                                        for ((idx, clip) in clipResults.withIndex()) {
                                            statusText = "Merender video ${idx + 1}/${clipResults.size} (${clip.title})..."
                                            try {
                                                val uri = trimAndExportClip(
                                                    context = context,
                                                    inputUri = selectedVideoUri!!,
                                                    clip = clip,
                                                    clipIndex = idx + 1,
                                                    aspectRatio = exportAspect
                                                )
                                                if (uri != null) count++
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        isExportingAll = false
                                        statusText = "Berhasil menyimpan $count video ke Galeri (Movies/AutoClipper)."
                                        Toast.makeText(context, "Tersimpan $count video di Galeri (Movies/AutoClipper)", Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !isExportingAll && selectedVideoUri != null,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                if (isExportingAll) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Merender...", fontSize = 12.sp)
                                } else {
                                    Text("Export Semua", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Global Quick Template Bar to style all clips at once
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(CaptionTemplate.values()) { tpl ->
                                SuggestionChip(
                                    onClick = {
                                        clipResults = clipResults.map { it.copy(template = tpl) }
                                        Toast.makeText(context, "Semua klip diubah ke gaya ${tpl.label}", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Gaya: ${tpl.label}", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Clip Highlight Cards
                itemsIndexed(clipResults) { index, clip ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { editingClipIndex = index },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Category + Timestamp + Score
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        clip.category,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${clip.startStr} – ${clip.endStr}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.weight(1f))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "${(clip.score * 100).toInt()}% Match",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Clean Editorial Title
                            Text(
                                clip.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(Modifier.height(6.dp))

                            // Active Style & Filter Badges
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        clip.template.label,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "Tone: ${clip.colorFilter.label}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Subtle Subtitle & Context Preview Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (clip.hook.isNotBlank()) {
                                        Text(
                                            "\"${clip.hook}\"",
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (clip.captions.isNotEmpty() && clip.showBottomSubtitle && clip.template != CaptionTemplate.NO_OVERLAY) {
                                        if (clip.hook.isNotBlank()) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                            )
                                        }
                                        Text(
                                            "Subtitle (${clip.captions.size} segmen):",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        clip.captions.forEachIndexed { cIdx, capLine ->
                                            Text(
                                                "${cIdx + 1}. $capLine",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }

                            if (clip.reason.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    clip.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (clip.fypHashtags.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    clip.fypHashtags,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Action buttons: Edit Clip & Export Video
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { editingClipIndex = index },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Edit Klip & Teks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                val isThisExporting = exportingIndex == index
                                Button(
                                    onClick = {
                                        exportingIndex = index
                                        coroutineScope.launch {
                                            statusText = "Merender video (${clip.startStr} – ${clip.endStr})..."
                                            try {
                                                val uri = trimAndExportClip(
                                                    context = context,
                                                    inputUri = selectedVideoUri!!,
                                                    clip = clip,
                                                    clipIndex = index + 1,
                                                    aspectRatio = exportAspect
                                                )
                                                if (uri != null) {
                                                    statusText = "Video '${clip.title}' berhasil disimpan ke Galeri (Movies/AutoClipper)."
                                                    Toast.makeText(context, "Tersimpan di Galeri (Movies/AutoClipper)", Toast.LENGTH_LONG).show()
                                                } else {
                                                    statusText = "Export gagal."
                                                }
                                            } catch (e: Exception) {
                                                statusText = "Export Error: ${e.message}"
                                                e.printStackTrace()
                                            } finally {
                                                exportingIndex = null
                                            }
                                        }
                                    },
                                    enabled = !isThisExporting && !isExportingAll && selectedVideoUri != null,
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isThisExporting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Merender...", fontSize = 12.sp)
                                    } else {
                                        Text("Export Video ($exportAspect)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            initialSettings = appSettings,
            analysisService = analysisService,
            onDismiss = { showSettingsDialog = false },
            onSave = { updated ->
                appSettings = updated
                exportAspect = updated.defaultAspect
                settingsManager.saveSettings(
                    apiKey = updated.apiKey,
                    baseUrl = updated.baseUrl,
                    modelName = updated.modelName,
                    provider = updated.provider,
                    defaultAspect = updated.defaultAspect
                )
                Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            },
            onReset = {
                settingsManager.resetToDefaults()
                appSettings = settingsManager.getSettings()
                exportAspect = appSettings.defaultAspect
                Toast.makeText(context, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Interactive Short Studio & FYP Editor Dialog
    if (editingClipIndex != null && selectedVideoUri != null) {
        val idx = editingClipIndex!!
        val targetClip = clipResults.getOrNull(idx)
        if (targetClip != null) {
            ShortEditorDialog(
                videoUri = selectedVideoUri!!,
                initialClip = targetClip,
                onDismiss = { editingClipIndex = null },
                onSaveClip = { updatedClip ->
                    val mutable = clipResults.toMutableList()
                    if (idx in mutable.indices) {
                        mutable[idx] = updatedClip
                        clipResults = mutable
                    }
                    Toast.makeText(context, "Editan Short disimpan!", Toast.LENGTH_SHORT).show()
                },
                onSaveAndExport = { updatedClip ->
                    val mutable = clipResults.toMutableList()
                    if (idx in mutable.indices) {
                        mutable[idx] = updatedClip
                        clipResults = mutable
                    }
                    exportingIndex = idx
                    coroutineScope.launch {
                        statusText = "Merender video '${updatedClip.title}'..."
                        try {
                            val uri = trimAndExportClip(
                                context = context,
                                inputUri = selectedVideoUri!!,
                                clip = updatedClip,
                                clipIndex = idx + 1,
                                aspectRatio = exportAspect
                            )
                            if (uri != null) {
                                statusText = "Video '${updatedClip.title}' berhasil disimpan di Galeri!"
                                Toast.makeText(context, "Video tersimpan di Galeri (Movies/AutoClipper)", Toast.LENGTH_LONG).show()
                            } else {
                                statusText = "Export gagal."
                            }
                        } catch (e: Exception) {
                            statusText = "Export Error: ${e.message}"
                            e.printStackTrace()
                        } finally {
                            exportingIndex = null
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun VideoPlayer(uri: Uri, startStr: String, endStr: String) {
    val context = LocalContext.current
    val startMs = remember(startStr) { parseMmSsToMs(startStr) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(startMs) {
        exoPlayer.seekTo(startMs)
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

suspend fun extractVideoFrames(context: Context, uri: Uri): List<Pair<Long, Bitmap>> = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    val frames = mutableListOf<Pair<Long, Bitmap>>()
    try {
        retriever.setDataSource(context, uri)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationMs = durationStr?.toLongOrNull() ?: 0L

        val numFrames = 15
        val intervalMs = if (durationMs > 0) durationMs / numFrames else 0L

        if (intervalMs > 0) {
            for (i in 0 until numFrames) {
                val timeMs = i * intervalMs
                val timeUsec = timeMs * 1000
                val bmp = retriever.getFrameAtTime(timeUsec, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bmp != null) {
                    val targetW = 512
                    val targetH = (512.0 * bmp.height / bmp.width).toInt()
                    val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                    frames.add(Pair(timeMs, scaled))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        retriever.release()
    }
    frames
}

suspend fun trimAndExportClip(
    context: Context,
    inputUri: Uri,
    clip: VideoClip,
    clipIndex: Int,
    aspectRatio: String
): Uri? = suspendCancellableCoroutine { continuation ->
    try {
        val startMs = parseMmSsToMs(clip.startStr)
        val endMs = parseMmSsToMs(clip.endStr).coerceAtLeast(startMs + 2000L)
        val clipDurationMs = (endMs - startMs).coerceAtLeast(2000L)

        val outputFile = File(context.cacheDir, "fyp_short_${clipIndex}_${System.currentTimeMillis()}.mp4")

        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(clippingConfig)
            .build()

        // Build full hardware-accelerated effects: 9:16 crop + Color Grading + Dynamic Timed Captions & Emoticons Burn-In
        val effects = ShortVideoOverlayRenderer.buildMedia3EffectsForClip(
            clip = clip,
            aspectRatio = aspectRatio,
            clipDurationMs = clipDurationMs
        )

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    try {
                        val finalUri = saveToMediaStore(context, outputFile)
                        outputFile.delete()
                        if (continuation.isActive) {
                            continuation.resume(finalUri)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(exportException)
                    }
                }
            })
            .build()

        transformer.start(editedMediaItem, outputFile.absolutePath)

        continuation.invokeOnCancellation {
            transformer.cancel()
        }
    } catch (e: Exception) {
        if (continuation.isActive) {
            continuation.resumeWithException(e)
        }
    }
}

fun saveToMediaStore(context: Context, file: File): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AutoClipper")
    }

    val uri = context.contentResolver.insert(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )

    uri?.let { destUri ->
        context.contentResolver.openOutputStream(destUri)?.use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    return uri
}
