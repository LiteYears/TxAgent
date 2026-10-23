package top.wkbin.taixu.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.wkbin.taixu.runtime.environment.ExecutionFileEntry
import top.wkbin.taixu.runtime.remote.RemoteSystemService
import top.wkbin.taixu.ui.components.*

@Composable
fun RemoteWorkspaceScreen(serverId: String, onBack: () -> Unit) {
    val service = koinInject<RemoteSystemService?>()
    val target = service?.let { runCatching { it.server(serverId) }.getOrNull() }
    var path by remember(serverId) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<ExecutionFileEntry>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var createProject by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(serverId, path, refresh, target) {
        val current = service
        if (current == null) { loading = false; entries = emptyList(); message = null; return@LaunchedEffect }
        loading = true; entries = emptyList(); message = null
        try { entries = if (path.isEmpty()) current.projects(serverId, target) else current.list(serverId, path, target) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = "Failed to read server directory. Check connection, working directory, and permissions." }
        finally { loading = false }
    }
    BackHandler(path.isNotBlank()) { path = path.substringBeforeLast('/', "") }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar(title = if (path.isBlank()) "Workshop" else path.substringAfterLast('/'),
            statusText = target?.name ?: "Active Server", onBack = { if (path.isNotBlank()) path = path.substringBeforeLast('/', "") else onBack() },
            actions = { RuntimeIconButton(onClick = { refresh++ }, contentDescription = "Refresh") { RuntimeIcon(RuntimeIconName.Refresh) } }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().then(top.wkbin.taixu.ui.components.runtimeScrollViewport(padding)), contentPadding = top.wkbin.taixu.ui.components.runtimeScrollContentPadding(padding, PaddingValues(16.dp, 16.dp, 16.dp,
            24.dp)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconTile(RuntimeIconName.FolderOpen, size = 40.dp, color = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(if (path.isBlank()) "Server Projects" else "Project Files", style = MaterialTheme.typography.titleMedium)
                                Text((target?.workspace.orEmpty().trimEnd('/') + "/" + path).trimEnd('/'), style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (path.isBlank()) RuntimeFilledTonalButton(onClick = { projectName = ""; message = null; createProject = true }) { Text("New Project") }
                    }
                }
            }
            if (loading) item { RuntimeLinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && entries.isEmpty() && message == null) item {
                Text(if (path.isBlank()) "No projects found in working directory. Create a new project or place existing code here." else "This directory is empty.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(entries, key = { it.name }) { item ->
                RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), onClick = {
                    if (loading) return@RuntimeCard
                    val relative = listOf(path, item.name).filter { it.isNotEmpty() }.joinToString("/")
                    val parent = path
                    if (item.directory) path = relative
                    else scope.launch {
                        val current = service ?: return@launch
                        loading = true
                        try { val content = current.read(serverId, relative, target); if (path == parent) editor = relative to content }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = "Cannot open file. Only UTF-8 text files under 1 MiB are supported." }
                        finally { loading = false }
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(if (item.directory) RuntimeIconName.Folder else RuntimeIconName.Code, size = 36.dp, color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (item.directory) "Browse Directory" else "${item.size / 1024} KB · Tap to edit", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RuntimeIcon(RuntimeIconName.ChevronRight, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (createProject) RuntimeAlertDialog(onDismissRequest = { if (!creating) createProject = false }, title = { Text("New Server Project") },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Will create project directory in current server workspace.")
            OutlinedTextField(projectName, { projectName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !creating, label = { Text("Project Name") })
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { RuntimeTextButton(enabled = !creating && projectName.isNotBlank(), onClick = {
            val snapshot = projectName.trim()
            scope.launch {
                val current = service ?: return@launch
                creating = true
                try { current.createProject(serverId, snapshot, target); createProject = false; refresh++ }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = "Creation failed. Check name validity, uniqueness, and directory permissions." }
                finally { creating = false }
            }
        }) { Text(if (creating) "Creating…" else "Create") } },
        dismissButton = { RuntimeTextButton(enabled = !creating, onClick = { createProject = false }) { Text("Cancel") } })
    service?.let { current -> editor?.let { (file, original) -> RemoteFileEditor(serverId, file, original, current, target) { editor = null; refresh++ } } }
}

@Composable
private fun RemoteFileEditor(serverId: String, path: String, original: String, service: RemoteSystemService, target: top.wkbin.taixu.core.model.RemoteServer?, onDismiss: () -> Unit) {
    var content by remember(path, original) { mutableStateOf(original) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    RuntimeAlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(path, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(content, { content = it }, modifier = Modifier.fillMaxWidth(), maxLines = 14, minLines = 8,
                enabled = !busy, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { RuntimeTextButton(enabled = !busy && content != original, onClick = {
            val snapshot = content
            scope.launch {
                busy = true
                try { service.save(serverId, path, original, snapshot, target); onDismiss() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = "Save failed. File may have been modified externally. Re-open and check permissions." }
                finally { busy = false }
            }
        }) { Text(if (busy) "Saving…" else "Save to Server") } },
        dismissButton = { RuntimeTextButton(enabled = !busy, onClick = onDismiss) { Text("Close") } })
}
