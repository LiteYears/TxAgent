package top.wkbin.taixu.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.model.*
import top.wkbin.taixu.ui.components.*
import java.util.UUID

@Composable
internal fun RemoteServerDialog(viewModel: RemoteServerViewModel, existing: RemoteServer? = null, onDismiss: () -> Unit) {
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var host by remember { mutableStateOf(existing?.host.orEmpty()) }
    var port by remember { mutableStateOf((existing?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(existing?.username ?: "root") }
    var workspace by remember { mutableStateOf(existing?.workspace ?: "/root") }
    var keyMode by remember { mutableStateOf(existing?.authentication == SshAuthentication.PRIVATE_KEY) }
    var secret by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var showSecret by remember { mutableStateOf(false) }
    val serverId = remember { existing?.id ?: UUID.randomUUID().toString() }
    fun server() = RemoteServer(serverId, name.trim(), host.trim(), port.toIntOrNull() ?: 0,
        username.trim(), if (keyMode) SshAuthentication.PRIVATE_KEY else SshAuthentication.PASSWORD, workspace.trim(),
        existing?.takeIf { it.host == host.trim() && it.port == port.toIntOrNull() }?.fingerprint.orEmpty())
    val valid = name.isNotBlank() && host.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535 &&
        username.isNotBlank() && workspace.startsWith('/') && secret.isNotBlank()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(existing?.id) {
        if (existing != null) {
            try { val credentials = viewModel.credentials(existing.id); secret = credentials.secret; phrase = credentials.passphrase }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { importError = "Failed to read credentials; please re-enter authentication info" }
        }
    }
    val importKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = it.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 65536)
                            output.write(buffer, 0, count)
                        }
                        String(output.toByteArray(), Charsets.UTF_8)
                    } ?: error("File is not readable")
                }
                secret = content
                importError = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { importError = "Failed to read private key (max 64 KiB)" }
        }
    }
    LaunchedEffect(state.saved) { if (state.saved) { secret = ""; phrase = ""; onDismiss(); viewModel.clear() } }
    if (state.fingerprint != null) {
        RuntimeAlertDialog(
            onDismissRequest = { viewModel.clear() },
            title = { Text("Verify Server Host Key") },
            text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Verify the SSH host key fingerprint below before authenticating.")
                Spacer(Modifier.height(12.dp))
                Text(state.fingerprint.orEmpty(), style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { RuntimeTextButton(onClick = viewModel::confirmFingerprint) { Text("Trust & Connect") } },
            dismissButton = { RuntimeTextButton(onClick = viewModel::clear) { Text("取消") } },
        )
        return
    }
    RuntimeAlertDialog(
        onDismissRequest = { if (!state.busy) { viewModel.clear(); onDismiss() } },
        title = { Text(if (existing == null) "Add Connection" else "Edit Connection") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Saved SSH profiles can be selected as your primary runtime.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true, enabled = !state.busy)
                OutlinedTextField(host, { host = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Host / IP") }, singleLine = true, enabled = !state.busy)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(port, { port = it }, modifier = Modifier.weight(1f), label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !state.busy)
                    OutlinedTextField(username, { username = it }, modifier = Modifier.weight(1f), label = { Text("Username") }, singleLine = true, enabled = !state.busy)
                }
                OutlinedTextField(workspace, { workspace = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Working Directory") }, supportingText = { Text("Existing directory on server accessible to this user") }, singleLine = true, enabled = !state.busy)
                HorizontalDivider()
                Text("Authentication Method", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeOutlinedButton(modifier = Modifier.weight(1f), onClick = { if (keyMode) { keyMode = false; secret = "" } }, enabled = !state.busy) { Text(if (!keyMode) "✓ Password" else "Password") }
                    RuntimeOutlinedButton(modifier = Modifier.weight(1f), onClick = { if (!keyMode) { keyMode = true; secret = "" } }, enabled = !state.busy) { Text(if (keyMode) "✓ SSH Private Key" else "SSH Private Key") }
                }
                OutlinedTextField(secret, { secret = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (keyMode) "PEM / OpenSSH Private Key" else "Password") },
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(), singleLine = !keyMode, maxLines = 5, enabled = !state.busy,
                    trailingIcon = { RuntimeTextButton(onClick = { showSecret = !showSecret }) { Text(if (showSecret) "Hide" else "Show") } })
                if (keyMode) {
                    RuntimeTextButton(onClick = { importKey.launch(arrayOf("*/*")) }, enabled = !state.busy) { Text("Import Key from File") }
                    OutlinedTextField(phrase, { phrase = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Key Passphrase (Optional)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), enabled = !state.busy)
                }
                HorizontalDivider()
                Text("Test Connection", style = MaterialTheme.typography.labelLarge)
                Text("Testing does not save the profile. Requires SSH / SFTP enabled on the server.", style = MaterialTheme.typography.bodySmall)
                RuntimeOutlinedButton(enabled = valid && !state.busy, onClick = { viewModel.test(server(), SshCredentials(secret, phrase)) }) { Text("测试连接") }
                (state.message ?: importError)?.let { Text(it, color = if (state.tested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                if (state.busy) RuntimeLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { RuntimeTextButton(enabled = valid && !state.busy, onClick = {
            viewModel.connect(server(), SshCredentials(secret, phrase))
        }) { Text(if (state.busy) "Verifying…" else "保存") } },
        dismissButton = { RuntimeTextButton(enabled = !state.busy, onClick = { viewModel.clear(); onDismiss() }) { Text("取消") } },
    )
}
