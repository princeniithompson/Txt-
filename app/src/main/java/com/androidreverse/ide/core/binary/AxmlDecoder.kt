package com.androidreverse.ide.core.binary

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin Android Binary XML (AXML) decoder supporting UTF-8 and UTF-16LE string pools,
 * namespaces, elements, and TypedValue attributes for AndroidManifest.xml and compiled resource XMLs.
 */
object AxmlDecoder {

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_TYPE = 0x0003
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val RES_XML_CDATA_TYPE = 0x0104
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180

    private const val UTF8_FLAG = 1 shl 8

    fun isBinaryXml(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val type = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
        return type == RES_XML_TYPE
    }

    fun decode(bytes: ByteArray): String {
        if (!isBinaryXml(bytes)) {
            return String(bytes, StandardCharsets.UTF_8)
        }
        return try {
            decodeInternal(bytes)
        } catch (e: Exception) {
            "<!-- AXML decode notice: ${e.message ?: "malformed chunk"} -->\n" +
                ElfParser.formatHexDump(bytes, maxBytes = 2048)
        }
    }

    private fun decodeInternal(bytes: ByteArray): String {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rootType = buf.short.toInt() and 0xFFFF
        val rootHeaderSize = buf.short.toInt() and 0xFFFF
        val rootChunkSize = buf.int
        if (rootType != RES_XML_TYPE || rootChunkSize <= 0) {
            return String(bytes, StandardCharsets.UTF_8)
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

        var strings: List<String> = emptyList()
        val namespaceMap = LinkedHashMap<String, String>()
        val pendingNamespaces = mutableListOf<Pair<String, String>>()
        var indent = 0
        var offset = rootHeaderSize

        while (offset + 8 <= bytes.size && offset < rootChunkSize) {
            buf.position(offset)
            val chunkType = buf.short.toInt() and 0xFFFF
            val headerSize = buf.short.toInt() and 0xFFFF
            val chunkSize = buf.int
            if (chunkSize <= 0 || offset + chunkSize > bytes.size) break

            when (chunkType) {
                RES_STRING_POOL_TYPE -> {
                    strings = parseStringPool(bytes, offset, headerSize, chunkSize)
                }
                RES_XML_RESOURCE_MAP_TYPE -> {
                    // Resource ID map chunk; skipped during structural XML rendering
                }
                RES_XML_START_NAMESPACE_TYPE -> {
                    if (offset + headerSize + 8 <= bytes.size) {
                        buf.position(offset + headerSize)
                        val prefixIdx = buf.int
                        val uriIdx = buf.int
                        val prefix = strings.getOrNull(prefixIdx).orEmpty()
                        val uri = strings.getOrNull(uriIdx).orEmpty()
                        if (prefix.isNotEmpty() && uri.isNotEmpty()) {
                            namespaceMap[uri] = prefix
                            pendingNamespaces.add(prefix to uri)
                        }
                    }
                }
                RES_XML_END_NAMESPACE_TYPE -> {
                    // Handled implicitly by element closure
                }
                RES_XML_START_ELEMENT_TYPE -> {
                    if (offset + headerSize + 20 <= bytes.size) {
                        buf.position(offset + headerSize)
                        val nsIdx = buf.int
                        val nameIdx = buf.int
                        val attrStart = buf.short.toInt() and 0xFFFF
                        val attrSize = buf.short.toInt() and 0xFFFF
                        val attrCount = buf.short.toInt() and 0xFFFF
                        buf.short // idIndex
                        buf.short // classIndex
                        buf.short // styleIndex

                        val tagName = qualifiedName(strings, namespaceMap, nsIdx, nameIdx)
                        appendIndent(sb, indent)
                        sb.append('<').append(tagName)

                        if (pendingNamespaces.isNotEmpty()) {
                            for ((prefix, uri) in pendingNamespaces) {
                                sb.append('\n')
                                appendIndent(sb, indent + 1)
                                sb.append("xmlns:").append(prefix).append("=\"").append(escapeXml(uri)).append('"')
                            }
                            pendingNamespaces.clear()
                        }

                        val attrsBase = offset + headerSize + attrStart
                        val step = if (attrSize >= 20) attrSize else 20
                        for (i in 0 until attrCount) {
                            val aPos = attrsBase + i * step
                            if (aPos + 20 > offset + chunkSize) break
                            buf.position(aPos)
                            val attrNsIdx = buf.int
                            val attrNameIdx = buf.int
                            val rawValueIdx = buf.int
                            buf.short // typedValue size
                            buf.get() // res0
                            val dataType = buf.get().toInt() and 0xFF
                            val data = buf.int

                            val attrName = qualifiedName(strings, namespaceMap, attrNsIdx, attrNameIdx)
                            val valueStr = formatAttributeValue(strings, rawValueIdx, dataType, data)
                            if (attrCount > 1) {
                                sb.append('\n')
                                appendIndent(sb, indent + 1)
                            } else {
                                sb.append(' ')
                            }
                            sb.append(attrName).append("=\"").append(escapeXml(valueStr)).append('"')
                        }

                        sb.append(">\n")
                        indent++
                    }
                }
                RES_XML_END_ELEMENT_TYPE -> {
                    if (offset + headerSize + 8 <= bytes.size) {
                        buf.position(offset + headerSize)
                        val nsIdx = buf.int
                        val nameIdx = buf.int
                        indent = (indent - 1).coerceAtLeast(0)
                        val tagName = qualifiedName(strings, namespaceMap, nsIdx, nameIdx)
                        appendIndent(sb, indent)
                        sb.append("</").append(tagName).append(">\n")
                    }
                }
                RES_XML_CDATA_TYPE -> {
                    if (offset + headerSize + 4 <= bytes.size) {
                        buf.position(offset + headerSize)
                        val dataIdx = buf.int
                        val text = strings.getOrNull(dataIdx).orEmpty()
                        if (text.isNotBlank()) {
                            appendIndent(sb, indent)
                            sb.append(escapeXml(text)).append('\n')
                        }
                    }
                }
            }
            offset += chunkSize
        }

        return sb.toString()
    }

    private fun parseStringPool(
        bytes: ByteArray,
        chunkOffset: Int,
        headerSize: Int,
        chunkSize: Int
    ): List<String> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(chunkOffset + 8)
        val stringCount = buf.int
        buf.int // styleCount
        val flags = buf.int
        val stringsStart = buf.int
        buf.int // stylesStart

        val isUtf8 = (flags and UTF8_FLAG) != 0
        val offsetsStart = chunkOffset + headerSize
        val dataStart = chunkOffset + stringsStart
        val result = ArrayList<String>(stringCount.coerceAtLeast(0))

        for (i in 0 until stringCount) {
            val offPos = offsetsStart + i * 4
            if (offPos + 4 > chunkOffset + chunkSize) break
            buf.position(offPos)
            val relOffset = buf.int
            val pos = dataStart + relOffset
            if (pos < 0 || pos >= bytes.size) {
                result.add("")
                continue
            }
            result.add(if (isUtf8) readUtf8String(bytes, pos) else readUtf16String(bytes, pos))
        }
        return result
    }

    private fun readUtf8String(bytes: ByteArray, start: Int): String {
        var pos = start
        // Skip UTF-16 char length (1 or 2 bytes)
        if (pos >= bytes.size) return ""
        val u16Len = bytes[pos++].toInt() and 0xFF
        if ((u16Len and 0x80) != 0 && pos < bytes.size) pos++

        // Read UTF-8 byte length (1 or 2 bytes)
        if (pos >= bytes.size) return ""
        var byteLen = bytes[pos++].toInt() and 0xFF
        if ((byteLen and 0x80) != 0 && pos < bytes.size) {
            byteLen = ((byteLen and 0x7F) shl 8) or (bytes[pos++].toInt() and 0xFF)
        }
        val safeLen = byteLen.coerceIn(0, (bytes.size - pos).coerceAtLeast(0))
        return String(bytes, pos, safeLen, StandardCharsets.UTF_8)
    }

    private fun readUtf16String(bytes: ByteArray, start: Int): String {
        var pos = start
        if (pos + 2 > bytes.size) return ""
        var charLen = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        if ((charLen and 0x8000) != 0 && pos + 2 <= bytes.size) {
            val low = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
            charLen = ((charLen and 0x7FFF) shl 16) or low
            pos += 2
        }
        val byteLen = (charLen * 2).coerceIn(0, (bytes.size - pos).coerceAtLeast(0))
        return String(bytes, pos, byteLen, StandardCharsets.UTF_16LE)
    }

    private fun qualifiedName(
        strings: List<String>,
        namespaces: Map<String, String>,
        nsIdx: Int,
        nameIdx: Int
    ): String {
        val name = strings.getOrNull(nameIdx).orEmpty().ifEmpty { "item" }
        if (nsIdx < 0) return name
        val uri = strings.getOrNull(nsIdx) ?: return name
        val prefix = namespaces[uri]
        return if (prefix.isNullOrEmpty()) name else "$prefix:$name"
    }

    private fun formatAttributeValue(
        strings: List<String>,
        rawValueIdx: Int,
        dataType: Int,
        data: Int
    ): String {
        if (rawValueIdx >= 0 && rawValueIdx < strings.size && dataType == 0x03) {
            return strings[rawValueIdx]
        }
        return when (dataType) {
            0x01 -> "@0x${Integer.toHexString(data).padStart(8, '0')}"
            0x02 -> "?0x${Integer.toHexString(data).padStart(8, '0')}"
            0x03 -> strings.getOrNull(data).orEmpty()
            0x04 -> java.lang.Float.intBitsToFloat(data).toString()
            0x05 -> formatDimension(data)
            0x06 -> "${complexToFloat(data) * 100f}%"
            0x10 -> data.toString()
            0x11 -> "0x${Integer.toHexString(data)}"
            0x12 -> if (data != 0) "true" else "false"
            0x1C, 0x1D, 0x1E, 0x1F -> "#${Integer.toHexString(data).uppercase().padStart(8, '0')}"
            else -> if (rawValueIdx >= 0) strings.getOrNull(rawValueIdx).orEmpty() else "0x${Integer.toHexString(data)}"
        }
    }

    private fun formatDimension(data: Int): String {
        val unit = when (data and 0x0F) {
            0 -> "px"
            1 -> "dp"
            2 -> "sp"
            3 -> "pt"
            4 -> "in"
            5 -> "mm"
            else -> "dp"
        }
        val value = complexToFloat(data)
        return if (value == value.toInt().toFloat()) "${value.toInt()}$unit" else "${"%.1f".format(value)}$unit"
    }

    private fun complexToFloat(complex: Int): Float {
        val mantissa = (complex and 0xFFFFFF00.toInt()).toFloat()
        val radixMult = when ((complex shr 4) and 0x03) {
            0 -> 1.0f / (1 shl 8)
            1 -> 1.0f / (1 shl 15)
            2 -> 1.0f / (1 shl 23)
            else -> 1.0f / (1 shl 31)
        }
        return mantissa * radixMult
    }

    private fun appendIndent(sb: StringBuilder, indent: Int) {
        repeat(indent) { sb.append("  ") }
    }

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
