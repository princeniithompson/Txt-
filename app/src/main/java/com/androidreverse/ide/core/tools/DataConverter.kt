package com.androidreverse.ide.core.tools

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.CRC32

enum class ConverterMode(val label: String) {
    BASE64_ENCODE("Base64 Enc"),
    BASE64_DECODE("Base64 Dec"),
    TEXT_TO_HEX("Text to Hex"),
    HEX_TO_TEXT("Hex to Text"),
    DEC_TO_HEX("Dec to Hex"),
    HEX_TO_DEC("Hex to Dec"),
    URL_ENCODE("URL Enc"),
    URL_DECODE("URL Dec"),
    XOR_HEX("XOR Cipher")
}

data class ChecksumBundle(
    val sha256: String,
    val md5: String,
    val crc32: String
)

object DataConverter {

    fun convert(input: String, mode: ConverterMode, xorKey: String = "0x5A"): String {
        if (input.isEmpty()) return ""
        return try {
            when (mode) {
                ConverterMode.BASE64_ENCODE ->
                    Base64.encodeToString(input.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                ConverterMode.BASE64_DECODE ->
                    String(Base64.decode(input.trim(), Base64.DEFAULT), StandardCharsets.UTF_8)
                ConverterMode.TEXT_TO_HEX ->
                    input.toByteArray(StandardCharsets.UTF_8)
                        .joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
                ConverterMode.HEX_TO_TEXT -> {
                    val clean = input.replace(Regex("[^0-9a-fA-F]"), "")
                    val bytes = ByteArray(clean.length / 2) { idx ->
                        clean.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
                    }
                    String(bytes, StandardCharsets.UTF_8)
                }
                ConverterMode.DEC_TO_HEX -> {
                    val value = input.trim().toLong()
                    "0x${value.toString(16).uppercase()}"
                }
                ConverterMode.HEX_TO_DEC -> {
                    val clean = input.trim().removePrefix("0x").removePrefix("0X")
                    clean.toLong(16).toString()
                }
                ConverterMode.URL_ENCODE ->
                    URLEncoder.encode(input, StandardCharsets.UTF_8.name())
                ConverterMode.URL_DECODE ->
                    URLDecoder.decode(input, StandardCharsets.UTF_8.name())
                ConverterMode.XOR_HEX -> {
                    val keyByte = parseKeyByte(xorKey)
                    input.toByteArray(StandardCharsets.UTF_8)
                        .joinToString(" ") { "%02X".format((it.toInt() and 0xFF) xor keyByte) }
                }
            }
        } catch (e: Exception) {
            "Invalid input (${e.message ?: "format error"})"
        }
    }

    fun checksums(bytes: ByteArray): ChecksumBundle {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        val md5 = MessageDigest.getInstance("MD5").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        val crc = CRC32().apply { update(bytes) }.value
        val crcHex = crc.toString(16).uppercase().padStart(8, '0')
        return ChecksumBundle(sha256, md5, crcHex)
    }

    fun parseColorHexOrNull(input: String): Long? {
        val clean = input.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        return when (clean.length) {
            6 -> clean.toLongOrNull(16)?.let { 0xFF000000L or it }
            8 -> clean.toLongOrNull(16)
            else -> null
        }
    }

    private fun parseKeyByte(raw: String): Int {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0x5A
        return if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2).toIntOrNull(16)?.and(0xFF) ?: 0x5A
        } else {
            trimmed.toIntOrNull()?.and(0xFF) ?: (trimmed.first().code and 0xFF)
        }
    }
}
