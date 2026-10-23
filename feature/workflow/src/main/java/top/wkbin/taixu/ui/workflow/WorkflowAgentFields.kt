package top.wkbin.taixu.ui.workflow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import top.wkbin.taixu.ui.workflow.LocalizedText as Text; import top.wkbin.taixu.ui.workflow.localized
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.wkbin.taixu.core.model.workflow.WorkflowResultSchema

@Composable
internal fun WorkflowAgentFields(config: MutableMap<String, String>, delegate: Boolean) {
    OutlinedTextField(value = config["prompt"].orEmpty(), onValueChange = { config["prompt"] = it },
        label = { Text(if (delegate) "Sub-task Prompt (Required)" else "Task requirements / prompt (Required)") },
        supportingText = { Text("Supports referencing upstream output via \${previous.output}") },
        minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = config["role"].orEmpty(), onValueChange = { config["role"] = it },
        label = { Text("Agent Role (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (!delegate) OutlinedTextField(value = config["modelId"].orEmpty(), onValueChange = { config["modelId"] = it },
        label = { Text("Fixed model profile ID (Optional)") }, supportingText = { Text("Defaults to runtime WORKFLOW_MODEL_ID if left empty") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = config[WorkflowResultSchema.CONFIG_KEY].orEmpty(), onValueChange = {
        if (it.isBlank()) config.remove(WorkflowResultSchema.CONFIG_KEY) else config[WorkflowResultSchema.CONFIG_KEY] = it
    }, label = { Text("Structured Result JSON Schema (Optional)") },
        supportingText = { Text("Dynamic expansion requires nodes and edges; see documentation for details.") },
        minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
}
