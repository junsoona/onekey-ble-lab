package com.onekey.blelab

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.settingsStore by preferencesDataStore("advertising_settings")

internal class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    suspend fun load(): AdvertisingConfiguration {
        val saved = store.data.first()
        val defaults = AdvertisingConfiguration()
        return AdvertisingConfiguration(
            localName = saved[localName] ?: defaults.localName,
            manufacturerIdHex = saved[manufacturerId] ?: defaults.manufacturerIdHex,
            manufacturerDataHex = saved[manufacturerData] ?: defaults.manufacturerDataHex,
            mode = AdvertisingMode.entries.firstOrNull { it.name == saved[mode] } ?: defaults.mode,
            txPower = TxPower.entries.firstOrNull { it.name == saved[txPower] } ?: defaults.txPower,
            startOnBoot = saved[startOnBoot] ?: defaults.startOnBoot,
        )
    }

    suspend fun save(configuration: AdvertisingConfiguration) {
        store.edit {
            it[localName] = configuration.localName
            it[manufacturerId] = configuration.manufacturerIdHex
            it[manufacturerData] = configuration.manufacturerDataHex
            it[mode] = configuration.mode.name
            it[txPower] = configuration.txPower.name
            it[startOnBoot] = configuration.startOnBoot
        }
    }

    private companion object {
        val localName = stringPreferencesKey("local_name")
        val manufacturerId = stringPreferencesKey("manufacturer_id")
        val manufacturerData = stringPreferencesKey("manufacturer_data")
        val mode = stringPreferencesKey("mode")
        val txPower = stringPreferencesKey("tx_power")
        val startOnBoot = booleanPreferencesKey("start_on_boot")
    }
}
