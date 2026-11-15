package com.onekey.blelab

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MainActivity : ComponentActivity() {
    private val viewModel: AdvertiserViewModel by viewModels()
    private val permissions by lazy { PermissionManager(this) }
    private val bluetooth by lazy { MutableStateFlow(permissions.snapshot()) }
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshBluetooth()
    }

    private val bluetoothRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshBluetooth()
        if (!bluetooth.value.hasPermissions) {
            DiagnosticLogger.record("주변 기기 권한이 승인되지 않았습니다.")
        }
    }

    private val notificationsRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshBluetooth()
    }

    private val exportRequest = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val content = viewModel.pendingExport
        viewModel.pendingExport = null
        if (uri != null && content != null) lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = contentResolver.openOutputStream(uri)
                        ?: throw IOException("선택한 파일을 열 수 없습니다.")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                }
                showMessage("로그를 내보냈습니다.")
            } catch (error: IOException) {
                exportFailed(error)
            } catch (error: SecurityException) {
                exportFailed(error)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val editor = viewModel.editor.collectAsStateWithLifecycle().value
            val status = bluetooth.collectAsStateWithLifecycle().value
            val session = LabRuntime.state.collectAsStateWithLifecycle().value
            val entries = DiagnosticLogger.entries.collectAsStateWithLifecycle().value
            LabScreen(
                editor = editor,
                bluetooth = status,
                session = session,
                entries = entries,
                edit = viewModel::edit,
                save = viewModel::save,
                load = { viewModel.load() },
                start = { startAdvertising(viewModel.editor.value.configuration) },
                stop = { BleForegroundService.requestStop(this) },
                requestBluetooth = { bluetoothRequest.launch(permissions.missingBluetoothPermissions().toTypedArray()) },
                openBluetooth = { openSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                openAppSettings = {
                    openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                },
                openNotificationSettings = ::requestNotifications,
                applyDeviceName = ::applyDeviceName,
                copyLogs = ::copyLogs,
                exportLogs = ::exportLogs,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, bluetoothReceiver, PermissionManager.bluetoothEvents(), ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onResume() {
        super.onResume()
        refreshBluetooth()
    }

    override fun onStop() {
        unregisterReceiver(bluetoothReceiver)
        super.onStop()
    }

    private fun refreshBluetooth() {
        bluetooth.value = permissions.snapshot()
    }

    private fun startAdvertising(configuration: AdvertisingConfiguration) {
        refreshBluetooth()
        val status = bluetooth.value
        val packet = configuration.validate().packet ?: return
        when {
            !status.hasPermissions -> showMessage("주변 기기 권한을 허용하세요.")
            status.state != BluetoothAdapter.STATE_ON -> showMessage("Bluetooth를 켜세요.")
            status.capability != Capability.SUPPORTED -> showMessage("이 기기는 BLE 광고를 지원하지 않습니다.")
            packet.localName.isNotEmpty() && status.name != packet.localName ->
                showMessage("먼저 기기 이름을 적용하세요.")
            else -> BleForegroundService.requestStart(this, configuration, "앱 화면")
        }
    }

    private fun requestNotifications() {
        if (permissions.canRequestNotifications() &&
            (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) ||
                !getPreferences(MODE_PRIVATE).getBoolean("requested_notifications", false))
        ) {
            getPreferences(MODE_PRIVATE).edit { putBoolean("requested_notifications", true) }
            notificationsRequest.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun applyDeviceName(name: String) {
        if (LabRuntime.state.value.phase != Phase.IDLE) return
        try {
            if (permissions.applyDeviceName(name)) {
                DiagnosticLogger.record("기기 Bluetooth 이름 변경 요청 · $name")
                showMessage("기기 이름 변경을 요청했습니다.")
            } else {
                LabRuntime.error("NAME_UPDATE_REJECTED · Bluetooth 설정에서 기기 이름을 확인하세요.")
                showMessage("기기 이름을 변경하지 못했습니다.")
            }
        } catch (error: RuntimeException) {
            LabRuntime.error("NAME_UPDATE_EXCEPTION · 기기 Bluetooth 이름을 변경하지 못했습니다.", error)
            showMessage("기기 이름을 변경하지 못했습니다.")
        }
        refreshBluetooth()
    }

    private fun copyLogs() {
        val text = logText()
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("OneKey BLE Lab 로그", text))
        if (Build.VERSION.SDK_INT < 33) showMessage("로그를 복사했습니다.")
    }

    private fun exportLogs() {
        viewModel.pendingExport = logText()
        exportRequest.launch("onekey-ble-lab-logs.txt")
    }

    private fun logText() = DiagnosticLogger.entries.value.joinToString("\n") { it.format() } + "\n"

    private fun exportFailed(error: Exception) {
        DiagnosticLogger.record("로그 파일을 쓰지 못했습니다.", error)
        showMessage("로그 파일을 쓰지 못했습니다.")
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (error: android.content.ActivityNotFoundException) {
            showMessage("기기 설정에서 해당 메뉴를 열어 주세요.")
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
