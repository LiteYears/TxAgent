package top.wkbin.taixu.ui.workflow

import android.content.ClipData
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import top.wkbin.taixu.ui.workflow.LocalizedText as Text; import top.wkbin.taixu.ui.workflow.localized
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.model.workflow.WorkflowDefinition
import top.wkbin.taixu.core.model.workflow.WorkflowDefinitionExport
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton

@Composable
internal fun WorkflowExportButton(definition: WorkflowDefinition, isDirty: Boolean) {
    var snapshot by remember { mutableStateOf<Pair<WorkflowDefinition, Boolean>?>(null) }
    RuntimeOutlinedButton(onClick = { snapshot = definition to isDirty }, modifier = Modifier.fillMaxWidth()) { Text("Export JSON", maxLines = 1) }
    snapshot?.let { (source, dirty) -> WorkflowExportDialog(source, dirty, onDismiss = { snapshot = null }) }
}

@Composable
private fun WorkflowExportDialog(definition: WorkflowDefinition, isDirty: Boolean, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var raw by remember(definition) { mutableStateOf<String?>(null) }
    var error by remember(definition) { mutableStateOf<String?>(null) }
    var copying by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(definition) {
        val result = withContext(Dispatchers.Default) { runCatching { WorkflowDefinitionExport.encode(definition) } }
        raw = result.getOrNull()
        error = result.exceptionOrNull()?.message
    }
    RuntimeAlertDialog(onDismissRequest = onDismiss, title = { Text("Export Workflow JSON") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (isDirty) "Export current draft including unsaved edits." else "Export workflow definition in editor.")
            Text("JSON contains node configs and default variables. Re-importing creates a new workflow.", style = MaterialTheme.typography.bodySmall)
            raw?.let { text ->
                Text("${definition.nodes.size} nodes · ${definition.edges.size} edges · ${text.length} chars", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(value = text.take(24_000), onValueChange = {}, readOnly = true,
                    label = { Text("JSON Preview") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
                if (text.length > 24_000) Text("Preview shows first 24,000 chars; 'Copy Full JSON' copies everything.", style = MaterialTheme.typography.bodySmall)
            }
            if (raw == null && error == null) Text("Generating…", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (copied) Text("Full JSON copied", style = MaterialTheme.typography.bodySmall)
            WorkflowSaveDocumentButton(raw, definition.name)
        }
    }, dismissButton = { RuntimeOutlinedButton(onClick = onDismiss) { Text("Close", maxLines = 1) } }, confirmButton = {
        RuntimeButton(enabled = raw != null && !copying, onClick = {
            val text = raw ?: return@RuntimeButton
            copying = true
            scope.launch {
                try {
                    val clip = ClipData.newPlainText("workflow_definition", text).apply {
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
