package com.example.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionService : Service() {
    private val TAG = "AURASH_SessionService"
    private val CHANNEL_ID = "aurash_connection_channel"
    private val NOTIFICATION_ID = 2026

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val binder = LocalBinder()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _commandOutput = MutableStateFlow<String>("")
    val commandOutput: StateFlow<String> = _commandOutput.asStateFlow()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    inner class LocalBinder : Binder() {
        fun getService(): SessionService = this@SessionService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SessionService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getStringExtra("EXTRA_HOST") ?: "127.0.0.1"
        val port = intent?.getIntExtra("EXTRA_PORT", -1) ?: -1
        val isPairing = intent?.getBooleanExtra("EXTRA_PAIRING", false) ?: false

        if (port != -1) {
            if (isPairing) {
                val code = intent?.getStringExtra("EXTRA_CODE") ?: ""
                pairAndInit(host, port, code)
            } else {
                connectToAdb(host, port)
            }
        }

        // Keep service alive running in foreground
        startForeground(NOTIFICATION_ID, createNotification("AURASH Service Active", "Awaiting system commands..."))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun connectToAdb(host: String, port: Int) {
        serviceScope.launch {
            _connectionState.value = ConnectionState.Connecting
            updateNotification("Connecting...", "Attempting handshake with ADB at $host:$port")

            if (JniBridge.isNativeLoaded) {
                // Execute using the Rust-backed session via JniBridge
                val sessionId = JniBridge.initSession(host, port, true)
                if (sessionId != 0L) {
                    _connectionState.value = ConnectionState.Connected
                    updateNotification("AURASH Active (Native Engine)", "Connected via Rust to ADB daemon")
                    streamRustCommand(sessionId, "") // Warm up connection
                } else {
                    _connectionState.value = ConnectionState.Error("Native JNI initialization failed")
                    updateNotification("Connection Failed", "Unable to establish local native session")
                }
            } else {
                _connectionState.value = ConnectionState.Error("Native Engine Missing. Execution disabled.")
                updateNotification("Connection Failed", "Rust core is mandatory but missing.")
            }
        }
    }

    private fun pairAndInit(host: String, port: Int, code: String) {
        serviceScope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting
            updateNotification("Pairing...", "Performing handshake on port $port")

            val resultMsg = if (JniBridge.isNativeLoaded) {
                JniBridge.pairDevice(host, port, code)
            } else {
                "Error: Native Engine Missing. Pairing disabled."
            }

            launch(Dispatchers.Main) {
                _commandOutput.value = _commandOutput.value + ">> System Message: $resultMsg\n"
                _connectionState.value = ConnectionState.Disconnected
                updateNotification("Pairing Process Ended", resultMsg)
            }
        }
    }

    fun submitCommand(command: String, safeMode: Boolean = true) {
        serviceScope.launch {
            _commandOutput.value = _commandOutput.value + "\n$ $command\n"

            val preAnalysis = com.example.adb.engine.CommandExecutionEngine.analyzePreExecution(command, this@SessionService)
            _commandOutput.value = _commandOutput.value + com.example.adb.engine.CommandExecutionEngine.formatPreExecutionAnsi(preAnalysis)

            if (JniBridge.isNativeLoaded) {
                // Route through high-speed JNI stream executor
                val sessionId = 1L // Base native reference ID
                streamRustCommand(sessionId, command, safeMode)
            } else {
                _commandOutput.value = _commandOutput.value + "System Error: Native Engine Missing. Execution disabled.\n"
            }
        }
    }

    private fun streamRustCommand(sessionId: Long, command: String, safeMode: Boolean = true) {
        serviceScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val token = JniBridge.executeCommand(sessionId, command, safeMode)
            val outputBuilder = java.lang.StringBuilder()
            if (token != 0L) {
                var polling = true
                while (polling) {
                    val rawOutput = JniBridge.readStream(token)
                    if (rawOutput.isNotEmpty()) {
                        outputBuilder.append(rawOutput)
                        launch(Dispatchers.Main) {
                            _commandOutput.value = _commandOutput.value + rawOutput
                        }
                    } else {
                        polling = false
                    }
                    kotlinx.coroutines.delay(100)
                }

                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                val finalOutput = outputBuilder.toString()

                val postAnalysis = com.example.adb.engine.CommandExecutionEngine.analyzePostExecution(command, finalOutput, 0, duration)
                val postAnsi = com.example.adb.engine.CommandExecutionEngine.formatPostExecutionAnsi(postAnalysis)
                launch(Dispatchers.Main) {
                    _commandOutput.value = _commandOutput.value + postAnsi
                }
            } else {
                launch(Dispatchers.Main) {
                    _commandOutput.value = _commandOutput.value + "Native execution token expired or command blocked.\n"
                }
            }
        }
    }

    fun clearLogs() {
        _commandOutput.value = ""
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.Disconnected
        updateNotification("AURASH Disconnected", "Shell session terminated safely")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.i(TAG, "SessionService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "AURASH Session persistent status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(title, text))
    }
}
