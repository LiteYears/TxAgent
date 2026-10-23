package top.wkbin.taixu.ui.workflow

import java.util.UUID
import top.wkbin.taixu.core.model.workflow.*

internal fun blankWorkflowDefinition() = WorkflowDefinition(
    id = "workflow_${UUID.randomUUID().toString().take(8)}", name = "New Workflow",
    description = "Add and connect nodes on canvas", category = "Custom",
    nodes = listOf(
        WorkflowNode("start", WorkflowNodeType.TRIGGER, "Manual Trigger", canvasX = 80f, canvasY = 160f),
        WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Done", canvasX = 420f, canvasY = 160f),
    ), edges = listOf(WorkflowEdge("edge_start_done", "start", "output", "done")),
)
