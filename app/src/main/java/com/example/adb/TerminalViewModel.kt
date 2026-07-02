package com.example.adb

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    private val database = HistoryDatabase.getDatabase(application)
    private val dao = database.commandHistoryDao()

    private var sessionService: SessionService? = null
    private var isBound = false

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _safeMode = MutableStateFlow(true)
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()

    private val _host = MutableStateFlow("127.0.0.1")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _port = MutableStateFlow("5555")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _pairingCode = MutableStateFlow("")
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    // Command suggestions derived from historical entries
    val autocompleteSuggestions = dao.getAllHistory()
        .map { list -> list.map { it.command }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All historic entries for chronological up/down traversal
    val fullHistory = dao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Terminal outputs aggregated from active service
    private val _terminalOutput = MutableStateFlow("Android ADB Wireless Interface\nWaiting for connection...\n")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    private val _connectionState = MutableStateFlow<SessionService.ConnectionState>(SessionService.ConnectionState.Disconnected)
    val connectionState: StateFlow<SessionService.ConnectionState> = _connectionState.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SessionService.LocalBinder
            val boundService = binder.getService()
            sessionService = boundService
            isBound = true
            _serviceConnected.value = true

            // Gather realtime data streams
            viewModelScope.launch {
                boundService.connectionState.collectLatest { state ->
                    _connectionState.value = state
                }
            }

            viewModelScope.launch {
                boundService.commandOutput.collectLatest { output ->
                    if (output.isNotEmpty()) {
                        _terminalOutput.value = output
                    }
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            sessionService = null
            isBound = false
            _serviceConnected.value = false
            _connectionState.value = SessionService.ConnectionState.Disconnected
        }
    }

    fun setHost(newHost: String) {
        _host.value = newHost
    }

    fun setPort(newPort: String) {
        _port.value = newPort
    }

    fun setPairingCode(code: String) {
        _pairingCode.value = code
    }

    fun toggleSafeMode(enabled: Boolean) {
        _safeMode.value = enabled
    }

    fun connectDevice(context: Context, isPairing: Boolean = false) {
        val intent = Intent(context, SessionService::class.java).apply {
            putExtra("EXTRA_HOST", _host.value)
            putExtra("EXTRA_PORT", _port.value.toIntOrNull() ?: 5555)
            putExtra("EXTRA_PAIRING", isPairing)
            if (isPairing) {
                putExtra("EXTRA_CODE", _pairingCode.value)
            }
        }

        context.startForegroundService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun execute(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        // Store command cache inside database asynchronously
        viewModelScope.launch {
            dao.insertCommand(CommandHistory(command = trimmed))
        }

        if (trimmed == "clear") {
            sessionService?.clearLogs()
            _terminalOutput.value = "$ \n"
            return
        }

        if (trimmed == "help") {
            _terminalOutput.value = _terminalOutput.value + "\n" +
                    ">> AURASH System Help Guide <<\n" +
                    "clear           - Clear the terminal logs buffer\n" +
                    "help            - Show this command reference menu\n" +
                    "pm list packages- View installed applications on device\n" +
                    "getprop         - Check build characteristics and settings\n" +
                    "dumpsys battery - Check current charging status & telemetry\n"
            return
        }

        sessionService?.submitCommand(trimmed, _safeMode.value)
    }

    fun disconnectService(context: Context) {
        if (isBound) {
            context.unbindService(connection)
            isBound = false
            _serviceConnected.value = false
        }
        sessionService?.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        // Unbind gracefully
        if (isBound) {
            getApplication<Application>().unbindService(connection)
        }
    }
}
