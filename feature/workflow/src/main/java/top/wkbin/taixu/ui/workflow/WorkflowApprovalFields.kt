package top.wkbin.taixu.ui.workflow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import top.wkbin.taixu.ui.workflow.LocalizedText as Text; import top.wkbin.taixu.ui.workflow.localized
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.wkbin.taixu.core.model.workflow.WorkflowExpansions
import top.wkbin.taixu.core.model.workflow.WorkflowApprovalTimeout

@Composable
internal fun WorkflowApprovalFields(config: MutableMap<String, String>) {
    OutlinedTextField(value = config[WorkflowApprovalTimeout.CONFIG_KEY].orEmpty(), onValueChange = {
        if (it.isBlank()) config.remove(WorkflowApprovalTimeout.CONFIG_KEY) else config[WorkflowApprovalTimeout.CONFIG_KEY] = it.trim()
    }, label = { Text("Approval Timeout (Seconds, Optional)") },
        isError = WorkflowApprovalTimeout.validationError(config[WorkflowApprovalTimeout.CONFIG_KEY]) != null,
        supportingText = { Text("Leave empty for no limit; 1–3600s applies failure policy on timeout.") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = config["prompt"].orEmpty(), onValueChange = { config["prompt"] = it },
        label = { Text("Approval Prompt (Required)") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = config[WorkflowExpansions.SOURCE_KEY].orEmpty(), onValueChange = {
        if (it.isBlank()) config.remove(WorkflowExpansions.SOURCE_KEY) else config[WorkflowExpansions.SOURCE_KEY] = it.trim()
    }, label = { Text("Dynamic expansion source node ID (Optional)") },
        supportingText = { Text("Approves structured nodes/edges expansion from the upstream agent node.") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
}
