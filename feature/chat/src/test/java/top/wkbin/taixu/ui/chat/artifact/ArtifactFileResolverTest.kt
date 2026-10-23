package top.wkbin.taixu.ui.chat.artifact

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtifactFileResolverTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createSandboxContext(filesDir: File): Context = object : ContextWrapper(null) {
        override fun getFilesDir(): File = filesDir
    }

    @Test
    fun `rejects arbitrary host files outside linux-runtime sandbox`() {
        val appDataDir = tempFolder.newFolder("app-data")
        val filesDir = File(appDataDir, "files").apply { mkdirs() }
        val dbDir = File(appDataDir, "databases").apply { mkdirs() }
        val secretDb = File(dbDir, "taixu.db").apply { writeText("secret-sqlite-content") }
        val noBackupDir = File(appDataDir, "no_backup").apply { mkdirs() }
        val keystoreFile = File(noBackupDir, "remote-servers.enc").apply { writeText("encrypted-credentials") }

        val context = createSandboxContext(filesDir)

        assertNull(ArtifactFileResolver.resolveFile(context, secretDb.absolutePath))
        assertNull(ArtifactFileResolver.resolveFile(context, keystoreFile.absolutePath))
    }

    @Test
    fun `rejects path traversal escaping workspace or attachments`() {
        val appDataDir = tempFolder.newFolder("app-data-traversal")
        val filesDir = File(appDataDir, "files").apply { mkdirs() }
        val workspaceDir = File(filesDir, "linux-runtime/workspace/demo").apply { mkdirs() }
        val validArtifact = File(workspaceDir, "report.md").apply { writeText("# Report") }
        val outsideSecret = File(filesDir, "outside-secret.txt").apply { writeText("top-secret") }

        val context = createSandboxContext(filesDir)

        // Valid in-sandbox workspace file resolves
        val resolved = ArtifactFileResolver.resolveFile(context, "report.md", "/workspace/demo")
        assertNotNull(resolved)
        assertEquals(validArtifact.canonicalPath, resolved?.canonicalPath)

        // Path traversal via relative path, /workspace, or /attachments is rejected
        assertNull(ArtifactFileResolver.resolveFile(context, "../../../outside-secret.txt", "/workspace/demo"))
        assertNull(ArtifactFileResolver.resolveFile(context, "/workspace/../../outside-secret.txt", "/workspace/demo"))
        assertNull(ArtifactFileResolver.resolveFile(context, "/attachments/../../outside-secret.txt"))
        assertNull(ArtifactFileResolver.resolveFile(context, outsideSecret.absolutePath))
    }

    @Test
    fun `resolves distro home and rootfs guest paths within sandbox`() {
        val filesDir = tempFolder.newFolder("files-distro")
        val distroDir = File(filesDir, "linux-runtime/distros/ubuntu")
        val homeFile = File(distroDir, "home/build/output.apk").apply {
            parentFile.mkdirs()
            writeText("apk-binary")
        }
        val rootfsFile = File(distroDir, "rootfs/var/log/apt/term.log").apply {
            parentFile.mkdirs()
            writeText("apt-log")
        }

        val context = createSandboxContext(filesDir)

        val resolvedHome = ArtifactFileResolver.resolveFile(context, "/root/build/output.apk")
        assertEquals(homeFile.canonicalPath, resolvedHome?.canonicalPath)

        val resolvedRootfs = ArtifactFileResolver.resolveFile(context, "/var/log/apt/term.log")
        assertEquals(rootfsFile.canonicalPath, resolvedRootfs?.canonicalPath)

        // Traversal out of distro home is rejected
        assertNull(ArtifactFileResolver.resolveFile(context, "/root/../../../../outside.txt"))
    }

    @Test
    fun `bounds text preview to MAX_PREVIEW_BYTES`() {
        val filesDir = tempFolder.newFolder("files-preview")
        val workspaceDir = File(filesDir, "linux-runtime/workspace/proj").apply { mkdirs() }
        val smallMd = File(workspaceDir, "small.md").apply { writeText("# Hello") }
        val oversizedJson = File(workspaceDir, "huge.json").apply {
            writeBytes(ByteArray((ArtifactFileResolver.MAX_PREVIEW_BYTES + 16).toInt()) { 'a'.code.toByte() })
        }

        val context = createSandboxContext(filesDir)
        val smallInfo = ArtifactFileResolver.resolveFileInfo(context, "small.md", "proj")
        val hugeInfo = ArtifactFileResolver.resolveFileInfo(context, "huge.json", "proj")

        assertTrue(ArtifactFileResolver.isPreviewableTextFile(smallInfo))
        assertEquals("# Hello", ArtifactFileResolver.readPreviewText(smallMd))

        assertFalse(ArtifactFileResolver.isPreviewableTextFile(hugeInfo))
        assertNull(ArtifactFileResolver.readPreviewText(oversizedJson))
    }
}
