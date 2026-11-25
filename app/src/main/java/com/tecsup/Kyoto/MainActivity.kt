package com.tecsup.Kyoto

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tecsup.Kyoto.ui.theme.KyotoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val Ink = Color(0xFF18221E)
private val Green = Color(0xFF28634E)
private val Mint = Color(0xFFDCEFE5)
private val CanvasBg = Color(0xFFF5F7F3)
private val Muted = Color(0xFF75817A)
private val SppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class MainActivity : ComponentActivity() {
    private val adapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var socket: BluetoothSocket? = null
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var dark by remember { mutableStateOf(false) }
            KyotoTheme(darkTheme = dark, dynamicColor = false) {
                KyotoApp(
                    adapter = adapter,
                    onConnect = { device, connected, onReading, onError -> connect(device, connected, onReading, onError) },
                    onDisconnect = { disconnect() },
                    requestBluetoothPermission = { requestBluetoothPermissions() },
                    onDarkChange = { dark = it }
                )
            }
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionRequest.launch(permissions)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice, connected: (String) -> Unit, reading: (Float) -> Unit, error: (String) -> Unit) {
        requestBluetoothPermissions()
        Thread {
            try {
                adapter?.cancelDiscovery()
                val newSocket = device.createRfcommSocketToServiceRecord(SppUuid)
                newSocket.connect()
                socket = newSocket
                runOnUiThread { connected(device.name ?: "ESP32") }
                val input = newSocket.inputStream.bufferedReader()
                while (newSocket.isConnected) {
                    val line = input.readLine() ?: break
                    Regex("[-+]?[0-9]*\\.?[0-9]+").find(line)?.value?.toFloatOrNull()?.let { value ->
                        if (value in 0f..160f) runOnUiThread { reading(value) }
                    }
                }
                runOnUiThread { connected("") }
            } catch (e: Exception) {
                runOnUiThread { error(e.localizedMessage ?: "No se pudo conectar al dispositivo") }
            }
        }.start()
    }

    private fun disconnect() {
        try { socket?.close() } catch (_: Exception) { }
        socket = null
    }

    override fun onDestroy() { disconnect(); super.onDestroy() }
}

private enum class Screen(val label: String, val glyph: String) {
    LIVE("En vivo", "⌁"), HISTORY("Historial", "◷"), PROFILE("Perfil", "◉")
}

@Composable
private fun KyotoApp(
    adapter: BluetoothAdapter?,
    onConnect: (BluetoothDevice, (String) -> Unit, (Float) -> Unit, (String) -> Unit) -> Unit,
    onDisconnect: () -> Unit,
    requestBluetoothPermission: () -> Unit,
    onDarkChange: (Boolean) -> Unit
) {
    var screen by remember { mutableStateOf(Screen.LIVE) }
    var db by remember { mutableFloatStateOf(32f) }
    var connectedName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showDevices by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }
    var alerts by remember { mutableStateOf(true) }
    val readings = remember { mutableStateListOf(48f, 42f, 38f, 45f, 36f, 40f, 34f, 39f, 32f) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Screen.entries.forEach { item ->
                    NavigationBarItem(selected = screen == item, onClick = { screen = item },
                        icon = { Text(item.glyph, fontSize = 21.sp, fontWeight = FontWeight.Medium) },
                        label = { Text(item.label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Mint))
                }
            }
        }, snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
            when (screen) {
                Screen.LIVE -> LiveScreen(db, connectedName, readings, onBluetooth = { showDevices = true })
                Screen.HISTORY -> HistoryScreen(readings)
                Screen.PROFILE -> ProfileScreen(dark, alerts, onDark = { dark = it; onDarkChange(it) }, onAlerts = { alerts = it }, onSettings = { scope.launch { snackbar.showSnackbar("Ajustes del dispositivo") } }, onLogout = { scope.launch { snackbar.showSnackbar("Sesión cerrada") } })
            }
        }
    }
    if (error.isNotBlank()) LaunchedEffect(error) { snackbar.showSnackbar(error); error = "" }
    if (showDevices) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(androidx.compose.ui.platform.LocalContext.current, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val devices = if (hasPermission) try { adapter?.bondedDevices?.toList().orEmpty() } catch (_: SecurityException) { emptyList() } else emptyList()
        AlertDialog(onDismissRequest = { showDevices = false }, title = { Text("Conectar detector") },
            text = { Column {
                Text("Selecciona un ESP32 emparejado por Bluetooth.", color = Muted)
                Spacer(Modifier.height(12.dp))
                if (devices.isEmpty()) Text("No hay dispositivos emparejados. Empareja el ESP32 desde Ajustes de Android e inténtalo de nuevo.", color = Muted)
                devices.forEach { device -> TextButton(onClick = {
                    showDevices = false
                    onConnect(device, { connectedName = it }, { value -> db = value; readings.add(value); if (readings.size > 24) readings.removeAt(0) }, { error = it })
                }, modifier = Modifier.fillMaxWidth()) { Text("●   ${runCatching { device.name ?: device.address }.getOrDefault("Dispositivo Bluetooth")}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start) } }
            } }, confirmButton = { TextButton(onClick = { requestBluetoothPermission() }) { Text("Permisos Bluetooth") } }, dismissButton = { TextButton(onClick = { showDevices = false }) { Text("Cerrar") } })
    }
}

@Composable
private fun LiveScreen(db: Float, connectedName: String, readings: List<Float>, onBluetooth: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("KYOTO", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("Sala de lectura", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground) }
            Surface(shape = CircleShape, color = Mint) { Text("  K  ", Modifier.padding(vertical = 10.dp), color = Green, fontWeight = FontWeight.Bold) }
        }
        Surface(onClick = onBluetooth, shape = RoundedCornerShape(18.dp), color = if (connectedName.isNotEmpty()) Mint else MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(if (connectedName.isNotEmpty()) Green else MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) { Text("ᛒ", color = if (connectedName.isNotEmpty()) Color.White else Green, fontSize = 20.sp) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(if (connectedName.isNotEmpty()) "Detector conectado" else "Detector desconectado", fontWeight = FontWeight.SemiBold); Text(if (connectedName.isNotEmpty()) connectedName else "Toca para conectar por Bluetooth", fontSize = 12.sp, color = Muted) }
                Text(if (connectedName.isNotEmpty()) "●" else "＋", color = Green, fontSize = 18.sp)
            }
        }
        Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("NIVEL ACTUAL", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp); Text("AHORA", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Box(Modifier.size(226.dp), contentAlignment = Alignment.Center) {
                    val progress = (db / 100f).coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxSize()) {
                        drawArc(Color(0xFFE9EEE9), 135f, 270f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(14.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(if (db > 60) Color(0xFFCF8050) else Green, 135f, 270f * progress, false, style = androidx.compose.ui.graphics.drawscope.Stroke(14.dp.toPx(), cap = StrokeCap.Round))
                        val angle = Math.toRadians((135 + 270 * progress).toDouble())
                        val center = Offset(size.width / 2, size.height / 2); val radius = size.minDimension / 2 - 8.dp.toPx()
                        drawCircle(if (db > 60) Color(0xFFCF8050) else Green, 7.dp.toPx(), Offset(center.x + kotlin.math.cos(angle).toFloat() * radius, center.y + kotlin.math.sin(angle).toFloat() * radius))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(db.toInt().toString(), fontSize = 62.sp, lineHeight = 66.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface); Text("decibeles", color = Muted, fontSize = 14.sp); Spacer(Modifier.height(12.dp)); Surface(shape = RoundedCornerShape(50), color = if (db <= 45) Mint else Color(0xFFFFE8D7)) { Text(if (db <= 45) "Tranquilo" else "Mantén la calma", Modifier.padding(horizontal = 13.dp, vertical = 6.dp), color = if (db <= 45) Green else Color(0xFF99592C), fontSize = 12.sp, fontWeight = FontWeight.Medium) } }
                }
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Actividad reciente", fontWeight = FontWeight.SemiBold); Text("Últimos minutos", color = Muted, fontSize = 11.sp) }
                    Spacer(Modifier.height(8.dp)); MiniChart(readings, Modifier.fillMaxWidth().height(58.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("hace 8 min", color = Muted, fontSize = 10.sp); Text("ahora", color = Muted, fontSize = 10.sp) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCard("PROMEDIO", "${readings.average().toInt()} dB", Modifier.weight(1f))
            Spacer(Modifier.width(12.dp)); StatCard("MÁXIMO", "${readings.maxOrNull()?.toInt() ?: 0} dB", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) { Column(Modifier.padding(16.dp)) { Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(5.dp)); Text(value, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Green) } }
}

@Composable
private fun MiniChart(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = (values.minOrNull() ?: 0f).coerceAtMost(30f); val max = (values.maxOrNull() ?: 100f).coerceAtLeast(70f)
        val points = values.mapIndexed { index, value -> Offset(size.width * index / (values.size - 1), size.height - ((value - min) / (max - min)) * size.height) }
        for (index in 0 until points.lastIndex) drawLine(Green, points[index], points[index + 1], 3.dp.toPx(), cap = StrokeCap.Round)
        points.lastOrNull()?.let { drawCircle(Green, 5.dp.toPx(), it) }
    }
}

@Composable
private fun HistoryScreen(readings: List<Float>) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(22.dp)); Text("Historial", fontSize = 28.sp, fontWeight = FontWeight.SemiBold); Text("Seguimiento del ambiente", color = Muted)
        Spacer(Modifier.height(20.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(18.dp)) { Text("HOY · ÚLTIMAS LECTURAS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(14.dp)); MiniChart(readings, Modifier.fillMaxWidth().height(130.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("08:00", color = Muted, fontSize = 11.sp); Text("12:00", color = Muted, fontSize = 11.sp); Text("Ahora", color = Muted, fontSize = 11.sp) } } }
        Spacer(Modifier.height(22.dp)); Text("Actividad reciente", fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(readings.reversed().mapIndexed { i, v -> i to v }) { (i, value) ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(if (value <= 45) Mint else Color(0xFFFFE8D7), CircleShape), contentAlignment = Alignment.Center) { Text("⌁", color = Green, fontSize = 19.sp) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(if (value <= 45) "Ambiente tranquilo" else "Nivel elevado", fontWeight = FontWeight.Medium); Text(if (i == 0) "Ahora" else "Hace ${i * 2} min", color = Muted, fontSize = 12.sp) }
                    Text("${value.toInt()} dB", fontWeight = FontWeight.SemiBold, color = Green)
                } }
            }
        }
    }
}

@Composable
private fun ProfileScreen(dark: Boolean, alerts: Boolean, onDark: (Boolean) -> Unit, onAlerts: (Boolean) -> Unit, onSettings: () -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(22.dp)); Text("Perfil", fontSize = 28.sp, fontWeight = FontWeight.SemiBold); Text("Administración del espacio", color = Muted)
        Spacer(Modifier.height(24.dp))
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(58.dp), shape = CircleShape, color = Mint) { Box(contentAlignment = Alignment.Center) { Text("K", color = Green, fontWeight = FontWeight.Bold, fontSize = 24.sp) } }
            Column(Modifier.padding(start = 14.dp)) { Text("Bibliotecario", fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text("Administrador de Kyoto", color = Muted, fontSize = 13.sp) }
        } }
        Spacer(Modifier.height(24.dp)); Text("PREFERENCIAS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp); Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) { Column {
            PreferenceRow("☾", "Modo oscuro", "Apariencia de la aplicación", dark, onDark)
            HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            PreferenceRow("♧", "Alertas de ruido", "Avisos cuando suba el nivel", alerts, onAlerts)
            HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { Text("⚙", color = Green, fontSize = 20.sp); Column(Modifier.weight(1f).padding(start = 14.dp)) { Text("Ajustes", fontWeight = FontWeight.Medium); Text("Dispositivo y cuenta", color = Muted, fontSize = 12.sp) }; TextButton(onClick = onSettings) { Text("Abrir") } }
        } }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAD5144))) { Text("↪   Cerrar sesión", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp)); Text("Kyoto · versión 1.0", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted, fontSize = 11.sp); Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun PreferenceRow(glyph: String, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(glyph, color = Green, fontSize = 19.sp); Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, color = Muted, fontSize = 12.sp) }; Switch(checked, onCheckedChange = onChecked) }
}
