package top.wkbin.taixu.ui.workflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import top.wkbin.taixu.ui.workflow.LocalizedText as Text; import top.wkbin.taixu.ui.workflow.localized
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton

@Composable
internal fun WorkflowSaveDocumentButton(raw: String?, name: String) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<String?>(null) }
    var writing by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pending
        pending = null
        if (uri == null) writing = false
        else if (text == null) { writing = false; fileError = "Export preview expired; please export again" }
        else scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    coroutine.ensureActive()
                    val stream = resolver.openOutputStream(uri, "wt") ?: error("Failed to open selected target file")
                    WorkflowDocumentTransfer.write(stream, text) { coroutine.ensureActive() }
                }
                saved = true
                fileError = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fileError = error.message ?: "Failed to save JSON file" }
            finally { writing = false }
        }
    }
    RuntimeOutlinedButton(enabled = raw != null && !writing, modifier = Modifier.fillMaxWidth(), onClick = {
        if (writing) return@RuntimeOutlinedButton
        val text = raw ?: return@RuntimeOutlinedButton
        pending = text
        saved = false
        fileError = null
        writing = true
        try { picker.launch(WorkflowDocumentTransfer.fileName(name)) }
        catch (_: Exception) { pending = null; writing = false; fileError = "Failed to open system file save dialog" }
    }) { Text(if (writing) "Saving…" else "Save as JSON File", maxLines = 1) }
    if (saved) Text("Full JSON file saved")
    fileError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}
