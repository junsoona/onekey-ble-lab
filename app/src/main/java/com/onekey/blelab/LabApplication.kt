package com.onekey.blelab

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo

internal class LabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val lastExit = getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(packageName, 0, 1)
            .firstOrNull()
        if (lastExit?.reason == ApplicationExitInfo.REASON_USER_REQUESTED) {
            if (!ActiveSessionStore.clear(this)) {
                DiagnosticLogger.record("사용자 중지 뒤 송신 복구 설정을 지우지 못했습니다.")
            }
            DiagnosticLogger.record("사용자가 Android 활성 앱 목록에서 앱을 중지했습니다 · 송신 자동 복구 해제")
        }
    }
}
