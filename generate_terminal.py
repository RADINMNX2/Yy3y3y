with open('app/src/main/java/com/example/ui/TerminalScreen.kt', 'w') as f:
    f.write("""package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.adb.SessionService
import com.example.adb.TerminalViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.ExperimentalComposeUiApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TerminalScreen(viewModel: TerminalViewModel = viewModel()) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val terminalOutput by viewModel.terminalOutput.collectAsState()
    
    val safeMode by viewModel.safeMode.collectAsState()
    var performanceMode by remember { mutableStateOf(false) }
    var autoReconnect by remember { mutableStateOf(false) }
    var streamBuffer by remember { mutableStateOf(4096f) }
    
    val isConnected = connectionState is SessionService.ConnectionState.Connected
    
    var showConnectionModal by remember { mutableStateOf(false) }
    var showSettingsModal by remember { mutableStateOf(false) }
    
    val terminalLines = terminalOutput.split("\n")
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(terminalOutput) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    Scaffold(
        containerColor = Color(0xFF050505),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isConnected) {
                        viewModel.disconnectService(context)
                    } else {
                        showConnectionModal = true
                    }
                },
                containerColor = if (isConnected) Color(0xFF8B0000) else Color(0xFFFF2E2E),
                contentColor = Color.White
            ) {
                Icon(if (isConnected) Icons.Default.Close else Icons.Default.Link, "Connect")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "AURASH",
                        color = Color(0xFFFF2E2E),
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        if (isConnected) "CONNECTED" else "DISCONNECTED",
                        color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF2E2E).copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                IconButton(onClick = { showSettingsModal = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
            
            // Terminal
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF050505))
                    .padding(8.dp)
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(terminalLines) { line ->
                        Text(
                            text = line,
                            color = Color(0xFFE6E1E5),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
            
            // Input
            var command by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0A0A))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ">",
                    color = Color(0xFFFF2E2E),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0A0A0A),
                        unfocusedContainerColor = Color(0xFF0A0A0A),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    placeholder = { Text("Enter command...", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (command.isNotBlank()) {
                            viewModel.execute(command)
                            command = ""
                        }
                    })
                )
            }
        }
    }
    
    if (showConnectionModal) {
        ConnectionModal(
            viewModel = viewModel,
            onDismiss = { showConnectionModal = false }
        )
    }
    
    if (showSettingsModal) {
        SettingsModal(
            safeMode = safeMode,
            onSafeModeChange = { viewModel.toggleSafeMode(it) },
            performanceMode = performanceMode,
            onPerformanceModeChange = { performanceMode = it },
            autoReconnect = autoReconnect,
            onAutoReconnectChange = { autoReconnect = it },
            streamBuffer = streamBuffer,
            onStreamBufferChange = { streamBuffer = it },
            onDismiss = { showSettingsModal = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionModal(viewModel: TerminalViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val host by viewModel.host.collectAsState()
    val port by viewModel.port.collectAsState()
    val pairingCode by viewModel.pairingCode.collectAsState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "SECURE ADB HANDSHAKE",
                color = Color(0xFFFF2E2E),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Network discovery mock representation (since actual mDNS requires a lot of native network code)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Target Interface", color = Color.Gray, fontSize = 12.sp)
                        Text("$host:$port", color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { Toast.makeText(context, "Scanning local subnet (Not fully implemented without root/permissions)", Toast.LENGTH_SHORT).show() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))
                    ) {
                        Text("SCAN", fontFamily = FontFamily.Monospace)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Wireless Pairing Code", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Telegram-style 6-digit input
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                for (i in 0 until 6) {
                    val char = pairingCode.getOrNull(i)?.toString() ?: ""
                    OutlinedTextField(
                        value = char,
                        onValueChange = { 
                            if (it.length <= 1 && it.all { c -> c.isDigit() }) {
                                val newCode = pairingCode.take(i) + it + pairingCode.drop(i + 1)
                                viewModel.setPairingCode(newCode.take(6))
                            }
                        },
                        modifier = Modifier
                            .width(48.dp)
                            .height(56.dp),
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontSize = 24.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF2E2E),
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        viewModel.connectDevice(context, isPairing = true)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E2E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("PAIR DEVICE", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                
                Button(
                    onClick = {
                        viewModel.connectDevice(context, isPairing = false)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CONNECT ONLY", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModal(
    safeMode: Boolean, onSafeModeChange: (Boolean) -> Unit,
    performanceMode: Boolean, onPerformanceModeChange: (Boolean) -> Unit,
    autoReconnect: Boolean, onAutoReconnectChange: (Boolean) -> Unit,
    streamBuffer: Float, onStreamBufferChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "SYSTEM SETTINGS",
                color = Color(0xFFFF2E2E),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            SettingToggle("Safe Mode", "Enables command filter in Rust core", safeMode, onSafeModeChange)
            SettingToggle("Performance Mode", "Adjusts scheduling of command queue", performanceMode, onPerformanceModeChange)
            SettingToggle("Auto Reconnect", "Reconnects real ADB session only", autoReconnect, onAutoReconnectChange)
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Stream Buffer Size: ${streamBuffer.toInt()} B", color = Color.White)
            Slider(
                value = streamBuffer,
                onValueChange = onStreamBufferChange,
                valueRange = 1024f..16384f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF2E2E),
                    activeTrackColor = Color(0xFFFF2E2E)
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingToggle(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(desc, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFF2E2E),
                checkedTrackColor = Color(0xFF8B0000)
            )
        )
    }
}
""")
