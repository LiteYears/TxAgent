package top.wkbin.taixu.service

import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage

internal val NOTIFIABLE_AGENT_STATES = setOf(SessionRunState.RUNNING, SessionRunState.WAITING_APPROVAL)

/** Reuses execution status and tool counters without copying model or tool-result bodies. */
internal fun agentNotificationProgress(state: SessionRunState, status: String?, messages: List<HarnessMessage>): String {
    val current = messages.drop((messages.indexOfLast { it is UserMessage }).coerceAtLeast(0))
    val calls = current.filterIsInstance<ToolCall>().distinctBy { it.id }
    val results = current.filterIsInstance<ToolResult>().filterNot { it.awaitingApproval }.associateBy { it.toolCallId }
    val done = calls.count { it.id in results }
    val phase = if (state == SessionRunState.WAITING_APPROVAL) "Waiting for user approval"
        else status?.takeIf { it.isNotBlank() }?.take(240) ?: "Waiting for model response"
    val active = calls.lastOrNull { it.id !in results }
    return buildList {
        add(phase)
        if (calls.isNotEmpty()) add("Tool steps: completed $done / total ${calls.size}")
        if (state == SessionRunState.RUNNING && active != null) {
            add("Current tool: ${(active.rawToolName ?: active.tool.name).take(80)}")
        }
    }.joinToString("\n")
}
