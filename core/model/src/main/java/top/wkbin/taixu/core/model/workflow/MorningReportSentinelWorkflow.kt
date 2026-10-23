package top.wkbin.taixu.core.model.workflow

/**
 * 晨报哨兵：每天定时对当前工作区做只读巡检（Git 状态 / 最近提交 / 未提交改动），
 * AI 汇总成一份晨间简报后投递系统通知。
 *
 * 由首页「晨报哨兵」卡片创建 DAILY 定时计划驱动；也可通过 /wf morning_report_sentinel 手动触发。
 */
object MorningReportSentinel {
    const val WORKFLOW_ID = "morning_report_sentinel"

    val definition: WorkflowDefinition = WorkflowDefinition(
        id = WORKFLOW_ID,
        name = "Morning Report Sentinel",
        description = "Read-only inspection of the active workspace: Git uncommitted changes, recent commits, and diff stats; AI summarizes into a morning brief and pushes a system notification.",
        category = "Inspection",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf $WORKFLOW_ID"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Start", canvasX = 40f, canvasY = 120f),
            WorkflowNode(
                "git_survey",
                WorkflowNodeType.BASH_COMMAND,
                "Git Inspection",
                config = mapOf(
                    "command" to
                        "git rev-parse --is-inside-work-tree 2>/dev/null && " +
                        "{ echo '=== Uncommitted Changes ==='; git status --short; echo '=== Recent Commits ==='; " +
                        "git log --oneline -8; echo '=== Diff Stats ==='; git diff --stat; } || " +
                        "echo 'Current workspace is not a git repository, skipping Git inspection'",
                ),
                timeoutSeconds = 120,
                canvasX = 280f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "summarize",
                WorkflowNodeType.AGENT_INFERENCE,
                "AI Summarize Brief",
                config = mapOf(
                    "prompt" to
                        "You are the Morning Report Sentinel. Below is the read-only inspection output of the workspace (path: \${'$'}{WORKSPACE_PATH}). " +
                        "Please summarize into a concise morning report containing: 1) Uncommitted changes overview (grouped by module/topic); " +
                        "2) Recent commit progress; 3) Recommended focus items for today (omit if none). " +
                        "Strictly read-only analysis: do not modify, stage, commit, or push any files, and do not execute any write operations.\n" +
                        "Inspection output:\n\${'$'}{git_survey.output}",
                ),
                timeoutSeconds = 600,
                canvasX = 520f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "notify",
                WorkflowNodeType.HOST_ACTION,
                "Push Brief Notification",
                config = mapOf(
                    "action" to "notification",
                    "title" to "Morning Report Sentinel",
                    "text" to "\${'$'}{summarize.output}",
                ),
                failurePolicy = FailurePolicy.CONTINUE,
                canvasX = 760f,
                canvasY = 120f,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Report Completed", canvasX = 1000f, canvasY = 120f),
        ),
        edges = chain("start", "git_survey", "summarize", "notify", "done"),
    )

    private fun chain(vararg ids: String): List<WorkflowEdge> = ids.toList().zipWithNext().mapIndexed { index, pair ->
        WorkflowEdge("edge_$index", pair.first, "success", pair.second)
    }
}
