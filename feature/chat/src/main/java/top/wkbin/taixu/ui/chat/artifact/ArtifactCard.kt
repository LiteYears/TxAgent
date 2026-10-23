package top.wkbin.taixu.ui.chat.artifact

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.FileArtifactOrigin
import top.wkbin.taixu.harness.task.DeliveryArtifact
import top.wkbin.taixu.harness.task.DeliveryArtifactKind
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import java.io.File
import java.io.FileInputStream

/**
 * Universal Artifact Download Card:
 * Renders an artifact with verified file existence, format badge, size, and working SAF download.
 */
@Composable
fun ArtifactCard(
    artifact: DeliveryArtifact,
    workspace: String = "",
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fileInfo = remember(artifact.path, workspace) {
        ArtifactFileResolver.resolveFileInfo(context, artifact.path, workspace)
    }

    // Only render verified files or clear unavailable status
    val file = fileInfo?.file
    val fileName = fileInfo?.fileName ?: artifact.path.substringAfterLast('/')
    val sizeText = fileInfo?.formattedSize ?: ArtifactFileResolver.formatFileSize(artifact.bytes)
    val formatBadge = fileInfo?.formatBadge ?: when (artifact.kind) {
        DeliveryArtifactKind.APK -> "APK"
        DeliveryArtifactKind.IMAGE -> "IMG"
        DeliveryArtifactKind.OUTPUT_LOG -> "LOG"
        DeliveryArtifactKind.FILE -> ArtifactFileResolver.getFormatBadge(artifact.path)
    }
    val mimeType = fileInfo?.mimeType ?: ArtifactFileResolver.getMimeType(fileName)

    var showPreviewSheet by remember { mutableStateOf(false) }

    // SAF Document creation launcher for direct file download/export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType)
    ) { uri ->
        if (uri != null && file != null && file.exists() && file.canRead()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    outputStream.flush()
                } ?: error("Failed to open destination stream")
            }.onSuccess {
                Toast.makeText(
                    context,
                    context.getString(R.string.delivery_download_success, fileName),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    context.getString(R.string.delivery_download_failed, err.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Preview Sheet for text/markdown files
    if (showPreviewSheet && file != null && file.exists()) {
        val previewContent = remember(file) {
            runCatching { file.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
        }
        ArtifactPreviewSheet(
            title = fileName,
            content = previewContent,
            relativePath = artifact.path,
            workspace = workspace,
            onDismiss = { showPreviewSheet = false },
        )
    }

    val (iconName, iconColor, iconBg) = when (artifact.kind) {
        DeliveryArtifactKind.APK -> Triple(RuntimeIconName.Android, Color(0xFF3DDC84), Color(0xFF1B382B))
        DeliveryArtifactKind.IMAGE -> Triple(RuntimeIconName.Image, Color(0xFF79C0FF), Color(0xFF1B2C47))
        DeliveryArtifactKind.OUTPUT_LOG -> Triple(RuntimeIconName.Logs, Color(0xFFFFA657), Color(0xFF382618))
        DeliveryArtifactKind.FILE -> when (fileInfo?.extension) {
            "sh", "py", "kt", "java", "c", "cpp", "rs", "go", "js", "ts" ->
                Triple(RuntimeIconName.Code, Color(0xFF7EE787), Color(0xFF183321))
            "md", "txt", "pdf", "doc" ->
                Triple(RuntimeIconName.Document, Color(0xFF79C0FF), Color(0xFF1B2C47))
            else -> Triple(RuntimeIconName.File, Color(0xFFD2A8FF), Color(0xFF2D1E3E))
        }
    }

    val originText = artifact.origin?.let { origin ->
        when (origin) {
            FileArtifactOrigin.NEW_AFTER_COMMAND -> stringResource(R.string.delivery_origin_new)
            FileArtifactOrigin.CHANGED_AFTER_COMMAND -> stringResource(R.string.delivery_origin_changed)
            FileArtifactOrigin.UNCHANGED_AFTER_COMMAND -> stringResource(R.string.delivery_origin_unchanged)
            FileArtifactOrigin.OBSERVED_AFTER_COMMAND -> stringResource(R.string.delivery_origin_observed)
            FileArtifactOrigin.AFTER_DOWNLOAD -> stringResource(R.string.delivery_origin_download)
            FileArtifactOrigin.IMAGE_READ -> stringResource(R.string.delivery_origin_image_read)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // File Type Icon Badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    RuntimeIcon(iconName, modifier = Modifier.size(22.dp), tint = iconColor)
                }

                // File Details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Format Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = formatBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        // File Size
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Path snippet
                        Text(
                            text = "· " + artifact.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            // Status or availability
            if (file == null || !file.exists() || !file.canRead()) {
                Text(
                    text = stringResource(R.string.delivery_file_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                // Interactive Actions: Download, Preview, Open in Editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Visible Download Button (Primary)
                    RuntimeFilledTonalButton(
                        onClick = { exportLauncher.launch(fileName) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        RuntimeIcon(RuntimeIconName.Download, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.delivery_download_button),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    // Preview Button (for text, markdown, or scripts)
                    val isTextual = fileInfo?.extension in setOf("md", "txt", "json", "py", "sh", "kt", "java", "c", "cpp", "xml", "html", "yaml", "yml", "csv")
                    if (isTextual) {
                        RuntimeOutlinedButton(
                            onClick = { showPreviewSheet = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            RuntimeIcon(RuntimeIconName.Document, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.chat_artifact_preview),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Open in Editor if project and workspace path are available
                    val projectName = remember(workspace, artifact.path) {
                        when {
                            workspace.startsWith("/workspace/") -> workspace.removePrefix("/workspace/").substringBefore('/')
                            artifact.path.startsWith("/workspace/") -> artifact.path.removePrefix("/workspace/").substringBefore('/')
                            else -> ""
                        }
                    }
                    val relativeEditorPath = remember(workspace, artifact.path, projectName) {
                        when {
                            artifact.path.startsWith("/workspace/$projectName/") -> artifact.path.removePrefix("/workspace/$projectName/")
                            artifact.path.startsWith("/workspace/") -> artifact.path.substringAfter("/workspace/")
                            artifact.path.startsWith("/") -> artifact.path.removePrefix("/")
                            else -> artifact.path
                        }
                    }

                    if (projectName.isNotBlank() && onOpenFile != null) {
                        RuntimeOutlinedButton(
                            onClick = { onOpenFile(projectName, relativeEditorPath) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            RuntimeIcon(RuntimeIconName.Code, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.delivery_open_editor),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
