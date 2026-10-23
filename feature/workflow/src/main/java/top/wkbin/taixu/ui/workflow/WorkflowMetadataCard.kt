package top.wkbin.taixu.ui.workflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import top.wkbin.taixu.ui.workflow.LocalizedText as Text; import top.wkbin.taixu.ui.workflow.localized
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.core.model.workflow.WorkflowDefinition
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton

internal fun updatedWorkflowMetadata(definition: WorkflowDefinition, name: String, description: String,
    category: String, maxParallelNodes: Int): WorkflowDefinition {
    require(maxParallelNodes in 1..8) { "Max concurrency must be between 1 and 8" }
    return definition.copy(name = name, description = description, category = category,
        maxParallelNodes = maxParallelNodes, updatedAt = System.currentTimeMillis())
}

@Composable
internal fun WorkflowMetadataCard(state: WorkflowEditorUiState, onApply: (String, String, String, Int) -> Unit) {
    var name by remember(state.definition.id, state.definition.name) { mutableStateOf(state.definition.name) }
    var description by remember(state.definition.id, state.definition.description) { mutableStateOf(state.definition.description) }
    var category by remember(state.definition.id, state.definition.category) { mutableStateOf(state.definition.category) }
    var parallel by remember(state.definition.id, state.definition.maxParallelNodes) { mutableStateOf(state.definition.maxParallelNodes.toString()) }
    var expanded by remember { mutableStateOf(false) }
    val parallelValue = parallel.toIntOrNull()?.takeIf { it in 1..8 }

    RuntimeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Text("Workflow Info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (expanded) "Collapse" else "Modify", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            WorkflowExportButton(state.definition, state.isDirty)
            if (expanded) {
                OutlinedTextField(name, { name = it }, label = { Text("Workflow Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(parallel, { parallel = it }, label = { Text("Max Concurrency (1–8)") },
                    supportingText = { Text(if (parallelValue == null) "Enter an integer from 1 to 8" else "Manual approval nodes do not consume concurrency; set to 1 for serial execution.") },
                    isError = parallelValue == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                RuntimeOutlinedButton(enabled = parallelValue != null, onClick = { parallelValue?.let { onApply(name, description, category, it) } },
                    modifier = Modifier.align(Alignment.End)) { Text("Apply Info") }
            } else {
                Text("${state.definition.name} · [${state.definition.category}] · Max Concurrency ${state.definition.maxParallelNodes}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
