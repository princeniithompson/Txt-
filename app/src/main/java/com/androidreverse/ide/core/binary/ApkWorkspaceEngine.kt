package com.androidreverse.ide.core.binary

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.androidreverse.ide.core.tools.ChecksumBundle
import com.androidreverse.ide.core.tools.DataConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class ArchiveNodeKind {
    FOLDER,
    DEX_ROOT,
    DEX_PACKAGE,
    DEX_CLASS,
    XML,
    ELF,
    CERTIFICATE,
    IMAGE,
    TEXT,
    BINARY
}

data class ArchiveEntryMeta(
    val path: String,
    val size: Long,
    val compressedSize: Long,
    val kind: ArchiveNodeKind
)

data class CertificateInfo(
    val source: String,
    val subject: String,
    val issuer: String,
    val algorithm: String,
    val sha256: String,
    val validFrom: String,
    val validUntil: String
)

data class SearchHit(
    val id: String,
    val title: String,
    val location: String,
    val snippet: String,
    val isDexClass: Boolean,
    val dexName: String = "",
    val classDescriptor: String = "",
    val filePath: String = ""
)

sealed interface InspectedContent {
    data class XmlFile(
        val path: String,
        val content: String,
        val isModified: Boolean,
        val byteSize: Long,
        val checksums: ChecksumBundle
    ) : InspectedContent

    data class TextFile(
        val path: String,
        val content: String,
        val isModified: Boolean,
        val byteSize: Long,
        val checksums: ChecksumBundle
    ) : InspectedContent

    data class DexClass(
        val detail: DexClassDetail
    ) : InspectedContent

    data class ElfBinary(
        val path: String,
        val byteSize: Long,
        val analysis: ElfAnalysis,
        val checksums: ChecksumBundle
    ) : InspectedContent

    data class RawBinary(
        val path: String,
        val byteSize: Long,
        val hexDump: String,
        val checksums: ChecksumBundle
    ) : InspectedContent
}

data class LoadedWorkspace(
    val displayName: String,
    val packageName: String = "",
    val packageFile: File,
    val packageSizeBytes: Long,
    val entries: List<ArchiveEntryMeta>,
    val dexParsers: Map<String, DexFileParser>,
    val dexClasses: List<DexClassSummary>,
    val certificates: List<CertificateInfo>,
    val nativeLibCount: Int
)

class ApkWorkspaceEngine(private val context: Context) {

    suspend fun openInstalledApp(app: InstalledAppInfo): LoadedWorkspace = withContext(Dispatchers.IO) {
        val sourceFile = File(app.sourceDir)
        val safeName = app.packageName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cached = File(context.cacheDir, "workspace_${safeName}.apk")
        sourceFile.copyTo(cached, overwrite = true)
        val certs = extractPackageCertificates(app.packageName)
        loadArchiveFile(
            file = cached,
            displayName = app.appName,
            packageName = app.packageName,
            fallbackCerts = certs
        )
    }

    suspend fun openSelfApk(): LoadedWorkspace = withContext(Dispatchers.IO) {
        val sourceFile = File(context.applicationInfo.sourceDir)
        val cached = File(context.cacheDir, "workspace_self.apk")
        sourceFile.copyTo(cached, overwrite = true)
        val certs = extractPackageCertificates(context.packageName)
        loadArchiveFile(cached, "${context.packageName}.apk", context.packageName, certs)
    }

    suspend fun openFromUri(uri: Uri): LoadedWorkspace = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "package.apk"
        val cached = File(context.cacheDir, "workspace_import.apk")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cached).use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open selected file")
        loadArchiveFile(cached, displayName, "", emptyList())
    }

    private fun loadArchiveFile(
        file: File,
        displayName: String,
        packageName: String,
        fallbackCerts: List<CertificateInfo>
    ): LoadedWorkspace {
        val entries = mutableListOf<ArchiveEntryMeta>()
        val dexParsers = LinkedHashMap<String, DexFileParser>()
        val allClasses = mutableListOf<DexClassSummary>()
        val certs = mutableListOf<CertificateInfo>()
        var nativeCount = 0

        ZipFile(file).use { zip ->
            val enumEntries = zip.entries()
            while (enumEntries.hasMoreElements()) {
                val entry = enumEntries.nextElement()
                if (entry.isDirectory) continue
                val path = entry.name
                val kind = classifyPath(path)
                if (kind == ArchiveNodeKind.ELF) nativeCount++
                entries.add(
                    ArchiveEntryMeta(
                        path = path,
                        size = entry.size.coerceAtLeast(0L),
                        compressedSize = entry.compressedSize.coerceAtLeast(0L),
                        kind = kind
                    )
                )

                if (path.endsWith(".dex", ignoreCase = true) && !path.contains('/')) {
                    runCatching {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val parser = DexFileParser(path, bytes)
                        if (parser.isValid) {
                            dexParsers[path] = parser
                            allClasses.addAll(parser.listClasses())
                        }
                    }
                } else if (kind == ArchiveNodeKind.CERTIFICATE) {
                    runCatching {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        certs.addAll(parseCertificatesFromBytes(path, bytes))
                    }
                }
            }
        }

        if (certs.isEmpty()) {
            certs.addAll(fallbackCerts)
        }

        return LoadedWorkspace(
            displayName = displayName,
            packageName = packageName,
            packageFile = file,
            packageSizeBytes = file.length(),
            entries = entries.sortedBy { it.path },
            dexParsers = dexParsers,
            dexClasses = allClasses.sortedWith(compareBy({ it.packageName }, { it.simpleName })),
            certificates = certs,
            nativeLibCount = nativeCount
        )
    }

    suspend fun buildAiContext(
        workspace: LoadedWorkspace,
        inspected: InspectedContent?
    ): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("App: ").append(workspace.displayName).append('\n')
        if (workspace.packageName.isNotBlank()) {
            sb.append("Package: ").append(workspace.packageName).append('\n')
        }
        sb.append("Size: ").append(formatSize(workspace.packageSizeBytes))
            .append(", Files: ").append(workspace.entries.size)
            .append(", DEX classes indexed: ").append(workspace.dexClasses.size)
            .append(", Native libs: ").append(workspace.nativeLibCount).append("\n\n")

        // Decode AndroidManifest.xml if present
        runCatching {
            ZipFile(workspace.packageFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                if (manifestEntry != null) {
                    val decoded = AxmlDecoder.decode(zip.getInputStream(manifestEntry).use { it.readBytes() })
                    sb.append("ANDROIDMANIFEST.XML (decoded excerpt):\n")
                    sb.append(decoded.take(4500)).append("\n\n")
                }
            }
        }

        // Top packages from DEX (app packages prioritized over third-party libraries)
        val appPrefix = workspace.packageName
            .removeSuffix(".debug").removeSuffix(".dev").removeSuffix(".beta")
            .split('.').take(3).joinToString(".")
        val packages = workspace.dexClasses
            .map { it.packageName }
            .distinct()
            .sortedWith(
                compareByDescending<String> { appPrefix.isNotBlank() && it.startsWith(appPrefix) }
                    .thenBy { it }
            )
            .take(35)
        if (packages.isNotEmpty()) {
            sb.append("DEX PACKAGES:\n")
            sb.append(packages.joinToString(", ")).append("\n\n")
        }

        // Native libraries
        val nativeLibs = workspace.entries.filter { it.kind == ArchiveNodeKind.ELF }.map { it.path }.take(20)
        if (nativeLibs.isNotEmpty()) {
            sb.append("NATIVE LIBRARIES:\n")
            sb.append(nativeLibs.joinToString("\n")).append("\n\n")
        }

        // Certificates
        if (workspace.certificates.isNotEmpty()) {
            sb.append("SIGNING CERTIFICATES:\n")
            for (c in workspace.certificates.take(3)) {
                sb.append("- Subject: ").append(c.subject)
                    .append(" | Alg: ").append(c.algorithm)
                    .append(" | Valid: ").append(c.validFrom).append(" to ").append(c.validUntil)
                    .append('\n')
            }
            sb.append('\n')
        }

        // Currently inspected file or class snippet
        when (inspected) {
            is InspectedContent.DexClass -> {
                sb.append("CURRENTLY OPEN CLASS: ")
                    .append(inspected.detail.summary.packageName).append('.')
                    .append(inspected.detail.summary.simpleName).append('\n')
                sb.append(inspected.detail.smaliCode.take(3000)).append('\n')
            }
            is InspectedContent.XmlFile -> {
                sb.append("CURRENTLY OPEN XML: ").append(inspected.path).append('\n')
                sb.append(inspected.content.take(3000)).append('\n')
            }
            is InspectedContent.TextFile -> {
                sb.append("CURRENTLY OPEN FILE: ").append(inspected.path).append('\n')
                sb.append(inspected.content.take(3000)).append('\n')
            }
            is InspectedContent.ElfBinary -> {
                sb.append("CURRENTLY OPEN ELF: ").append(inspected.path)
                    .append(" (").append(inspected.analysis.machine)
                    .append(", PIE=").append(inspected.analysis.isPie)
                    .append(", NX=").append(inspected.analysis.hasNx)
                    .append(", RELRO=").append(inspected.analysis.hasRelro).append(")\n")
            }
            is InspectedContent.RawBinary -> {
                sb.append("CURRENTLY OPEN BINARY: ").append(inspected.path)
                    .append(" (").append(formatSize(inspected.byteSize)).append(")\n")
            }
            null -> Unit
        }

        sb.toString()
    }

    suspend fun inspectArchiveFile(
        workspace: LoadedWorkspace,
        path: String,
        pendingEdits: Map<String, String>
    ): InspectedContent = withContext(Dispatchers.IO) {
        val overrideText = pendingEdits[path]
        val rawBytes = ZipFile(workspace.packageFile).use { zip ->
            val entry = zip.getEntry(path) ?: error("Entry not found: $path")
            zip.getInputStream(entry).use { it.readBytes() }
        }
        val activeBytes = overrideText?.toByteArray(Charsets.UTF_8) ?: rawBytes
        val checksums = DataConverter.checksums(activeBytes)
        val kind = classifyPath(path)

        when {
            kind == ArchiveNodeKind.XML -> {
                val xmlText = overrideText ?: AxmlDecoder.decode(rawBytes)
                InspectedContent.XmlFile(
                    path = path,
                    content = xmlText,
                    isModified = overrideText != null,
                    byteSize = activeBytes.size.toLong(),
                    checksums = checksums
                )
            }
            kind == ArchiveNodeKind.ELF || ElfParser.isElf(rawBytes) -> {
                val analysis = ElfParser.analyze(rawBytes)
                if (analysis != null) {
                    InspectedContent.ElfBinary(
                        path = path,
                        byteSize = rawBytes.size.toLong(),
                        analysis = analysis,
                        checksums = checksums
                    )
                } else {
                    InspectedContent.RawBinary(
                        path = path,
                        byteSize = rawBytes.size.toLong(),
                        hexDump = ElfParser.formatHexDump(rawBytes),
                        checksums = checksums
                    )
                }
            }
            kind == ArchiveNodeKind.TEXT -> {
                val text = overrideText ?: String(rawBytes, Charsets.UTF_8)
                InspectedContent.TextFile(
                    path = path,
                    content = text,
                    isModified = overrideText != null,
                    byteSize = activeBytes.size.toLong(),
                    checksums = checksums
                )
            }
            else -> {
                InspectedContent.RawBinary(
                    path = path,
                    byteSize = rawBytes.size.toLong(),
                    hexDump = ElfParser.formatHexDump(rawBytes),
                    checksums = checksums
                )
            }
        }
    }

    suspend fun inspectDexClass(
        workspace: LoadedWorkspace,
        summary: DexClassSummary
    ): InspectedContent.DexClass = withContext(Dispatchers.IO) {
        val parser = workspace.dexParsers[summary.dexName]
            ?: error("DEX not loaded: ${summary.dexName}")
        InspectedContent.DexClass(parser.inspectClass(summary))
    }

    suspend fun searchWorkspace(
        workspace: LoadedWorkspace,
        query: String
    ): List<SearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val hits = mutableListOf<SearchHit>()

        for (cls in workspace.dexClasses) {
            if (cls.simpleName.contains(q, ignoreCase = true) ||
                cls.packageName.contains(q, ignoreCase = true)
            ) {
                hits.add(
                    SearchHit(
                        id = "cls:${cls.dexName}:${cls.descriptor}",
                        title = cls.simpleName,
                        location = "${cls.dexName} · ${cls.packageName}",
                        snippet = cls.descriptor,
                        isDexClass = true,
                        dexName = cls.dexName,
                        classDescriptor = cls.descriptor
                    )
                )
                if (hits.size >= 60) break
            }
        }

        for (entry in workspace.entries) {
            if (entry.path.contains(q, ignoreCase = true)) {
                hits.add(
                    SearchHit(
                        id = "file:${entry.path}",
                        title = entry.path.substringAfterLast('/'),
                        location = entry.path,
                        snippet = formatSize(entry.size),
                        isDexClass = false,
                        filePath = entry.path
                    )
                )
                if (hits.size >= 100) break
            }
        }

        // Also search decoded AndroidManifest.xml if present
        ZipFile(workspace.packageFile).use { zip ->
            val manifestEntry = zip.getEntry("AndroidManifest.xml")
            if (manifestEntry != null) {
                val decoded = AxmlDecoder.decode(zip.getInputStream(manifestEntry).use { it.readBytes() })
                val matchingLine = decoded.lineSequence().firstOrNull { it.contains(q, ignoreCase = true) }
                if (matchingLine != null) {
                    hits.add(
                        0,
                        SearchHit(
                            id = "manifest:$q",
                            title = "AndroidManifest.xml",
                            location = "Manifest match",
                            snippet = matchingLine.trim().take(80),
                            isDexClass = false,
                            filePath = "AndroidManifest.xml"
                        )
                    )
                }
            }
        }

        hits
    }

    suspend fun exportArchiveToUri(
        workspace: LoadedWorkspace,
        pendingEdits: Map<String, String>,
        targetUri: Uri
    ): Int = withContext(Dispatchers.IO) {
        var writtenCount = 0
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            ZipOutputStream(out).use { zos ->
                ZipFile(workspace.packageFile).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val srcEntry = entries.nextElement()
                        val newEntry = ZipEntry(srcEntry.name)
                        zos.putNextEntry(newEntry)
                        val edited = pendingEdits[srcEntry.name]
                        if (edited != null) {
                            zos.write(edited.toByteArray(Charsets.UTF_8))
                        } else if (!srcEntry.isDirectory) {
                            zip.getInputStream(srcEntry).use { it.copyTo(zos) }
                        }
                        zos.closeEntry()
                        writtenCount++
                    }
                }
            }
        } ?: error("Could not write to output destination")
        writtenCount
    }

    private fun extractPackageCertificates(pkgName: String): List<CertificateInfo> {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
                    else signingInfo.signingCertificateHistory
                } else emptyArray()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES).signatures ?: emptyArray()
            }
            val cf = CertificateFactory.getInstance("X.509")
            signatures.mapNotNull { sig ->
                val cert = cf.generateCertificate(ByteArrayInputStream(sig.toByteArray())) as? X509Certificate
                cert?.let { formatX509("PackageManager", it) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCertificatesFromBytes(source: String, bytes: ByteArray): List<CertificateInfo> {
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val certs = cf.generateCertificates(ByteArrayInputStream(bytes))
            certs.filterIsInstance<X509Certificate>().map { formatX509(source, it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun formatX509(source: String, cert: X509Certificate): CertificateInfo {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        return CertificateInfo(
            source = source,
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            algorithm = cert.sigAlgName,
            sha256 = digest,
            validFrom = fmt.format(cert.notBefore ?: Date(0)),
            validUntil = fmt.format(cert.notAfter ?: Date(0))
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun classifyPath(path: String): ArchiveNodeKind {
            val lower = path.lowercase(Locale.US)
            return when {
                lower.endsWith(".dex") -> ArchiveNodeKind.DEX_ROOT
                lower.endsWith(".xml") -> ArchiveNodeKind.XML
                lower.endsWith(".so") -> ArchiveNodeKind.ELF
                lower.endsWith(".rsa") || lower.endsWith(".dsa") || lower.endsWith(".ec") || lower.endsWith(".pem") ->
                    ArchiveNodeKind.CERTIFICATE
                lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".jpg") ->
                    ArchiveNodeKind.IMAGE
                lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".properties") ||
                    lower.endsWith(".mf") || lower.endsWith(".sf") || lower.endsWith(".pro") ||
                    lower.endsWith(".version") || lower.endsWith(".kt") || lower.endsWith(".java") ->
                    ArchiveNodeKind.TEXT
                else -> ArchiveNodeKind.BINARY
            }
        }

        fun formatSize(bytes: Long): String = when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        }
    }
}
