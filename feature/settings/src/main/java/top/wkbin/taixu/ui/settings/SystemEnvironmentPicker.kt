package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.model.RemoteServer
import top.wkbin.taixu.ui.components.*

@Composable
internal fun SystemEnvironmentPicker(localCount: Int, onInstallLocal: () -> Unit, viewModel: RemoteServerViewModel? = previewOrNull { koinViewModel() }) {
    if (viewModel == null) {
        Text("Runtime Backend", style = MaterialTheme.typography.titleMedium)
        return
    }
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val active by viewModel.activeId.collectAsStateWithLifecycle()
    val configurationError by viewModel.configurationError.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()
    var addRemote by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<RemoteServer?>(null) }
    var editing by remember { mutableStateOf<RemoteServer?>(null) }
    var chooseRemote by remember { mutableStateOf(false) }
    var choose by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        configurationError?.let { NoticeBanner(text = it, isError = true) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Runtime Backend", style = MaterialTheme.typography.titleMedium)
            RuntimeButton(onClick = { choose = true }) { Text("Add System") }
        }
        NoticeBanner(text = "The primary system determines the environment for new chats, home metrics, and projects.")
        BackendChoiceCard("Local PRoot", "Run Linux on-device using the local workspace.", RuntimeIconName.Linux, active == null) { viewModel.select(null) }
        BackendChoiceCard("SSH Remote Runtime", servers.firstOrNull { it.id == active }?.name ?: "Use a remote server's compute, toolchain, and project directories.",
            RuntimeIconName.Server, active != null) {
            when (servers.size) {
                0 -> { viewModel.clear(); addRemote = true }
                1 -> viewModel.select(servers.single().id)
                else -> chooseRemote = true
            }
        }
        connection.message?.let { Text(it, color = if (connection.tested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Connection Profiles (${servers.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            RuntimeTextButton(onClick = { viewModel.clear(); addRemote = true }) { Text("Add Connection") }
        }
        if (servers.isEmpty()) Text("Add an SSH profile to select it as your primary runtime.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        servers.forEach { server ->
            ConnectionProfileCard(server, server.id == active, onSelect = { viewModel.select(server.id) },
                onEdit = { viewModel.clear(); editing = server }, onRemove = { remove = server })
        }
    }
    if (choose) RuntimeAlertDialog(
        onDismissRequest = { choose = false }, title = { Text("Select System Source") },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RuntimeOutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { choose = false; onInstallLocal() }) { Text("Install Local Linux") }
            RuntimeOutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { choose = false; viewModel.clear(); addRemote = true }) { Text("Connect Remote Server") }
        } },
        confirmButton = { RuntimeTextButton(onClick = { choose = false }) { Text("取消") } },
    )
    if (addRemote) RemoteServerDialog(viewModel) { addRemote = false }
    editing?.let { target -> key(target.id) { RemoteServerDialog(viewModel, target) { editing = null } } }
    if (chooseRemote) RuntimeAlertDialog(onDismissRequest = { chooseRemote = false }, title = { Text("Select Connection Profile") },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            servers.forEach { server -> RuntimeOutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.select(server.id); chooseRemote = false }) {
                Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } }
        } }, confirmButton = { RuntimeTextButton(onClick = { chooseRemote = false }) { Text("取消") } })
    remove?.let { server -> RuntimeAlertDialog(
        onDismissRequest = { remove = null }, title = { Text("Remove ${server.name}?") },
        text = { Text("Only removes saved connection credentials from this device; server files are untouched.") },
        confirmButton = { RuntimeTextButton(onClick = { viewModel.remove(server.id); remove = null }) { Text("Remove") } },
        dismissButton = { RuntimeTextButton(onClick = { remove = null }) { Text("取消") } },
    ) }
}

