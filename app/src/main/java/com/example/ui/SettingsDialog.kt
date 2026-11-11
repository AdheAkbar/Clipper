package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.SettingsManager
import com.example.network.LanScanner
import com.example.network.VideoAnalysisService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    initialSettings: AppSettings,
    analysisService: VideoAnalysisService,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit,
    onReset: () -> Unit
) {
    var apiKey by remember { mutableStateOf(initialSettings.apiKey) }
    var baseUrl by remember { mutableStateOf(initialSettings.baseUrl) }
    var modelName by remember { mutableStateOf(initialSettings.modelName) }
    var provider by remember { mutableStateOf(initialSettings.provider) }
    var defaultAspect by remember { mutableStateOf(initialSettings.defaultAspect) }
    var isKeyVisible by remember { mutableStateOf(false) }

    val deviceIps = remember { LanScanner.getDeviceLanIps() }
    val defaultSubnetPrefix = remember(deviceIps) {
        val firstIp = deviceIps.firstOrNull()
        if (firstIp != null && firstIp.count { it == '.' } == 3) {
            firstIp.substringBeforeLast(".") + "."
        } else {
            "192.168.1."
        }
    }
    var lanIpInput by remember { mutableStateOf(defaultSubnetPrefix) }

    var isScanningLan by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚙️ 9Router & AI Settings", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Checklist Card for HP Fisik vs Emulator
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "📱 Info Jaringan HP Anda Saat Ini:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (deviceIps.isNotEmpty()) {
                                "• IP Wi-Fi HP Anda: ${deviceIps.joinToString(", ")}\n" +
                                "• IP PC Anda biasanya diawali: $defaultSubnetPrefix...\n" +
                                "• Jangan gunakan 10.0.2.2 di HP fisik (10.0.2.2 hanya untuk Emulator)."
                            } else {
                                "• HP belum terdeteksi di Wi-Fi LAN.\n• Pastikan HP & PC terhubung ke Wi-Fi / Hotspot yang sama."
                            },
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Provider Mode
                Text("AI Provider Engine", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = provider == SettingsManager.PROVIDER_9ROUTER,
                        onClick = { provider = SettingsManager.PROVIDER_9ROUTER },
                        label = { Text("9Router Proxy") }
                    )
                    FilterChip(
                        selected = provider == SettingsManager.PROVIDER_GEMINI_DIRECT,
                        onClick = { provider = SettingsManager.PROVIDER_GEMINI_DIRECT },
                        label = { Text("Gemini Direct") }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Model / Combo Selection
                Text("Combo / Model 9Router", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("AdheRouter") }
                )

                Spacer(Modifier.height(6.dp))
                Text("Pilih Combo dari Dashboard Anda:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))

                // Quick chips for Combos
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    SuggestionChip(
                        onClick = { modelName = "AdheRouter" },
                        label = { Text("⚡ AdheRouter (Smart)", fontWeight = if (modelName == "AdheRouter") FontWeight.Bold else FontWeight.Normal) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (modelName == "AdheRouter") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    )
                    SuggestionChip(
                        onClick = { modelName = "Bandel-Gacor" },
                        label = { Text("🔄 Bandel-Gacor", fontWeight = if (modelName == "Bandel-Gacor") FontWeight.Bold else FontWeight.Normal) }
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text("Atau Vision Adapter Models:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("zd/gpt-6-luna", "ag/claude-sonnet-4-6", "oc-prod/deepseek-v4.1-flash", "ag/gemini-3.8-flash-high").forEach { m ->
                        SuggestionChip(
                            onClick = { modelName = m },
                            label = { Text("👁️ $m", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Endpoint URL
                if (provider == SettingsManager.PROVIDER_9ROUTER) {
                    Text("Endpoint URL (9Router)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))

                    // Auto Scan Button
                    Button(
                        onClick = {
                            isScanningLan = true
                            testResultText = "Memindai Wi-Fi untuk mencari port 20128..."
                            isTestSuccess = false
                            coroutineScope.launch {
                                val foundUrl = LanScanner.discover9RouterOnLan { msg ->
                                    testResultText = msg
                                }
                                isScanningLan = false
                                if (foundUrl != null) {
                                    baseUrl = foundUrl
                                    isTestSuccess = true
                                    testResultText = "✅ Ditemukan 9Router otomatis di:\n$foundUrl\nKlik 'Simpan' untuk menggunakan alamat ini!"
                                } else {
                                    isTestSuccess = false
                                    testResultText = "⚠️ Tidak ditemukan port 20128 terbuka di jaringan ${defaultSubnetPrefix}1-254.\n" +
                                            "Pastikan:\n1. 9Router di PC jalan di 0.0.0.0:20128\n2. Firewall PC tidak memblokir port 20128\n3. Atau ketik IP PC Anda secara manual di bawah."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isScanningLan && !isTestingConnection,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isScanningLan) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Mencari IP PC di Wi-Fi...", fontSize = 12.sp)
                        } else {
                            Text("🔍 Cari Otomatis IP 9Router di Wi-Fi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("URL Aktif") },
                        placeholder = { Text("http://${defaultSubnetPrefix}10:20128/v1") }
                    )

                    // Helper input for IP LAN PC
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = lanIpInput,
                            onValueChange = { lanIpInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("IP LAN PC Anda", fontSize = 11.sp) },
                            placeholder = { Text("${defaultSubnetPrefix}5", fontSize = 11.sp) }
                        )
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val cleanIp = lanIpInput.trim()
                                    .removePrefix("http://")
                                    .removePrefix("https://")
                                    .substringBefore(":")
                                    .substringBefore("/")
                                if (cleanIp.isNotBlank()) {
                                    baseUrl = "http://$cleanIp:20128/v1"
                                }
                            },
                            enabled = lanIpInput.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text("Set IP", fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = { baseUrl = "http://${defaultSubnetPrefix}1:20128/v1" },
                            label = { Text("Hotspot (${defaultSubnetPrefix}1)", fontSize = 10.sp) }
                        )
                        SuggestionChip(
                            onClick = { baseUrl = "http://10.0.2.2:20128/v1" },
                            label = { Text("Emulator (10.0.2.2)", fontSize = 10.sp) }
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                }

                // API Key
                Text("API Key (Opsional)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Otomatis mengirim header 'Authorization: Bearer 9router' jika dikosongkan",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Text(if (isKeyVisible) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    placeholder = { Text("9router") }
                )

                Spacer(Modifier.height(14.dp))

                // Test Connection Button
                Button(
                    onClick = {
                        isTestingConnection = true
                        testResultText = null
                        coroutineScope.launch {
                            try {
                                val reply = analysisService.testConnection(
                                    AppSettings(
                                        apiKey = apiKey,
                                        baseUrl = baseUrl,
                                        modelName = modelName,
                                        provider = provider,
                                        defaultAspect = defaultAspect
                                    )
                                )
                                isTestSuccess = true
                                testResultText = "✅ Berhasil terhubung ke 9Router!\nRespon: $reply"
                            } catch (e: Exception) {
                                isTestSuccess = false
                                testResultText = "❌ Gagal Terhubung:\n${e.message}"
                            } finally {
                                isTestingConnection = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    enabled = !isTestingConnection && !isScanningLan
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Menguji Koneksi...")
                    } else {
                        Text("🔌 Uji Koneksi 9Router")
                    }
                }

                if (testResultText != null) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isTestSuccess) Color(0xFF1B4D2E) else Color(0xFF5A1E1E),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            testResultText!!,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Export Aspect Ratio
                Text("Default Export Aspect", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = defaultAspect == SettingsManager.ASPECT_VERTICAL,
                        onClick = { defaultAspect = SettingsManager.ASPECT_VERTICAL },
                        label = { Text("📱 9:16 Shorts") }
                    )
                    FilterChip(
                        selected = defaultAspect == SettingsManager.ASPECT_ORIGINAL,
                        onClick = { defaultAspect = SettingsManager.ASPECT_ORIGINAL },
                        label = { Text("📺 16:9 Original") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        AppSettings(
                            apiKey = apiKey,
                            baseUrl = baseUrl,
                            modelName = modelName,
                            provider = provider,
                            defaultAspect = defaultAspect
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onReset()
                        apiKey = SettingsManager.DEFAULT_API_KEY
                        baseUrl = SettingsManager.DEFAULT_BASE_URL
                        modelName = SettingsManager.DEFAULT_MODEL
                        provider = SettingsManager.PROVIDER_9ROUTER
                        defaultAspect = SettingsManager.ASPECT_VERTICAL
                        testResultText = null
                    }
                ) {
                    Text("Reset")
                }
                TextButton(onClick = onDismiss) {
                    Text("Tutup")
                }
            }
        }
    )
}
