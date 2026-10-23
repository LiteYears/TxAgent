package top.wkbin.taixu.ui.workflow

import top.wkbin.taixu.core.model.workflow.WorkflowNodeType

internal fun WorkflowNodeType.defaultTitle(): String = when (this) {
    WorkflowNodeType.PLUGIN_ACTION -> "Plugin Action"
    WorkflowNodeType.TRIGGER -> "Trigger"
    WorkflowNodeType.BASH_COMMAND -> "Execute Command"
    WorkflowNodeType.PROCESS_SERVICE -> "Start Service"
    WorkflowNodeType.AGENT_INFERENCE -> "Agent Inference"
    WorkflowNodeType.SUBAGENT_DELEGATE -> "Delegate Sub-agent"
    WorkflowNodeType.TAIXU_BUILD -> "TaiXu Build"
    WorkflowNodeType.CONDITION_BRANCH -> "Condition Branch"
    WorkflowNodeType.HUMAN_APPROVAL -> "Manual Approval"
    WorkflowNodeType.HOST_ACTION -> "Host Action"
    WorkflowNodeType.DELAY -> "Delay / Wait"
    WorkflowNodeType.SET_VARIABLE -> "Set Variables"
    WorkflowNodeType.TERMINAL_OUTPUT -> "Output Result"
}

internal fun WorkflowNodeType.defaultConfig(): Map<String, String> = when (this) {
    WorkflowNodeType.PLUGIN_ACTION -> mapOf("plugin" to "taixu.harness", "extension" to "text-result", "arguments" to "{\"text\":\"Hello\"}")
    WorkflowNodeType.AGENT_INFERENCE -> mapOf(
        "prompt" to "Analyze the upstream output below and provide a clear conclusion:\n\${previous.output}",
    )
    WorkflowNodeType.SUBAGENT_DELEGATE -> mapOf(
        "taskName" to "Workflow Sub-task",
        "prompt" to "Complete the following task and return the result:\n\${previous.output}",
        "role" to "",
        "writePaths" to "",
    )
    WorkflowNodeType.HOST_ACTION -> mapOf("action" to "status")
    WorkflowNodeType.CONDITION_BRANCH -> mapOf("expression" to "exitCode == 0")
    WorkflowNodeType.DELAY -> mapOf("seconds" to "1")
    WorkflowNodeType.SET_VARIABLE -> mapOf("variables" to "EXAMPLE_KEY=example_value")
    else -> emptyMap()
}
