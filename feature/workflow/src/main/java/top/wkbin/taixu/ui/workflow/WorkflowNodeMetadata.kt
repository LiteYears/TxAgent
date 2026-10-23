package top.wkbin.taixu.ui.workflow

import top.wkbin.taixu.core.model.workflow.WorkflowNodeType
import top.wkbin.taixu.ui.components.RuntimeIconName

data class NodeTypeMeta(
    val type: WorkflowNodeType,
    val label: String,
    val icon: RuntimeIconName,
    val summary: String,
    val guide: String,
)

fun WorkflowNodeType.metadata(): NodeTypeMeta {
    return when (this) {
        WorkflowNodeType.PLUGIN_ACTION -> NodeTypeMeta(
            this, "Plugin Action", RuntimeIconName.Extension,
            "Execute a compiled plugin node",
            "Specify plugin ID, extension ID, and JSON arguments. Confirmation required before execution."
        )
        WorkflowNodeType.TRIGGER -> NodeTypeMeta(
            this, "Trigger", RuntimeIconName.Play,
            "Workflow entry point, triggered manually or by external events",
            "Starting node of the DAG. Execution begins here when triggered manually or externally."
        )
        WorkflowNodeType.BASH_COMMAND -> NodeTypeMeta(
            this, "Shell Command", RuntimeIconName.Terminal,
            "Execute a Shell command or script in Linux PRoot sandbox",
            "Runs installed commands (git, make, python, curl). Supports \${WORKSPACE_PATH} or \${previous.output}."
        )
        WorkflowNodeType.PROCESS_SERVICE -> NodeTypeMeta(
            this, "Background Service", RuntimeIconName.Settings,
            "Start a background daemon process without blocking subsequent nodes",
            "Runs servers or daemons. Process ID is saved in \${PROCESS_ID} for downstream reference."
        )
        WorkflowNodeType.AGENT_INFERENCE -> NodeTypeMeta(
            this, "AI Agent Inference", RuntimeIconName.Sparkles,
            "Dispatch an AI agent to analyze inputs and produce results",
            "Processes prompts and upstream output. Supports code review, diagnostics, and patch generation."
        )
        WorkflowNodeType.SUBAGENT_DELEGATE -> NodeTypeMeta(
            this, "Subagent Delegation", RuntimeIconName.Hub,
            "Delegate sub-tasks to dedicated autonomous subagents",
            "Delegates tasks to specialized subagents while isolating main agent context."
        )
        WorkflowNodeType.TAIXU_BUILD -> NodeTypeMeta(
            this, "TaiXu Offline Build", RuntimeIconName.Package,
            "Invoke sandbox build engine to compile Android/C++/Rust",
            "Built-in taixu-build engine. Compiles Android, CMake, Cargo into APKs or executables."
        )
        WorkflowNodeType.CONDITION_BRANCH -> NodeTypeMeta(
            this, "Conditional Branch", RuntimeIconName.Link,
            "Evaluate expression and branch on true / false",
            "Supports exitCode, output contains, variable comparisons, and logical operators."
        )
        WorkflowNodeType.HUMAN_APPROVAL -> NodeTypeMeta(
            this, "Manual Approval Gate", RuntimeIconName.Alert,
            "Pause workflow and wait for manual approval before continuing",
            "Safety checkpoint before sensitive actions. Pauses execution until manually confirmed."
        )
        WorkflowNodeType.HOST_ACTION -> NodeTypeMeta(
            this, "Host System Action", RuntimeIconName.Android,
            "Android host capabilities: apps, intents, settings, GUI gestures",
            "Accesses Android host features outside sandbox. Actions marked 'Privileged' require Shizuku or Root."
        )
        WorkflowNodeType.DELAY -> NodeTypeMeta(
            this, "Delay / Wait", RuntimeIconName.Speed,
            "Pause for specified seconds before continuing",
            "Used for UI transitions, broadcast settling, or inspection cooldowns."
        )
        WorkflowNodeType.SET_VARIABLE -> NodeTypeMeta(
            this, "Set Variables", RuntimeIconName.Tune,
            "Inject KEY=value variables into downstream nodes",
            "One assignment per line. Variables enter global context for conditions and actions."
        )
        WorkflowNodeType.TERMINAL_OUTPUT -> NodeTypeMeta(
            this, "Result Archive Output", RuntimeIconName.Check,
            "Workflow destination, aggregates logs, duration, and produced artifacts",
            "Final terminal node of the DAG. Summarizes stdout, exit codes, and artifacts from upstream stages."
        )
    }
}
