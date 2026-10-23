package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.background
import top.wkbin.taixu.ui.theme.semanticColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.core.common.translation.TranslationModelStatus
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton

/**
 * 离线语种模型状态与下载卡片（英语 -> 简体中文）。
 */
@Composable
fun TranslationModelCard(
    status: TranslationModelStatus,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(
                    name = RuntimeIconName.Globe,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "English (en) → Chinese (zh-CN)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Google ML Kit Offline Translation Pack (~60MB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (status) {
                is TranslationModelStatus.Ready -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = semanticColors().success.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "Ready (Offline)",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = semanticColors().success,
                        )
                    }
                }
                is TranslationModelStatus.Downloading -> {
                    val percent = ((status.progress ?: 0f) * 100).toInt().coerceIn(0, 100)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RuntimeCircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 1.5.dp,
                            )
                            Text(
                                text = "Downloading $percent%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                is TranslationModelStatus.Checking -> {
                    Text(
                        text = "Checking...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is TranslationModelStatus.Error -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "Download Failed",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                is TranslationModelStatus.NotDownloaded -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = "Not Downloaded",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (status is TranslationModelStatus.Downloading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status.stepName,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = status.detailText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val progressFraction = status.progress
                RuntimeLinearProgressIndicator(
                    progress = if (progressFraction != null) { { progressFraction } } else null,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Downloads the offline language pack from Google once for local translation.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        } else if (status is TranslationModelStatus.Error) {
            Text(
                text = "${status.message} (Check network connection and retry)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(
                text = "Runs 100% on-device with zero cloud dependency or extra token usage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (status) {
                is TranslationModelStatus.Ready -> {
                    OutlinedButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Trash, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Model to Free Space", style = MaterialTheme.typography.labelMedium)
                    }
                }
                is TranslationModelStatus.Downloading -> {
                    val percent = ((status.progress ?: 0f) * 100).toInt().coerceIn(0, 100)
                    Button(
                        onClick = {},
                        enabled = false,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        RuntimeCircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Downloading ($percent%)", style = MaterialTheme.typography.labelMedium)
                    }
                }
                is TranslationModelStatus.Error -> {
                    Button(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry Download (~60MB)", style = MaterialTheme.typography.labelMedium)
                    }
                }
                else -> {
                    Button(
                        onClick = onDownload,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Download, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download Offline Model (~60MB)", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
