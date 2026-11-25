package com.androidreverse.ide.core.binary

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ElfSectionInfo(
    val name: String,
    val offset: Long,
    val size: Long,
    val flags: String
)

data class ElfAnalysis(
    val is64Bit: Boolean,
    val endianness: String,
    val machine: String,
    val elfType: String,
    val entryPoint: String,
    val isPie: Boolean,
    val hasNx: Boolean,
    val hasRelro: Boolean,
    val sections: List<ElfSectionInfo>,
    val hexPreview: String
)

/**
 * Pure-Kotlin ELF32/ELF64 native binary header, program flag, section table, and hex dump analyzer.
 */
object ElfParser {

    fun isElf(bytes: ByteArray): Boolean =
        bytes.size >= 52 &&
            bytes[0] == 0x7F.toByte() &&
            bytes[1] == 'E'.code.toByte() &&
            bytes[2] == 'L'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()

    fun analyze(bytes: ByteArray): ElfAnalysis? {
        if (!isElf(bytes)) return null
        val is64 = bytes[4].toInt() == 2
        val isLittle = bytes[5].toInt() != 2
        val buf = ByteBuffer.wrap(bytes).order(if (isLittle) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)

        val eType = buf.getShort(16).toInt() and 0xFFFF
        val eMachine = buf.getShort(18).toInt() and 0xFFFF

        val elfType = when (eType) {
            1 -> "Relocatable"
            2 -> "Executable"
            3 -> "Shared Object"
            4 -> "Core"
            else -> "Type 0x${eType.toString(16)}"
        }

        val machine = when (eMachine) {
            3 -> "x86"
            40 -> "ARMv7"
            62 -> "x86-64"
            183 -> "ARM64"
            243 -> "RISC-V"
            else -> "Arch $eMachine"
        }

        val entry: Long
        val phOff: Long
        val shOff: Long
        val phEntSize: Int
        val phNum: Int
        val shEntSize: Int
        val shNum: Int
        val shStrNdx: Int

        if (is64 && bytes.size >= 64) {
            entry = buf.getLong(24)
            phOff = buf.getLong(32)
            shOff = buf.getLong(40)
            phEntSize = buf.getShort(54).toInt() and 0xFFFF
            phNum = buf.getShort(56).toInt() and 0xFFFF
            shEntSize = buf.getShort(58).toInt() and 0xFFFF
            shNum = buf.getShort(60).toInt() and 0xFFFF
            shStrNdx = buf.getShort(62).toInt() and 0xFFFF
        } else {
            entry = buf.getInt(24).toLong() and 0xFFFFFFFFL
            phOff = buf.getInt(28).toLong() and 0xFFFFFFFFL
            shOff = buf.getInt(32).toLong() and 0xFFFFFFFFL
            phEntSize = buf.getShort(42).toInt() and 0xFFFF
            phNum = buf.getShort(44).toInt() and 0xFFFF
            shEntSize = buf.getShort(46).toInt() and 0xFFFF
            shNum = buf.getShort(48).toInt() and 0xFFFF
            shStrNdx = buf.getShort(50).toInt() and 0xFFFF
        }

        var hasNx = false
        var hasRelro = false
        if (phOff > 0 && phEntSize >= 32) {
            for (i in 0 until phNum.coerceAtMost(64)) {
                val pPos = (phOff + i.toLong() * phEntSize).toInt()
                if (pPos < 0 || pPos + phEntSize > bytes.size) break
                val pType = buf.getInt(pPos)
                if (pType == 0x6474e551) { // PT_GNU_STACK
                    val pFlags = if (is64) buf.getInt(pPos + 4) else buf.getInt(pPos + 24)
                    hasNx = (pFlags and 0x1) == 0
                } else if (pType == 0x6474e552) { // PT_GNU_RELRO
                    hasRelro = true
                }
            }
        }

        val sections = parseSections(bytes, buf, is64, shOff, shEntSize, shNum, shStrNdx)

        return ElfAnalysis(
            is64Bit = is64,
            endianness = if (isLittle) "Little Endian" else "Big Endian",
            machine = machine,
            elfType = elfType,
            entryPoint = "0x${entry.toString(16)}",
            isPie = eType == 3,
            hasNx = hasNx,
            hasRelro = hasRelro,
            sections = sections,
            hexPreview = formatHexDump(bytes, maxBytes = 4096)
        )
    }

    private fun parseSections(
        bytes: ByteArray,
        buf: ByteBuffer,
        is64: Boolean,
        shOff: Long,
        shEntSize: Int,
        shNum: Int,
        shStrNdx: Int
    ): List<ElfSectionInfo> {
        if (shOff <= 0 || shEntSize < 40 || shNum <= 0) return emptyList()

        val strSecPos = (shOff + shStrNdx.toLong() * shEntSize).toInt()
        val strTabOff = if (strSecPos in 0..(bytes.size - shEntSize)) {
            if (is64) buf.getLong(strSecPos + 24).toInt() else buf.getInt(strSecPos + 16)
        } else {
            -1
        }

        val result = mutableListOf<ElfSectionInfo>()
        for (i in 0 until shNum.coerceAtMost(80)) {
            val sPos = (shOff + i.toLong() * shEntSize).toInt()
            if (sPos < 0 || sPos + shEntSize > bytes.size) break
            val nameOff = buf.getInt(sPos)
            val flags = if (is64) buf.getLong(sPos + 8) else buf.getInt(sPos + 8).toLong()
            val offset = if (is64) buf.getLong(sPos + 24) else buf.getInt(sPos + 16).toLong() and 0xFFFFFFFFL
            val size = if (is64) buf.getLong(sPos + 32) else buf.getInt(sPos + 20).toLong() and 0xFFFFFFFFL
            val name = readCString(bytes, strTabOff + nameOff)
            if (name.isNotEmpty()) {
                val flagText = buildString {
                    if ((flags and 0x2L) != 0L) append('A')
                    if ((flags and 0x1L) != 0L) append('W')
                    if ((flags and 0x4L) != 0L) append('X')
                }.ifEmpty { "-" }
                result.add(ElfSectionInfo(name, offset, size, flagText))
            }
        }
        return result
    }

    private fun readCString(bytes: ByteArray, start: Int): String {
        if (start < 0 || start >= bytes.size) return ""
        var end = start
        while (end < bytes.size && bytes[end].toInt() != 0 && end - start < 64) end++
        return String(bytes, start, end - start, Charsets.UTF_8)
    }

    fun formatHexDump(bytes: ByteArray, maxBytes: Int = 4096): String {
        val len = bytes.size.coerceAtMost(maxBytes)
        val sb = StringBuilder(len * 4)
        var offset = 0
        while (offset < len) {
            sb.append(offset.toString(16).padStart(6, '0')).append("  ")
            val rowEnd = (offset + 16).coerceAtMost(len)
            for (i in offset until offset + 16) {
                if (i < rowEnd) {
                    sb.append((bytes[i].toInt() and 0xFF).toString(16).padStart(2, '0')).append(' ')
                } else {
                    sb.append("   ")
                }
                if (i == offset + 7) sb.append(' ')
            }
            sb.append(" |")
            for (i in offset until rowEnd) {
                val c = bytes[i].toInt() and 0xFF
                sb.append(if (c in 32..126) c.toChar() else '.')
            }
            sb.append("|\n")
            offset += 16
        }
        if (bytes.size > maxBytes) {
            sb.append("\n... ").append(bytes.size - maxBytes).append(" more bytes")
        }
        return sb.toString()
    }
}
