package com.onekey.blelab

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.Looper

internal class BleAdvertiserManager(context: Context) {
    private val permissions = PermissionManager(context)
    private val main = Handler(Looper.getMainLooper())
    private var attempt: Attempt? = null

    private class Attempt(
        val advertiser: BluetoothLeAdvertiser,
        val callback: AdvertiseCallback,
        val timeout: Runnable,
    )

    // Called on the main thread from the service. Platform callbacks arrive on the main looper.
    @SuppressLint("MissingPermission")
    fun start(packet: LegacyPacket, onStarted: () -> Unit, onError: (String, Throwable?) -> Unit) {
        check(attempt == null)
        try {
            val status = permissions.snapshot()
            if (status.capability != Capability.SUPPORTED) {
                onError("Bluetooth가 켜져 있고 BLE 광고가 지원되는지 확인하세요.", null)
                return
            }
            if (packet.localName.isNotEmpty() && packet.localName != status.name) {
                onError("Local Name이 기기 Bluetooth 이름과 다릅니다. 송신 화면에서 기기 이름을 적용하세요.", null)
                return
            }
            val advertiser = permissions.adapter?.bluetoothLeAdvertiser
                ?: error("BLE Advertiser를 사용할 수 없습니다.")
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(packet.mode.platformValue())
                .setTxPowerLevel(packet.txPower.platformValue())
                .setConnectable(false)
                .setTimeout(0)
                .build()
            val data = AdvertiseData.Builder()
                .addManufacturerData(packet.manufacturerId, packet.manufacturerData)
                .build()
            val scanResponse = packet.localName.takeIf { it.isNotEmpty() }?.let {
                AdvertiseData.Builder().setIncludeDeviceName(true).build()
            }
            val timeout = Runnable {
                stop()
                onError("START_TIMEOUT · 12초 안에 Android 시작 콜백을 받지 못했습니다.", null)
            }
            val callback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    val current = attempt?.takeIf { it.callback === this } ?: return
                    main.removeCallbacks(current.timeout)
                    onStarted()
                }

                override fun onStartFailure(errorCode: Int) {
                    if (attempt?.callback !== this) return
                    stop()
                    onError(describeAdvertiseError(errorCode), null)
                }
            }
            attempt = Attempt(advertiser, callback, timeout)
            main.postDelayed(timeout, START_TIMEOUT_MS)
            advertiser.startAdvertising(settings, data, scanResponse, callback)
        } catch (error: RuntimeException) {
            stop()
            onError("START_EXCEPTION · ${error.javaClass.simpleName} · ${error.message.orEmpty()}", error)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(): Boolean {
        val current = attempt ?: return true
        attempt = null
        main.removeCallbacks(current.timeout)
        return try {
            current.advertiser.stopAdvertising(current.callback)
            true
        } catch (error: RuntimeException) {
            LabRuntime.error("STOP_EXCEPTION · Android 중지 요청 실패 · ${error.javaClass.simpleName}", error)
            false
        }
    }

    private fun AdvertisingMode.platformValue() = when (this) {
        AdvertisingMode.LOW_POWER -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
        AdvertisingMode.BALANCED -> AdvertiseSettings.ADVERTISE_MODE_BALANCED
        AdvertisingMode.LOW_LATENCY -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
    }

    private fun TxPower.platformValue() = when (this) {
        TxPower.ULTRA_LOW -> AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW
        TxPower.LOW -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW
        TxPower.MEDIUM -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
        TxPower.HIGH -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
    }

    private companion object {
        const val START_TIMEOUT_MS = 12_000L
    }
}

internal fun describeAdvertiseError(code: Int): String {
    val explanation = when (code) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE · ADV 또는 Scan Response 길이 초과"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS · 기기의 광고 슬롯 부족"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED · 동일 콜백으로 이미 시작됨"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR · Bluetooth 스택 내부 오류"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED · 기기가 광고 기능을 지원하지 않음"
        else -> "UNKNOWN · Android가 반환한 알 수 없는 광고 오류"
    }
    return "Android 오류 $code · $explanation"
}
