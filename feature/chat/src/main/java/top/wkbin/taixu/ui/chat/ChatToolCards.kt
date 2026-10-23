package top.wkbin.taixu.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import top.wkbin.taixu.core.database.AgentApprovalRequestEntity
import top.wkbin.taixu.feature.a2uipoc.A2uiPocSurfaceCard
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.AskUserQuestions
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCheckbox as Checkbox
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import top.wkbin.taixu.ui.theme.semanticColors

private val DotRunning: Color @Composable get() = semanticColors().warning
private val DotSuccess: Color @Composable get() = semanticColors().success
private val DotFailed: Color @Composable get() = MaterialTheme.colorScheme.error

@Composable
internal fun ToolCard(
    call: ToolCall,
    result: ToolResult?,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
    running: Boolean,
    liveStatus: String?,
    showReasoning: Boolean = false,
    defaultExpanded: Boolean = false,
    onRetry: () -> Unit = {},
) {
    var expanded by remember(call.id) { mutableStateOf(false) }
    val isActivelyRunning = result == null && running
    val rawDotColor = when {
        isActivelyRunning -> DotRunning
        result?.success == true -> DotSuccess
        result?.success == false -> DotFailed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dotColor by animateColorAsState(rawDotColor, tween(280), label = "tool_dot_color")
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "tool_chevron_rotation",
    )
    val borderGlowAlpha = if (isActivelyRunning) {
        rememberInfiniteTransition(label = "tool_card_glow").animateFloat(
            initialValue = 0.28f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "tool_border_alpha",
        ).value
    } else 0.48f

    val targetContainerColor = when {
        isActivelyRunning -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
        result?.success == false -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.16f)
        expanded -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
    }
    val containerColor by animateColorAsState(targetContainerColor, tween(250), label = "tool_bg")
    val borderColor = when {
        isActivelyRunning -> dotColor.copy(alpha = borderGlowAlpha)
        result?.success == false -> DotFailed.copy(alpha = 0.45f)
        expanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
    }
    val dotDesc = when {
        isActivelyRunning -> stringResource(R.string.chat_tool_state_running)
        result?.success == true -> stringResource(R.string.chat_tool_state_success)
        result?.success == false -> stringResource(R.string.chat_tool_state_failed)
        else -> null
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showReasoning) {
                call.reasoning?.takeIf { it.isNotBlank() }?.let {
                    ThinkingBlock(id = call.id, reasoning = it, defaultExpanded = defaultExpanded)
                }
            }
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                containerColor = containerColor,
                borderColor = borderColor,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            ToolStatusIndicator(toolIcon(call.tool), dotColor, dotDesc, running = isActivelyRunning)
                        }
                        Surface(color = dotColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = toolDisplayName(call),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            text = toolArgsSummary(call).replace('\n', ' ').replace('\r', ' '),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val diffStat = remember(call, result) {
                            if (call.tool == HarnessTool.WRITE || call.tool == HarnessTool.EDIT) parseDiffStat(call, result) else null
                        }
                        diffStat?.let { (added, deleted) -> DiffStatBadge(added = added, deleted = deleted) }
                        val durationText = if (result != null) {
                            formatChatDuration(result.durationMs ?: (result.createdAt - call.createdAt).coerceAtLeast(0L))
                        } else if (running) {
                            var elapsed by remember(call.id) { mutableStateOf(0L) }
                            LaunchedEffect(call.id) {
                                while (true) {
                                    elapsed = (System.currentTimeMillis() - call.createdAt).coerceAtLeast(0L)
                                    delay(500)
                                }
                            }
                            formatChatDuration(elapsed)
                        } else null

                        durationText?.let {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(5.dp),
                            ) {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = if (isActivelyRunning) dotColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        RuntimeIcon(
                            RuntimeIconName.ChevronRight,
                            Modifier.size(16.dp).rotate(chevronRotation),
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isActivelyRunning) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                            color = dotColor,
                            trackColor = dotColor.copy(alpha = 0.15f),
                        )
                    }
                }
            }

            val downloadingPrefix = stringResource(R.string.chat_downloading_prefix)
            val verifyingDownload = stringResource(R.string.chat_verifying_download)
            val downloadStatus = liveStatus?.takeIf {
                call.tool == HarnessTool.DOWNLOAD && isActivelyRunning &&
                    (it.startsWith(downloadingPrefix) || it.startsWith(verifyingDownload))
            }
            if (downloadStatus != null) {
                Text(
                    downloadStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                val percent = Regex("\\((\\d+)%\\)").find(downloadStatus)
                    ?.groupValues?.getOrNull(1)?.toFloatOrNull()?.div(100f)
                if (percent == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { percent },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }

            if (call.tool == HarnessTool.RENDER_SURFACE && result?.success == true) {
                A2uiPocSurfaceCard(call = call, modifier = Modifier.fillMaxWidth())
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToolDiffView(call = call, result = result, workspace = workspace, onOpenFile = onOpenFile)
                    if (result != null && !running) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onRetry) {
                                RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.chat_retry_from_tool), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ApprovalRequestCard(
    request: AgentApprovalRequestEntity,
    onApprove: (rememberForSession: Boolean) -> Unit,
    onReject: () -> Unit,
) {
    val riskColor = when (request.riskLevel) {
        "critical" -> MaterialTheme.colorScheme.error
        "high" -> semanticColors().warning
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, riskColor.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(26.dp).background(riskColor.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(RuntimeIconName.Shield, Modifier.size(15.dp), riskColor)
                }
                Text(
                    stringResource(R.string.chat_approval_required),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Surface(color = riskColor.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        request.riskLevel.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = riskColor,
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    request.summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(10.dp),
                )
            }
            Text(request.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            var remainingMinutes by remember(request.expiresAt) { mutableStateOf(Int.MAX_VALUE) }
            LaunchedEffect(request.expiresAt) {
                while (true) {
                    val remaining = request.expiresAt - System.currentTimeMillis()
                    remainingMinutes = if (remaining <= 0) 0 else ((remaining + 59_999) / 60_000).toInt()
                    if (remainingMinutes == 0) break
                    delay(15_000)
                }
            }
            if (remainingMinutes > 0 && remainingMinutes != Int.MAX_VALUE) {
                val urgent = remainingMinutes <= 2
                Text(
                    if (remainingMinutes == 1) stringResource(R.string.chat_approval_expires_soon)
                    else stringResource(R.string.chat_approval_expires_minutes, remainingMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            var rememberForSession by remember { mutableStateOf(false) }
            if (!request.riskLevel.equals("critical", ignoreCase = true)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { rememberForSession = !rememberForSession },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = rememberForSession, onCheckedChange = { rememberForSession = it })
                    Text(
                        stringResource(R.string.chat_approval_remember_session),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            var resolving by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { resolving = true; onReject() },
                    enabled = !resolving,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.chat_reject)) }
                Button(
                    onClick = { resolving = true; onApprove(rememberForSession) },
                    enabled = !resolving,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.chat_approve_continue)) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AskUserCard(
    request: AgentApprovalRequestEntity,
    onAnswer: (requestId: String, answersJson: String) -> Unit,
    onSkip: () -> Unit,
) {
    val questions = remember(request.id) {
        runCatching { AskUserQuestions.parse(Json.parseToJsonElement(request.argumentsJson).jsonObject) }.getOrNull().orEmpty()
    }
    val selected = remember(request.id) { mutableStateMapOf<Int, String?>() }
    val custom = remember(request.id) { mutableStateMapOf<Int, String?>() }
    val allAnswered = questions.indices.all { !selected[it].isNullOrBlank() || !custom[it].isNullOrBlank() }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(RuntimeIconName.Chat, Modifier.size(15.dp), MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(R.string.chat_ask_user_title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
            if (questions.isEmpty()) {
                Text(stringResource(R.string.chat_ask_user_parse_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            questions.forEachIndexed { index, question ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        buildString { question.header?.let { append("[$it] ") }; append(question.question) },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    if (question.options.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            question.options.forEach { option ->
                                AskUserOptionChip(
                                    label = option.label,
                                    description = option.description,
                                    isSelected = selected[index] == option.label && custom[index].isNullOrBlank(),
                                    onClick = {
                                        if (selected[index] == option.label && custom[index].isNullOrBlank()) selected[index] = null
                                        else { selected[index] = option.label; custom.remove(index) }
                                    },
                                )
                            }
                        }
                    }
                    if (question.allowCustom) {
                        OutlinedTextField(
                            value = custom[index].orEmpty(),
                            onValueChange = { value -> custom[index] = value; if (value.isNotBlank()) selected.remove(index) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.chat_ask_user_custom_hint), style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                        )
                    }
                }
            }
            var resolving by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { resolving = true; onSkip() },
                    enabled = !resolving,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.chat_ask_user_skip)) }
                Button(
                    onClick = {
                        resolving = true
                        val answers = buildJsonArray {
                            questions.indices.forEach { index ->
                                val answer = custom[index]?.trim()?.ifBlank { null }
                                    ?: selected[index]?.trim()?.ifBlank { null }
                                    ?: return@forEach
                                add(buildJsonObject { put("index", index); put("answer", answer) })
                            }
                        }
                        onAnswer(request.id, answers.toString())
                    },
                    enabled = !resolving && allAnswered,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.chat_ask_user_submit)) }
            }
        }
    }
}

@Composable
private fun AskUserOptionChip(
    label: String,
    description: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        onClick = onClick,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (!description.isNullOrBlank()) {
                Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun toolIcon(tool: HarnessTool): RuntimeIconName = when (tool) {
    HarnessTool.READ -> RuntimeIconName.Document
    HarnessTool.WRITE -> RuntimeIconName.WriteDocument
    HarnessTool.EDIT -> RuntimeIconName.Edit
    HarnessTool.BASE -> RuntimeIconName.Terminal
    HarnessTool.PROCESS -> RuntimeIconName.Play
    HarnessTool.HOST -> RuntimeIconName.Phone
    HarnessTool.DOWNLOAD -> RuntimeIconName.Download
    HarnessTool.MEMORY -> RuntimeIconName.Bookmark
    HarnessTool.PLAN -> RuntimeIconName.Checklist
    HarnessTool.SCRATCHPAD -> RuntimeIconName.StickyNote
    HarnessTool.HISTORY_SEARCH -> RuntimeIconName.Search
    HarnessTool.HISTORY_READ -> RuntimeIconName.History
    HarnessTool.BUILD_SCRIPT -> RuntimeIconName.Hammer
    HarnessTool.SUBAGENT -> RuntimeIconName.Bot
    HarnessTool.MCP -> RuntimeIconName.Extension
    HarnessTool.LOAD_RULE -> RuntimeIconName.BookOpen
    HarnessTool.LOAD_SKILL -> RuntimeIconName.BookOpen
    HarnessTool.COMPRESS -> RuntimeIconName.Compress
    HarnessTool.ASK_USER -> RuntimeIconName.Chat
    HarnessTool.RENDER_SURFACE -> RuntimeIconName.Palette
}

internal fun toolArgsSummary(call: ToolCall): String =
    call.args.toMap().entries.take(3).joinToString(", ") { (k, v) -> "$k=${v.toString().take(40)}" }

@Composable
internal fun DiffStatBadge(
    added: Int,
    deleted: Int,
    modifier: Modifier = Modifier,
) {
    if (added == 0 && deleted == 0) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (added > 0) {
            Text(
                text = "+$added",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = DotSuccess,
            )
        }
        if (deleted > 0) {
            Text(
                text = "-$deleted",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = DotFailed,
            )
        }
    }
}

private val DIFF_STAT_REGEX = Regex("""DIFF_STAT:\s*\+(\d+)\s*-(\d+)""")

internal fun parseDiffStat(call: ToolCall, result: ToolResult?): Pair<Int, Int>? {
    if (result != null && !result.success) return null
    if (result != null) {
        val match = DIFF_STAT_REGEX.find(result.output)
        if (match != null) {
            val added = match.groupValues[1].toIntOrNull() ?: 0
            val deleted = match.groupValues[2].toIntOrNull() ?: 0
            if (added > 0 || deleted > 0) return added to deleted
        }
    }
    return when (call.tool) {
        HarnessTool.WRITE -> {
            val content = call.args["content"]?.jsonPrimitive?.contentOrNull
            if (!content.isNullOrBlank()) content.lines().size to 0 else null
        }
        HarnessTool.EDIT -> {
            val newText = call.args["newText"]?.jsonPrimitive?.contentOrNull
            val oldText = call.args["oldText"]?.jsonPrimitive?.contentOrNull
            if (newText != null || oldText != null) {
                val added = newText?.lines()?.size ?: 0
                val deleted = oldText?.lines()?.size ?: 0
                if (added > 0 || deleted > 0) added to deleted else null
            } else null
        }
        else -> null
    }
}
