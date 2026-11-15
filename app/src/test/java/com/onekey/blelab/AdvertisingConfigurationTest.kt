package com.onekey.blelab

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvertisingConfigurationTest {
    @Test
    fun defaultPacketUsesTestBytesWithoutName() {
        val packet = requireNotNull(AdvertisingConfiguration().validate().packet)
        assertEquals(0xFFFF, packet.manufacturerId)
        assertArrayEquals("ONEKEY-LAB".toByteArray() + 1.toByte(), packet.manufacturerData)
        assertEquals(15, packet.advertisingBytes)
        assertEquals(0, packet.scanResponseBytes)
        assertEquals(1000, packet.mode.intervalMs)
    }

    @Test
    fun parsesIdSeparatelyAndAcceptsMixedCaseAndSeparators() {
        val packet = requireNotNull(
            AdvertisingConfiguration(
                manufacturerIdHex = " 0X0059 ",
                manufacturerDataHex = "00:Ff-12\n7e",
            ).validate().packet,
        )
        assertEquals(0x0059, packet.manufacturerId)
        assertArrayEquals(byteArrayOf(0, -1, 0x12, 0x7e), packet.manufacturerData)
        assertEquals("0x0059", packet.manufacturerLabel)
        assertEquals("00 FF 12 7E", packet.payloadLabel)
    }

    @Test
    fun acceptsManufacturerIdBounds() {
        for (id in listOf("0", "FFFF")) {
            assertNotNull(AdvertisingConfiguration(manufacturerIdHex = id).validate().packet)
        }
    }

    @Test
    fun rejectsInvalidManufacturerIds() {
        for (id in listOf("", "-1", "10000", "0x", "GG", "00 59", "ＦＦ")) {
            val result = AdvertisingConfiguration(manufacturerIdHex = id).validate()
            assertNull(id, result.packet)
            assertNotNull(id, result.idError)
        }
    }

    @Test
    fun rejectsMalformedAndEmptyPayloads() {
        for (payload in listOf("", " : - ", "0", "02 5", "GG", "0x42", "42,00", "ＦＦ")) {
            val result = AdvertisingConfiguration(manufacturerDataHex = payload).validate()
            assertNull(payload, result.packet)
            assertNotNull(payload, result.payloadError)
        }
    }

    @Test
    fun enforcesIndependentAdvertisingAndScanResponseBudgets() {
        val configuration = AdvertisingConfiguration(
            localName = "X".repeat(26),
            manufacturerDataHex = "AB".repeat(27),
        )
        val packet = requireNotNull(configuration.validate().packet)
        assertEquals(31, packet.advertisingBytes)
        assertEquals(28, packet.scanResponseBytes)
        val oversized = configuration.copy(manufacturerDataHex = "AB".repeat(28)).validate()
        assertNull(oversized.packet)
        assertTrue(requireNotNull(oversized.payloadError).contains("27"))
    }

    @Test
    fun countsLocalNameInUtf8AndAvoidsAndroidShortenedName() {
        val name = "가".repeat(8) + "AB"
        val packet = requireNotNull(AdvertisingConfiguration(localName = name).validate().packet)
        assertEquals(28, packet.scanResponseBytes)
        assertNull(AdvertisingConfiguration(localName = name + "C").validate().packet)
        assertNull(AdvertisingConfiguration(localName = "X".repeat(27)).validate().packet)
    }

    @Test
    fun rejectsControlCharactersAndMalformedUnicodeInLocalName() {
        for (name in listOf("   ", "One\nKey", "X\u0000Y", "\uD800")) {
            assertNotNull(AdvertisingConfiguration(localName = name).validate().nameError)
        }
    }

    @Test
    fun preservesModeAndPowerInValidatedPacket() {
        val packet = requireNotNull(
            AdvertisingConfiguration(mode = AdvertisingMode.BALANCED, txPower = TxPower.HIGH).validate().packet,
        )
        assertEquals(250, packet.mode.intervalMs)
        assertEquals(TxPower.HIGH, packet.txPower)
        assertTrue(packet.summary().contains("ADV 15/31 B"))
    }
}
