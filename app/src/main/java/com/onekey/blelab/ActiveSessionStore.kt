package com.onekey.blelab

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

@SuppressLint("UseKtx")
internal object ActiveSessionStore {
    private const val PREFERENCES = "active_broadcast_session"
    private const val ACTIVE = "active"
    private const val LOCAL_NAME = "local_name"
    private const val MANUFACTURER_ID = "manufacturer_id"
    private const val MANUFACTURER_DATA = "manufacturer_data"
    private const val MODE = "mode"
    private const val TX_POWER = "tx_power"

    fun save(context: Context, configuration: AdvertisingConfiguration): Boolean {
        val editor = preferences(context).edit()
        editor.putBoolean(ACTIVE, true)
        editor.putString(LOCAL_NAME, configuration.localName)
        editor.putString(MANUFACTURER_ID, configuration.manufacturerIdHex)
        editor.putString(MANUFACTURER_DATA, configuration.manufacturerDataHex)
        editor.putString(MODE, configuration.mode.name)
        editor.putString(TX_POWER, configuration.txPower.name)
        return editor.commit()
    }

    fun load(context: Context): AdvertisingConfiguration? {
        val stored = preferences(context)
        if (!stored.getBoolean(ACTIVE, false)) return null
        val configuration = try {
            AdvertisingConfiguration(
                localName = requireNotNull(stored.getString(LOCAL_NAME, null)),
                manufacturerIdHex = requireNotNull(stored.getString(MANUFACTURER_ID, null)),
                manufacturerDataHex = requireNotNull(stored.getString(MANUFACTURER_DATA, null)),
                mode = AdvertisingMode.valueOf(requireNotNull(stored.getString(MODE, null))),
                txPower = TxPower.valueOf(requireNotNull(stored.getString(TX_POWER, null))),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
        if (configuration?.validate()?.packet == null) {
            clear(context)
            return null
        }
        return configuration
    }

    fun clear(context: Context): Boolean {
        return preferences(context).edit().clear().commit()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
