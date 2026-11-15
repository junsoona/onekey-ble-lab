package com.onekey.blelab

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal enum class Capability(val label: String) {
    SUPPORTED("지원"),
    UNSUPPORTED("미지원"),
    UNKNOWN("Bluetooth ON 및 권한 확인 후 판별"),
}

internal data class BluetoothStatus(
    val state: Int?,
    val capability: Capability,
    val name: String?,
    val hasPermissions: Boolean,
    val notificationsEnabled: Boolean,
) {
    val label: String
        get() = when (state) {
            BluetoothAdapter.STATE_ON -> "켜짐"
            BluetoothAdapter.STATE_OFF -> "꺼짐"
            BluetoothAdapter.STATE_TURNING_ON -> "켜는 중"
            BluetoothAdapter.STATE_TURNING_OFF -> "끄는 중"
            else -> if (!hasPermissions) "주변 기기 권한 필요" else "확인할 수 없음"
        }
}

internal class PermissionManager(private val context: Context) {
    private val required = listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
    val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun missingBluetoothPermissions(): List<String> =
        required.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    fun canRequestNotifications(): Boolean = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun snapshot(): BluetoothStatus {
        val permitted = missingBluetoothPermissions().isEmpty()
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val bluetooth = adapter
        if (bluetooth == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return BluetoothStatus(null, Capability.UNSUPPORTED, null, permitted, notifications)
        }
        if (!permitted) return BluetoothStatus(null, Capability.UNKNOWN, null, false, notifications)
        return try {
            val state = bluetooth.state
            if (state != BluetoothAdapter.STATE_ON) {
                BluetoothStatus(state, Capability.UNKNOWN, null, true, notifications)
            } else {
                val supported = bluetooth.isMultipleAdvertisementSupported && bluetooth.bluetoothLeAdvertiser != null
                BluetoothStatus(
                    state, if (supported) Capability.SUPPORTED else Capability.UNSUPPORTED,
                    bluetooth.name, true, notifications,
                )
            }
        } catch (_: SecurityException) {
            BluetoothStatus(null, Capability.UNKNOWN, null, false, notifications)
        }
    }

    @SuppressLint("MissingPermission")
    fun applyDeviceName(name: String): Boolean {
        require(name.isNotEmpty() && AdvertisingConfiguration.validateLocalName(name) == null)
        return missingBluetoothPermissions().isEmpty() && adapter?.setName(name) == true
    }

    companion object {
        fun bluetoothEvents() = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)
        }
    }
}
