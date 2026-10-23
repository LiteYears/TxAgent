package top.wkbin.taixu.ui.settings
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.runtime.StorageCategory
import top.wkbin.taixu.runtime.StorageEntry
import top.wkbin.taixu.runtime.StorageRiskLevel
import top.wkbin.taixu.runtime.StorageUsage
import top.wkbin.taixu.ui.components.IconTile
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeSettingsScaffold
import top.wkbin.taixu.ui.theme.semanticColors
import java.util.Locale
/** Keep diagnostic paths out of the main storage overview. */
@Composable
private fun StorageScanNotice(warnings: List<String>) {
    var showDetails by remember(warnings) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RuntimeIcon(
            name = RuntimeIconName.Info,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Some directories were skipped",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        RuntimeTextButton(onClick = { showDetails = true }) {
            Text("Details", style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
    if (showDetails) {
        RuntimeAlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Scan Details") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Some directories could not be fully read; reported size may be lower than actual usage.")
                    warnings.forEach { warning ->
                        Text(
                            warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                RuntimeTextButton(onClick = { showDetails = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
fun StorageUsageScreen(
    onBack: () -> Unit,
    viewModel: StorageUsageViewModel? = previewOrNull { koinViewModel() },
) {
    val uiState by viewModel?.uiState?.collectAsStateWithLifecycle() ?: previewStateOf(StorageUsageUiState())
    val usage = uiState.usage
    val refreshing = uiState.refreshing
    val cleaningAction = uiState.cleaningAction
    val message = uiState.message
    val messageIsError = uiState.messageIsError
    val activeFilter = uiState.activeFilter
    val dialogTarget = uiState.dialogTarget
    val filteredCategories = uiState.filteredCategories

    dialogTarget?.let { target ->
        when (target) {
            is CleanupDialogTarget.QuickSafe -> {
                RuntimeAlertDialog(
                    onDismissRequest = { viewModel?.dismissDialog() },
                    title = { Text("Clean Expired Archived Logs?") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Deletes archived logs older than 7 days:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("• Historical .log.gz and rotated log files")
                            Text("• Active logs, install payloads, and rollback backups are preserved")
                            Spacer(Modifier.height(4.dp))
                            Text("Historical diagnostic logs will be removed. Please close active terminals and tasks first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    confirmButton = {
                        RuntimeButton(
                            onClick = { viewModel?.quickSafeClean() },
                        ) {
                            Text("Clean Now")
                        }
                    },
                    dismissButton = {
                        RuntimeTextButton(onClick = { viewModel?.dismissDialog() }) {
                            Text("取消")
                        }
                    },
                )
            }

            is CleanupDialogTarget.AllProjectBuilds -> {
                RuntimeAlertDialog(
                    onDismissRequest = { viewModel?.dismissDialog() },
                    title = { Text("Clean Project Build Caches?") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Cleans unmodified cache files older than 7 days in recognized projects:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("• .gradle in Gradle projects and .dart_tool in Dart/Flutter projects")
                            Spacer(Modifier.height(4.dp))
                            Text("• build, dist, out, and target folders are managed in Workspace.", color = MaterialTheme.colorScheme.primary)
                            Text("• Next build will regenerate caches; please stop running tasks first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    confirmButton = {
                        RuntimeButton(
                            onClick = { viewModel?.cleanProjectBuilds() },
                        ) {
                            Text("Confirm Clean")
                        }
                    },
                    dismissButton = {
                        RuntimeTextButton(onClick = { viewModel?.dismissDialog() }) {
                            Text("取消")
                        }
                    },
                )
            }

            is CleanupDialogTarget.CategoryTarget -> {
                val category = target.category
                RuntimeAlertDialog(
                    onDismissRequest = { viewModel?.dismissDialog() },
                    title = { Text("Clean Category '${category.name}'?") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Estimated space freed: ${category.reclaimableBytes.readableSize()}")
                            Text(category.cleanupHint ?: "Clean all reclaimable data in this category?")
                            category.entries.filter { it.cleanable }.forEach { item ->
                                Text("• ${item.name}：${item.reclaimableBytes.readableSize()}")
                            }
                            if (category.riskLevel == StorageRiskLevel.CAUTION) {
                                Text("Note: Caches will need to be regenerated or re-downloaded on next build.", style = MaterialTheme.typography.bodySmall, color = semanticColors().warning)
                            } else if (category.riskLevel == StorageRiskLevel.DANGEROUS) {
                                Text("Warning: This action cannot be undone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        RuntimeButton(
                            onClick = { viewModel?.clearCategory(category.id) },
                        ) {
                            Text("Confirm Clean")
                        }
                    },
                    dismissButton = {
                        RuntimeTextButton(onClick = { viewModel?.dismissDialog() }) {
                            Text("取消")
                        }
                    },
                )
            }

            is CleanupDialogTarget.EntryTarget -> {
                val entry = target.entry
                RuntimeAlertDialog(
                    onDismissRequest = { viewModel?.dismissDialog() },
                    title = { Text("Clean '${entry.name}'?") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Estimated space freed: ${entry.reclaimableBytes.readableSize()}")
                            Text(entry.cleanupHint ?: "Clean this item?")
                            entry.path?.let {
                                Text("Target path: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (entry.riskLevel == StorageRiskLevel.DANGEROUS) {
                                Text("Warning: This permanently deletes the corresponding data!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        RuntimeButton(
                            onClick = { viewModel?.clearEntry(target.categoryId, entry.id, entry.name) },
                        ) {
                            Text("Confirm Clean")
                        }
                    },
                    dismissButton = {
                        RuntimeTextButton(onClick = { viewModel?.dismissDialog() }) {
                            Text("取消")
                        }
                    },
                )
            }
        }
    }

    RuntimeSettingsScaffold(
        title = "Storage Management", onBack = onBack,
        statusText = usage?.totalManagedBytes?.readableSize() ?: "Scanning",
    ) {
        // 1. 顶部存储看板与多段占比仪表条
        item(key = "header_dashboard") {
            usage?.let {
                StorageDashboardCard(
                    usage = it,
                    refreshing = refreshing,
                    onRefresh = { viewModel?.refresh() },
                )
            } ?: SettingsDetailCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RuntimeCircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        "Analyzing Linux sandbox, dependency caches, and workspace storage…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        usage?.scanWarnings?.takeIf { it.isNotEmpty() }?.let { warnings ->
            item(key = "scan_warnings") {
                StorageScanNotice(warnings)
            }
        }
        usage?.excludedMountPointCount?.takeIf { it > 0 }?.let { count ->
            item(key = "excluded_mount_points") {
                Text(
                    "Skipped $count restricted mount points; workspace and attachments are counted directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 2. 智能清理建议卡片（安全释放 + 谨慎释放）
        usage?.let { u ->
            if (u.safeCleanableBytes > 0 || u.projectCleanableBytes > 0) {
                item(key = "smart_cleanup_suggestions") {
                    SmartCleanupCards(
                        safeCleanableBytes = u.safeCleanableBytes,
                        cautionCleanableBytes = u.projectCleanableBytes,
                        cleaningAction = cleaningAction,
                        onQuickSafeClean = { viewModel?.openDialog(CleanupDialogTarget.QuickSafe) },
                        onCleanProjectBuilds = { viewModel?.openDialog(CleanupDialogTarget.AllProjectBuilds) },
                    )
                }
            }
        }

        // 3. 全局反馈提示横幅
        message?.let {
            item(key = "global_notice_banner") {
                NoticeBanner(it, isError = messageIsError, onDismiss = { viewModel?.dismissMessage() })
            }
        }

        // 4. 分类视图筛选 Chip 栏
        item(key = "filter_chips") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Category Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(StorageFilter.entries) { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { viewModel?.setFilter(filter) },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }

        // 5. 过滤后的分类卡片列表与细化折叠树
        val categories = usage?.categories.orEmpty()
        if (filteredCategories.isEmpty() && usage != null) {
            item(key = "empty_filter") {
                SettingsDetailCard(Modifier.fillMaxWidth()) {
                    Text(
                        "No items in selected filter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(filteredCategories, key = { it.id }) { category ->
                val catIndex = categories.indexOfFirst { it.id == category.id }.coerceAtLeast(0)
                StorageCategoryDetailCard(
                    category = category,
                    color = categoryColor(catIndex),
                    cleaningAction = cleaningAction,
                    onCleanCategory = { viewModel?.openDialog(CleanupDialogTarget.CategoryTarget(category)) },
                    onCleanEntry = { entry -> viewModel?.openDialog(CleanupDialogTarget.EntryTarget(category.id, entry)) },
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 顶部存储看板与多段渐变占比仪表条（竖向深度占比分析）
 */
@Composable
private fun StorageDashboardCard(
    usage: StorageUsage,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) = SettingsDetailCard(Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "Managed Storage",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                usage.totalManagedBytes.readableSize(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        RuntimeOutlinedButton(
            onClick = onRefresh,
            enabled = !refreshing,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (refreshing) {
                RuntimeCircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("Scanning", style = MaterialTheme.typography.labelSmall)
            } else {
                RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rescan", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text("Calculated by file size (excluding APK). Reclaimable size counts recognized cache files older than 7 days.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(14.dp))

    // 多段存储色彩条
    val visibleCategories = usage.categories.filter { it.bytes > 0L }
    val totalBytes = visibleCategories.sumOf { it.bytes }.coerceAtLeast(1L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics {
                contentDescription = visibleCategories.joinToString("；") { cat ->
                    val pct = (cat.bytes * 100 / totalBytes)
                    "${cat.name} ($pct%)"
                }
            },
    ) {
        if (visibleCategories.isEmpty()) {
            Spacer(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        } else {
            visibleCategories.forEachIndexed { index, category ->
                val weight = (category.bytes.toFloat() / totalBytes).coerceAtLeast(0.008f)
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxSize()
                        .padding(horizontal = 0.5.dp)
                        .background(categoryColor(index)),
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    // 竖向分类占比深度分析列表（每个分类独占一行，绝不折叠与截断）
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        visibleCategories.forEachIndexed { index, item ->
            val pct = (item.bytes.toDouble() / totalBytes * 100.0)
            val pctStr = if (pct >= 1.0) String.format(Locale.US, "%.1f%%", pct) else "< 1%"

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(categoryColor(index)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = item.bytes.readableSize(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = CircleShape,
                            color = categoryColor(index).copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = pctStr,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = categoryColor(index),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 单项轻量视觉进度细条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (pct / 100.0).toFloat().coerceIn(0.01f, 1f))
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(categoryColor(index)),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Available on device: ${usage.availableBytes.readableSize()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${usage.categories.size} categories",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * 智能清理建议卡片：安全清理与谨慎清理快捷入口（宽敞防挤压设计）
 */
@Composable
private fun SmartCleanupCards(
    safeCleanableBytes: Long,
    cautionCleanableBytes: Long,
    cleaningAction: String?,
    onQuickSafeClean: () -> Unit,
    onCleanProjectBuilds: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. 可清理的归档日志卡片
        if (safeCleanableBytes > 0) {
            SettingsDetailCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 上半部分：图标 + 标题/描述 + 醒目大字容量
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        IconTile(
                            icon = RuntimeIconName.Shield,
                            color = top.wkbin.taixu.ui.theme.semanticColors().success,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "Reclaimable Archived Logs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Logs older than 7 days; active logs preserved",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            safeCleanableBytes.readableSize(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = top.wkbin.taixu.ui.theme.semanticColors().success,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // 下半部分：保障标签 + 独立操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = semanticColors().successContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Check,
                                    modifier = Modifier.size(12.dp),
                                    tint = semanticColors().onSuccessContainer,
                                )
                                Text(
                                    "Safe · Does not affect code or settings",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = semanticColors().onSuccessContainer,
                                )
                            }
                        }

                        RuntimeButton(
                            onClick = onQuickSafeClean,
                            enabled = cleaningAction == null,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            if (cleaningAction == "quick_safe") {
                                RuntimeCircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Cleaning…", style = MaterialTheme.typography.labelMedium)
                            } else {
                                Text("Clean Old Logs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. 工作区构建生成物卡片
        if (cautionCleanableBytes > 0) {
            SettingsDetailCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 上半部分：图标 + 标题/描述 + 醒目大字容量
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        IconTile(
                            icon = RuntimeIconName.Code,
                            color = top.wkbin.taixu.ui.theme.semanticColors().warning,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "Workspace Build Caches",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Old .gradle / .dart_tool caches in recognized projects",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            cautionCleanableBytes.readableSize(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = top.wkbin.taixu.ui.theme.semanticColors().warning,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // 下半部分：保障标签 + 独立操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = semanticColors().warningContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Info,
                                    modifier = Modifier.size(12.dp),
                                    tint = semanticColors().onWarningContainer,
                                )
                                Text(
                                    "Regenerated on next build",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = semanticColors().onWarningContainer,
                                )
                            }
                        }

                        RuntimeFilledTonalButton(
                            onClick = onCleanProjectBuilds,
                            enabled = cleaningAction == null,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            if (cleaningAction == "all_project_builds") {
                                RuntimeCircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Cleaning…", style = MaterialTheme.typography.labelMedium)
                            } else {
                                Text("Clean Project Caches", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分类详情卡片：宽松大气的两段式布局（上层标题与占用量，下层风险徽标与操作按钮）
 */
@Composable
private fun StorageCategoryDetailCard(
    category: StorageCategory,
    color: Color,
    cleaningAction: String?,
    onCleanCategory: () -> Unit,
    onCleanEntry: (StorageEntry) -> Unit,
) {
    var expanded by remember(category.id) { mutableStateOf(false) }
    val isCategoryCleaning = cleaningAction == "category_${category.id}"

    SettingsDetailCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (category.entries.isNotEmpty()) expanded = !expanded },
    ) {
        // 顶部行：图标 + 标题/描述 (拥有 100% 完整水平空间) + 大小 + 展开箭头
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = categoryIcon(category.id),
                color = color,
                size = 42.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Reclaimable ${category.reclaimableBytes.readableSize()} · ${category.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = category.bytes.readableSize(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (category.entries.isNotEmpty()) {
                    RuntimeIcon(
                        name = if (expanded) RuntimeIconName.ChevronUp else RuntimeIconName.ChevronDown,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 底部状态与操作行：风险等级标签（完整不换行） + 清理按钮
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RiskBadge(category.riskLevel)

            if (category.cleanable && category.bytes > 0) {
                RuntimeFilledTonalButton(
                    onClick = onCleanCategory,
                    enabled = cleaningAction == null,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    if (isCategoryCleaning) {
                        RuntimeCircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (category.id == "package_cache") "Clean" else "Clean Category",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // 折叠展开的明细项列表
        AnimatedVisibility(
            visible = expanded && category.entries.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                category.entries.forEachIndexed { index, entry ->
                    StorageEntryRow(
                        entry = entry,
                        cleaningAction = cleaningAction,
                        onClean = { onCleanEntry(entry) },
                    )
                    if (index < category.entries.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 细化项目单行展现 (包括代码工程与构建产物、SDK 子项等)
 */
@Composable
private fun StorageEntryRow(
    entry: StorageEntry,
    cleaningAction: String?,
    onClean: () -> Unit,
) {
    val isEntryCleaning = cleaningAction == "entry_${entry.id}" || cleaningAction == "project_build_${entry.name}"

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        // 主条目行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.detail.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Reclaimable ${entry.reclaimableBytes.readableSize()} · ${entry.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    entry.bytes.readableSize(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entry.cleanable && entry.bytes > 0) {
                    RuntimeFilledTonalButton(
                        onClick = onClean,
                        enabled = cleaningAction == null,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        if (isEntryCleaning) {
                            RuntimeCircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                "Clean",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (entry.riskLevel) {
                                    StorageRiskLevel.SAFE -> top.wkbin.taixu.ui.theme.semanticColors().success
                                    StorageRiskLevel.CAUTION -> top.wkbin.taixu.ui.theme.semanticColors().warning
                                    StorageRiskLevel.DANGEROUS -> MaterialTheme.colorScheme.error
                                    StorageRiskLevel.READONLY -> MaterialTheme.colorScheme.outline
                                },
                            )
                        }
                    }
                }
            }
        }

        // 次级细项卡片（例如工程下的源码资产 vs 编译构建产物，或者 Android SDK 下的 components）
        if (entry.subItems.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entry.subItems.forEach { sub ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (sub.cleanable) top.wkbin.taixu.ui.theme.semanticColors().warning else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    sub.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                sub.bytes.readableSize(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sub.cleanable) semanticColors().warning else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 风险等级标签：绝不垂直折行，色彩清晰优雅
 */
@Composable
private fun RiskBadge(riskLevel: StorageRiskLevel, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (riskLevel) {
        StorageRiskLevel.SAFE -> Triple(
            "Archived Logs · Safe to Clean",
            semanticColors().successContainer,
            semanticColors().onSuccessContainer,
        )
        StorageRiskLevel.CAUTION -> Triple(
            "Caches · Rebuilt on Demand",
            semanticColors().warningContainer,
            semanticColors().onWarningContainer,
        )
        StorageRiskLevel.DANGEROUS -> Triple(
            "Persistent Data · High Risk",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        StorageRiskLevel.READONLY -> Triple(
            "Retained · Managed by Feature",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Surface(
        shape = CircleShape,
        color = bg,
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun categoryIcon(categoryId: String): RuntimeIconName = when (categoryId) {
    "environment" -> RuntimeIconName.Wrench
    "package_cache" -> RuntimeIconName.Package
    "projects" -> RuntimeIconName.Code
    "linux_system" -> RuntimeIconName.Linux
    "plugins" -> RuntimeIconName.Extension
    "runtimes" -> RuntimeIconName.Cpu
    "user_home" -> RuntimeIconName.Home
    "logs" -> RuntimeIconName.Logs
    "cache" -> RuntimeIconName.Download
    "conversations" -> RuntimeIconName.Attach
    "database" -> RuntimeIconName.Storage
    "app_data" -> RuntimeIconName.Tune
    else -> RuntimeIconName.Storage
}

@Composable
private fun categoryColor(index: Int): Color {
    val palette = semanticColors().categorical
    return palette[index % palette.size]
}

private fun Long.readableSize(): String = when {
    this < 1024L -> "$this B"
    this < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", this / 1024.0)
    this < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", this / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", this / (1024.0 * 1024 * 1024))
}



