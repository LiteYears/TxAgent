package top.wkbin.taixu.ui.workflow

import android.content.ClipData
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import top.wkbin.taixu.ui.workflow.LocalizedText as Text; import top.wkbin.taixu.ui.workflow.localized
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import top.wkbin.taixu.ui.components.*

@Composable
internal fun WorkflowStructuredResultButton(result: JsonObject) {
    var snapshot by remember { mutableStateOf<JsonObject?>(null) }
    RuntimeOutlinedButton(onClick = { snapshot = result }, modifier = Modifier.fillMaxWidth()) {
        Text("View Structured Results", maxLines = 1)
    }
    snapshot?.let { source -> WorkflowStructuredResultDialog(source) { snapshot = null } }
}

@Composable
private fun WorkflowStructuredResultDialog(result: JsonObject, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var raw by remember(result) { mutableStateOf<String?>(null) }
    var pages by remember(result) { mutableStateOf(emptyList<String>()) }
    var page by remember(result) { mutableIntStateOf(0) }
    var error by remember(result) { mutableStateOf<String?>(null) }
    var copying by remember(result) { mutableStateOf(false) }
    var copied by remember(result) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(page) { scroll.scrollTo(0) }
    LaunchedEffect(result) {
        try {
            val document = withContext(Dispatchers.Default) {
                val text = WorkflowResultDocument.encode(result)
                text to WorkflowResultDocument.pages(text)
            }
            pages = document.second
            raw = document.first
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Failed to generate result JSON" }
    }
    RuntimeAlertDialog(onDismissRequest = onDismiss, title = { Text("Structured Result JSON") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Shows results captured when opened. Copy and Save export full JSON for analysis.", style = MaterialTheme.typography.bodySmall)
            raw?.let { text ->
                Text("${text.length} chars · Page ${page + 1} / ${pages.size}", style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeOutlinedButton(enabled = page > 0, onClick = { if (page > 0) page-- }, modifier = Modifier.weight(1f)) { Text("Previous Page", maxLines = 1) }
                    RuntimeOutlinedButton(enabled = page < pages.lastIndex, onClick = { if (page < pages.lastIndex) page++ }, modifier = Modifier.weight(1f)) { Text("Next Page", maxLines = 1) }
                }
                key(page) {
                    SelectionContainer { Text(pages[page], style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                }
                WorkflowSaveDocumentButton(text, "workflow_result")
            }
            if (raw == null && error == null) Text("Generating…")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (copied) Text("Full result JSON copied", style = MaterialTheme.typography.bodySmall)
        }
    }, dismissButton = { RuntimeOutlinedButton(onClick = onDismiss) { Text("Close", maxLines = 1) } }, confirmButton = {
        RuntimeButton(enabled = raw != null && !copying, onClick = {
            if (copying) return@RuntimeButton
            val text = raw ?: return@RuntimeButton
            copying = true
            copied = false
            scope.launch {
                try {
                    val clip = ClipData.newPlainText("workflow_result", text).apply {
                        description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                    }
                    clipboard.setClipEntry(ClipEntry(clip))
                    copied = true
                    error = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "Copy failed, please retry" }
                finally { copying = false }
            }
        }) { Text(if (copying) "Copying" else "Copy Full JSON", maxLines = 1) }
    })
}
