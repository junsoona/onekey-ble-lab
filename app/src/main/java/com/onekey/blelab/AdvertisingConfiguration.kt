package com.onekey.blelab

import java.util.Locale

internal enum class AdvertisingMode(val label: String, val intervalMs: Int) {
    LOW_POWER("절전", 1000),
    BALANCED("균형", 250),
    LOW_LATENCY("저지연", 100),
}

internal enum class TxPower(val label: String) {
    ULTRA_LOW("매우 낮음"),
    LOW("낮음"),
    MEDIUM("보통"),
    HIGH("높음"),
}

internal data class AdvertisingConfiguration(
    val localName: String = "",
    val manufacturerIdHex: String = "FFFF",
    val manufacturerDataHex: String = "4F 4E 45 4B 45 59 2D 4C 41 42 01",
    val mode: AdvertisingMode = AdvertisingMode.LOW_POWER,
    val txPower: TxPower = TxPower.LOW,
    val startOnBoot: Boolean = false,
) {
    fun validate(): PacketValidation {
        val idDigits = manufacturerIdHex.trim().replaceFirst(hexPrefix, "")
        val id = idDigits.takeIf { manufacturerIdPattern.matches(it) }?.toInt(16)
        val payloadDigits = manufacturerDataHex.replace(hexSeparators, "")
        val payloadError = when {
            payloadDigits.isEmpty() -> "Manufacturer Data를 한 바이트 이상 입력하세요."
            !hexPattern.matches(payloadDigits) -> "HEX 숫자와 공백, 콜론, 하이픈만 사용할 수 있습니다."
            payloadDigits.length % 2 != 0 -> "HEX 두 자리마다 한 바이트를 입력하세요."
            payloadDigits.length > MAX_MANUFACTURER_PAYLOAD * 2 ->
                "Manufacturer Data는 최대 ${MAX_MANUFACTURER_PAYLOAD}바이트입니다."
            else -> null
        }
        val nameError = validateLocalName(localName)
        val idError = if (id == null) "0000–FFFF 범위의 HEX를 입력하세요." else null

        val packet = if (id != null && payloadError == null && nameError == null) {
            val payload = payloadDigits.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            LegacyPacket(localName, id, payload, mode, txPower)
        } else {
            null
        }
        return PacketValidation(packet, nameError, idError, payloadError)
    }

    companion object {
        const val LEGACY_LIMIT = 31
        const val FIELD_OVERHEAD = 2 // AD length and type.
        const val MANUFACTURER_ID_SIZE = 2
        const val MAX_MANUFACTURER_PAYLOAD = LEGACY_LIMIT - FIELD_OVERHEAD - MANUFACTURER_ID_SIZE
        const val MAX_NAME_BYTES = 26 // Android uses Shortened Local Name above 26 UTF-8 bytes.
        private val hexPrefix = Regex("^0x", RegexOption.IGNORE_CASE)
        private val manufacturerIdPattern = Regex("[0-9a-fA-F]{1,4}")
        private val hexPattern = Regex("[0-9a-fA-F]+")
        private val hexSeparators = Regex("[\\s:-]+")

        fun validateLocalName(name: String): String? = when {
            name.isEmpty() -> null
            name.isBlank() || name.any(Char::isISOControl) || !Charsets.UTF_8.newEncoder().canEncode(name) ->
                "표시 가능한 이름을 입력하세요."
            name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES ->
                "Local Name은 UTF-8 기준 최대 ${MAX_NAME_BYTES}바이트입니다."
            else -> null
        }
    }
}

internal data class PacketValidation(
    val packet: LegacyPacket?,
    val nameError: String?,
    val idError: String?,
    val payloadError: String?,
)

internal class LegacyPacket(
    val localName: String,
    val manufacturerId: Int,
    val manufacturerData: ByteArray,
    val mode: AdvertisingMode,
    val txPower: TxPower,
) {
    val advertisingBytes: Int
        get() = AdvertisingConfiguration.FIELD_OVERHEAD +
            AdvertisingConfiguration.MANUFACTURER_ID_SIZE + manufacturerData.size

    val scanResponseBytes: Int
        get() = if (localName.isEmpty()) 0 else {
            AdvertisingConfiguration.FIELD_OVERHEAD + localName.toByteArray(Charsets.UTF_8).size
        }

    val manufacturerLabel: String
        get() = String.format(Locale.ROOT, "0x%04X", manufacturerId)

    val payloadLabel: String
        get() = manufacturerData.joinToString(" ") { String.format(Locale.ROOT, "%02X", it) }

    fun summary(): String = "Legacy · ${mode.label} ~${mode.intervalMs} ms · TX ${txPower.label}\n" +
        "ADV ${advertisingBytes}/31 B · ${manufacturerLabel} · ${payloadLabel}\n" +
        "SCAN_RSP ${scanResponseBytes}/31 B · ${localName.ifEmpty { "이름 생략" }}"
}
