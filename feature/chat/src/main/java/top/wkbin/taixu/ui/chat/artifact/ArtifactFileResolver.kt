package top.wkbin.taixu.ui.chat.artifact

import android.content.Context
import java.io.File
import java.util.Locale

data class ArtifactFileInfo(
    val file: File,
    val fileName: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val mimeType: String,
    val formatBadge: String,
    val extension: String,
    val relativeOrDisplayPath: String,
)

/**
 * Universal Artifact File Resolver:
 * Resolves sandbox, PRoot guest, and host paths to readable, verified Android host files.
 * Verifies existence and readability before marking downloadable.
 */
object ArtifactFileResolver {

    const val MAX_PREVIEW_BYTES: Long = 1024L * 1024L

    private val TEXTUAL_EXTENSIONS = setOf(
        "md", "markdown", "txt", "log", "json", "py", "sh", "bash",
        "kt", "kts", "java", "c", "cpp", "h", "rs", "go", "js", "ts",
        "jsx", "tsx", "xml", "html", "htm", "yaml", "yml", "csv",
    )

    fun isPreviewableTextFile(info: ArtifactFileInfo?): Boolean {
        if (info == null) return false
        return info.extension in TEXTUAL_EXTENSIONS && info.sizeBytes in 0..MAX_PREVIEW_BYTES
    }

    fun readPreviewText(file: File, maxBytes: Long = MAX_PREVIEW_BYTES): String? {
        if (maxBytes < 0L || !file.exists() || !file.isFile || !file.canRead()) return null
        val length = file.length()
        if (length < 0L || length > maxBytes) return null
        return runCatching {
            file.inputStream().use { input ->
                val targetSize = length.toInt().coerceAtMost(maxBytes.toInt())
                val buffer = ByteArray(targetSize)
                var offset = 0
                while (offset < targetSize) {
                    val read = input.read(buffer, offset, targetSize - offset)
                    if (read <= 0) break
                    offset += read
                }
                String(buffer, 0, offset, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private fun verifyWithinDirectory(candidate: File, allowedDir: File): File? {
        if (!candidate.exists() || !candidate.isFile || !candidate.canRead()) return null
        val canonicalAllowed = runCatching { allowedDir.canonicalPath }.getOrNull() ?: return null
        val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (!canonicalCandidate.exists() || !canonicalCandidate.isFile || !canonicalCandidate.canRead()) return null
        return if (canonicalCandidate.path.startsWith(canonicalAllowed + File.separator)) canonicalCandidate else null
    }

    /**
     * Attempts to resolve a path to an existing, readable host file strictly within the runtime sandbox.
     * Returns null if the file does not exist, is a directory, escapes the sandbox, or cannot be read.
     */
    fun resolveFile(context: Context, rawPath: String, workspace: String? = null): File? {
        val clean = rawPath.trim()
        if (clean.isBlank() || clean.any { it.code < 32 || it == '\\' }) return null

        val baseDir = File(context.filesDir, "linux-runtime")
        val workspaceDir = File(baseDir, "workspace")
        val attachmentsDir = File(baseDir, "attachments")
        val tmpDir = File(baseDir, "tmp")
        val distrosDir = File(baseDir, "distros")
        val allowedRoots = listOf(workspaceDir, attachmentsDir, tmpDir, distrosDir)

        // 1. Direct host path check (must canonically reside within an allowed runtime sandbox root)
        val directFile = File(clean)
        if (directFile.isAbsolute) {
            allowedRoots.firstNotNullOfOrNull { verifyWithinDirectory(directFile, it) }?.let { return it }
        }

        // 2. /workspace/... guest path (mounted from baseDir/workspace)
        if (clean.startsWith("/workspace/")) {
            val sub = clean.removePrefix("/workspace/").trimStart('/')
            verifyWithinDirectory(File(workspaceDir, sub), workspaceDir)?.let { return it }
        }

        // 3. /attachments/... guest path
        if (clean.startsWith("/attachments/")) {
            val sub = clean.removePrefix("/attachments/").trimStart('/')
            verifyWithinDirectory(File(attachmentsDir, sub), attachmentsDir)?.let { return it }
        }

        // 4. /tmp/... guest path
        if (clean.startsWith("/tmp/")) {
            val sub = clean.removePrefix("/tmp/").trimStart('/')
            verifyWithinDirectory(File(tmpDir, sub), tmpDir)?.let { return it }
        }

        // 5. Relative path inside the specified workspace
        val wsClean = workspace?.trim().orEmpty()
            .removePrefix("/workspace/")
            .removePrefix("/workspace")
            .removePrefix("/")
            .trimEnd('/')

        val normalizedRelative = clean.removePrefix("/workspace/").trimStart('/')

        if (wsClean.isNotBlank() && !clean.startsWith("/")) {
            val wsRoot = File(workspaceDir, wsClean)
            if (verifyWithinDirectory(wsRoot, workspaceDir) != null || wsRoot.canonicalPath.startsWith(workspaceDir.canonicalPath + File.separator)) {
                verifyWithinDirectory(File(wsRoot, normalizedRelative), wsRoot)?.let { return it }
            }
        }

        // 6. Direct child under workspace directory (for relative paths or /workspace/ paths)
        if (!clean.startsWith("/") || clean.startsWith("/workspace/")) {
            verifyWithinDirectory(File(workspaceDir, normalizedRelative), workspaceDir)?.let { return it }
        }

        // 7. Check installed distros (guest /root -> distro/home, guest /... -> distro/rootfs/...)
        if (distrosDir.exists()) {
            distrosDir.listFiles()?.filter { it.isDirectory }?.forEach { distro ->
                if (clean.startsWith("/root/")) {
                    val homeSub = clean.removePrefix("/root/").trimStart('/')
                    verifyWithinDirectory(File(File(distro, "home"), homeSub), distro)?.let { return it }
                }
                val relFromRoot = clean.trimStart('/')
                verifyWithinDirectory(File(File(distro, "rootfs"), relFromRoot), distro)?.let { return it }
                verifyWithinDirectory(File(distro, relFromRoot), distro)?.let { return it }
            }
        }

        return null
    }

    /**
     * Resolves and extracts verified metadata for an artifact file.
     * Returns null if the file cannot be verified on disk.
     */
    fun resolveFileInfo(context: Context, rawPath: String, workspace: String? = null): ArtifactFileInfo? {
        val file = resolveFile(context, rawPath, workspace) ?: return null
        val fileName = file.name.ifBlank { rawPath.substringAfterLast('/') }
        val size = file.length()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ArtifactFileInfo(
            file = file,
            fileName = fileName,
            sizeBytes = size,
            formattedSize = formatFileSize(size),
            mimeType = getMimeType(fileName),
            formatBadge = getFormatBadge(fileName),
            extension = ext,
            relativeOrDisplayPath = rawPath,
        )
    }

    /**
     * Checks if the file is verified, existing and readable on disk.
     */
    fun isVerified(context: Context, rawPath: String, workspace: String? = null): Boolean {
        return resolveFile(context, rawPath, workspace) != null
    }

    /**
     * Formats bytes to human-readable string (e.g. 52.4 MB, 12 KB, 800 B).
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return if (digitGroups == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }

    /**
     * Resolves the appropriate MIME type for SAF document creation and sharing.
     */
    fun getMimeType(fileNameOrPath: String): String {
        val extension = fileNameOrPath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "apk" -> "application/vnd.android.package-archive"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz", "tgz" -> "application/gzip"
            "json" -> "application/json"
            "md", "markdown" -> "text/markdown"
            "html", "htm" -> "text/html"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            "txt", "log" -> "text/plain"
            "sh", "bash" -> "application/x-sh"
            "py" -> "text/x-python"
            "kt", "kts" -> "text/x-kotlin"
            "java" -> "text/x-java-source"
            "c", "cpp", "h" -> "text/x-c"
            "rs" -> "text/x-rust"
            "go" -> "text/x-go"
            "js", "ts", "jsx", "tsx" -> "application/javascript"
            "yaml", "yml" -> "text/yaml"
            else -> "*/*"
        }
    }

    /**
     * Resolves the format label badge (e.g. APK, MD, PY, SH, PNG, LOG).
     */
    fun getFormatBadge(fileNameOrPath: String): String {
        val extension = fileNameOrPath.substringAfterLast('.', "").uppercase()
        return if (extension.isNotBlank()) extension else "FILE"
    }
}
