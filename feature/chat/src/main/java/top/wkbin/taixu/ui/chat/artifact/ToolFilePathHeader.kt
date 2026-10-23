package top.wkbin.taixu.ui.chat.artifact

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import java.io.FileInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolFilePathHeader(
    path: String,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
    previewContent: String? = null,
) {
    val context = LocalContext.current
    var showPreviewSheet by remember { mutableStateOf(false) }

    val fileInfo = remember(path, workspace) {
        ArtifactFileResolver.resolveFileInfo(context, path, workspace)
    }

    val defaultFileName = remember(path, fileInfo) {
        fileInfo?.fileName ?: path.substringAfterLast('/').ifBlank { "file.txt" }
    }
    val mimeType = remember(path, fileInfo) {
        fileInfo?.mimeType ?: ArtifactFileResolver.getMimeType(defaultFileName)
    }

    // SAF 外部目录导出选择器
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    val file = fileInfo?.file
                    if (file != null && file.exists() && file.canRead()) {
                        FileInputStream(file).use { input ->
                            input.copyTo(output)
                        }
                    } else if (previewContent != null) {
                        output.write(previewContent.toByteArray(Charsets.UTF_8))
                    } else {
                        error("无法读取文件内容")
                    }
                    output.flush()
                } ?: error("无法打开目标文件流")
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.chat_artifact_export_success), Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, context.getString(R.string.chat_artifact_export_failed, err.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val activePreviewContent = remember(previewContent, fileInfo) {
        if (!previewContent.isNullOrEmpty()) previewContent
        else if (fileInfo != null && fileInfo.sizeBytes <= 1024 * 1024) {
            runCatching { fileInfo.file.readText(Charsets.UTF_8) }.getOrNull()
        } else null
    }

    if (showPreviewSheet && !activePreviewContent.isNullOrEmpty()) {
        ArtifactPreviewSheet(
            title = defaultFileName,
            content = activePreviewContent,
            relativePath = path,
            workspace = workspace,
            onDismiss = { showPreviewSheet = false },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            RuntimeIcon(RuntimeIconName.File, Modifier.size(15.dp), tint = Color(0xFF8FA1BA))
            Text(
                text = path,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFDCE6F5),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            fileInfo?.let { info ->
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFF1B2C47),
                ) {
                    Text(
                        text = "${info.formatBadge} · ${info.formattedSize}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = Color(0xFF79C0FF),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // 解析工作区项目名与相对路径，提供跳转
        val projectName = remember(workspace, path) {
            when {
                workspace.startsWith("/workspace/") -> workspace.removePrefix("/workspace/").substringBefore('/')
                path.startsWith("/workspace/") -> path.removePrefix("/workspace/").substringBefore('/')
                else -> ""
            }
        }
        val relativePath = remember(workspace, path, projectName) {
            when {
                path.startsWith("/workspace/$projectName/") -> path.removePrefix("/workspace/$projectName/")
                path.startsWith("/workspace/") -> path.substringAfter("/workspace/")
                path.startsWith("/") -> path.removePrefix("/")
                else -> path
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!activePreviewContent.isNullOrEmpty()) {
                Surface(
                    color = Color(0xFF1B2C47),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize().clickable { showPreviewSheet = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Document, Modifier.size(12.dp), tint = Color(0xFF79C0FF))
                        Text(
                            text = stringResource(R.string.chat_artifact_preview),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF79C0FF),
                        )
                    }
                }
            }

            val isDownloadable = fileInfo != null || !previewContent.isNullOrEmpty()
            if (isDownloadable) {
                Surface(
                    color = Color(0xFF1B2C47),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize().clickable {
                        exportDocumentLauncher.launch(defaultFileName)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Download, Modifier.size(12.dp), tint = Color(0xFF79C0FF))
                        Text(
                            text = stringResource(R.string.chat_artifact_export_short),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF79C0FF),
                        )
                    }
                }
            }

            if (projectName.isNotBlank() && onOpenFile != null) {
                Surface(
                    color = Color(0xFF1E283D),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize().clickable { onOpenFile(projectName, relativePath) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Code, Modifier.size(12.dp), tint = Color(0xFF7EE787))
                        Text(
                            text = stringResource(R.string.chat_open_editor),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7EE787),
                        )
                    }
                }
            }
        }
    }
}
