package top.wkbin.taixu.ui.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.feature.terminal.R
import top.wkbin.taixu.ui.components.*

@Composable
internal fun TerminalRestoreWarnings(viewModel: TerminalViewModel?) {
    if (viewModel == null) return
    val failures by viewModel.restoreFailures.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    if (failures.isEmpty()) return
    RuntimeTextButton(onClick = { expanded = true }) { Text(stringResource(R.string.terminal_restore_failures_btn, failures.size)) }
    if (expanded) RuntimeAlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text(stringResource(R.string.terminal_unrestored_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.terminal_unrestored_desc), style = MaterialTheme.typography.bodySmall)
                RuntimeTextButton(onClick = viewModel::retryFailedRestorations) { Text(stringResource(R.string.terminal_retry_restore)) }
                failures.forEach { failure ->
                    Text(failure.label, style = MaterialTheme.typography.titleSmall)
                    Text(failure.message, style = MaterialTheme.typography.bodySmall)
                    RuntimeTextButton(onClick = { viewModel.removeFailedRestoration(failure.id) }) { Text(stringResource(R.string.terminal_remove_record)) }
                }
            }
        },
        confirmButton = { RuntimeTextButton(onClick = { expanded = false }) { Text(stringResource(R.string.terminal_close)) } },
    )
}
