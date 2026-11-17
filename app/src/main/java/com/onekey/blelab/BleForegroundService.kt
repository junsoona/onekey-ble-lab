package com.onekey.blelab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.time.Instant

internal class BleForegroundService : Service() {
    private lateinit var advertiser: BleAdvertiserManager
    private lateinit var permissions: PermissionManager
    private var packet: LegacyPacket? = null
    private var listening = false
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateBluetoothState()
    }

    override fun onCreate() {
        super.onCreate()
        permissions = PermissionManager(this)
        advertiser = BleAdvertiserManager(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (!ActiveSessionStore.clear(this)) {
                DiagnosticLogger.record("송신 복구 설정을 지우지 못했습니다.")
            }
            endSession()
            return START_NOT_STICKY
        }
        if (packet != null) return START_STICKY
        if (intent != null && intent.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "BLE 광고 송신", NotificationManager.IMPORTANCE_LOW),
            )
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(Phase.STARTING),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            LabRuntime.update { it.copy(foreground = true) }
            val configuration = if (intent == null) {
                ActiveSessionStore.load(this) ?: run {
                    stopSelf()
                    return START_NOT_STICKY
                }
            } else {
                readConfiguration(intent)
            }
            packet = configuration.validate().packet
            if (packet == null) {
                fail("INVALID_CONFIGURATION · 광고 설정을 다시 확인하세요.")
                return START_NOT_STICKY
            }
            ContextCompat.registerReceiver(
                this, bluetoothReceiver, PermissionManager.bluetoothEvents(), ContextCompat.RECEIVER_EXPORTED,
            )
            listening = true
            if (intent == null) {
                LabRuntime.update {
                    SessionState(phase = Phase.STARTING, summary = packet?.summary())
                }
                DiagnosticLogger.record("시스템 서비스 재시작 · 저장한 송신 설정 복구")
            }
            updateBluetoothState()
        } catch (error: RuntimeException) {
            fail("SERVICE_EXCEPTION · ${error.javaClass.simpleName} · ${error.message.orEmpty()}", error)
        }
        return START_STICKY
    }

    private fun updateBluetoothState() {
        val config = packet ?: return
        val status = permissions.snapshot()
        if (!status.hasPermissions) {
            fail("MISSING_PERMISSION · 주변 기기 권한을 허용하세요.")
            return
        }
        if (status.state != BluetoothAdapter.STATE_ON) {
            if (status.state == null) {
                fail("BLUETOOTH_UNAVAILABLE · Bluetooth 어댑터를 사용할 수 없습니다.")
            } else {
                waitForBluetooth()
            }
            return
        }
        if (config.localName.isNotEmpty() && config.localName != status.name) {
            fail("NAME_MISMATCH · 송신 화면에서 Local Name과 기기 Bluetooth 이름을 일치시키세요.")
            return
        }
        when (LabRuntime.state.value.phase) {
            Phase.WAITING_FOR_BLUETOOTH -> {
                DiagnosticLogger.record("Bluetooth ON · 광고 복구 시작")
                setPhase(Phase.STARTING)
                startTransmission(config)
            }
            Phase.STARTING -> if (!startIssued) startTransmission(config)
            else -> Unit
        }
    }

    private var startIssued = false

    private fun startTransmission(config: LegacyPacket) {
        startIssued = true
        advertiser.start(
            config,
            onStarted = {
                LabRuntime.update {
                    it.copy(startedAt = Instant.now(), lastStartResult = "성공 · Android 시작 콜백")
                }
                setPhase(Phase.TRANSMITTING)
                DiagnosticLogger.record("광고 시작 성공 · Android 콜백")
            },
            onError = { message, error ->
                val status = permissions.snapshot()
                if (status.hasPermissions && status.state != null && status.state != BluetoothAdapter.STATE_ON) {
                    DiagnosticLogger.record(message, error)
                    waitForBluetooth()
                } else {
                    fail(message, error)
                }
            },
        )
    }

    private fun waitForBluetooth() {
        val phase = LabRuntime.state.value.phase
        if (phase == Phase.WAITING_FOR_BLUETOOTH) return
        stopTransmission()
        setPhase(Phase.WAITING_FOR_BLUETOOTH)
        DiagnosticLogger.record("Bluetooth OFF 또는 전환 중 · 재활성화 대기")
    }

    private fun fail(message: String, cause: Throwable? = null) {
        if (!ActiveSessionStore.clear(this)) {
            DiagnosticLogger.record("실패한 송신의 복구 설정을 지우지 못했습니다.")
        }
        if (LabRuntime.state.value.phase != Phase.TRANSMITTING) {
            LabRuntime.update { it.copy(lastStartResult = "실패 · $message") }
        }
        LabRuntime.error(message, cause)
        endSession()
    }

    private fun stopTransmission() {
        if (!startIssued) return
        startIssued = false
        val stopRequested = advertiser.stop()
        LabRuntime.update { it.copy(stoppedAt = Instant.now()) }
        DiagnosticLogger.record(if (stopRequested) "광고 세션 종료 요청 처리" else "광고 중지 API 호출 실패")
    }

    private fun endSession() {
        LabRuntime.update { it.copy(phase = Phase.STOPPING) }
        packet = null
        stopTransmission()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        LabRuntime.update { it.copy(foreground = false) }
        stopSelf()
    }

    override fun onDestroy() {
        if (listening) unregisterReceiver(bluetoothReceiver)
        packet = null
        stopTransmission()
        LabRuntime.update { it.copy(phase = Phase.IDLE, foreground = false) }
        DiagnosticLogger.record("Foreground Service 종료")
        super.onDestroy()
    }

    private fun setPhase(phase: Phase) {
        LabRuntime.update { it.copy(phase = phase) }
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(phase))
        } catch (error: RuntimeException) {
            DiagnosticLogger.record("송신 알림 갱신 실패", error)
        }
    }

    private fun notification(phase: Phase): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BleForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("BLE 광고 · ${phase.label}")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_notification, "광고 중지", stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ble_advertising"
        private const val NOTIFICATION_ID = 101
        private const val ACTION_START = "com.onekey.blelab.START"
        private const val ACTION_STOP = "com.onekey.blelab.STOP"

        // Activity and boot receiver both dispatch on the main thread.
        fun requestStart(context: Context, configuration: AdvertisingConfiguration, source: String) {
            if (LabRuntime.state.value.phase != Phase.IDLE) return
            val packet = configuration.validate().packet ?: return
            LabRuntime.update {
                SessionState(phase = Phase.STARTING, summary = packet.summary())
            }
            DiagnosticLogger.record("광고 시작 요청 · $source\n${packet.summary()}")
            val intent = Intent(context, BleForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra("name", configuration.localName)
                .putExtra("id", packet.manufacturerLabel)
                .putExtra("data", packet.payloadLabel)
                .putExtra("mode", configuration.mode.name)
                .putExtra("power", configuration.txPower.name)
            try {
                check(ActiveSessionStore.save(context, configuration)) {
                    "송신 복구 설정을 저장하지 못했습니다."
                }
                checkNotNull(ContextCompat.startForegroundService(context, intent))
            } catch (error: RuntimeException) {
                ActiveSessionStore.clear(context)
                val message = "FGS_START_REJECTED · ${error.javaClass.simpleName} · ${error.message.orEmpty()}"
                LabRuntime.update { it.copy(phase = Phase.IDLE, lastStartResult = "실패 · $message") }
                LabRuntime.error(message, error)
            }
        }

        fun requestStop(context: Context) {
            if (LabRuntime.state.value.phase == Phase.IDLE) return
            try {
                context.startService(Intent(context, BleForegroundService::class.java).setAction(ACTION_STOP))
            } catch (error: RuntimeException) {
                LabRuntime.error("FGS_STOP_REJECTED · 알림의 광고 중지 버튼을 사용하세요.", error)
            }
        }

        private fun readConfiguration(intent: Intent) = AdvertisingConfiguration(
            localName = intent.getStringExtra("name").orEmpty(),
            manufacturerIdHex = intent.getStringExtra("id").orEmpty(),
            manufacturerDataHex = intent.getStringExtra("data").orEmpty(),
            mode = AdvertisingMode.valueOf(requireNotNull(intent.getStringExtra("mode"))),
            txPower = TxPower.valueOf(requireNotNull(intent.getStringExtra("power"))),
        )
    }
}
