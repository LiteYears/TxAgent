package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.HarnessTool
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.ui.chat.artifact.ToolFilePathHeader

private val DiffAddedBg = Color(0xFF0E2E1E)
private val DiffAddedText = Color(0xFF7EE787)
private val DiffRemovedBg = Color(0xFF38141B)
private val DiffRemovedText = Color(0xFFFFA198)
private val TerminalBoxBg = Color(0xFF070B12)

@Composable
fun ToolDiffView(
    call: ToolCall,
    result: ToolResult?,
    workspace: String = "",
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF090D14), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (call.tool) {
            HarnessTool.EDIT -> EditToolDiff(call, result, workspace, onOpenFile)
            HarnessTool.WRITE -> WriteToolDiff(call, result, workspace, onOpenFile)
            HarnessTool.READ -> ReadToolDiff(call, result, workspace, onOpenFile)
            HarnessTool.BASE -> BaseToolDiff(call, result)
            HarnessTool.PROCESS, HarnessTool.HOST, HarnessTool.DOWNLOAD, HarnessTool.MEMORY, HarnessTool.PLAN, HarnessTool.SCRATCHPAD,
            HarnessTool.HISTORY_SEARCH, HarnessTool.HISTORY_READ, HarnessTool.BUILD_SCRIPT, HarnessTool.SUBAGENT, HarnessTool.MCP,
            HarnessTool.LOAD_RULE, HarnessTool.LOAD_SKILL, HarnessTool.COMPRESS, HarnessTool.ASK_USER, HarnessTool.RENDER_SURFACE -> BaseToolDiff(call, result)
        }
    }
}

@Composable
private fun EditToolDiff(
    call: ToolCall,
    result: ToolResult?,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
) {
    val path = call.args["path"]?.jsonPrimitive?.content.orEmpty()
    val oldText = call.args["oldText"]?.jsonPrimitive?.content.orEmpty()
    val newText = call.args["newText"]?.jsonPrimitive?.content.orEmpty()

    // 顶部路径与在编辑器中打开/产物预览按钮
    ToolFilePathHeader(path = path, workspace = workspace, onOpenFile = onOpenFile, previewContent = newText.ifEmpty { oldText })

    // 优先渲染 harness 侧生成的精确 Unified Diff（反映真实命中区间与缩进重排）；
    // 旧数据或写入失败时回退到按参数粗渲染。
    val unifiedDiff = result?.takeIf { it.success }?.metadata?.get("diff")
    if (!unifiedDiff.isNullOrBlank()) {
        UnifiedDiffLines(unifiedDiff)
    } else {
        val oldLines = remember(oldText) { if (oldText.isEmpty()) emptyList() else oldText.split('\n') }
        val newLines = remember(newText) { if (newText.isEmpty()) emptyList() else newText.split('\n') }

        // Diff 区域：LazyColumn 虚拟化渲染，大 diff 只组合可见行，不再整块全量重组
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF06090F), RoundedCornerShape(6.dp))
                .heightIn(max = 320.dp)
                .padding(vertical = 4.dp),
        ) {
            itemsIndexed(oldLines, key = { index, _ -> "old-$index" }) { _, line ->
                DiffLine(text = "- $line", background = DiffRemovedBg, foreground = DiffRemovedText)
            }
            itemsIndexed(newLines, key = { index, _ -> "new-$index" }) { _, line ->
                DiffLine(text = "+ $line", background = DiffAddedBg, foreground = DiffAddedText)
            }
        }
    }

    // 执行状态提示
    result?.let {
        if (!it.success) {
            Text(
                text = it.output,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun UnifiedDiffLines(diff: String) {
    val lines = remember(diff) { diff.split('\n').filter { it.isNotEmpty() } }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06090F), RoundedCornerShape(6.dp))
            .heightIn(max = 320.dp)
            .padding(vertical = 4.dp),
    ) {
        itemsIndexed(lines, key = { index, _ -> "diff-$index" }) { _, line ->
            val (background, foreground) = when {
                line.startsWith("+++") || line.startsWith("---") ->
                    Color(0xFF141C29) to Color(0xFF9FB3C8)
                line.startsWith("@@") ->
                    Color(0xFF12202F) to Color(0xFF82AAFF)
                line.startsWith("+") -> DiffAddedBg to DiffAddedText
                line.startsWith("-") -> DiffRemovedBg to DiffRemovedText
                else -> Color(0xFF06090F) to Color(0xFF9AA7B8)
            }
            DiffLine(text = line, background = background, foreground = foreground)
        }
    }
}
@Composable
private fun DiffLine(text: String, background: Color, foreground: Color) {
    Text(
        text = text,
        color = foreground,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun WriteToolDiff(
    call: ToolCall,
    result: ToolResult?,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
) {
    val path = call.args["path"]?.jsonPrimitive?.content.orEmpty()
    val content = call.args["content"]?.jsonPrimitive?.content.orEmpty()

    ToolFilePathHeader(path = path, workspace = workspace, onOpenFile = onOpenFile, previewContent = content)

    val lineCount = remember(content) { content.count { it == '\n' } + 1 }
    Text(
        text = stringResource(R.string.chat_full_file_write, lineCount, content.length),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // 内容预览（前 10 行）
    val preview = content.lines().take(10).joinToString("\n") +
        if (lineCount > 10) stringResource(R.string.chat_total_lines, lineCount) else ""

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06090F), RoundedCornerShape(6.dp))
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        Text(
            text = preview,
            color = Color(0xFFDCE6F5),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }

    result?.let {
        if (!it.success) {
            Text(it.output, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ReadToolDiff(
    call: ToolCall,
    result: ToolResult?,
    workspace: String,
    onOpenFile: ((String, String) -> Unit)?,
) {
    val path = call.args["path"]?.jsonPrimitive?.content.orEmpty()
    ToolFilePathHeader(path = path, workspace = workspace, onOpenFile = onOpenFile, previewContent = result?.output)

    result?.let { res ->
        if (res.success) {
            val preview = res.output.lines().take(8).joinToString("\n") +
                if (res.output.lines().size > 8) stringResource(R.string.chat_characters_read, res.output.length) else ""
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF06090F), RoundedCornerShape(6.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                Text(
                    text = preview,
                    color = Color(0xFF8FA1BA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        } else {
            Text(res.output, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BaseToolDiff(
    call: ToolCall,
    result: ToolResult?,
) {
    val command = call.args["command"]?.jsonPrimitive?.content
        ?: call.args["cmd"]?.jsonPrimitive?.content
        ?: call.args["text"]?.jsonPrimitive?.content
        ?: call.args.toString()

    // 命令行
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF06090F), RoundedCornerShape(4.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$",
            color = Color(0xFF7EE787),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = command,
            color = Color(0xFFDCE6F5),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }

    // 执行输出
    result?.let { res ->
        val output = res.output.trim()
        if (output.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalBoxBg, RoundedCornerShape(6.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                Text(
                    text = output.take(2000) + if (output.length > 2000) stringResource(R.string.chat_output_truncated) else "",
                    color = if (res.success) Color(0xFFDCE6F5) else Color(0xFFFF8896),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
