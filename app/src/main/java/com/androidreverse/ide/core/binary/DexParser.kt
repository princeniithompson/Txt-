package com.androidreverse.ide.core.binary

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DexClassSummary(
    val dexName: String,
    val descriptor: String,
    val simpleName: String,
    val packageName: String,
    val superDescriptor: String,
    val accessFlags: Int,
    val classDataOff: Int,
    val staticValuesOff: Int = 0
)

data class DexFieldInfo(
    val name: String,
    val type: String,
    val accessFlags: Int,
    val initialValue: String? = null
)

data class DexMethodInfo(
    val name: String,
    val signature: String,
    val returnType: String,
    val parameters: List<String>,
    val accessFlags: Int,
    val registersSize: Int,
    val insSize: Int,
    val instructions: List<DecodedInstruction>
)

data class DecodedInstruction(
    val offset: Int,
    val opcode: Int,
    val mnemonic: String,
    val operands: String,
    val branchTarget: Int? = null,
    val isConditionalBranch: Boolean = false,
    val isTerminal: Boolean = false,
    val invokedMethod: String? = null,
    val accessedField: String? = null,
    val isFieldWrite: Boolean = false,
    val destReg: String? = null,
    val srcReg: String? = null,
    val constString: String? = null,
    val constValue: String? = null,
    val newInstanceType: String? = null,
    val invokeRegs: List<String> = emptyList()
)

data class DexClassDetail(
    val summary: DexClassSummary,
    val fields: List<DexFieldInfo>,
    val methods: List<DexMethodInfo>,
    val smaliCode: String,
    val javaPseudoCode: String,
    val stringLiterals: List<String> = emptyList()
)

data class CfgBlock(
    val id: Int,
    val startOffset: Int,
    val lines: List<String>
)

enum class CfgEdgeType {
    TRUE_BRANCH,
    FALSE_BRANCH,
    FALLTHROUGH,
    JUMP
}

data class CfgEdge(
    val fromBlockId: Int,
    val toBlockId: Int,
    val type: CfgEdgeType
)

data class CfgGraph(
    val methodName: String,
    val blocks: List<CfgBlock>,
    val edges: List<CfgEdge>
)

enum class CallEdgeKind {
    READ,
    WRITE,
    CALL
}

data class CallNode(
    val id: String,
    val title: String,
    val subtitle: String,
    val detail: String,
    val depth: Int
)

data class CallEdge(
    val fromId: String,
    val toId: String,
    val kind: CallEdgeKind
)

data class CallGraphData(
    val targetTitle: String,
    val nodes: List<CallNode>,
    val edges: List<CallEdge>
)

/**
 * Pure-Kotlin DEX binary parser, Dalvik bytecode disassembler, constant-propagating
 * Java pseudo-decompiler, method CFG constructor, and cross-reference call graph analyzer.
 */
class DexFileParser(
    val dexName: String,
    private val bytes: ByteArray
) {
    private val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    val isValid: Boolean
    val version: String
    val stringCount: Int
    val typeCount: Int
    val methodCount: Int
    val fieldCount: Int
    val classCount: Int

    private val stringIdsOff: Int
    private val typeIdsOff: Int
    private val protoIdsOff: Int
    private val fieldIdsOff: Int
    private val methodIdsOff: Int
    private val classDefsOff: Int

    private val stringCache = HashMap<Int, String>()
    private val typeCache = HashMap<Int, String>()

    init {
        val hasMagic = bytes.size >= 112 &&
            bytes[0] == 'd'.code.toByte() &&
            bytes[1] == 'e'.code.toByte() &&
            bytes[2] == 'x'.code.toByte() &&
            bytes[3] == '\n'.code.toByte()
        isValid = hasMagic
        if (hasMagic) {
            version = String(bytes, 4, 3)
            stringCount = buf.getInt(56)
            stringIdsOff = buf.getInt(60)
            typeCount = buf.getInt(64)
            typeIdsOff = buf.getInt(68)
            protoIdsOff = buf.getInt(76)
            fieldCount = buf.getInt(80)
            fieldIdsOff = buf.getInt(84)
            methodCount = buf.getInt(88)
            methodIdsOff = buf.getInt(92)
            classCount = buf.getInt(96)
            classDefsOff = buf.getInt(100)
        } else {
            version = ""
            stringCount = 0
            stringIdsOff = 0
            typeCount = 0
            typeIdsOff = 0
            protoIdsOff = 0
            fieldCount = 0
            fieldIdsOff = 0
            methodCount = 0
            methodIdsOff = 0
            classCount = 0
            classDefsOff = 0
        }
    }

    fun getString(idx: Int): String {
        if (idx < 0 || idx >= stringCount) return ""
        return stringCache.getOrPut(idx) {
            val idPos = stringIdsOff + idx * 4
            if (idPos + 4 > bytes.size) return@getOrPut ""
            val dataOff = buf.getInt(idPos)
            if (dataOff <= 0 || dataOff >= bytes.size) return@getOrPut ""
            val (_, afterLen) = readUleb128(dataOff)
            readMutf8(afterLen)
        }
    }

    fun getType(idx: Int): String {
        if (idx < 0 || idx >= typeCount) return "Ljava/lang/Object;"
        return typeCache.getOrPut(idx) {
            val pos = typeIdsOff + idx * 4
            if (pos + 4 > bytes.size) return@getOrPut "V"
            val strIdx = buf.getInt(pos)
            getString(strIdx)
        }
    }

    fun getFieldRef(idx: Int): String {
        if (idx < 0 || idx >= fieldCount) return "field_$idx"
        val pos = fieldIdsOff + idx * 8
        if (pos + 8 > bytes.size) return "field_$idx"
        val classIdx = buf.getShort(pos).toInt() and 0xFFFF
        val typeIdx = buf.getShort(pos + 2).toInt() and 0xFFFF
        val nameIdx = buf.getInt(pos + 4)
        return "${getType(classIdx)}->${getString(nameIdx)}:${getType(typeIdx)}"
    }

    fun getMethodRef(idx: Int): String {
        if (idx < 0 || idx >= methodCount) return "method_$idx()"
        val pos = methodIdsOff + idx * 8
        if (pos + 8 > bytes.size) return "method_$idx()"
        val classIdx = buf.getShort(pos).toInt() and 0xFFFF
        val protoIdx = buf.getShort(pos + 2).toInt() and 0xFFFF
        val nameIdx = buf.getInt(pos + 4)
        val (ret, params) = getProtoParts(protoIdx)
        return "${getType(classIdx)}->${getString(nameIdx)}(${params.joinToString("")})$ret"
    }

    private fun getProtoParts(protoIdx: Int): Pair<String, List<String>> {
        val pos = protoIdsOff + protoIdx * 12
        if (protoIdx < 0 || pos + 12 > bytes.size) return "V" to emptyList()
        val returnTypeIdx = buf.getInt(pos + 4)
        val paramsOff = buf.getInt(pos + 8)
        val returnType = getType(returnTypeIdx)
        if (paramsOff <= 0 || paramsOff + 4 > bytes.size) return returnType to emptyList()
        val size = buf.getInt(paramsOff)
        val params = ArrayList<String>(size.coerceIn(0, 64))
        for (i in 0 until size.coerceIn(0, 64)) {
            val tPos = paramsOff + 4 + i * 2
            if (tPos + 2 > bytes.size) break
            params.add(getType(buf.getShort(tPos).toInt() and 0xFFFF))
        }
        return returnType to params
    }

    fun listClasses(limit: Int = Int.MAX_VALUE): List<DexClassSummary> {
        if (!isValid) return emptyList()
        val count = classCount.coerceAtMost(limit)
        val list = ArrayList<DexClassSummary>(count)
        for (i in 0 until count) {
            val pos = classDefsOff + i * 32
            if (pos + 32 > bytes.size) break
            val classIdx = buf.getInt(pos)
            val accessFlags = buf.getInt(pos + 4)
            val superIdx = buf.getInt(pos + 8)
            val classDataOff = buf.getInt(pos + 24)
            val staticValuesOff = buf.getInt(pos + 28)
            val desc = getType(classIdx)
            val superDesc = if (superIdx >= 0 && superIdx < typeCount) getType(superIdx) else "Ljava/lang/Object;"
            val clean = descriptorToClassName(desc)
            val dotIdx = clean.lastIndexOf('.')
            val pkg = if (dotIdx > 0) clean.substring(0, dotIdx) else "(default)"
            val simple = if (dotIdx > 0) clean.substring(dotIdx + 1) else clean
            list.add(
                DexClassSummary(
                    dexName = dexName,
                    descriptor = desc,
                    simpleName = simple,
                    packageName = pkg,
                    superDescriptor = superDesc,
                    accessFlags = accessFlags,
                    classDataOff = classDataOff,
                    staticValuesOff = staticValuesOff
                )
            )
        }
        return list
    }

    fun extractInterestingStrings(limit: Int = 120): List<String> {
        if (!isValid) return emptyList()
        val found = LinkedHashSet<String>()
        for (i in 0 until stringCount) {
            if (found.size >= limit) break
            val s = getString(i)
            if (s.length !in 6..400) continue
            // Skip Dalvik descriptors, Kotlin inline markers, and internal OkHttp/AndroidX noise
            if (s.startsWith("(") || (s.startsWith("L") && s.endsWith(";")) ||
                s.startsWith("[L") || s.startsWith("\$i\$") || s.startsWith("\$this\$") ||
                s.contains("okhttp3/internal") || s.contains("androidx/")
            ) continue
            if (s.startsWith("http://") ||
                s.startsWith("https://") ||
                s.startsWith("wss://") ||
                s.startsWith("ws://") ||
                s.startsWith("content://") ||
                s.contains("generativelanguage.googleapis.com", ignoreCase = true) ||
                s.contains("gemini-", ignoreCase = true) ||
                s.contains("/v1beta/", ignoreCase = true) ||
                s.contains("/v1alpha/", ignoreCase = true) ||
                s.contains("api/", ignoreCase = true) ||
                s.endsWith(".json") ||
                s.endsWith(".db")
            ) {
                found.add(s)
            }
        }
        return found.toList()
    }

    fun inspectClass(summary: DexClassSummary): DexClassDetail {
        val fields = mutableListOf<DexFieldInfo>()
        val methods = mutableListOf<DexMethodInfo>()
        val classStrings = LinkedHashSet<String>()

        val staticInitialValues = if (summary.staticValuesOff > 0 && summary.staticValuesOff < bytes.size) {
            runCatching { parseEncodedArray(summary.staticValuesOff).first }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        if (summary.classDataOff > 0 && summary.classDataOff < bytes.size) {
            var pos = summary.classDataOff
            val (staticFieldsCount, p1) = readUleb128(pos); pos = p1
            val (instanceFieldsCount, p2) = readUleb128(pos); pos = p2
            val (directMethodsCount, p3) = readUleb128(pos); pos = p3
            val (virtualMethodsCount, p4) = readUleb128(pos); pos = p4

            var fieldIdx = 0
            repeat(staticFieldsCount + instanceFieldsCount) { idx ->
                val (diff, n1) = readUleb128(pos); pos = n1
                val (flags, n2) = readUleb128(pos); pos = n2
                fieldIdx = if (idx == 0 || idx == staticFieldsCount) diff else fieldIdx + diff
                val fPos = fieldIdsOff + fieldIdx * 8
                if (fPos + 8 <= bytes.size) {
                    val typeIdx = buf.getShort(fPos + 2).toInt() and 0xFFFF
                    val nameIdx = buf.getInt(fPos + 4)
                    val initVal = if (idx < staticFieldsCount) staticInitialValues.getOrNull(idx) else null
                    if (initVal != null && initVal.startsWith("\"") && initVal.endsWith("\"") && initVal.length >= 4) {
                        classStrings.add(initVal.substring(1, initVal.length - 1))
                    }
                    fields.add(DexFieldInfo(getString(nameIdx), getType(typeIdx), flags, initVal))
                }
            }

            var methodIdx = 0
            repeat(directMethodsCount + virtualMethodsCount) { idx ->
                val (diff, n1) = readUleb128(pos); pos = n1
                val (flags, n2) = readUleb128(pos); pos = n2
                val (codeOff, n3) = readUleb128(pos); pos = n3
                methodIdx = if (idx == 0 || idx == directMethodsCount) diff else methodIdx + diff
                val mPos = methodIdsOff + methodIdx * 8
                if (mPos + 8 <= bytes.size) {
                    val protoIdx = buf.getShort(mPos + 2).toInt() and 0xFFFF
                    val nameIdx = buf.getInt(mPos + 4)
                    val name = getString(nameIdx)
                    val (ret, params) = getProtoParts(protoIdx)
                    val (regs, ins, decoded) = if (codeOff > 0 && codeOff + 16 <= bytes.size) {
                        decodeCodeItem(codeOff)
                    } else {
                        Triple(0, 0, emptyList())
                    }
                    for (di in decoded) {
                        val cs = di.constString
                        if (!cs.isNullOrBlank()) {
                            classStrings.add(cs)
                        }
                    }
                    methods.add(
                        DexMethodInfo(
                            name = name,
                            signature = "$name(${params.joinToString("")})$ret",
                            returnType = ret,
                            parameters = params,
                            accessFlags = flags,
                            registersSize = regs,
                            insSize = ins,
                            instructions = decoded
                        )
                    )
                }
            }
        }

        val smali = buildSmali(summary, fields, methods)
        val java = buildJavaPseudo(summary, fields, methods)
        return DexClassDetail(summary, fields, methods, smali, java, classStrings.toList())
    }

    private fun parseEncodedArray(startOff: Int): Pair<List<String>, Int> {
        if (startOff <= 0 || startOff >= bytes.size) return emptyList<String>() to startOff
        val (size, afterSize) = readUleb128(startOff)
        var pos = afterSize
        val safeCount = size.coerceIn(0, 512)
        val values = ArrayList<String>(safeCount)
        for (i in 0 until safeCount) {
            if (pos >= bytes.size) break
            val (formatted, nextPos) = parseEncodedValue(pos)
            values.add(formatted)
            pos = nextPos
        }
        return values to pos
    }

    private fun parseEncodedValue(startOff: Int): Pair<String, Int> {
        var pos = startOff
        if (pos >= bytes.size) return "?" to pos
        val header = bytes[pos++].toInt() and 0xFF
        val valueType = header and 0x1F
        val valueArg = (header shr 5) and 0x07
        val byteCount = valueArg + 1

        fun readSignedLong(len: Int): Long {
            var v = 0L
            for (i in 0 until len) {
                if (pos >= bytes.size) break
                val b = bytes[pos++].toLong() and 0xFFL
                v = v or (b shl (i * 8))
            }
            val shift = (8 - len) * 8
            return (v shl shift) shr shift
        }

        fun readUnsignedLong(len: Int): Long {
            var v = 0L
            for (i in 0 until len) {
                if (pos >= bytes.size) break
                val b = bytes[pos++].toLong() and 0xFFL
                v = v or (b shl (i * 8))
            }
            return v
        }

        fun readRightZeroExtendedInt(len: Int): Int {
            var v = 0
            for (i in 0 until len) {
                if (pos >= bytes.size) break
                val b = bytes[pos++].toInt() and 0xFF
                v = v or (b shl ((4 - len + i) * 8))
            }
            return v
        }

        fun readRightZeroExtendedLong(len: Int): Long {
            var v = 0L
            for (i in 0 until len) {
                if (pos >= bytes.size) break
                val b = bytes[pos++].toLong() and 0xFFL
                v = v or (b shl ((8 - len + i) * 8))
            }
            return v
        }

        val formatted = when (valueType) {
            0x00 -> readSignedLong(1).toString() // VALUE_BYTE
            0x02 -> readSignedLong(byteCount).toString() // VALUE_SHORT
            0x03 -> "'${readUnsignedLong(byteCount).toInt().toChar()}'" // VALUE_CHAR
            0x04 -> {
                val intVal = readSignedLong(byteCount).toInt()
                formatIntLiteral(intVal)
            }
            0x06 -> "${readSignedLong(byteCount)}L" // VALUE_LONG
            0x10 -> {
                val f = java.lang.Float.intBitsToFloat(readRightZeroExtendedInt(byteCount))
                "${f}f"
            }
            0x11 -> {
                val d = java.lang.Double.longBitsToDouble(readRightZeroExtendedLong(byteCount))
                d.toString()
            }
            0x17 -> {
                val strIdx = readUnsignedLong(byteCount).toInt()
                "\"${escapeForCode(getString(strIdx), 600)}\""
            }
            0x18 -> {
                val typeIdx = readUnsignedLong(byteCount).toInt()
                "${descriptorToSimpleJavaType(getType(typeIdx))}.class"
            }
            0x19, 0x1B -> {
                val fieldIdx = readUnsignedLong(byteCount).toInt()
                getFieldRef(fieldIdx).substringAfter("->").substringBefore(":")
            }
            0x1A -> {
                val methodIdx = readUnsignedLong(byteCount).toInt()
                getMethodRef(methodIdx)
            }
            0x1C -> {
                val (arr, next) = parseEncodedArray(pos)
                pos = next
                "[${arr.take(8).joinToString(", ")}]"
            }
            0x1D -> {
                // VALUE_ANNOTATION: skip type_idx + size + elements
                val (_, p1) = readUleb128(pos)
                val (elemCount, p2) = readUleb128(p1)
                pos = p2
                repeat(elemCount.coerceIn(0, 64)) {
                    val (_, pn) = readUleb128(pos)
                    val (_, pv) = parseEncodedValue(pn)
                    pos = pv
                }
                "@annotation"
            }
            0x1E -> "null" // VALUE_NULL
            0x1F -> (valueArg != 0).toString() // VALUE_BOOLEAN
            else -> {
                pos = (pos + byteCount).coerceAtMost(bytes.size)
                "?"
            }
        }
        return formatted to pos
    }

    private fun decodeCodeItem(codeOff: Int): Triple<Int, Int, List<DecodedInstruction>> {
        val registersSize = buf.getShort(codeOff).toInt() and 0xFFFF
        val insSize = buf.getShort(codeOff + 2).toInt() and 0xFFFF
        val insnsSize = buf.getInt(codeOff + 12).coerceIn(0, 8192)
        val insnsStart = codeOff + 16
        if (insnsStart + insnsSize * 2 > bytes.size) {
            return Triple(registersSize, insSize, emptyList())
        }

        val words = IntArray(insnsSize) { i ->
            buf.getShort(insnsStart + i * 2).toInt() and 0xFFFF
        }
        val list = mutableListOf<DecodedInstruction>()
        var pc = 0
        while (pc < words.size) {
            val w0 = words[pc]
            // Handle Dalvik payload pseudo-instructions cleanly so switch/array tables don't produce noise
            if (w0 == 0x0100 && pc + 1 < words.size) {
                val size = words[pc + 1]
                pc += (size * 2 + 4).coerceAtLeast(2)
                continue
            } else if (w0 == 0x0200 && pc + 1 < words.size) {
                val size = words[pc + 1]
                pc += (size * 4 + 2).coerceAtLeast(2)
                continue
            } else if (w0 == 0x0300 && pc + 3 < words.size) {
                val elemWidth = words[pc + 1]
                val size = (words[pc + 2] and 0xFFFF) or ((words[pc + 3] and 0xFFFF) shl 16)
                val dataWords = ((size.toLong() * elemWidth.toLong() + 1L) / 2L).coerceIn(0L, 4096L).toInt()
                pc += (dataWords + 4).coerceAtLeast(4)
                continue
            }

            val op = w0 and 0xFF
            val a = (w0 shr 8) and 0x0F
            val b = (w0 shr 12) and 0x0F
            val aa = (w0 shr 8) and 0xFF

            val (step, insn) = decodeOpcode(pc, op, a, b, aa, words)
            list.add(insn)
            pc += step.coerceAtLeast(1)
        }
        return Triple(registersSize, insSize, list)
    }

    private fun decodeOpcode(
        pc: Int,
        op: Int,
        a: Int,
        b: Int,
        aa: Int,
        words: IntArray
    ): Pair<Int, DecodedInstruction> {
        fun word(delta: Int): Int = if (pc + delta < words.size) words[pc + delta] else 0
        fun signedWord(delta: Int): Int = word(delta).toShort().toInt()
        fun int32(delta: Int): Int = (word(delta) and 0xFFFF) or ((word(delta + 1) and 0xFFFF) shl 16)
        fun long64(delta: Int): Long {
            val lo = int32(delta).toLong() and 0xFFFFFFFFL
            val hi = int32(delta + 2).toLong() and 0xFFFFFFFFL
            return lo or (hi shl 32)
        }

        return when (op) {
            0x00 -> 1 to DecodedInstruction(pc, op, "nop", "")
            0x01, 0x04, 0x07 -> 1 to DecodedInstruction(
                pc, op, moveName(op), "v$a, v$b",
                destReg = "v$a", srcReg = "v$b"
            )
            0x02, 0x05, 0x08 -> 2 to DecodedInstruction(
                pc, op, "${moveName(op - 1)}/from16", "v$aa, v${word(1)}",
                destReg = "v$aa", srcReg = "v${word(1)}"
            )
            0x03, 0x06, 0x09 -> 3 to DecodedInstruction(
                pc, op, "${moveName(op - 2)}/16", "v${word(1)}, v${word(2)}",
                destReg = "v${word(1)}", srcReg = "v${word(2)}"
            )
            0x0A -> 1 to DecodedInstruction(pc, op, "move-result", "v$aa", destReg = "v$aa")
            0x0B -> 1 to DecodedInstruction(pc, op, "move-result-wide", "v$aa", destReg = "v$aa")
            0x0C -> 1 to DecodedInstruction(pc, op, "move-result-object", "v$aa", destReg = "v$aa")
            0x0D -> 1 to DecodedInstruction(pc, op, "move-exception", "v$aa", destReg = "v$aa")
            0x0E -> 1 to DecodedInstruction(pc, op, "return-void", "", isTerminal = true)
            0x0F -> 1 to DecodedInstruction(pc, op, "return", "v$aa", srcReg = "v$aa", isTerminal = true)
            0x10 -> 1 to DecodedInstruction(pc, op, "return-wide", "v$aa", srcReg = "v$aa", isTerminal = true)
            0x11 -> 1 to DecodedInstruction(pc, op, "return-object", "v$aa", srcReg = "v$aa", isTerminal = true)
            0x12 -> {
                val signedB = if (b >= 8) b - 16 else b
                1 to DecodedInstruction(
                    pc, op, "const/4", "v$a, 0x${Integer.toHexString(signedB)}",
                    destReg = "v$a", constValue = signedB.toString()
                )
            }
            0x13 -> {
                val v = signedWord(1)
                2 to DecodedInstruction(
                    pc, op, "const/16", "v$aa, 0x${Integer.toHexString(v)}",
                    destReg = "v$aa", constValue = v.toString()
                )
            }
            0x14 -> {
                val v = int32(1)
                3 to DecodedInstruction(
                    pc, op, "const", "v$aa, 0x${Integer.toHexString(v)}",
                    destReg = "v$aa", constValue = formatIntLiteral(v)
                )
            }
            0x15 -> {
                val v = word(1) shl 16
                2 to DecodedInstruction(
                    pc, op, "const/high16", "v$aa, 0x${Integer.toHexString(v)}",
                    destReg = "v$aa", constValue = formatHigh16FloatOrInt(v)
                )
            }
            0x16 -> {
                val v = signedWord(1).toLong()
                2 to DecodedInstruction(
                    pc, op, "const-wide/16", "v$aa, 0x${java.lang.Long.toHexString(v)}",
                    destReg = "v$aa", constValue = "${v}L"
                )
            }
            0x17 -> {
                val v = int32(1).toLong()
                3 to DecodedInstruction(
                    pc, op, "const-wide/32", "v$aa, 0x${java.lang.Long.toHexString(v)}",
                    destReg = "v$aa", constValue = "${v}L"
                )
            }
            0x18 -> {
                val v = long64(1)
                5 to DecodedInstruction(
                    pc, op, "const-wide", "v$aa, 0x${java.lang.Long.toHexString(v)}",
                    destReg = "v$aa", constValue = formatLongLiteral(v)
                )
            }
            0x19 -> {
                val v = (word(1).toLong() and 0xFFFFL) shl 48
                2 to DecodedInstruction(
                    pc, op, "const-wide/high16", "v$aa, 0x${java.lang.Long.toHexString(v)}",
                    destReg = "v$aa", constValue = formatLongLiteral(v)
                )
            }
            0x1A -> {
                val full = getString(word(1))
                val escaped = escapeForCode(full, 240)
                2 to DecodedInstruction(
                    pc, op, "const-string", "v$aa, \"$escaped\"",
                    destReg = "v$aa", constString = full
                )
            }
            0x1B -> {
                val full = getString(int32(1))
                val escaped = escapeForCode(full, 240)
                3 to DecodedInstruction(
                    pc, op, "const-string/jumbo", "v$aa, \"$escaped\"",
                    destReg = "v$aa", constString = full
                )
            }
            0x1C -> {
                val type = getType(word(1))
                2 to DecodedInstruction(
                    pc, op, "const-class", "v$aa, $type",
                    destReg = "v$aa", constValue = "${descriptorToSimpleJavaType(type)}.class"
                )
            }
            0x1D -> 1 to DecodedInstruction(pc, op, "monitor-enter", "v$aa")
            0x1E -> 1 to DecodedInstruction(pc, op, "monitor-exit", "v$aa")
            0x1F -> 2 to DecodedInstruction(pc, op, "check-cast", "v$aa, ${getType(word(1))}")
            0x20 -> 2 to DecodedInstruction(pc, op, "instance-of", "v$a, v$b, ${getType(word(1))}", destReg = "v$a")
            0x21 -> 1 to DecodedInstruction(pc, op, "array-length", "v$a, v$b", destReg = "v$a")
            0x22 -> {
                val type = getType(word(1))
                2 to DecodedInstruction(
                    pc, op, "new-instance", "v$aa, $type",
                    destReg = "v$aa", newInstanceType = type
                )
            }
            0x23 -> 2 to DecodedInstruction(pc, op, "new-array", "v$a, v$b, ${getType(word(1))}", destReg = "v$a")
            0x27 -> 1 to DecodedInstruction(pc, op, "throw", "v$aa", srcReg = "v$aa", isTerminal = true)
            0x28 -> {
                val target = pc + aa.toByte().toInt()
                1 to DecodedInstruction(pc, op, "goto", ":goto_${target.toString(16)}", branchTarget = target, isTerminal = true)
            }
            0x29 -> {
                val target = pc + signedWord(1)
                2 to DecodedInstruction(pc, op, "goto/16", ":goto_${target.toString(16)}", branchTarget = target, isTerminal = true)
            }
            0x2A -> {
                val target = pc + int32(1)
                3 to DecodedInstruction(pc, op, "goto/32", ":goto_${target.toString(16)}", branchTarget = target, isTerminal = true)
            }
            in 0x32..0x37 -> {
                val target = pc + signedWord(1)
                val name = when (op) {
                    0x32 -> "if-eq"
                    0x33 -> "if-ne"
                    0x34 -> "if-lt"
                    0x35 -> "if-ge"
                    0x36 -> "if-gt"
                    else -> "if-le"
                }
                2 to DecodedInstruction(
                    pc, op, name, "v$a, v$b, :cond_${target.toString(16)}",
                    branchTarget = target, isConditionalBranch = true,
                    destReg = "v$a", srcReg = "v$b"
                )
            }
            in 0x38..0x3D -> {
                val target = pc + signedWord(1)
                val name = when (op) {
                    0x38 -> "if-eqz"
                    0x39 -> "if-nez"
                    0x3A -> "if-ltz"
                    0x3B -> "if-gez"
                    0x3C -> "if-gtz"
                    else -> "if-lez"
                }
                2 to DecodedInstruction(
                    pc, op, name, "v$aa, :cond_${target.toString(16)}",
                    branchTarget = target, isConditionalBranch = true,
                    destReg = "v$aa"
                )
            }
            in 0x52..0x5F -> {
                val field = getFieldRef(word(1))
                val isPut = op >= 0x59
                val name = fieldOpName(op)
                2 to DecodedInstruction(
                    pc, op, name, "v$a, v$b, $field",
                    accessedField = field, isFieldWrite = isPut,
                    destReg = "v$a", srcReg = "v$b"
                )
            }
            in 0x60..0x6D -> {
                val field = getFieldRef(word(1))
                val isPut = op >= 0x67
                val name = staticFieldOpName(op)
                2 to DecodedInstruction(
                    pc, op, name, "v$aa, $field",
                    accessedField = field, isFieldWrite = isPut,
                    destReg = "v$aa"
                )
            }
            in 0x6E..0x72 -> {
                val method = getMethodRef(word(1))
                val argCount = b
                val argWord = word(2)
                val regList = decodeInvokeRegList(argCount, argWord, a)
                val name = when (op) {
                    0x6E -> "invoke-virtual"
                    0x6F -> "invoke-super"
                    0x70 -> "invoke-direct"
                    0x71 -> "invoke-static"
                    else -> "invoke-interface"
                }
                3 to DecodedInstruction(
                    pc, op, name, "{${regList.joinToString(", ")}}, $method",
                    invokedMethod = method,
                    invokeRegs = regList
                )
            }
            in 0x74..0x78 -> {
                val method = getMethodRef(word(1))
                val firstReg = word(2)
                val count = aa.coerceIn(0, 16)
                val regList = (0 until count).map { "v${firstReg + it}" }
                val lastReg = firstReg + count - 1
                val name = when (op) {
                    0x74 -> "invoke-virtual/range"
                    0x75 -> "invoke-super/range"
                    0x76 -> "invoke-direct/range"
                    0x77 -> "invoke-static/range"
                    else -> "invoke-interface/range"
                }
                3 to DecodedInstruction(
                    pc, op, name, "{v$firstReg .. v$lastReg}, $method",
                    invokedMethod = method,
                    invokeRegs = regList
                )
            }
            in 0x90..0xAF -> {
                val w1 = word(1)
                2 to DecodedInstruction(pc, op, "binop-0x${op.toString(16)}", "v$aa, v${w1 and 0xFF}, v${(w1 shr 8) and 0xFF}", destReg = "v$aa")
            }
            in 0xB0..0xCF -> 1 to DecodedInstruction(pc, op, "binop/2addr-0x${op.toString(16)}", "v$a, v$b", destReg = "v$a")
            in 0xD0..0xE2 -> 2 to DecodedInstruction(pc, op, "binop/lit-0x${op.toString(16)}", "v$a, v$b, 0x${word(1).toString(16)}", destReg = "v$a")
            else -> {
                val size = opcodeWidth(op)
                size to DecodedInstruction(pc, op, "op_0x${op.toString(16).padStart(2, '0')}", "v$aa")
            }
        }
    }

    private fun decodeInvokeRegList(count: Int, w2: Int, g: Int): List<String> {
        val list = ArrayList<String>(count.coerceIn(0, 5))
        if (count >= 1) list.add("v${w2 and 0xF}")
        if (count >= 2) list.add("v${(w2 shr 4) and 0xF}")
        if (count >= 3) list.add("v${(w2 shr 8) and 0xF}")
        if (count >= 4) list.add("v${(w2 shr 12) and 0xF}")
        if (count >= 5) list.add("v$g")
        return list
    }

    private fun moveName(op: Int): String = when (op) {
        0x01 -> "move"
        0x04 -> "move-wide"
        else -> "move-object"
    }

    private fun fieldOpName(op: Int): String = when (op) {
        0x52 -> "iget"
        0x53 -> "iget-wide"
        0x54 -> "iget-object"
        0x55 -> "iget-boolean"
        0x56 -> "iget-byte"
        0x57 -> "iget-char"
        0x58 -> "iget-short"
        0x59 -> "iput"
        0x5A -> "iput-wide"
        0x5B -> "iput-object"
        0x5C -> "iput-boolean"
        0x5D -> "iput-byte"
        0x5E -> "iput-char"
        else -> "iput-short"
    }

    private fun staticFieldOpName(op: Int): String = when (op) {
        0x60 -> "sget"
        0x61 -> "sget-wide"
        0x62 -> "sget-object"
        0x63 -> "sget-boolean"
        0x64 -> "sget-byte"
        0x65 -> "sget-char"
        0x66 -> "sget-short"
        0x67 -> "sput"
        0x68 -> "sput-wide"
        0x69 -> "sput-object"
        0x6A -> "sput-boolean"
        0x6B -> "sput-byte"
        0x6C -> "sput-char"
        else -> "sput-short"
    }

    private fun opcodeWidth(op: Int): Int = when (op) {
        0x03, 0x06, 0x09, 0x17, 0x1B, 0x24, 0x25, 0x26, 0x2B, 0x2C, 0xFA, 0xFB, 0xFC, 0xFD -> 3
        0x18 -> 5
        0x19, 0x2D, 0x2E, 0x2F, 0x30, 0x31 -> 2
        in 0x44..0x51 -> 2
        in 0x7B..0x8F -> 1
        else -> 1
    }

    private fun buildSmali(
        summary: DexClassSummary,
        fields: List<DexFieldInfo>,
        methods: List<DexMethodInfo>
    ): String {
        val sb = StringBuilder()
        sb.append(".class ").append(accessFlagsString(summary.accessFlags)).append(summary.descriptor).append('\n')
        sb.append(".super ").append(summary.superDescriptor).append('\n')
        sb.append('\n')

        if (fields.isNotEmpty()) {
            sb.append("# fields\n")
            for (f in fields) {
                sb.append(".field ").append(accessFlagsString(f.accessFlags))
                    .append(f.name).append(':').append(f.type)
                if (f.initialValue != null) {
                    sb.append(" = ").append(f.initialValue)
                }
                sb.append('\n')
            }
            sb.append('\n')
        }

        if (methods.isNotEmpty()) {
            sb.append("# methods\n")
            for (m in methods) {
                sb.append(".method ").append(accessFlagsString(m.accessFlags))
                    .append(m.signature).append('\n')
                if (m.registersSize > 0) {
                    sb.append("    .registers ").append(m.registersSize).append('\n')
                }
                val targets = m.instructions.mapNotNull { it.branchTarget }.toSet()
                for (ins in m.instructions) {
                    if (ins.offset in targets) {
                        sb.append("\n    :cond_").append(ins.offset.toString(16)).append('\n')
                    }
                    sb.append("    ").append(ins.mnemonic)
                    if (ins.operands.isNotEmpty()) {
                        sb.append(' ').append(ins.operands)
                    }
                    sb.append('\n')
                }
                sb.append(".end method\n\n")
            }
        }
        return sb.toString()
    }

    private fun buildJavaPseudo(
        summary: DexClassSummary,
        fields: List<DexFieldInfo>,
        methods: List<DexMethodInfo>
    ): String {
        val sb = StringBuilder()
        if (summary.packageName != "(default)") {
            sb.append("package ").append(summary.packageName).append(";\n\n")
        }
        val superClean = descriptorToSimpleJavaType(summary.superDescriptor)
        sb.append(accessFlagsString(summary.accessFlags))
            .append("class ").append(summary.simpleName)
        if (superClean != "Object") {
            sb.append(" extends ").append(superClean)
        }
        sb.append(" {\n")

        for (f in fields) {
            sb.append("    ").append(accessFlagsString(f.accessFlags))
                .append(descriptorToSimpleJavaType(f.type)).append(' ').append(f.name)
            if (f.initialValue != null) {
                sb.append(" = ").append(f.initialValue)
            }
            sb.append(";\n")
        }
        if (fields.isNotEmpty()) sb.append('\n')

        for (m in methods) {
            val paramsStr = m.parameters.mapIndexed { idx, p ->
                "${descriptorToSimpleJavaType(p)} p$idx"
            }.joinToString(", ")
            val isCtor = m.name == "<init>" || m.name == "<clinit>"
            val headerName = if (m.name == "<init>") summary.simpleName else m.name
            sb.append("    ").append(accessFlagsString(m.accessFlags))
            if (!isCtor) {
                sb.append(descriptorToSimpleJavaType(m.returnType)).append(' ')
            }
            sb.append(headerName).append('(').append(paramsStr).append(") {\n")

            if (m.instructions.isEmpty()) {
                sb.append("        // native or abstract\n")
            } else {
                emitPseudoMethodBody(sb, m)
            }
            sb.append("    }\n\n")
        }
        sb.append("}\n")
        return sb.toString()
    }

    private fun emitPseudoMethodBody(sb: StringBuilder, m: DexMethodInfo) {
        val regValues = HashMap<String, String>()
        val isStatic = (m.accessFlags and 0x0008) != 0
        var regCursor = (m.registersSize - m.insSize).coerceAtLeast(0)
        if (!isStatic && m.insSize > 0) {
            regValues["v$regCursor"] = "this"
            regCursor++
        }
        for ((idx, paramType) in m.parameters.withIndex()) {
            regValues["v$regCursor"] = "p$idx"
            regCursor += if (paramType == "J" || paramType == "D") 2 else 1
        }

        val branchTargets = m.instructions.mapNotNull { it.branchTarget }.toSet()
        var lastCallSummary: String? = null
        var emittedLines = 0
        val maxLines = 450

        fun resolveReg(reg: String?): String {
            if (reg == null) return "?"
            return regValues[reg] ?: reg
        }

        for (ins in m.instructions) {
            if (emittedLines >= maxLines) {
                sb.append("        // ... (").append(m.instructions.size).append(" total bytecode instructions)\n")
                break
            }
            if (ins.offset in branchTargets) {
                sb.append("      L_").append(ins.offset.toString(16)).append(":\n")
                emittedLines++
            }

            when {
                ins.constString != null && ins.destReg != null -> {
                    val lit = "\"${escapeForCode(ins.constString, 500)}\""
                    regValues[ins.destReg] = lit
                }
                ins.constValue != null && ins.destReg != null -> {
                    regValues[ins.destReg] = ins.constValue
                }
                ins.newInstanceType != null && ins.destReg != null -> {
                    regValues[ins.destReg] = "new ${descriptorToSimpleJavaType(ins.newInstanceType)}"
                }
                ins.mnemonic.startsWith("move-result") && ins.destReg != null -> {
                    regValues[ins.destReg] = lastCallSummary ?: ins.destReg
                    lastCallSummary = null
                }
                ins.mnemonic.startsWith("move") && ins.destReg != null && ins.srcReg != null -> {
                    regValues[ins.destReg] = resolveReg(ins.srcReg)
                }
                ins.invokedMethod != null -> {
                    val ref = ins.invokedMethod
                    val owner = descriptorToSimpleJavaType(ref.substringBefore("->"))
                    val member = ref.substringAfter("->").substringBefore("(")
                    // Skip noisy Kotlin Intrinsics null checks and Compose sourceInformation metadata
                    if (owner == "Intrinsics" && (member == "checkNotNullParameter" || member == "checkNotNullExpressionValue")) {
                        continue
                    }
                    if (owner == "ComposerKt" && (member == "sourceInformation" || member == "sourceInformationMarkerStart" || member == "sourceInformationMarkerEnd" || member == "traceEventStart" || member == "traceEventEnd")) {
                        continue
                    }
                    val isStaticInvoke = ins.mnemonic.startsWith("invoke-static")
                    val args = if (!isStaticInvoke && ins.invokeRegs.isNotEmpty()) {
                        ins.invokeRegs.drop(1).map { resolveReg(it) }
                    } else {
                        ins.invokeRegs.map { resolveReg(it) }
                    }
                    val receiver = if (!isStaticInvoke && ins.invokeRegs.isNotEmpty()) {
                        resolveReg(ins.invokeRegs.first())
                    } else null

                    val argPreview = args.joinToString(", ").let {
                        if (it.length > 260) it.take(257) + "..." else it
                    }
                    val callExpr = if (member == "<init>") {
                        "new $owner($argPreview)"
                    } else if (receiver != null && receiver != "this" && !receiver.startsWith("v") && receiver.length <= 36) {
                        "$receiver.$member($argPreview)"
                    } else {
                        "$owner.$member($argPreview)"
                    }
                    sb.append("        ").append(callExpr).append(";\n")
                    lastCallSummary = "$owner.$member(..)".take(42)
                    if (member == "<init>" && ins.invokeRegs.isNotEmpty()) {
                        regValues[ins.invokeRegs.first()] = "new $owner(..)"
                    }
                    emittedLines++
                }
                ins.accessedField != null && ins.isFieldWrite -> {
                    val fieldName = ins.accessedField.substringAfter("->").substringBefore(":")
                    val valueStr = resolveReg(ins.destReg)
                    sb.append("        ").append(fieldName).append(" = ").append(valueStr).append(";\n")
                    emittedLines++
                }
                ins.accessedField != null -> {
                    val owner = descriptorToSimpleJavaType(ins.accessedField.substringBefore("->"))
                    val fieldName = ins.accessedField.substringAfter("->").substringBefore(":")
                    val expr = if (ins.mnemonic.startsWith("sget")) "$owner.$fieldName" else fieldName
                    if (ins.destReg != null) {
                        regValues[ins.destReg] = expr
                    }
                }
                ins.isConditionalBranch -> {
                    val lhs = resolveReg(ins.destReg)
                    val cond = if (ins.srcReg != null) {
                        "${ins.mnemonic}($lhs, ${resolveReg(ins.srcReg)})"
                    } else {
                        "${ins.mnemonic}($lhs)"
                    }
                    sb.append("        if (").append(cond).append(") goto L_")
                        .append(ins.branchTarget?.toString(16)).append(";\n")
                    emittedLines++
                }
                ins.mnemonic.startsWith("return") -> {
                    val retVal = if (ins.srcReg != null) " ${resolveReg(ins.srcReg)}" else ""
                    sb.append("        return").append(retVal).append(";\n")
                    emittedLines++
                }
                ins.mnemonic == "throw" -> {
                    sb.append("        throw ").append(resolveReg(ins.srcReg)).append(";\n")
                    emittedLines++
                }
            }
        }
    }

    fun buildMethodCfg(method: DexMethodInfo): CfgGraph {
        val insns = method.instructions
        if (insns.isEmpty()) {
            return CfgGraph(
                methodName = method.name,
                blocks = listOf(CfgBlock(0, 0, listOf("// empty or native method"))),
                edges = emptyList()
            )
        }

        val leaders = sortedSetOf(insns.first().offset)
        val offsetToIndex = insns.withIndex().associate { (i, ins) -> ins.offset to i }

        for ((idx, ins) in insns.withIndex()) {
            if (ins.branchTarget != null && ins.branchTarget in offsetToIndex) {
                leaders.add(ins.branchTarget)
                if (idx + 1 < insns.size) leaders.add(insns[idx + 1].offset)
            } else if (ins.isTerminal && idx + 1 < insns.size) {
                leaders.add(insns[idx + 1].offset)
            }
        }

        val leaderList = leaders.toList()
        val blocks = mutableListOf<CfgBlock>()
        val offsetToBlockId = HashMap<Int, Int>()

        for ((bIdx, startOff) in leaderList.withIndex()) {
            offsetToBlockId[startOff] = bIdx
            val nextStart = leaderList.getOrNull(bIdx + 1) ?: Int.MAX_VALUE
            val blockInsns = insns.filter { it.offset in startOff until nextStart }
            val lines = blockInsns.map { ins ->
                if (ins.operands.isEmpty()) ins.mnemonic else "${ins.mnemonic} ${ins.operands}"
            }
            blocks.add(CfgBlock(bIdx, startOff, lines))
        }

        val edges = mutableListOf<CfgEdge>()
        for ((bIdx, startOff) in leaderList.withIndex()) {
            val nextStart = leaderList.getOrNull(bIdx + 1) ?: Int.MAX_VALUE
            val lastInsn = insns.lastOrNull { it.offset in startOff until nextStart } ?: continue
            val targetId = lastInsn.branchTarget?.let { offsetToBlockId[it] }
            val fallthroughId = if (bIdx + 1 < blocks.size) bIdx + 1 else null

            when {
                lastInsn.isConditionalBranch -> {
                    if (targetId != null) edges.add(CfgEdge(bIdx, targetId, CfgEdgeType.TRUE_BRANCH))
                    if (fallthroughId != null) edges.add(CfgEdge(bIdx, fallthroughId, CfgEdgeType.FALSE_BRANCH))
                }
                lastInsn.branchTarget != null -> {
                    if (targetId != null) edges.add(CfgEdge(bIdx, targetId, CfgEdgeType.JUMP))
                }
                !lastInsn.isTerminal && fallthroughId != null -> {
                    edges.add(CfgEdge(bIdx, fallthroughId, CfgEdgeType.FALLTHROUGH))
                }
            }
        }

        return CfgGraph(method.name, blocks, edges)
    }

    fun buildClassCallGraph(detail: DexClassDetail, maxDepth: Int): CallGraphData {
        val rootId = "target:${detail.summary.descriptor}"
        val nodes = LinkedHashMap<String, CallNode>()
        val edges = mutableListOf<CallEdge>()

        nodes[rootId] = CallNode(
            id = rootId,
            title = detail.summary.simpleName,
            subtitle = detail.summary.packageName,
            detail = "${detail.methods.size} methods",
            depth = 0
        )

        var added = 0
        val maxNodes = (maxDepth.coerceIn(1, 5) * 6).coerceAtMost(24)

        for (method in detail.methods) {
            if (added >= maxNodes) break
            val mId = "m:${method.signature}"
            if (mId !in nodes) {
                nodes[mId] = CallNode(
                    id = mId,
                    title = method.name,
                    subtitle = detail.summary.simpleName,
                    detail = method.signature.take(36),
                    depth = 1
                )
                edges.add(CallEdge(mId, rootId, CallEdgeKind.CALL))
                added++
            }
            if (maxDepth >= 2) {
                for (ins in method.instructions) {
                    if (added >= maxNodes) break
                    val fieldRef = ins.accessedField
                    val invokeRef = ins.invokedMethod
                    if (fieldRef != null) {
                        val fId = "f:$fieldRef"
                        if (fId !in nodes) {
                            val owner = descriptorToSimpleJavaType(fieldRef.substringBefore("->"))
                            val fname = fieldRef.substringAfter("->")
                            nodes[fId] = CallNode(
                                id = fId,
                                title = fname.substringBefore(":"),
                                subtitle = owner,
                                detail = ins.mnemonic,
                                depth = 2
                            )
                            added++
                        }
                        val kind = if (ins.isFieldWrite) CallEdgeKind.WRITE else CallEdgeKind.READ
                        if (edges.none { it.fromId == fId && it.toId == mId && it.kind == kind }) {
                            edges.add(CallEdge(fId, mId, kind))
                        }
                    } else if (invokeRef != null && maxDepth >= 3) {
                        val cId = "c:$invokeRef"
                        if (cId !in nodes) {
                            val owner = descriptorToSimpleJavaType(invokeRef.substringBefore("->"))
                            val mname = invokeRef.substringAfter("->").substringBefore("(")
                            nodes[cId] = CallNode(
                                id = cId,
                                title = "$mname()",
                                subtitle = owner,
                                detail = ins.mnemonic,
                                depth = 2
                            )
                            added++
                        }
                        if (edges.none { it.fromId == cId && it.toId == mId }) {
                            edges.add(CallEdge(cId, mId, CallEdgeKind.CALL))
                        }
                    }
                }
            }
        }

        return CallGraphData(
            targetTitle = detail.summary.simpleName,
            nodes = nodes.values.toList(),
            edges = edges
        )
    }

    private fun readUleb128(start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var pos = start
        while (pos < bytes.size && shift < 35) {
            val b = bytes[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result to pos
    }

    private fun readMutf8(start: Int): String {
        var end = start
        while (end < bytes.size && bytes[end].toInt() != 0 && end - start < 16_384) {
            end++
        }
        return String(bytes, start, (end - start).coerceAtLeast(0), Charsets.UTF_8)
    }

    companion object {
        fun formatIntLiteral(v: Int): String {
            // Detect likely ARGB color or bitmask ints vs standard integers/floats
            val u = v.toLong() and 0xFFFFFFFFL
            return if (u in 0xFF000000L..0xFFFFFFFFL || u in 0x7F000000L..0x7FFFFFFFL) {
                "0x%08X".format(v)
            } else {
                v.toString()
            }
        }

        fun formatHigh16FloatOrInt(v: Int): String {
            val f = java.lang.Float.intBitsToFloat(v)
            return if (f.isFinite() && f != 0f && kotlin.math.abs(f) in 0.001f..100000f) {
                "${f}f"
            } else {
                "0x${Integer.toHexString(v)}"
            }
        }

        fun formatLongLiteral(v: Long): String {
            val abs = kotlin.math.abs(v)
            // Compose Color(0xFF......) packs ARGB into upper 32 bits of a ULong
            if ((v and 0xFFFFFFFFL) == 0L && (v ushr 32) in 0x01000000L..0xFFFFFFFFL) {
                return "Color(0x%08X)".format((v ushr 32).toInt())
            }
            if (abs < 1_000_000_000_000L) {
                return "${v}L"
            }
            val d = java.lang.Double.longBitsToDouble(v)
            return if (d.isFinite() && kotlin.math.abs(d) in 1e-6..1e9) {
                d.toString()
            } else {
                "0x${java.lang.Long.toHexString(v)}L"
            }
        }

        fun escapeForCode(raw: String, maxLen: Int): String {
            val cleaned = raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            return if (cleaned.length > maxLen) cleaned.take(maxLen - 3) + "..." else cleaned
        }

        fun descriptorToClassName(desc: String): String {
            if (desc.startsWith("L") && desc.endsWith(";")) {
                return desc.substring(1, desc.length - 1).replace('/', '.')
            }
            return desc
        }

        fun descriptorToSimpleJavaType(desc: String): String = when {
            desc == "V" -> "void"
            desc == "Z" -> "boolean"
            desc == "B" -> "byte"
            desc == "S" -> "short"
            desc == "C" -> "char"
            desc == "I" -> "int"
            desc == "J" -> "long"
            desc == "F" -> "float"
            desc == "D" -> "double"
            desc.startsWith("[") -> "${descriptorToSimpleJavaType(desc.substring(1))}[]"
            desc.startsWith("L") && desc.endsWith(";") -> {
                val full = desc.substring(1, desc.length - 1).replace('/', '.')
                full.substringAfterLast('.')
            }
            else -> desc
        }

        fun accessFlagsString(flags: Int): String {
            val parts = mutableListOf<String>()
            if ((flags and 0x0001) != 0) parts.add("public")
            if ((flags and 0x0002) != 0) parts.add("private")
            if ((flags and 0x0004) != 0) parts.add("protected")
            if ((flags and 0x0008) != 0) parts.add("static")
            if ((flags and 0x0010) != 0) parts.add("final")
            if ((flags and 0x0400) != 0) parts.add("abstract")
            return if (parts.isEmpty()) "" else parts.joinToString(" ") + " "
        }
    }
}
