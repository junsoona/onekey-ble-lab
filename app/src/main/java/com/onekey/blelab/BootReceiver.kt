package com.onekey.blelab

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                val saved = withTimeoutOrNull(4_000) { SettingsRepository(app).load() }
                if (saved == null) {
                    LabRuntime.error("BOOT_LOAD_TIMEOUT · 저장 설정을 읽지 못했습니다.")
                    return@launch
                }
                if (!saved.startOnBoot) return@launch
                val status = PermissionManager(app).snapshot()
                when {
                    saved.validate().packet == null ->
                        LabRuntime.error("BOOT_CONFIGURATION · 저장된 광고 설정이 유효하지 않습니다.")
                    !status.hasPermissions ->
                        LabRuntime.error("BOOT_PERMISSION · 주변 기기 권한이 없어 자동 시작이 건너뛰어졌습니다.")
                    status.state != BluetoothAdapter.STATE_ON && status.state != BluetoothAdapter.STATE_TURNING_ON ->
                        LabRuntime.error("BOOT_BLUETOOTH_OFF · 앱을 열어 수동으로 시작하세요.")
                    else -> BleForegroundService.requestStart(app, saved, "부팅 완료")
                }
            } catch (error: IOException) {
                LabRuntime.error("BOOT_STORAGE · 저장된 설정을 읽지 못했습니다.", error)
            } finally {
                pending.finish()
            }
        }
    }
}
