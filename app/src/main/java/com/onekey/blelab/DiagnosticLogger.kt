package com.onekey.blelab

import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class DiagnosticEntry(val time: Instant, val message: String) {
    fun format(): String = "[${formatTime(time)}] $message"
}

internal fun formatTime(time: Instant?): String = time?.atZone(ZoneId.systemDefault())
    ?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS XXX")) ?: "—"

internal object DiagnosticLogger {
    private const val TAG = "OneKeyBleLab"
    private const val MAX_ENTRIES = 100
    private val mutableEntries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val entries = mutableEntries.asStateFlow()

    fun record(message: String, error: Throwable? = null) {
        val entry = DiagnosticEntry(Instant.now(), message)
        mutableEntries.update { (it + entry).takeLast(MAX_ENTRIES) }
        if (error == null) Log.i(TAG, message) else Log.w(TAG, message, error)
    }
}

internal enum class Phase(val label: String) {
    IDLE("대기"),
    STARTING("시작 중"),
    TRANSMITTING("송신 중"),
    WAITING_FOR_BLUETOOTH("Bluetooth 재활성화 대기"),
    STOPPING("중지 중"),
}

internal data class SessionState(
    val phase: Phase = Phase.IDLE,
    val foreground: Boolean = false,
    val lastError: String? = null,
    val lastStartResult: String? = null,
    val startedAt: Instant? = null,
    val stoppedAt: Instant? = null,
    val summary: String? = null,
)

internal object LabRuntime {
    private val mutableState = MutableStateFlow(SessionState())
    val state = mutableState.asStateFlow()

    fun update(transform: (SessionState) -> SessionState) = mutableState.update(transform)

    fun error(message: String, cause: Throwable? = null) {
        update { it.copy(lastError = message) }
        DiagnosticLogger.record(message, cause)
    }
}
