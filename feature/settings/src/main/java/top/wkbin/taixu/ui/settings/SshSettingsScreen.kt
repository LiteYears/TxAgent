package top.wkbin.taixu.ui.settings
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import org.koin.compose.viewmodel.koinViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.runtime.SshServiceState
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeSettingsScaffold
import top.wkbin.taixu.ui.settings.LocalizedText as Text
@Composable
fun SshSettingsScreen(
    onBack: () -> Unit,
    viewModel: SshSettingsViewModel? = previewOrNull { koinViewModel() },
) {
    val settings by viewModel?.settings?.collectAsStateWithLifecycle() ?: previewStateOf(SshSettingsUiState())
    val serviceState by viewModel?.serviceState?.collectAsStateWithLifecycle() ?: previewStateOf(top.wkbin.taixu.runtime.SshServiceState.Stopped("ubuntu"))
    val operating by viewModel?.operating?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val message by viewModel?.message?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val messageIsError by viewModel?.messageIsError?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val logs by viewModel?.logs?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val vpnActive by viewModel?.vpnActive?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val context = LocalContext.current

    var portText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(settings.port.toString()) }
    var authorizedKeysText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(settings.authorizedKeys) }
    var showPasswordDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.distroId, settings.port) { portText = settings.port.toString() }
    LaunchedEffect(settings.distroId, settings.authorizedKeys) { authorizedKeysText = settings.authorizedKeys }

    message?.let { current ->
        RuntimeAlertDialog(
            onDismissRequest = { viewModel?.consumeMessage() },
            title = { Text(if (messageIsError) "SSH operation incomplete" else "SSH Remote Access") },
            text = { Text(current) },
            confirmButton = { RuntimeTextButton(onClick = { viewModel?.consumeMessage() }) { Text("知道了") } },
        )
    }

    if (showPasswordDialog) {
        PasswordSettingsDialog(
            passwordConfigured = settings.passwordConfigured,
            onDismiss = { showPasswordDialog = false },
            onSave = { password ->
                showPasswordDialog = false
                viewModel?.savePassword(password)
            },
            onClear = {
                showPasswordDialog = false
                viewModel?.clearPassword()
            },
        )
    }

    val statusText = when (serviceState) {
        is SshServiceState.Stopped -> "已停止"
        is SshServiceState.Installing -> "Installing OpenSSH"
        is SshServiceState.Starting -> "Starting"
        is SshServiceState.Running -> "运行中"
        is SshServiceState.Failed -> "启动失败"
    }
    val busy = operating || serviceState is SshServiceState.Installing || serviceState is SshServiceState.Starting

    RuntimeSettingsScaffold(
        title = "SSH Remote Access", onBack = onBack,
        statusText = statusText,
    ) {
        item {
            NoticeBanner(
                text = "SSH is configured per Linux distro. Automatically installs OpenSSH on first use; supports root password and public key auth.",
            )
        }

        item {
            SectionLabel("Service Status")
            SettingsGroup {
                SettingsRow(
                    icon = RuntimeIconName.Server,
                    title = "Enable SSH Server",
                    subtitle = "Keep enabled and auto-start with Linux runtime",
                    value = if (busy) {
                        when (serviceState) {
                            is SshServiceState.Installing -> "Installing OpenSSH"
                            is SshServiceState.Starting -> "Waiting for SSH server to start"
                            else -> "正在处理"
                        }
                    } else {
                        when (serviceState) {
                            is SshServiceState.Running -> "运行中"
                            is SshServiceState.Installing -> "安装中"
                            is SshServiceState.Starting -> "启动中"
                            is SshServiceState.Failed -> "失败"
                            is SshServiceState.Stopped -> if ((serviceState as SshServiceState.Stopped).installed) "Installed" else "未安装"
                        }
                    },
                    trailing = {
                        if (busy) {
                            RuntimeCircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            RuntimeSwitch(
                                checked = settings.enabled,
                                onCheckedChange = { arg0 -> viewModel?.toggleEnabled(arg0) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    },
                )
            }
            (serviceState as? SshServiceState.Failed)?.let { failed ->
                SettingsDetailCard(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                ) {
                    Text(failed.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            SectionLabel("Authentication")
            SettingsGroup {
                SettingsRow(
                    icon = RuntimeIconName.Admin,
                    title = "Username",
                    subtitle = "PRoot Linux sandbox admin account",
                    value = "root",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    icon = RuntimeIconName.Key,
                    title = "Password",
                    subtitle = if (settings.passwordConfigured) "Password encrypted and saved; tap to change or clear" else "Not set; configure before using password login",
                    value = if (settings.passwordConfigured) "Configured" else "Not Set",
                    onClick = { showPasswordDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    icon = RuntimeIconName.Shield,
                    title = "Allow Password Login",
                    subtitle = if (settings.passwordAuthEnabled) "Log in with the configured root password" else "Password authentication disabled",
                    trailing = {
                        RuntimeSwitch(
                            checked = settings.passwordAuthEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !settings.passwordConfigured) {
                                    showPasswordDialog = true
                                } else {
                                    viewModel?.setPasswordAuthEnabled(enabled)
                                }
                            },
                            enabled = !busy,
                        )
                    },
                )
            }
        }

        item {
            SectionLabel("Connect")
            SettingsDetailCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = settings.connectionCommand,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Type 'yes' on first connect to accept host fingerprint, then enter your password",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RuntimeOutlinedButton(
                        onClick = { copyText(context, settings.connectionCommand, "SSH command copied") },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        RuntimeIcon(RuntimeIconName.Copy, Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("复制命令")
                    }
                }
            }
        }

        if (vpnActive) {
            item {
                NoticeBanner(
                    text = "Active VPN detected. Pause VPN or exclude TaiXu if LAN SSH connections time out.",
                    isError = true,
                )
            }
        }

        item {
            SectionLabel("Network Configuration")
            SettingsGroup {
                val portValue = portText.toIntOrNull()
                val portValid = portValue != null && portValue in 1024..65535
                SettingsPortEditor(
                    label = "SSH Port", value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    isError = portText.isNotBlank() && !portValid,
                    supportingText = if (portText.isNotBlank() && !portValid) "Port must be between 1024 and 65535" else "Allowed range: 1024–65535",
                ) {
                    RuntimeOutlinedButton(onClick = { viewModel?.savePort(portText) }, enabled = !busy && portValid) { Text("保存") }
                }
            }
        }

        item {
            SectionLabel("Public Key Login (Optional)")
            SettingsDetailCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Add one OpenSSH public key per line for key-based login.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RuntimeFilledTextField(
                        value = authorizedKeysText,
                        onValueChange = { authorizedKeysText = it },
                        label = { Text("authorized_keys") },
                        placeholder = { Text("ssh-ed25519 AAAAC3... user@device") },
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RuntimeButton(
                        onClick = { viewModel?.saveAuthorizedKeys(authorizedKeysText) },
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Save, Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Save Public Keys")
                    }
                }
            }
        }

        if (logs.isNotEmpty()) {
            item {
                SectionLabel("Recent Logs")
                SettingsDetailCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ) {
                    Text(
                        logs.takeLast(12).joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

private fun copyText(context: Context, text: String, toast: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("SSH command", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

@Composable
private fun PasswordSettingsDialog(
    passwordConfigured: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var password by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var confirmation by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val matches = password == confirmation
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (passwordConfigured) "Change SSH Password" else "Set SSH Password") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Password is encrypted via Android Keystore and applied to the distro root user.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RuntimeFilledTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text("New Password") },
                    supportingText = { Text("At least 8 characters, no colons") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                RuntimeFilledTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.take(128) },
                    label = { Text("Confirm Password") },
                    isError = confirmation.isNotEmpty() && !matches,
                    supportingText = {
                        if (confirmation.isNotEmpty() && !matches) Text("Passwords do not match")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            RuntimeButton(
                onClick = { onSave(password) },
                enabled = password.length >= 8 && matches && ':' !in password,
            ) { Text("Save & Enable") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (passwordConfigured) {
                    RuntimeTextButton(onClick = onClear) { Text("Clear Password") }
                }
                RuntimeTextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
