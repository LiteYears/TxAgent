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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import top.wkbin.taixu.runtime.FtpServiceState
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
fun FtpSettingsScreen(
    onBack: () -> Unit,
    viewModel: FtpSettingsViewModel? = previewOrNull { koinViewModel() },
) {
    val settings by viewModel?.settings?.collectAsStateWithLifecycle() ?: previewStateOf(FtpSettingsUiState())
    val serviceState by viewModel?.serviceState?.collectAsStateWithLifecycle() ?: previewStateOf(top.wkbin.taixu.runtime.FtpServiceState.Stopped("ubuntu"))
    val operating by viewModel?.operating?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val message by viewModel?.message?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val messageIsError by viewModel?.messageIsError?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val logs by viewModel?.logs?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val vpnActive by viewModel?.vpnActive?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val context = LocalContext.current

    var portText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(settings.port.toString()) }
    var usernameText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(settings.username) }
    var showPasswordDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showUsernameDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.distroId, settings.port) { portText = settings.port.toString() }
    LaunchedEffect(settings.distroId, settings.username) { usernameText = settings.username }

    message?.let { current ->
        RuntimeAlertDialog(
            onDismissRequest = { viewModel?.consumeMessage() },
            title = { Text(if (messageIsError) "FTP operation incomplete" else "FTP Remote File Server") },
            text = { Text(current) },
            confirmButton = { RuntimeTextButton(onClick = { viewModel?.consumeMessage() }) { Text("知道了") } },
        )
    }

    if (showPasswordDialog) {
        FtpPasswordDialog(
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

    if (showUsernameDialog) {
        FtpUsernameDialog(
            currentUsername = settings.username,
            onDismiss = { showUsernameDialog = false },
            onSave = { user ->
                showUsernameDialog = false
                viewModel?.saveUsername(user)
            },
        )
    }

    val statusText = when (serviceState) {
        is FtpServiceState.Stopped -> "已停止"
        is FtpServiceState.Starting -> "Starting"
        is FtpServiceState.Running -> "运行中"
        is FtpServiceState.Failed -> "启动失败"
    }
    val busy = operating || serviceState is FtpServiceState.Starting

    RuntimeSettingsScaffold(
        title = "FTP Remote File Access", onBack = onBack,
        statusText = statusText,
    ) {
        item {
            NoticeBanner(
                text = "Connect directly to the active Linux rootfs (/) over Wi-Fi/LAN using FileZilla, Cyberduck, or Windows Explorer.",
            )
        }

        item {
            SectionLabel("Service Status")
            SettingsGroup {
                SettingsRow(
                    icon = RuntimeIconName.Server,
                    title = "Enable FTP Server",
                    subtitle = "Keep enabled and auto-start with Linux runtime",
                    value = if (busy) {
                        "Working..."
                    } else {
                        when (serviceState) {
                            is FtpServiceState.Running -> "运行中"
                            is FtpServiceState.Starting -> "启动中"
                            is FtpServiceState.Failed -> "失败"
                            is FtpServiceState.Stopped -> "已停止"
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
            (serviceState as? FtpServiceState.Failed)?.let { failed ->
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
            SectionLabel("Connection Addresses & Client Parameters")
            SettingsDetailCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Client Connection Parameters:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ParamRow(
                        label = "Port",
                        value = settings.port.toString(),
                        highlight = true,
                        onCopy = { copyText(context, settings.port.toString(), "FTP port (${settings.port}) copied") },
                    )
                    ParamRow(
                        label = "Loopback Host (On-device apps)",
                        value = "127.0.0.1",
                        highlight = true,
                        onCopy = { copyText(context, "127.0.0.1", "Loopback IP (127.0.0.1) copied") },
                    )
                    ParamRow(
                        label = "LAN Host (PC / LAN devices)",
                        value = settings.connectionHost,
                        onCopy = { copyText(context, settings.connectionHost, "LAN IP (${settings.connectionHost}) copied") },
                    )
                    ParamRow(
                        label = "Username",
                        value = settings.username,
                        onCopy = { copyText(context, settings.username, "Username (${settings.username}) copied") },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    Text(
                        text = "Quick Connect URLs:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ParamRow(
                        label = "LAN / Desktop URL",
                        value = settings.connectionUrl,
                        onCopy = { copyText(context, settings.connectionUrl, "LAN FTP URL copied") },
                    )
                    ParamRow(
                        label = "On-Device FTP URL",
                        value = settings.localUrl,
                        onCopy = { copyText(context, settings.localUrl, "Local FTP URL copied") },
                    )

                    Text(
                        text = "Tip: Use host 127.0.0.1 and port ${settings.port} for on-device file managers, or ${settings.connectionHost} from a PC on the same Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (vpnActive) {
            item {
                NoticeBanner(
                    text = "Active VPN detected. If LAN connections time out, pause VPN or bypass TaiXu in VPN settings.",
                    isError = true,
                )
            }
        }

        item {
            SectionLabel("Authentication & Permissions")
            SettingsGroup {
                SettingsRow(
                    icon = RuntimeIconName.Admin,
                    title = "Username",
                    subtitle = "Username for FTP clients",
                    value = settings.username,
                    onClick = { showUsernameDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val passwordSubtitle = if (settings.passwordConfigured) {
                    "Password encrypted and saved; tap to change or clear"
                } else if (settings.sshPasswordConfigured) {
                    "Using SSH login password automatically"
                } else {
                    "No password set (passwordless login)"
                }
                val passwordValue = if (settings.passwordConfigured) {
                    "Configured"
                } else if (settings.sshPasswordConfigured) {
                    "Uses SSH Password"
                } else {
                    "No Password"
                }
                SettingsRow(
                    icon = RuntimeIconName.Key,
                    title = "Password",
                    subtitle = passwordSubtitle,
                    value = passwordValue,
                    onClick = { showPasswordDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    icon = RuntimeIconName.Shield,
                    title = "Allow Anonymous Access",
                    subtitle = if (settings.anonymousEnabled) "Clients can log in without username or password" else "Require username and password authentication",
                    trailing = {
                        RuntimeSwitch(
                            checked = settings.anonymousEnabled,
                            onCheckedChange = { arg0 -> viewModel?.toggleAnonymous(arg0) },
                            enabled = !busy,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingsRow(
                    icon = RuntimeIconName.Storage,
                    title = "Read-Only Mode",
                    subtitle = if (settings.readOnly) "Read-only active: prevents external writes, renames, or deletions" else "Read-write active: allows uploading, renaming, and deleting files",
                    trailing = {
                        RuntimeSwitch(
                            checked = settings.readOnly,
                            onCheckedChange = { arg0 -> viewModel?.toggleReadOnly(arg0) },
                            enabled = !busy,
                        )
                    },
                )
            }
        }

        item {
            SectionLabel("Network Configuration")
            SettingsGroup {
                val portValue = portText.toIntOrNull()
                val portValid = portValue != null && portValue in 1024..65535
                SettingsPortEditor(
                    label = "FTP Port", value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    isError = portText.isNotBlank() && !portValid,
                    supportingText = if (portText.isNotBlank() && !portValid) "Port must be between 1024 and 65535" else "Default: 2121 (Range: 1024–65535)",
                ) {
                    RuntimeOutlinedButton(
                        onClick = { copyText(context, settings.port.toString(), "FTP port (${settings.port}) copied") },
                    ) {
                        RuntimeIcon(RuntimeIconName.Copy, Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("复制")
                    }
                    RuntimeOutlinedButton(
                        onClick = { viewModel?.savePort(portText) },
                        enabled = !busy && portValid && portText != settings.port.toString(),
                    ) {
                        Text("保存")
                    }
                }
            }
        }

        if (logs.isNotEmpty()) {
            item {
                SectionLabel("Recent Access Logs")
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
    clipboard?.setPrimaryClip(ClipData.newPlainText("FTP URL", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

@Composable
private fun FtpPasswordDialog(
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
        title = { Text(if (passwordConfigured) "Change FTP Password" else "Set FTP Password") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Password is encrypted via Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RuntimeFilledTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text("New Password") },
                    supportingText = { Text("At least 4 characters") },
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
                enabled = password.length >= 4 && matches,
            ) { Text("保存") }
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

@Composable
private fun FtpUsernameDialog(
    currentUsername: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var username by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(currentUsername) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change FTP Username") },
        text = { SettingsDialogForm {
            RuntimeFilledTextField(
                value = username,
                onValueChange = { username = it.take(32) },
                label = { Text("Username") },
                supportingText = { Text("Defaults to root") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } },
        confirmButton = {
            RuntimeButton(
                onClick = { onSave(username.trim().ifBlank { "root" }) },
                enabled = username.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            RuntimeTextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ParamRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        RuntimeOutlinedButton(
            onClick = onCopy,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            RuntimeIcon(RuntimeIconName.Copy, Modifier.size(13.dp))
            Spacer(Modifier.size(4.dp))
            Text("复制", style = MaterialTheme.typography.labelSmall)
        }
    }
}
