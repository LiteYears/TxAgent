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

    /**
     * Attempts to resolve a path to an existing, readable host file.
     * Returns null if the file does not exist, is a directory, or cannot be read.
     */
    fun resolveFile(context: Context, rawPath: String, workspace: String? = null): File? {
        val clean = rawPath.trim()
        if (clean.isBlank()) return null

        val baseDir = File(context.filesDir, "linux-runtime")
        val workspaceDir = File(baseDir, "workspace")
        val attachmentsDir = File(baseDir, "attachments")
        val tmpDir = File(baseDir, "tmp")

        // 1. Direct host path check (e.g. /data/data/top.wkbin.taixu/files/...)
        val directFile = File(clean)
        if (directFile.isAbsolute && directFile.exists() && directFile.isFile && directFile.canRead()) {
            return directFile
        }

        // 2. /workspace/... guest path (mounted from baseDir/workspace)
        if (clean.startsWith("/workspace/")) {
            val sub = clean.removePrefix("/workspace/").trimStart('/')
            val candidate = File(workspaceDir, sub)
            if (candidate.exists() && candidate.isFile && candidate.canRead()) {
                return candidate
            }
        }

        // 3. /attachments/... guest path
        if (clean.startsWith("/attachments/")) {
            val sub = clean.removePrefix("/attachments/").trimStart('/')
            val candidate = File(attachmentsDir, sub)
            if (candidate.exists() && candidate.isFile && candidate.canRead()) {
                return candidate
            }
        }

        // 4. /tmp/... guest path
        if (clean.startsWith("/tmp/")) {
            val sub = clean.removePrefix("/tmp/").trimStart('/')
            val candidate = File(tmpDir, sub)
            if (candidate.exists() && candidate.isFile && candidate.canRead()) {
                return candidate
            }
        }

        // 5. Relative path inside the specified workspace
        val wsClean = workspace?.trim().orEmpty()
            .removePrefix("/workspace/")
            .removePrefix("/workspace")
            .removePrefix("/")
            .trimEnd('/')

        val normalizedRelative = clean.removePrefix("/workspace/").trimStart('/')

        if (wsClean.isNotBlank()) {
            val inWs = File(File(workspaceDir, wsClean), normalizedRelative)
            if (inWs.exists() && inWs.isFile && inWs.canRead()) {
                return inWs
            }
        }

        // 6. Direct child under workspace directory
        val directWs = File(workspaceDir, normalizedRelative)
        if (directWs.exists() && directWs.isFile && directWs.canRead()) {
            return directWs
        }

        // 7. Check installed distros (e.g. distros/ubuntu/...)
        val distrosDir = File(baseDir, "distros")
        if (distrosDir.exists()) {
            distrosDir.listFiles()?.filter { it.isDirectory }?.forEach { distro ->
                val rootCandidate = File(distro, clean.trimStart('/'))
                if (rootCandidate.exists() && rootCandidate.isFile && rootCandidate.canRead()) {
                    return rootCandidate
                }
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
