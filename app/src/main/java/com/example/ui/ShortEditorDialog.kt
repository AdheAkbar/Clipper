package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.model.CaptionTemplate
import com.example.model.VideoClip
import com.example.model.VideoColorFilter
import com.example.model.formatMsToMmSs
import com.example.model.parseMmSsToMs
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortEditorDialog(
    videoUri: Uri,
    initialClip: VideoClip,
    onDismiss: () -> Unit,
    onSaveClip: (VideoClip) -> Unit,
    onSaveAndExport: (VideoClip) -> Unit
) {
    val context = LocalContext.current

    var startStr by remember { mutableStateOf(initialClip.startStr) }
    var endStr by remember { mutableStateOf(initialClip.endStr) }
    var title by remember { mutableStateOf(initialClip.title) }
    var hook by remember { mutableStateOf(initialClip.hook) }
    var captions by remember {
        mutableStateOf(
            initialClip.captions.ifEmpty {
                listOfNotNull(
                    initialClip.hook.takeIf { it.isNotBlank() },
                    initialClip.title.takeIf { it.isNotBlank() && it != initialClip.hook }
                ).ifEmpty { listOf("") }
            }
        )
    }
    var fypHashtags by remember { mutableStateOf(initialClip.fypHashtags) }
    var template by remember { mutableStateOf(initialClip.template) }
    var colorFilter by remember { mutableStateOf(initialClip.colorFilter) }
    var showTopHeader by remember { mutableStateOf(initialClip.showTopHeader) }
    var showBottomSubtitle by remember { mutableStateOf(initialClip.showBottomSubtitle) }

    var selectedTab by remember { mutableStateOf(0) }

    val startMs = remember(startStr) { parseMmSsToMs(startStr) }
    val endMs = remember(endStr) { parseMmSsToMs(endStr).coerceAtLeast(startMs + 2000L) }
    val clipDurationMs = (endMs - startMs).coerceAtLeast(2000L)

    var activePhaseIndex by remember { mutableStateOf(0) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            seekTo(startMs)
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(startMs, endMs, captions.size) {
        exoPlayer.seekTo(startMs)
        while (true) {
            val pos = exoPlayer.currentPosition
            if (pos >= endMs || pos < startMs - 500L) {
                exoPlayer.seekTo(startMs)
            }
            val elapsed = (pos - startMs).coerceAtLeast(0L)
            val progress = (elapsed.toDouble() / clipDurationMs.toDouble()).coerceIn(0.0, 0.999)
            val phaseCount = captions.size.coerceAtLeast(1)
            activePhaseIndex = (progress * phaseCount).toInt().coerceIn(0, phaseCount - 1)
            delay(150L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    fun buildUpdatedClip(): VideoClip {
        val validCaptions = captions.map { it.trim() }.filter { it.isNotEmpty() }
        return initialClip.copy(
            startStr = startStr,
            endStr = endStr,
            title = title.trim(),
            hook = validCaptions.firstOrNull() ?: hook.trim(),
            captions = validCaptions,
            fypHashtags = fypHashtags.trim(),
            template = template,
            colorFilter = colorFilter,
            showTopHeader = showTopHeader,
            showBottomSubtitle = showBottomSubtitle
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Clean Minimal Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Clip Studio Editor",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Text(
                            "${startStr} – ${endStr} • ${template.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Tutup")
                    }
                }

                // Clean Editorial Video Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(265.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = {
                            PlayerView(context).apply {
                                player = exoPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Subtle bottom vignette matching ShortVideoOverlayRenderer
                    if (template != CaptionTemplate.NO_OVERLAY && showBottomSubtitle) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.36f)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                    )
                                )
                        )
                    }

                    // Live Editorial Overlay Preview
                    if (template != CaptionTemplate.NO_OVERLAY) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (showTopHeader && title.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color(0x900F172A),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(8.dp))
                            }

                            if (showBottomSubtitle) {
                                val currentSub = captions.getOrElse(activePhaseIndex) { captions.firstOrNull().orEmpty() }
                                if (currentSub.isNotBlank()) {
                                    when (template) {
                                        CaptionTemplate.CLEAN_EDITORIAL -> {
                                            Text(
                                                text = currentSub,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 19.sp,
                                                modifier = Modifier.padding(bottom = 12.dp)
                                            )
                                        }

                                        CaptionTemplate.CINEMATIC_PILL -> {
                                            Box(
                                                modifier = Modifier
                                                    .padding(bottom = 12.dp)
                                                    .background(
                                                        Color(template.pillBgColorHex),
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = currentSub,
                                                    color = Color(template.primaryColorHex),
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }

                                        CaptionTemplate.PODCAST_MINIMAL -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 12.dp)
                                                    .background(
                                                        Color(template.pillBgColorHex),
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .height(26.dp)
                                                        .background(
                                                            Color(template.accentColorHex),
                                                            RoundedCornerShape(2.dp)
                                                        )
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    text = currentSub,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }

                                        CaptionTemplate.NO_OVERLAY -> Unit
                                    }
                                }
                            }
                        }
                    }
                }

                // Clean Editor Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Subtitle & Judul", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Gaya & Warna", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Waktu & Deskripsi", fontSize = 12.sp) }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = showBottomSubtitle,
                                    onClick = { showBottomSubtitle = !showBottomSubtitle },
                                    label = { Text("Subtitle Bawah") }
                                )
                                FilterChip(
                                    selected = showTopHeader,
                                    onClick = { showTopHeader = !showTopHeader },
                                    label = { Text("Judul Atas Minimal") }
                                )
                            }

                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Judul Segmen") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Teks Subtitle di Video (${captions.size} bagian):",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                TextButton(
                                    onClick = { captions = captions + "" }
                                ) {
                                    Text("+ Tambah Bagian", fontSize = 12.sp)
                                }
                            }

                            captions.forEachIndexed { idx, capText ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = capText,
                                        onValueChange = { newVal ->
                                            val mutable = captions.toMutableList()
                                            mutable[idx] = newVal
                                            captions = mutable
                                        },
                                        label = { Text("Subtitle #${idx + 1}") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    if (captions.size > 1) {
                                        TextButton(
                                            onClick = {
                                                val mutable = captions.toMutableList()
                                                mutable.removeAt(idx)
                                                captions = mutable
                                            }
                                        ) {
                                            Text("Hapus", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            Text("Tipografi & Tampilan Subtitle:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CaptionTemplate.values().forEach { tpl ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (template == tpl)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        onClick = { template = tpl }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                tpl.label,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                tpl.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Text("Tone Warna Video (Color Grade):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(VideoColorFilter.values()) { vf ->
                                    FilterChip(
                                        selected = colorFilter == vf,
                                        onClick = { colorFilter = vf },
                                        label = { Text(vf.label) }
                                    )
                                }
                            }
                        }

                        2 -> {
                            Text(
                                "Presisi Waktu Potong (Durasi: ${clipDurationMs / 1000} detik):",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = startStr,
                                    onValueChange = { startStr = it },
                                    label = { Text("Mulai (MM:SS)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedButton(
                                    onClick = {
                                        startStr = formatMsToMmSs((startMs - 1000L).coerceAtLeast(0L))
                                    }
                                ) { Text("-1s") }
                                OutlinedButton(
                                    onClick = {
                                        if (startMs + 1000L < endMs - 1000L) {
                                            startStr = formatMsToMmSs(startMs + 1000L)
                                        }
                                    }
                                ) { Text("+1s") }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = endStr,
                                    onValueChange = { endStr = it },
                                    label = { Text("Selesai (MM:SS)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedButton(
                                    onClick = {
                                        if (endMs - 1000L > startMs + 1000L) {
                                            endStr = formatMsToMmSs(endMs - 1000L)
                                        }
                                    }
                                ) { Text("-1s") }
                                OutlinedButton(
                                    onClick = {
                                        endStr = formatMsToMmSs(endMs + 1000L)
                                    }
                                ) { Text("+1s") }
                            }

                            Spacer(Modifier.height(6.dp))

                            Text("Deskripsi & Hashtag Postingan:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            OutlinedTextField(
                                value = fypHashtags,
                                onValueChange = { fypHashtags = it },
                                label = { Text("Hashtag") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                onClick = {
                                    val fullPostText = "$title\n\n$hook\n\n$fypHashtags"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Clip Caption", fullPostText))
                                    Toast.makeText(context, "Deskripsi & hashtag disalin ke clipboard.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Salin Deskripsi untuk Upload")
                            }
                        }
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onSaveClip(buildUpdatedClip())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Simpan", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val updated = buildUpdatedClip()
                            onSaveAndExport(updated)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Export ke Galeri", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
