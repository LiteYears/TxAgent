package top.wkbin.taixu.ui.workflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import top.wkbin.taixu.ui.workflow.LocalizedText as Text; import top.wkbin.taixu.ui.workflow.localized
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.wkbin.taixu.core.model.workflow.*
import top.wkbin.taixu.ui.components.*

@Composable
internal fun WorkflowHistoryCard(
    entry: WorkflowHistoryEntry,
    onRerun: (WorkflowHistoryEntry) -> Unit,
    onHistory: (WorkflowRuntimeState) -> Unit,
    onResume: (WorkflowRuntimeState, Set<String>) -> Unit,
) {
    val run = entry.state
    val plan = remember(run) { WorkflowRecovery.inspect(run) }
    var showResume by remember { mutableStateOf(false) }
    RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${run.definition.name} · ${workflowStatusLabel(run.status)} · ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(run.startedAt ?: 0))}",
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (entry.triggerSource == "SCHEDULE") "Scheduled Trigger" else "Manual Trigger",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!plan.canResume && run.status in setOf(WorkflowRunStatus.FAILED, WorkflowRunStatus.CANCELLED)) {
                Text(plan.blockedReason.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plan.canResume) RuntimeOutlinedButton(onClick = { showResume = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Resume", maxLines = 1)
                }
                RuntimeOutlinedButton(onClick = { onRerun(entry) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text("Rerun", maxLines = 1) }
                RuntimeOutlinedButton(onClick = { onHistory(run) }, contentPadding = PaddingValues(9.dp)) { RuntimeIcon(RuntimeIconName.List, Modifier.size(16.dp)) }
            }
        }
    }
    if (showResume) WorkflowResumeDialog(run, plan, onDismiss = { showResume = false }) { retry ->
        showResume = false
        onResume(run, retry)
    }
}

@Composable
private fun WorkflowResumeDialog(
    run: WorkflowRuntimeState,
    plan: WorkflowRecoveryPlan,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var retries by remember(run) { mutableStateOf(emptySet<String>()) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resume ${run.definition.name}") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reuse ${plan.cachedOutputs.size} cached outputs and execute ${plan.pendingNodeIds.size} remaining nodes.")
                Text("Verify workspace files and artifacts before resuming. Completed approvals are preserved.")
                Text("Workspace: ${run.context?.workspacePath.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                run.definition.nodes.filter { it.id in plan.pendingNodeIds }.forEach { node ->
                    if (node.id in plan.retryNodeIds) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${node.title}: Failed or uncertain. Enable after confirming it is safe to retry.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            RuntimeSwitch(checked = node.id in retries, onCheckedChange = { checked ->
                                retries = if (checked) retries + node.id else retries - node.id
                            })
                        }
                    } else Text("Pending: ${node.title}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = { RuntimeOutlinedButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { RuntimeButton(onClick = { onConfirm(retries) }, enabled = retries == plan.retryNodeIds) { Text("Resume Execution") } },
    )
}
