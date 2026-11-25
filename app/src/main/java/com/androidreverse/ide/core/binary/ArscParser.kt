package com.androidreverse.ide.core.binary

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ArscSummary(
    val strings: Map<String, String>,
    val colors: Map<String, String>,
    val dimens: Map<String, String>,
    val booleans: Map<String, Boolean>,
    val integers: Map<String, Int>,
    val drawables: List<String>,
    val fonts: List<String>,
    val xmlResources: List<String>,
    val idToRef: Map<Int, String>
)

/**
 * Pure-Kotlin parser for Android compiled resource tables (resources.arsc).
 * Extracts named strings, colors, dimensions, booleans, integers, drawables, fonts,
 * and resource ID-to-name mappings for Manifest/XML annotation.
 */
object ArscParser {

    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val RES_TABLE_TYPE_TYPE = 0x0201

    private const val FLAG_UTF8 = 1 shl 8
    private const val ENTRY_FLAG_COMPLEX = 0x0001
    private const val ENTRY_FLAG_COMPACT = 0x0008

    private val boilerplatePrefixes = listOf(
        "abc_",
        "m3_",
        "mtrl_",
        "androidx_",
        "call_notification_",
        "status_bar_",
        "common_google_",
        "default_error_message",
        "dropdown_menu",
        "in_progress",
        "indeterminate",
        "navigation_menu",
        "not_selected",
        "range_end",
        "range_start",
        "selected",
        "state_empty",
        "state_off",
        "state_on",
        "switch_role",
        "tab",
        "template_percent",
        "tooltip_",
        "autofill",
        "close_drawer",
        "close_sheet"
    )

    fun parse(bytes: ByteArray): ArscSummary {
        val strings = LinkedHashMap<String, String>()
        val colors = LinkedHashMap<String, String>()
        val dimens = LinkedHashMap<String, String>()
        val booleans = LinkedHashMap<String, Boolean>()
        val integers = LinkedHashMap<String, Int>()
        val drawables = LinkedHashSet<String>()
        val fonts = LinkedHashSet<String>()
        val xmlResources = LinkedHashSet<String>()
        val idToRef = HashMap<Int, String>()

        if (bytes.size < 12) {
            return ArscSummary(strings, colors, dimens, booleans, integers, emptyList(), emptyList(), emptyList(), idToRef)
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rootType = buf.getShort(0).toInt() and 0xFFFF
        val rootHeaderSize = buf.getShort(2).toInt() and 0xFFFF
        val rootSize = buf.getInt(4).coerceAtMost(bytes.size)
        if (rootType != RES_TABLE_TYPE || rootHeaderSize < 12) {
            return ArscSummary(strings, colors, dimens, booleans, integers, emptyList(), emptyList(), emptyList(), idToRef)
        }

        var globalPool: List<String> = emptyList()
        var pos = rootHeaderSize

        while (pos + 8 <= rootSize) {
            val chunkType = buf.getShort(pos).toInt() and 0xFFFF
            val headerSize = buf.getShort(pos + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize < 8 || pos + chunkSize > rootSize) break

            when (chunkType) {
                RES_STRING_POOL_TYPE -> {
                    if (globalPool.isEmpty()) {
                        globalPool = parseStringPool(bytes, buf, pos, chunkSize)
                    }
                }
                RES_TABLE_PACKAGE_TYPE -> {
                    parsePackageChunk(
                        bytes = bytes,
                        buf = buf,
                        chunkStart = pos,
                        headerSize = headerSize,
                        chunkSize = chunkSize,
                        globalPool = globalPool,
                        strings = strings,
                        colors = colors,
                        dimens = dimens,
                        booleans = booleans,
                        integers = integers,
                        drawables = drawables,
                        fonts = fonts,
                        xmlResources = xmlResources,
                        idToRef = idToRef
                    )
                }
            }
            pos += chunkSize
        }

        return ArscSummary(
            strings = strings,
            colors = colors,
            dimens = dimens,
            booleans = booleans,
            integers = integers,
            drawables = drawables.toList(),
            fonts = fonts.toList(),
            xmlResources = xmlResources.toList(),
            idToRef = idToRef
        )
    }

    private fun parsePackageChunk(
        bytes: ByteArray,
        buf: ByteBuffer,
        chunkStart: Int,
        headerSize: Int,
        chunkSize: Int,
        globalPool: List<String>,
        strings: MutableMap<String, String>,
        colors: MutableMap<String, String>,
        dimens: MutableMap<String, String>,
        booleans: MutableMap<String, Boolean>,
        integers: MutableMap<String, Int>,
        drawables: MutableSet<String>,
        fonts: MutableSet<String>,
        xmlResources: MutableSet<String>,
        idToRef: MutableMap<Int, String>
    ) {
        if (headerSize < 284 || chunkStart + headerSize > bytes.size) return
        val pkgId = buf.getInt(chunkStart + 8)
        val typeStringsRel = buf.getInt(chunkStart + 268)
        val keyStringsRel = buf.getInt(chunkStart + 276)
        val chunkEnd = (chunkStart + chunkSize).coerceAtMost(bytes.size)

        val typeStrings = if (typeStringsRel > 0 && chunkStart + typeStringsRel + 8 <= chunkEnd) {
            val tStart = chunkStart + typeStringsRel
            val tSize = buf.getInt(tStart + 4)
            parseStringPool(bytes, buf, tStart, tSize)
        } else emptyList()

        val keyStrings = if (keyStringsRel > 0 && chunkStart + keyStringsRel + 8 <= chunkEnd) {
            val kStart = chunkStart + keyStringsRel
            val kSize = buf.getInt(kStart + 4)
            parseStringPool(bytes, buf, kStart, kSize)
        } else emptyList()

        var pos = chunkStart + headerSize
        while (pos + 8 <= chunkEnd) {
            val subType = buf.getShort(pos).toInt() and 0xFFFF
            val subHeader = buf.getShort(pos + 2).toInt() and 0xFFFF
            val subSize = buf.getInt(pos + 4)
            if (subSize < 8 || pos + subSize > chunkEnd) break

            if (subType == RES_TABLE_TYPE_TYPE && subHeader >= 20) {
                parseTypeChunk(
                    buf = buf,
                    chunkStart = pos,
                    headerSize = subHeader,
                    chunkSize = subSize,
                    pkgId = pkgId,
                    typeStrings = typeStrings,
                    keyStrings = keyStrings,
                    globalPool = globalPool,
                    strings = strings,
                    colors = colors,
                    dimens = dimens,
                    booleans = booleans,
                    integers = integers,
                    drawables = drawables,
                    fonts = fonts,
                    xmlResources = xmlResources,
                    idToRef = idToRef
                )
            }
            pos += subSize
        }
    }

    private fun parseTypeChunk(
        buf: ByteBuffer,
        chunkStart: Int,
        headerSize: Int,
        chunkSize: Int,
        pkgId: Int,
        typeStrings: List<String>,
        keyStrings: List<String>,
        globalPool: List<String>,
        strings: MutableMap<String, String>,
        colors: MutableMap<String, String>,
        dimens: MutableMap<String, String>,
        booleans: MutableMap<String, Boolean>,
        integers: MutableMap<String, Int>,
        drawables: MutableSet<String>,
        fonts: MutableSet<String>,
        xmlResources: MutableSet<String>,
        idToRef: MutableMap<Int, String>
    ) {
        val typeId = buf.get(chunkStart + 8).toInt() and 0xFF
        val flags = buf.get(chunkStart + 9).toInt() and 0xFF
        val entryCount = buf.getInt(chunkStart + 12)
        val entriesStart = buf.getInt(chunkStart + 16)
        val typeName = typeStrings.getOrNull(typeId - 1) ?: return

        // Skip sparse or offset16 flags if header doesn't contain full 32-bit offset array
        val isSparse = (flags and 0x01) != 0
        val isOffset16 = (flags and 0x02) != 0
        val offsetsStart = chunkStart + headerSize
        val dataBase = chunkStart + entriesStart
        val chunkEnd = chunkStart + chunkSize
        if (dataBase > chunkEnd || entryCount <= 0 || entryCount > 32_768) return

        for (i in 0 until entryCount) {
            val (entryIdx, relOff) = if (isSparse) {
                val p = offsetsStart + i * 4
                if (p + 4 > dataBase) break
                val idx = buf.getShort(p).toInt() and 0xFFFF
                val off = (buf.getShort(p + 2).toInt() and 0xFFFF) * 4
                idx to off
            } else if (isOffset16) {
                val p = offsetsStart + i * 2
                if (p + 2 > dataBase) break
                val raw = buf.getShort(p).toInt() and 0xFFFF
                if (raw == 0xFFFF) continue
                i to (raw * 4)
            } else {
                val p = offsetsStart + i * 4
                if (p + 4 > dataBase) break
                val raw = buf.getInt(p)
                if (raw == -1) continue
                i to raw
            }

            val entryPos = dataBase + relOff
            if (entryPos + 8 > chunkEnd) continue

            val entrySizeOrKey = buf.getShort(entryPos).toInt() and 0xFFFF
            val entryFlags = buf.getShort(entryPos + 2).toInt() and 0xFFFF
            val isCompact = (entryFlags and ENTRY_FLAG_COMPACT) != 0

            val keyIdx: Int
            val valueType: Int
            val valueData: Int

            if (isCompact) {
                keyIdx = entrySizeOrKey
                valueType = (entryFlags shr 8) and 0xFF
                valueData = buf.getInt(entryPos + 4)
            } else {
                if ((entryFlags and ENTRY_FLAG_COMPLEX) != 0) {
                    keyIdx = buf.getInt(entryPos + 4)
                    val keyName = keyStrings.getOrNull(keyIdx) ?: continue
                    val resId = (pkgId shl 24) or (typeId shl 16) or (entryIdx and 0xFFFF)
                    idToRef.putIfAbsent(resId, "@$typeName/$keyName")
                    continue
                }
                if (entryPos + entrySizeOrKey + 8 > chunkEnd) continue
                keyIdx = buf.getInt(entryPos + 4)
                val valPos = entryPos + entrySizeOrKey
                valueType = buf.get(valPos + 3).toInt() and 0xFF
                valueData = buf.getInt(valPos + 4)
            }

            val keyName = keyStrings.getOrNull(keyIdx) ?: continue
            val resId = (pkgId shl 24) or (typeId shl 16) or (entryIdx and 0xFFFF)
            idToRef.putIfAbsent(resId, "@$typeName/$keyName")

            if (isBoilerplateKey(keyName)) continue

            when (typeName) {
                "string" -> {
                    if (valueType == 0x03) {
                        val text = globalPool.getOrNull(valueData)
                        if (!text.isNullOrBlank() && keyName !in strings) {
                            strings[keyName] = text
                        }
                    }
                }
                "color" -> {
                    if (valueType in 0x1C..0x1F && keyName !in colors) {
                        colors[keyName] = "#%08X".format(valueData)
                    }
                }
                "dimen" -> {
                    if (valueType == 0x05 && keyName !in dimens) {
                        dimens[keyName] = formatDimension(valueData)
                    }
                }
                "bool" -> {
                    if (valueType == 0x12 && keyName !in booleans) {
                        booleans[keyName] = valueData != 0
                    }
                }
                "integer" -> {
                    if ((valueType == 0x10 || valueType == 0x11) && keyName !in integers) {
                        integers[keyName] = valueData
                    }
                }
                "drawable", "mipmap" -> {
                    if (valueType == 0x03) {
                        val path = globalPool.getOrNull(valueData).orEmpty()
                        drawables.add("$keyName -> $path")
                    } else {
                        drawables.add(keyName)
                    }
                }
                "font" -> {
                    if (valueType == 0x03) {
                        val path = globalPool.getOrNull(valueData).orEmpty()
                        fonts.add("$keyName -> $path")
                    } else {
                        fonts.add(keyName)
                    }
                }
                "xml" -> {
                    if (valueType == 0x03) {
                        val path = globalPool.getOrNull(valueData).orEmpty()
                        xmlResources.add("$keyName -> $path")
                    } else {
                        xmlResources.add(keyName)
                    }
                }
            }
        }
    }

    private fun isBoilerplateKey(key: String): Boolean {
        return boilerplatePrefixes.any { key.startsWith(it) }
    }

    private fun formatDimension(data: Int): String {
        val units = arrayOf("px", "dp", "sp", "pt", "in", "mm")
        val unitIdx = data and 0x0F
        val unit = units.getOrElse(unitIdx) { "u$unitIdx" }
        val mantissa = (data shr 8).toFloat()
        val radixShifts = floatArrayOf(1f, 1f / (1 shl 7), 1f / (1 shl 15), 1f / (1 shl 23))
        val radix = (data shr 4) and 0x03
        val value = mantissa * radixShifts[radix]
        return "${"%.1f".format(value)}$unit"
    }

    private fun parseStringPool(
        bytes: ByteArray,
        buf: ByteBuffer,
        chunkStart: Int,
        chunkSize: Int
    ): List<String> {
        if (chunkStart + 28 > bytes.size) return emptyList()
        val stringCount = buf.getInt(chunkStart + 8).coerceIn(0, 120_000)
        val flags = buf.getInt(chunkStart + 16)
        val stringsStart = buf.getInt(chunkStart + 20)
        val isUtf8 = (flags and FLAG_UTF8) != 0
        val offsetsBase = chunkStart + 28
        val dataBase = chunkStart + stringsStart
        val chunkEnd = (chunkStart + chunkSize).coerceAtMost(bytes.size)

        val result = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            val offPos = offsetsBase + i * 4
            if (offPos + 4 > chunkEnd) break
            val rel = buf.getInt(offPos)
            val strPos = dataBase + rel
            if (strPos < dataBase || strPos >= chunkEnd) {
                result.add("")
                continue
            }
            val text = if (isUtf8) readUtf8(bytes, strPos, chunkEnd) else readUtf16(bytes, strPos, chunkEnd)
            result.add(text)
        }
        return result
    }

    private fun readUtf8(bytes: ByteArray, start: Int, limit: Int): String {
        var pos = start
        if (pos >= limit) return ""
        val first = bytes[pos++].toInt() and 0xFF
        if ((first and 0x80) != 0 && pos < limit) pos++ // skip char count high byte
        if (pos >= limit) return ""
        var byteLen = bytes[pos++].toInt() and 0xFF
        if ((byteLen and 0x80) != 0 && pos < limit) {
            byteLen = ((byteLen and 0x7F) shl 8) or (bytes[pos++].toInt() and 0xFF)
        }
        val safeLen = byteLen.coerceIn(0, (limit - pos).coerceAtMost(8192))
        return String(bytes, pos, safeLen, Charsets.UTF_8)
    }

    private fun readUtf16(bytes: ByteArray, start: Int, limit: Int): String {
        var pos = start
        if (pos + 2 > limit) return ""
        var charLen = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        if ((charLen and 0x8000) != 0 && pos + 2 <= limit) {
            val low = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
            charLen = ((charLen and 0x7FFF) shl 16) or low
            pos += 2
        }
        val safeChars = charLen.coerceIn(0, ((limit - pos) / 2).coerceAtMost(4096))
        val chars = CharArray(safeChars)
        for (i in 0 until safeChars) {
            val lo = bytes[pos + i * 2].toInt() and 0xFF
            val hi = bytes[pos + i * 2 + 1].toInt() and 0xFF
            chars[i] = ((hi shl 8) or lo).toChar()
        }
        return String(chars)
    }
}
