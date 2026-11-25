package com.androidreverse.ide.core.binary

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Generates a complete plain-text (.txt) app blueprint designed to be uploaded
 * directly into Google AI Studio, Gemini, ChatGPT, or Claude for deep analysis or rebuilding.
 */
object AiReportExporter {

    private const val MAX_TOTAL_CODE_CHARS = 4_500_000
    private const val MAX_CHARS_PER_CLASS = 35_000
    private const val MAX_ASSET_FILE_BYTES = 48 * 1024

    private val libraryPackagePrefixes = listOf(
        "androidx.",
        "android.",
        "kotlin.",
        "kotlinx.",
        "java.",
        "javax.",
        "dalvik.",
        "sun.",
        "com.google.android.",
        "com.google.firebase.",
        "com.google.gson.",
        "com.google.common.",
        "com.google.protobuf.",
        "com.google.crypto.",
        "com.google.errorprone.",
        "com.google.j2objc.",
        "okhttp3.",
        "okio.",
        "retrofit2.",
        "coil.",
        "coil3.",
        "io.ktor.",
        "org.jetbrains.",
        "org.intellij.",
        "org.json.",
        "org.apache.",
        "org.slf4j.",
        "org.chromium.",
        "org.conscrypt.",
        "com.squareup.",
        "_COROUTINE"
    )

    private val boilerplateLayoutPrefixes = listOf(
        "res/layout/notification_",
        "res/layout-v21/notification_",
        "res/layout/abc_",
        "res/layout/m3_",
        "res/layout/mtrl_",
        "res/layout/design_",
        "res/layout/select_dialog_",
        "res/layout/support_simple_",
        "res/layout/custom_dialog.xml",
        "res/layout/ime_"
    )

    suspend fun generateReportText(workspace: LoadedWorkspace): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder(256_000)

        var manifestDecoded = ""
        var manifestPackage = workspace.packageName
        var arscSummary: ArscSummary? = null
        val libraryVersions = LinkedHashMap<String, String>()
        val decodedXmlFiles = LinkedHashMap<String, String>()
        val textAssets = LinkedHashMap<String, String>()
        val nativeEntries = workspace.entries.filter { it.kind == ArchiveNodeKind.ELF }
        val nativeLines = mutableListOf<String>()
        var isUnityIl2Cpp = false
        var isFlutterApp = false

        runCatching {
            ZipFile(workspace.packageFile).use { zip ->
                // 1. Parse resources.arsc first so we can resolve @0x7f... IDs in Manifest & XMLs
                zip.getEntry("resources.arsc")?.let { arscEntry ->
                    runCatching {
                        val bytes = zip.getInputStream(arscEntry).use { it.readBytes() }
                        arscSummary = ArscParser.parse(bytes)
                    }
                }

                // 2. Decode AndroidManifest.xml
                zip.getEntry("AndroidManifest.xml")?.let { manifestEntry ->
                    runCatching {
                        val raw = AxmlDecoder.decode(zip.getInputStream(manifestEntry).use { it.readBytes() })
                        manifestDecoded = annotateResourceIds(raw, arscSummary?.idToRef.orEmpty())
                        if (manifestPackage.isBlank()) {
                            Regex("package=\"([^\"]+)\"").find(raw)?.let { m ->
                                manifestPackage = m.groupValues[1]
                            }
                        }
                    }.onFailure {
                        manifestDecoded = "(Failed to decode AndroidManifest.xml: ${it.message})"
                    }
                }

                // 3. Read library versions from META-INF/*.version and app-metadata.properties
                for (entryMeta in workspace.entries) {
                    val p = entryMeta.path
                    if ((p.startsWith("META-INF/") && p.endsWith(".version") && entryMeta.size <= 512) ||
                        p == "META-INF/com/android/build/gradle/app-metadata.properties"
                    ) {
                        val ze = zip.getEntry(p) ?: continue
                        runCatching {
                            val value = zip.getInputStream(ze).use { it.readBytes() }
                                .toString(Charsets.UTF_8).trim().replace("\n", " | ")
                            if (value.isNotEmpty()) {
                                val label = p.removePrefix("META-INF/").removeSuffix(".version")
                                libraryVersions[label] = value
                            }
                        }
                    }
                }

                // 4. Decode app XML configs (res/xml/*.xml) and non-boilerplate layouts
                for (entryMeta in workspace.entries) {
                    val p = entryMeta.path
                    val isAppXmlConfig = p.startsWith("res/xml/") && p.endsWith(".xml")
                    val isAppLayout = (p.startsWith("res/layout/") || p.startsWith("res/layout-")) &&
                        p.endsWith(".xml") &&
                        boilerplateLayoutPrefixes.none { p.startsWith(it) }
                    if ((isAppXmlConfig || isAppLayout) && entryMeta.size <= 64 * 1024 && decodedXmlFiles.size < 45) {
                        val ze = zip.getEntry(p) ?: continue
                        runCatching {
                            val decoded = AxmlDecoder.decode(zip.getInputStream(ze).use { it.readBytes() })
                            decodedXmlFiles[p] = annotateResourceIds(decoded, arscSummary?.idToRef.orEmpty())
                        }
                    }
                }

                // 5. Read text/JSON/JS/HTML/XML files inside assets/ (and detect Unity / Flutter)
                for (entryMeta in workspace.entries) {
                    val p = entryMeta.path
                    if (p.contains("global-metadata.dat") || p.endsWith("libil2cpp.so")) {
                        isUnityIl2Cpp = true
                    }
                    if (p.endsWith("libflutter.so") || p.endsWith("libapp.so")) {
                        isFlutterApp = true
                    }
                    if (p.startsWith("assets/") && entryMeta.size in 1..MAX_ASSET_FILE_BYTES && textAssets.size < 35) {
                        val lower = p.lowercase()
                        if (lower.endsWith(".json") || lower.endsWith(".txt") || lower.endsWith(".js") ||
                            lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".xml") ||
                            lower.endsWith(".properties") || lower.endsWith(".md") || lower.endsWith(".yaml")
                        ) {
                            val ze = zip.getEntry(p) ?: continue
                            runCatching {
                                val content = zip.getInputStream(ze).use { it.readBytes() }.toString(Charsets.UTF_8)
                                if (content.isNotBlank()) {
                                    textAssets[p] = content.take(12_000)
                                }
                            }
                        }
                    }
                }

                // 6. Inspect ELF native libraries
                for (entryMeta in nativeEntries.take(40)) {
                    val ze = zip.getEntry(entryMeta.path)
                    val elfInfo = if (ze != null && entryMeta.size <= 8L * 1024L * 1024L) {
                        runCatching {
                            ElfParser.analyze(zip.getInputStream(ze).use { it.readBytes() })
                        }.getOrNull()
                    } else null
                    val line = buildString {
                        append("- ").append(entryMeta.path)
                            .append(" (").append(ApkWorkspaceEngine.formatSize(entryMeta.size)).append(')')
                        if (elfInfo != null) {
                            append(" | ").append(elfInfo.machine)
                                .append(if (elfInfo.is64Bit) " 64-bit" else " 32-bit")
                                .append(" | PIE=").append(elfInfo.isPie)
                                .append(" NX=").append(elfInfo.hasNx)
                                .append(" RELRO=").append(elfInfo.hasRelro)
                        }
                    }
                    nativeLines.add(line)
                }
            }
        }

        // 7. Determine app-owned packages & select ALL real app classes
        val appPrefixes = detectAppPackagePrefixes(manifestPackage, manifestDecoded, workspace.dexClasses)
        val allAppScopedClasses = if (appPrefixes.isNotEmpty()) {
            workspace.dexClasses.filter { cls ->
                appPrefixes.any { prefix ->
                    cls.packageName == prefix || cls.packageName.startsWith("$prefix.")
                }
            }
        } else {
            workspace.dexClasses.filterNot { isLibraryPackage(it.packageName) }
        }

        val realAppClasses = allAppScopedClasses
            .filterNot { isCompilerBoilerplateClass(it) }
            .sortedWith(
                compareBy<DexClassSummary> { packageOrderPriority(it.packageName) }
                    .thenBy { it.packageName }
                    .thenBy { it.simpleName.contains('$') }
                    .thenBy { it.simpleName }
            )

        val skippedSyntheticCount = (allAppScopedClasses.size - realAppClasses.size).coerceAtLeast(0)

        // Decompile EVERY real app class & collect app-owned string literals
        val codeSection = StringBuilder(200_000)
        val appUrlsAndEndpoints = LinkedHashSet<String>()
        val appLongStringsAndPrompts = LinkedHashSet<String>()
        var exportedClassCount = 0
        var truncatedExport = false

        for (cls in realAppClasses) {
            if (codeSection.length >= MAX_TOTAL_CODE_CHARS) {
                truncatedExport = true
                break
            }
            val parser = workspace.dexParsers[cls.dexName] ?: continue
            val detail = runCatching { parser.inspectClass(cls) }.getOrNull() ?: continue

            for (lit in detail.stringLiterals) {
                classifyAppStringLiteral(lit, appUrlsAndEndpoints, appLongStringsAndPrompts)
            }

            val snippet = if (detail.javaPseudoCode.length > MAX_CHARS_PER_CLASS) {
                detail.javaPseudoCode.take(MAX_CHARS_PER_CLASS) + "\n// ... [class output trimmed at $MAX_CHARS_PER_CLASS chars]\n"
            } else {
                detail.javaPseudoCode
            }
            codeSection.append("// ============================================================\n")
            codeSection.append("// CLASS: ").append(cls.packageName).append('.').append(cls.simpleName)
                .append(" (").append(cls.dexName).append(")\n")
            codeSection.append("// ============================================================\n")
            codeSection.append(snippet).append('\n')
            exportedClassCount++
        }

        // Also scan DEX string pools for any extra clean URLs/endpoints not already captured
        for (parser in workspace.dexParsers.values) {
            if (appUrlsAndEndpoints.size >= 180) break
            for (s in parser.extractInterestingStrings(limit = 60)) {
                if (appUrlsAndEndpoints.size >= 180) break
                appUrlsAndEndpoints.add(s)
            }
        }

        val coveragePct = if (realAppClasses.isEmpty()) 100 else (exportedClassCount * 100) / realAppClasses.size

        // ====================================================================
        // ASSEMBLE FINAL BLUEPRINT REPORT
        // ====================================================================
        sb.append("====================================================================\n")
        sb.append("ANDROID COMPLETE APP BLUEPRINT REPORT\n")
        sb.append("====================================================================\n")
        sb.append("App Name: ").append(workspace.displayName).append('\n')
        if (manifestPackage.isNotBlank()) {
            sb.append("Package Name: ").append(manifestPackage).append('\n')
        }
        if (appPrefixes.isNotEmpty()) {
            sb.append("Detected App Code Roots: ").append(appPrefixes.joinToString(", ")).append('\n')
        }
        sb.append("APK Size: ").append(ApkWorkspaceEngine.formatSize(workspace.packageSizeBytes)).append('\n')
        sb.append("Total Archive Files: ").append(workspace.entries.size).append('\n')
        sb.append("Total Indexed DEX Classes: ").append(workspace.dexClasses.size).append('\n')
        sb.append("App-Owned Real Classes Exported: ").append(exportedClassCount)
            .append(" / ").append(realAppClasses.size)
            .append(" (").append(coveragePct).append("%)\n")
        sb.append("Skipped Compiler Lambdas / R Classes: ").append(skippedSyntheticCount).append('\n')
        sb.append("App String Resources Decoded: ").append(arscSummary?.strings?.size ?: 0).append('\n')
        sb.append("Decoded XML Configs & Assets: ").append(decodedXmlFiles.size + textAssets.size).append('\n')
        sb.append("Native Libraries (.so): ").append(workspace.nativeLibCount).append('\n')
        if (isUnityIl2Cpp) {
            sb.append("Runtime Warning: Unity IL2CPP detected (core game code is compiled in native libil2cpp.so)\n")
        }
        if (isFlutterApp) {
            sb.append("Runtime Warning: Flutter detected (Dart UI/logic is compiled in native libapp.so)\n")
        }
        sb.append("Export Truncated: ").append(if (truncatedExport) "YES (hit 4.5 MB text limit)" else "NO (complete)").append('\n')
        sb.append('\n')

        // 1. AndroidManifest.xml
        sb.append("--------------------------------------------------------------------\n")
        sb.append("1. ANDROID MANIFEST (DECODED)\n")
        sb.append("--------------------------------------------------------------------\n")
        sb.append(manifestDecoded.ifBlank { "(AndroidManifest.xml not found)" }).append("\n\n")

        // 2. App Resources (resources.arsc)
        arscSummary?.let { res ->
            sb.append("--------------------------------------------------------------------\n")
            sb.append("2. APP RESOURCES (resources.arsc)\n")
            sb.append("--------------------------------------------------------------------\n")
            if (res.strings.isNotEmpty()) {
                sb.append("[Strings (${res.strings.size})]\n")
                for ((k, v) in res.strings.entries.take(400)) {
                    sb.append("  ").append(k).append(" = \"").append(DexFileParser.escapeForCode(v, 500)).append("\"\n")
                }
                sb.append('\n')
            }
            if (res.colors.isNotEmpty()) {
                sb.append("[Colors (${res.colors.size})]\n")
                for ((k, v) in res.colors.entries.take(150)) {
                    sb.append("  ").append(k).append(" = ").append(v).append('\n')
                }
                sb.append('\n')
            }
            if (res.dimens.isNotEmpty()) {
                sb.append("[Dimens (${res.dimens.size})]\n")
                for ((k, v) in res.dimens.entries.take(80)) {
                    sb.append("  ").append(k).append(" = ").append(v).append('\n')
                }
                sb.append('\n')
            }
            if (res.fonts.isNotEmpty()) {
                sb.append("[Fonts (${res.fonts.size})]\n")
                for (f in res.fonts.take(40)) {
                    sb.append("  ").append(f).append('\n')
                }
                sb.append('\n')
            }
            if (res.drawables.isNotEmpty()) {
                sb.append("[Drawables & Icons (${res.drawables.size})]\n")
                for (d in res.drawables.take(120)) {
                    sb.append("  ").append(d).append('\n')
                }
                sb.append('\n')
            }
            if (res.xmlResources.isNotEmpty()) {
                sb.append("[XML Resources (${res.xmlResources.size})]\n")
                for (x in res.xmlResources.take(60)) {
                    sb.append("  ").append(x).append('\n')
                }
                sb.append('\n')
            }
        }

        // 3. Decoded XML Configs, Layouts & Text Assets
        if (decodedXmlFiles.isNotEmpty() || textAssets.isNotEmpty()) {
            sb.append("--------------------------------------------------------------------\n")
            sb.append("3. DECODED APP XML CONFIGS, LAYOUTS & TEXT ASSETS\n")
            sb.append("--------------------------------------------------------------------\n")
            for ((path, xml) in decodedXmlFiles) {
                sb.append("--- FILE: ").append(path).append(" ---\n")
                sb.append(xml.trim()).append("\n\n")
            }
            for ((path, content) in textAssets) {
                sb.append("--- ASSET: ").append(path).append(" ---\n")
                sb.append(content.trim()).append("\n\n")
            }
        }

        // 4. Detected Libraries & Dependencies
        val libGroups = workspace.dexClasses
            .filter { isLibraryPackage(it.packageName) }
            .groupBy { it.packageName.split('.').take(2).joinToString(".") }
            .entries.sortedByDescending { it.value.size }
        if (libraryVersions.isNotEmpty() || libGroups.isNotEmpty()) {
            sb.append("--------------------------------------------------------------------\n")
            sb.append("4. DETECTED LIBRARIES & VERSIONS\n")
            sb.append("--------------------------------------------------------------------\n")
            for ((k, v) in libraryVersions) {
                sb.append("- ").append(k).append(" = ").append(v).append('\n')
            }
            if (libGroups.isNotEmpty()) {
                sb.append("Third-Party DEX Package Groups:\n")
                for ((prefix, list) in libGroups.take(35)) {
                    sb.append("  ").append(prefix).append(".* (").append(list.size).append(" classes)\n")
                }
            }
            sb.append('\n')
        }

        // 5. Native Libraries & Signing Certificates
        if (nativeLines.isNotEmpty() || workspace.certificates.isNotEmpty()) {
            sb.append("--------------------------------------------------------------------\n")
            sb.append("5. NATIVE LIBRARIES & SIGNING CERTIFICATES\n")
            sb.append("--------------------------------------------------------------------\n")
            for (line in nativeLines) {
                sb.append(line).append('\n')
            }
            for (cert in workspace.certificates) {
                sb.append("- Cert [").append(cert.source).append("] ")
                    .append(cert.subject).append(" | ").append(cert.algorithm)
                    .append(" | SHA-256: ").append(cert.sha256).append('\n')
            }
            sb.append('\n')
        }

        // 6. App URLs, Endpoints, Prompts & Key String Constants
        if (appUrlsAndEndpoints.isNotEmpty() || appLongStringsAndPrompts.isNotEmpty()) {
            sb.append("--------------------------------------------------------------------\n")
            sb.append("6. APP URLS, ENDPOINTS, PROMPTS & KEY STRINGS\n")
            sb.append("--------------------------------------------------------------------\n")
            if (appUrlsAndEndpoints.isNotEmpty()) {
                sb.append("[URLs, WebSockets & Endpoints]\n")
                for (u in appUrlsAndEndpoints.take(180)) {
                    sb.append("- ").append(u).append('\n')
                }
                sb.append('\n')
            }
            if (appLongStringsAndPrompts.isNotEmpty()) {
                sb.append("[Prompts, Scripts, Messages & Templates from App Code]\n")
                for (p in appLongStringsAndPrompts.take(250)) {
                    sb.append("- \"").append(DexFileParser.escapeForCode(p, 1200)).append("\"\n")
                }
                sb.append('\n')
            }
        }

        // 7. Complete App Package & Class Map (ZERO truncation on app packages)
        sb.append("--------------------------------------------------------------------\n")
        sb.append("7. COMPLETE APP PACKAGE & CLASS MAP\n")
        sb.append("--------------------------------------------------------------------\n")
        val appByPackage = realAppClasses.groupBy { it.packageName }
        if (appByPackage.isEmpty()) {
            sb.append("(No dedicated Java/Kotlin app classes found in DEX.)\n\n")
        } else {
            for ((pkg, classes) in appByPackage) {
                sb.append("Package: ").append(pkg).append(" (").append(classes.size).append(" real classes)\n")
                sb.append("  ").append(classes.joinToString(", ") { it.simpleName }).append('\n')
            }
            sb.append('\n')
        }

        // 8. Full Decompiled App Source Blueprint
        sb.append("--------------------------------------------------------------------\n")
        sb.append("8. FULL APP SOURCE BLUEPRINT (CONSTANT-PROPAGATED PSEUDO-JAVA)\n")
        sb.append("--------------------------------------------------------------------\n")
        if (codeSection.isEmpty()) {
            sb.append("(No app-owned DEX classes available to decompile.)\n")
        } else {
            sb.append(codeSection)
        }

        sb.toString()
    }

    suspend fun exportReportToUri(
        context: Context,
        workspace: LoadedWorkspace,
        targetUri: Uri
    ): Int = withContext(Dispatchers.IO) {
        val report = generateReportText(workspace)
        val bytes = report.toByteArray(Charsets.UTF_8)
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            out.write(bytes)
        } ?: error("Could not write report file")
        bytes.size
    }

    suspend fun createShareableReportFile(
        context: Context,
        workspace: LoadedWorkspace
    ): File = withContext(Dispatchers.IO) {
        val report = generateReportText(workspace)
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeBase = workspace.displayName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "android_app" }
        val file = File(exportsDir, "${safeBase}_blueprint.txt")
        file.writeText(report, Charsets.UTF_8)
        file
    }

    private fun detectAppPackagePrefixes(
        manifestPackage: String,
        manifestXml: String,
        allClasses: List<DexClassSummary>
    ): List<String> {
        val candidates = LinkedHashSet<String>()

        fun addCleanedPrefix(rawPkg: String) {
            val cleaned = rawPkg.trim()
                .removeSuffix(".debug")
                .removeSuffix(".dev")
                .removeSuffix(".beta")
                .removeSuffix(".staging")
                .removeSuffix(".release")
            if (cleaned.contains('.') && !isLibraryPackage(cleaned)) {
                candidates.add(cleaned)
            }
        }

        if (manifestPackage.isNotBlank()) {
            addCleanedPrefix(manifestPackage)
        }

        // Inspect declared Application, Activity, Service, Receiver names in AndroidManifest.xml
        val componentRegex = Regex("android:name=\"([a-zA-Z0-9_.]+)\"")
        for (match in componentRegex.findAll(manifestXml)) {
            val fullName = match.groupValues[1]
            if (!fullName.contains('.')) continue
            val pkg = fullName.substringBeforeLast('.')
            if (isLibraryPackage(pkg)) continue
            val parts = pkg.split('.')
            if (parts.size >= 3) {
                candidates.add(parts.take(3).joinToString("."))
            } else if (parts.size == 2) {
                candidates.add(pkg)
            }
        }

        // Keep only prefixes that actually match at least 1 class in DEX
        val matched = candidates.filter { prefix ->
            allClasses.any { it.packageName == prefix || it.packageName.startsWith("$prefix.") }
        }
        if (matched.isNotEmpty()) {
            // Remove redundant deeper prefixes if a shorter parent prefix is already included
            return matched.filter { p -> matched.none { other -> other != p && p.startsWith("$other.") } }
        }

        // Fallback: try 2-segment manifest prefix (e.g. com.example) if 3-segment didn't match
        if (manifestPackage.contains('.')) {
            val twoSeg = manifestPackage.split('.').take(2).joinToString(".")
            if (!isLibraryPackage(twoSeg) &&
                allClasses.any { it.packageName == twoSeg || it.packageName.startsWith("$twoSeg.") }
            ) {
                return listOf(twoSeg)
            }
        }

        return emptyList()
    }

    private fun isLibraryPackage(pkg: String): Boolean {
        if (pkg == "(default)") return false
        return libraryPackagePrefixes.any { prefix ->
            if (prefix.endsWith('.')) {
                pkg == prefix.dropLast(1) || pkg.startsWith(prefix)
            } else {
                pkg == prefix || pkg.startsWith("$prefix.")
            }
        }
    }

    private fun isCompilerBoilerplateClass(cls: DexClassSummary): Boolean {
        val name = cls.simpleName
        return name == "R" ||
            name.startsWith("R\$") ||
            name.contains("\$\$ExternalSyntheticLambda") ||
            name.contains("\$\$ExternalSyntheticBackport") ||
            name.contains("\$\$ExternalSyntheticRecord") ||
            name.endsWith("\$WhenMappings") ||
            name.contains("\$special\$\$inlined\$viewModels\$default")
    }

    private fun packageOrderPriority(pkg: String): Int {
        val lower = pkg.lowercase()
        return when {
            lower.endsWith(".theme") -> 1
            lower.contains(".core") -> 2
            lower.contains(".data") -> 3
            lower.contains(".audio") -> 4
            lower.contains(".websocket") || lower.contains(".network") || lower.contains(".api") -> 5
            lower.contains(".service") -> 6
            lower.contains(".diagnostics") -> 7
            lower.contains(".ui.screens.onboarding") -> 10
            lower.contains(".ui.screens") -> 9
            lower.contains(".ui") -> 8
            else -> 0
        }
    }

    private fun annotateResourceIds(xml: String, idToRef: Map<Int, String>): String {
        if (idToRef.isEmpty()) return xml
        return Regex("@0x([0-9a-fA-F]{8})").replace(xml) { match ->
            val id = match.groupValues[1].toLong(16).toInt()
            val ref = idToRef[id]
            if (ref != null) ref else match.value
        }
    }

    private fun classifyAppStringLiteral(
        raw: String,
        urls: MutableSet<String>,
        promptsAndMessages: MutableSet<String>
    ) {
        val s = raw.trim()
        if (s.length < 4) return
        if (s.startsWith("(") || (s.startsWith("L") && s.endsWith(";")) || s.startsWith("C(")) return
        if (s.startsWith("http://") || s.startsWith("https://") ||
            s.startsWith("wss://") || s.startsWith("ws://") ||
            s.startsWith("content://") || s.contains("googleapis.com") ||
            s.contains("gemini-") || s.contains("/v1beta/") || s.contains("/v1alpha/")
        ) {
            urls.add(s)
            return
        }
        // Capture meaningful prompts, JS automation scripts, JSON templates, or substantial UI/error messages
        if (s.length >= 24 && (s.contains(' ') || s.contains('\n') || s.contains("function(") || s.contains("document."))) {
            promptsAndMessages.add(s)
        }
    }
}
