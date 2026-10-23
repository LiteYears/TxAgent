package top.wkbin.taixu.ui.workflow

import top.wkbin.taixu.core.model.workflow.WorkflowNodeType
import top.wkbin.taixu.ui.components.RuntimeIconName
import androidx.compose.ui.graphics.Color

data class NodeVisualTheme(
    val accentColor: Color,
    val icon: RuntimeIconName,
    val tag: String,
)

fun WorkflowNodeType.visualTheme(): NodeVisualTheme = when (this) {
    WorkflowNodeType.PLUGIN_ACTION -> NodeVisualTheme(Color(0xFF818CF8), RuntimeIconName.Extension, "Plugin Action")
    WorkflowNodeType.TRIGGER -> NodeVisualTheme(
        accentColor = Color(0xFF10B981), // 翡翠绿
        icon = RuntimeIconName.Play,
        tag = "Trigger",
    )
    WorkflowNodeType.BASH_COMMAND -> NodeVisualTheme(
        accentColor = Color(0xFF06B6D4), // 终端青
        icon = RuntimeIconName.Terminal,
        tag = "Shell Command",
    )
    WorkflowNodeType.PROCESS_SERVICE -> NodeVisualTheme(
        accentColor = Color(0xFF818CF8), // 守护紫
        icon = RuntimeIconName.Settings,
        tag = "Daemon",
    )
    WorkflowNodeType.AGENT_INFERENCE -> NodeVisualTheme(
        accentColor = Color(0xFFA855F7), // 智能紫
        icon = RuntimeIconName.Sparkles,
        tag = "AI Inference",
    )
    WorkflowNodeType.SUBAGENT_DELEGATE -> NodeVisualTheme(
        accentColor = Color(0xFF3B82F6), // 协同蓝
        icon = RuntimeIconName.Hub,
        tag = "Sub-agent",
    )
    WorkflowNodeType.TAIXU_BUILD -> NodeVisualTheme(
        accentColor = Color(0xFFF59E0B), // 构建琥珀
        icon = RuntimeIconName.Package,
        tag = "Build",
    )
    WorkflowNodeType.CONDITION_BRANCH -> NodeVisualTheme(
        accentColor = Color(0xFFEAB308), // 分支黄
        icon = RuntimeIconName.Link,
        tag = "Condition Branch",
    )
    WorkflowNodeType.HUMAN_APPROVAL -> NodeVisualTheme(
        accentColor = Color(0xFFF43F5E), // 审批红
        icon = RuntimeIconName.Alert,
        tag = "Manual Gate",
    )
    WorkflowNodeType.HOST_ACTION -> NodeVisualTheme(
        accentColor = Color(0xFF14B8A6), // 宿主青
        icon = RuntimeIconName.Android,
        tag = "Host System",
    )
    WorkflowNodeType.DELAY -> NodeVisualTheme(
        accentColor = Color(0xFF94A3B8),
        icon = RuntimeIconName.Speed,
        tag = "Delay / Wait",
    )
    WorkflowNodeType.SET_VARIABLE -> NodeVisualTheme(
        accentColor = Color(0xFF2DD4BF),
        icon = RuntimeIconName.Tune,
        tag = "Set Variables",
    )
    WorkflowNodeType.TERMINAL_OUTPUT -> NodeVisualTheme(
        accentColor = Color(0xFF0EA5E9), // 归档天蓝
        icon = RuntimeIconName.Check,
        tag = "Result Output",
    )
}
