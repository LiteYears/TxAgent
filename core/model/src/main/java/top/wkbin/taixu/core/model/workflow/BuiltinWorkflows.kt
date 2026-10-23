package top.wkbin.taixu.core.model.workflow

object BuiltinWorkflows {
    val all: List<WorkflowDefinition>
        get() = listOf(
            systemHealth,
            conditionBranchDemo,
            networkDiagnostic,
            releaseApk,
            installGeneratedApk,
            buildDoctor,
            buildRepair,
            reverseAudit,
            atomicCommit,
            hostAutomationLab,
            hostBroadcastDemo,
            hostAgentGuiPilot, VirtualScreenWorkflowActions.template(), MorningReportSentinel.definition,
        ).map(WorkflowLayout::arrange)

    fun find(id: String): WorkflowDefinition? = all.firstOrNull { it.id == id }

    private fun chain(vararg ids: String): List<WorkflowEdge> = ids.toList().zipWithNext().mapIndexed { index, pair ->
        WorkflowEdge("edge_$index", pair.first, "success", pair.second)
    }

    val systemHealth = WorkflowDefinition(
        id = "linux_system_health",
        name = "Linux Sandbox & Hardware Checkup",
        description = "One-click health check: inspect Linux kernel, architecture, CPU, memory, disk, and CLI toolchain.",
        category = "Checkup",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf linux_system_health"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Start Checkup", canvasX = 40f, canvasY = 120f),
            WorkflowNode(
                "os_kernel",
                WorkflowNodeType.BASH_COMMAND,
                "System & Kernel",
                config = mapOf("command" to "echo '=== 1. Kernel & Architecture ===' && uname -a && echo '' && echo '=== 2. Distribution Info ===' && (cat /etc/os-release | head -n 8 || cat /etc/issue || echo 'Unknown distribution')"),
                canvasX = 280f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "hardware",
                WorkflowNodeType.BASH_COMMAND,
                "Memory & Storage",
                config = mapOf("command" to "echo '=== 1. Memory Usage ===' && (free -h || cat /proc/meminfo | head -n 4) && echo '' && echo '=== 2. Root Disk Space ===' && df -h /"),
                canvasX = 520f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "toolchain",
                WorkflowNodeType.BASH_COMMAND,
                "Toolchain Verification",
                config = mapOf("command" to "echo '=== Core CLI Tools ===' && for cmd in bash sh git python3 python curl wget make gcc tar find grep awk sed; do which \$cmd 2>/dev/null && echo \"  ✔ \$cmd: available (\$(which \$cmd))\" || echo \"  ✘ \$cmd: not installed\"; done"),
                canvasX = 760f,
                canvasY = 120f,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Checkup Report Generated", canvasX = 1000f, canvasY = 120f),
        ),
        edges = chain("start", "os_kernel", "hardware", "toolchain", "done"),
    )

    val conditionBranchDemo = WorkflowDefinition(
        id = "condition_branch_demo",
        name = "Conditional Branch Test (Demo)",
        description = "Test conditional branches: routes to Branch A (Normal) or Branch B (Abnormal) based on /etc presence.",
        category = "Demo",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf condition_branch_demo"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Start Test", canvasX = 40f, canvasY = 160f),
            WorkflowNode(
                "check_env",
                WorkflowNodeType.BASH_COMMAND,
                "Check Sandbox Environment",
                config = mapOf("command" to "echo '=== Checking Sandbox Root ===' && test -d /etc && echo '✔ /etc exists'"),
                canvasX = 280f,
                canvasY = 160f,
            ),
            WorkflowNode(
                "branch",
                WorkflowNodeType.CONDITION_BRANCH,
                "Branch Router",
                description = "Route execution to different branches based on check results",
                config = mapOf("expression" to "exitCode == 0"),
                failurePolicy = FailurePolicy.CONTINUE,
                canvasX = 520f,
                canvasY = 160f,
            ),
            WorkflowNode(
                "branch_success",
                WorkflowNodeType.BASH_COMMAND,
                "Branch A (Normal)",
                config = mapOf("command" to "echo '==============================' && echo '🎉 Branch A Triggered' && echo 'Sandbox basic configuration is normal, proceeding.' && echo '=============================='"),
                canvasX = 780f,
                canvasY = 80f,
            ),
            WorkflowNode(
                "branch_failure",
                WorkflowNodeType.BASH_COMMAND,
                "Branch B (Abnormal)",
                config = mapOf("command" to "echo '==============================' && echo '⚠️ Branch B Triggered' && echo 'Missing essential directories in sandbox, fallback alerted.' && echo '=============================='"),
                canvasX = 780f,
                canvasY = 240f,
            ),
            WorkflowNode(
                "done",
                WorkflowNodeType.TERMINAL_OUTPUT,
                "Test Completed",
                canvasX = 1040f,
                canvasY = 160f,
            ),
        ),
        edges = listOf(
            WorkflowEdge("edge_demo_1", "start", "output", "check_env"),
            WorkflowEdge("edge_demo_2", "check_env", "output", "branch"),
            WorkflowEdge("edge_demo_3", "branch", "output", "branch_success", conditionExpression = "exitCode == 0"),
            WorkflowEdge("edge_demo_4", "branch", "output", "branch_failure", conditionExpression = "exitCode != 0"),
            WorkflowEdge("edge_demo_5", "branch_success", "output", "done"),
            WorkflowEdge("edge_demo_6", "branch_failure", "output", "done"),
        ),
    )

    val networkDiagnostic = WorkflowDefinition(
        id = "network_dns_diagnostic",
        name = "Network & DNS Diagnostics",
        description = "Zero-config network test: inspect DNS configuration, domain resolution, and public HTTP connectivity.",
        category = "Network",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf network_dns_diagnostic"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Start Diagnostics", canvasX = 40f, canvasY = 120f),
            WorkflowNode(
                "dns_conf",
                WorkflowNodeType.BASH_COMMAND,
                "Inspect DNS Config",
                config = mapOf("command" to "echo '=== /etc/resolv.conf ===' && (cat /etc/resolv.conf || echo 'No resolv.conf') && echo '' && echo '=== /etc/hosts ===' && head -n 10 /etc/hosts"),
                canvasX = 280f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "http_ping",
                WorkflowNodeType.BASH_COMMAND,
                "Public HTTP Test",
                config = mapOf("command" to "echo '=== Public Connectivity Test (HTTP HEAD) ===' && (curl -I -s -m 5 https://www.baidu.com | head -n 4 || curl -I -s -m 5 https://www.qq.com | head -n 4 || curl -I -s -m 5 https://www.bilibili.com | head -n 4 || echo 'curl test completed')"),
                canvasX = 520f,
                canvasY = 120f,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Diagnostics Complete", canvasX = 760f, canvasY = 120f),
        ),
        edges = chain("start", "dns_conf", "http_ping", "done"),
    )

    val releaseApk = WorkflowDefinition(
        id = "release_apk_direct_install",
        name = "Build and Install APK",
        description = "Inspect repository, build ARM64 Release APK, and install to host after approval.",
        category = "Android",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf release_apk_direct_install"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Start", canvasX = 40f, canvasY = 80f),
            WorkflowNode("git", WorkflowNodeType.BASH_COMMAND, "Check Git Status", config = mapOf("command" to "git status --short"), canvasX = 260f, canvasY = 80f),
            WorkflowNode("build", WorkflowNodeType.TAIXU_BUILD, "Build Release APK", config = mapOf("projectType" to "android", "task" to "assembleRelease"), timeoutSeconds = 3600, canvasX = 500f, canvasY = 80f),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Approve Install", description = "Confirm whether to install APK on host device.", canvasX = 740f, canvasY = 80f),
            WorkflowNode("install", WorkflowNodeType.HOST_ACTION, "Install APK", config = mapOf("action" to "install-apk", "artifactFrom" to "build"), timeoutSeconds = 600, canvasX = 980f, canvasY = 80f),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Done", canvasX = 1220f, canvasY = 80f),
        ),
        edges = chain("start", "git", "build", "approve", "install", "done"),
    )

    val buildDoctor = WorkflowDefinition(
        id = "mobile_build_doctor",
        name = "Mobile Build Environment Diagnostics",
        description = "Run taixu-build doctor and analyze, producing actionable compatibility reports.",
        category = "Diagnostics",
        isBuiltin = true,
        trigger = WorkflowTrigger.Proactive("BUILD_FAILED", suggestionLabel = "Diagnose Build Failure"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Start"),
            WorkflowNode("doctor", WorkflowNodeType.TAIXU_BUILD, "Environment Doctor", config = mapOf("mode" to "doctor"), timeoutSeconds = 900),
            WorkflowNode("analyze", WorkflowNodeType.TAIXU_BUILD, "Analyze Project", config = mapOf("mode" to "analyze"), timeoutSeconds = 900),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "View Diagnostics"),
        ),
        edges = chain("start", "doctor", "analyze", "done"),
    )

    val installGeneratedApk = WorkflowDefinition(
        id = "install_generated_apk",
        name = "Install Latest Generated APK",
        description = "Install build artifact to host device after manual confirmation.",
        category = "Android",
        isBuiltin = true,
        trigger = WorkflowTrigger.Proactive("APK_GENERATED", suggestionLabel = "Install Generated APK"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Read APK", config = mapOf("requiredVariables" to "APK_PATH")),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Approve Install", description = "Confirm installing generated APK to host: \${APK_PATH}", canvasX = 260f),
            WorkflowNode("install", WorkflowNodeType.HOST_ACTION, "Install APK", config = mapOf("action" to "install-apk"), timeoutSeconds = 600, canvasX = 520f),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Install Request Submitted", canvasX = 780f),
        ),
        edges = chain("start", "approve", "install", "done"),
    )

    val reverseAudit = WorkflowDefinition(
        id = "android_reverse_audit",
        name = "APK Reverse Engineering Audit",
        description = "Unpack, decompile, and audit APK read-only; outputs evidence-backed risk report; existing output not overwritten.",
        category = "Reverse",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf android_reverse_audit"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Select APK", config = mapOf("requiredVariables" to "APK_PATH")),
            WorkflowNode("apktool", WorkflowNodeType.BASH_COMMAND, "Unpack Resources", config = mapOf("command" to "apktool d ${'$'}{APK_PATH} -o ./workflow-output/apktool"), timeoutSeconds = 1200),
            WorkflowNode("jadx", WorkflowNodeType.BASH_COMMAND, "Decompile Source", config = mapOf("command" to "jadx -d ./workflow-output/jadx ${'$'}{APK_PATH}"), timeoutSeconds = 1800),
            WorkflowNode("audit", WorkflowNodeType.AGENT_INFERENCE, "Read-Only Audit & Report", config = mapOf("prompt" to "Audit workspace workflow-output/apktool and workflow-output/jadx read-only. First index and search manifest, exported components, network security config, and sensitive data handling, then inspect source code. Do not modify files or run audited programs. Output Markdown report listing files and line numbers, evidence, impact, recommendations, and unverifiable items; do not present speculations as verified vulnerabilities."), timeoutSeconds = 1800),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Audit Report"),
        ),
        edges = chain("start", "apktool", "jadx", "audit", "done"),
    )

    val atomicCommit = WorkflowDefinition(
        id = "git_atomic_smart_commit",
        name = "Review and Commit Changes",
        description = "Inspect changes, await manual confirmation, and create local commit using provided message; does not push by default.",
        category = "Git",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf git_atomic_smart_commit"),
        defaultVariables = mapOf("COMMIT_MESSAGE" to "chore: update workspace"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Start"),
            WorkflowNode("diff", WorkflowNodeType.BASH_COMMAND, "Review Changes", config = mapOf("command" to "git status --short && git diff --stat && git diff && git diff --cached")),
            WorkflowNode("suggest", WorkflowNodeType.AGENT_INFERENCE, "Generate Commit Suggestion", config = mapOf("prompt" to "Review Git changes read-only, output a Conventional Commit suggestion and concise risk summary. Do not modify, stage, commit, or push files. Changes:\n${'$'}{diff.output}")),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Approve Local Commit", config = mapOf("requestedVariables" to "COMMIT_MESSAGE")),
            WorkflowNode("commit", WorkflowNodeType.BASH_COMMAND, "Create Commit", config = mapOf("command" to "git add -A && git commit -m ${'$'}{COMMIT_MESSAGE}")),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Commit Complete"),
        ),
        edges = chain("start", "diff", "suggest", "approve", "commit", "done"),
    )

    val buildRepair = WorkflowDefinition(
        id = "assisted_build_repair",
        name = "Fix Build After Confirmation",
        description = "Diagnose build failure, perform minimal repair upon approval, and verify with rebuild.",
        category = "Diagnostics",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf assisted_build_repair"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Input Build Error", config = mapOf("requiredVariables" to "BUILD_ERROR")),
            WorkflowNode("diagnose", WorkflowNodeType.AGENT_INFERENCE, "Read-Only Diagnosis", config = mapOf("prompt" to "Inspect workspace read-only, identify root cause and minimal repair plan for error below. Do not modify files.\n${'$'}{BUILD_ERROR}"), timeoutSeconds = 900),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Approve File Fixes", description = "Confirm allowing agent to modify current workspace per diagnosis. Ensure important changes are backed up."),
            WorkflowNode("repair", WorkflowNodeType.SUBAGENT_DELEGATE, "Apply Minimal Fix", config = mapOf("writePaths" to ".", "prompt" to "Apply minimal build fix per diagnosis, preserving existing user changes; do not delete project, reset Git, commit, or push. Stop and explain if uncertain. Diagnosis:\n${'$'}{diagnose.output}\nOriginal error:\n${'$'}{BUILD_ERROR}"), timeoutSeconds = 1800),
            WorkflowNode("diff", WorkflowNodeType.BASH_COMMAND, "Check Fix Diff", config = mapOf("command" to "git status --short && git diff && git diff --cached")),
            WorkflowNode("rebuildApproval", WorkflowNodeType.HUMAN_APPROVAL, "Approve Rebuild", description = "Review fixes and start build verification. Rejecting will not rollback modified files."),
            WorkflowNode("build", WorkflowNodeType.TAIXU_BUILD, "Verify Build", config = mapOf("task" to "assembleDebug"), timeoutSeconds = 3600),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Verification Result"),
        ),
        edges = chain("start", "diagnose", "approve", "repair", "diff", "rebuildApproval", "build", "done"),
    )

    val hostAutomationLab = WorkflowDefinition(
        id = "host_automation_lab",
        name = "Host Automation Lab",
        description = "Probe privilege -> Launch Settings -> Wait -> Observe Screen -> Branch -> Notify. Requires Shizuku/Root.",
        category = "Host",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf host_automation_lab"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Start", canvasX = 40f, canvasY = 160f),
            WorkflowNode(
                "status",
                WorkflowNodeType.HOST_ACTION,
                "Check Privilege Mode",
                config = mapOf("action" to "status"),
                canvasX = 240f,
                canvasY = 160f,
            ),
            WorkflowNode(
                "gate",
                WorkflowNodeType.CONDITION_BRANCH,
                "Is Privileged?",
                config = mapOf("expression" to "\${HOST_PRIVILEGED} == true"),
                failurePolicy = FailurePolicy.CONTINUE,
                canvasX = 460f,
                canvasY = 160f,
            ),
            WorkflowNode(
                "launch",
                WorkflowNodeType.HOST_ACTION,
                "Launch Settings",
                config = mapOf("action" to "app_launch", "package" to "com.android.settings"),
                canvasX = 700f,
                canvasY = 60f,
            ),
            WorkflowNode(
                "wait",
                WorkflowNodeType.HOST_ACTION,
                "Wait for Settings",
                config = mapOf("action" to "wait_foreground", "package" to "com.android.settings", "timeoutSeconds" to "12"),
                canvasX = 920f,
                canvasY = 60f,
            ),
            WorkflowNode(
                "observe",
                WorkflowNodeType.HOST_ACTION,
                "Observe Screen Hierarchy",
                config = mapOf("action" to "screen_observe", "onlyInteractive" to "true"),
                canvasX = 1140f,
                canvasY = 60f,
            ),
            WorkflowNode(
                "notify_ok",
                WorkflowNodeType.HOST_ACTION,
                "Notify: Success",
                config = mapOf(
                    "action" to "notification",
                    "title" to "TaiXu Workflow",
                    "text" to "Host automation lab completed screen observation",
                ),
                canvasX = 1360f,
                canvasY = 60f,
            ),
            WorkflowNode(
                "degraded",
                WorkflowNodeType.HOST_ACTION,
                "Fallback: Toast",
                config = mapOf(
                    "action" to "toast",
                    "text" to "Shizuku/Root not detected; privileged steps skipped",
                ),
                canvasX = 700f,
                canvasY = 260f,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Lab Complete", canvasX = 1580f, canvasY = 160f),
        ),
        edges = listOf(
            WorkflowEdge("e1", "start", "output", "status"),
            WorkflowEdge("e2", "status", "output", "gate"),
            WorkflowEdge("e3", "gate", "output", "launch", conditionExpression = "exitCode == 0"),
            WorkflowEdge("e4", "gate", "output", "degraded", conditionExpression = "exitCode != 0"),
            WorkflowEdge("e5", "launch", "output", "wait"),
            WorkflowEdge("e6", "wait", "output", "observe"),
            WorkflowEdge("e7", "observe", "output", "notify_ok"),
            WorkflowEdge("e8", "notify_ok", "output", "done"),
            WorkflowEdge("e9", "degraded", "output", "done"),
        ),
    )

    val hostBroadcastDemo = WorkflowDefinition(
        id = "host_broadcast_and_settings",
        name = "Broadcast & System Settings Demo",
        description = "Set variables -> Send broadcast -> Read/write brightness settings (privileged) with confirmation.",
        category = "Host",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf host_broadcast_and_settings"),
        defaultVariables = mapOf("DEMO_FLAG" to "taixu-workflow"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Start"),
            WorkflowNode(
                "vars",
                WorkflowNodeType.SET_VARIABLE,
                "Prepare Demo Variables",
                config = mapOf(
                    "variables" to "BROADCAST_ACTION=top.wkbin.taixu.action.WORKFLOW_DEMO\nPAYLOAD=\${DEMO_FLAG}",
                ),
            ),
            WorkflowNode(
                "broadcast",
                WorkflowNodeType.HOST_ACTION,
                "Send Demo Broadcast",
                config = mapOf(
                    "action" to "send_broadcast",
                    "intentAction" to "\${BROADCAST_ACTION}",
                    "extras" to "source=workflow\npayload=\${PAYLOAD}\nflag:bool=true",
                ),
            ),
            WorkflowNode(
                "read_brightness",
                WorkflowNodeType.HOST_ACTION,
                "Read Brightness",
                config = mapOf(
                    "action" to "settings_get",
                    "namespace" to "system",
                    "key" to "screen_brightness",
                    "outputVariable" to "BRIGHTNESS",
                ),
            ),
            WorkflowNode(
                "approve",
                WorkflowNodeType.HUMAN_APPROVAL,
                "Confirm Adjust Brightness",
                description = "Will set brightness to 120 (can skip if rejected). See upstream for current value.",
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "gate",
                WorkflowNodeType.CONDITION_BRANCH,
                "Is Approved?",
                config = mapOf("expression" to "exitCode == 0"),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "write_brightness",
                WorkflowNodeType.HOST_ACTION,
                "Set Brightness to 120",
                config = mapOf(
                    "action" to "settings_put",
                    "namespace" to "system",
                    "key" to "screen_brightness",
                    "value" to "120",
                ),
            ),
            WorkflowNode(
                "delay",
                WorkflowNodeType.DELAY,
                "Wait a Moment",
                config = mapOf("seconds" to "1"),
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Demo Complete"),
        ),
        edges = listOf(
            WorkflowEdge("e1", "start", "output", "vars"),
            WorkflowEdge("e2", "vars", "output", "broadcast"),
            WorkflowEdge("e3", "broadcast", "output", "read_brightness"),
            WorkflowEdge("e4", "read_brightness", "output", "approve"),
            WorkflowEdge("e5", "approve", "output", "gate"),
            WorkflowEdge("e6", "gate", "output", "write_brightness", conditionExpression = "exitCode == 0"),
            WorkflowEdge("e7", "gate", "output", "done", conditionExpression = "exitCode != 0"),
            WorkflowEdge("e8", "write_brightness", "output", "delay"),
            WorkflowEdge("e9", "delay", "output", "done"),
        ),
    )

    /**
     * Agent-in-the-loop GUI pilot: privilege check → approve → optional launch →
     * AGENT_INFERENCE that drives host(screen_*) tools to chase [GUI_GOAL].
     */
    val hostAgentGuiPilot = WorkflowDefinition(
        id = "host_agent_gui_pilot",
        name = "Agent GUI Flight Test",
        description = "Local loop under Shizuku/Root: observe screen -> model decides step -> click/input. Supervised run.",
        category = "Host",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf host_agent_gui_pilot"),
        defaultVariables = mapOf(
            "GUI_GOAL" to "Open Settings app, navigate to Display settings, and inspect current brightness options. Do not modify system settings without confirmation.",
            "TARGET_PACKAGE" to "com.tencent.mobileqq",
        ),
        nodes = listOf(
            WorkflowNode(
                "start",
                WorkflowNodeType.TRIGGER,
                "Input Goal",
                config = mapOf("requiredVariables" to "GUI_GOAL"),
            ),
            WorkflowNode(
                "status",
                WorkflowNodeType.HOST_ACTION,
                "Check Shizuku/Root",
                config = mapOf("action" to "status"),
            ),
            WorkflowNode(
                "priv_gate",
                WorkflowNodeType.CONDITION_BRANCH,
                "Privileges Ready?",
                config = mapOf("expression" to "\${HOST_PRIVILEGED} == true"),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "no_priv",
                WorkflowNodeType.HOST_ACTION,
                "Prompt Missing Privilege",
                config = mapOf(
                    "action" to "toast",
                    "text" to "Please authorize and switch to Shizuku or Root in Settings before running this workflow",
                ),
            ),
            WorkflowNode(
                "approve",
                WorkflowNodeType.HUMAN_APPROVAL,
                "Confirm Agent Screen Control",
                description = "The agent will use host primitives (observe, click, swipe, type, key, launch) to operate UI for GUI_GOAL.\n\n" +
                    "Please supervise the screen directly during execution; reject or interrupt immediately if unexpected views appear.\n\n" +
                    "Rejecting terminates without starting UI actions.",
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "approve_gate",
                WorkflowNodeType.CONDITION_BRANCH,
                "Is Approved?",
                config = mapOf("expression" to "exitCode == 0"),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "launch",
                WorkflowNodeType.HOST_ACTION,
                "Open Target App (Optional)",
                config = mapOf(
                    "action" to "app_launch",
                    "package" to "\${TARGET_PACKAGE}",
                ),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "wait_fg",
                WorkflowNodeType.DELAY,
                "Wait for UI to Settle",
                config = mapOf("seconds" to "2"),
            ),
            WorkflowNode(
                "pilot",
                WorkflowNodeType.HOST_ACTION,
                "GUI Flight Loop",
                config = mapOf(
                    "action" to "gui_pilot",
                    "goal" to "\${GUI_GOAL}",
                    "package" to "\${TARGET_PACKAGE}",
                    "maxSteps" to "18",
                ),
                timeoutSeconds = 900,
            ),
            WorkflowNode(
                "notify",
                WorkflowNodeType.HOST_ACTION,
                "Notify Flight Finished",
                config = mapOf(
                    "action" to "notification",
                    "title" to "GUI Flight Finished",
                    "text" to "Agent GUI flight test finished, please inspect workflow output",
                ),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Flight Report"),
            WorkflowNode("aborted", WorkflowNodeType.TERMINAL_OUTPUT, "Cancelled or Missing Privilege"),
        ),
        edges = listOf(
            WorkflowEdge("e1", "start", "output", "status"),
            WorkflowEdge("e2", "status", "output", "priv_gate"),
            WorkflowEdge("e3", "priv_gate", "output", "approve", conditionExpression = "exitCode == 0"),
            WorkflowEdge("e4", "priv_gate", "output", "no_priv", conditionExpression = "exitCode != 0"),
            WorkflowEdge("e5", "no_priv", "output", "aborted"),
            WorkflowEdge("e6", "approve", "output", "approve_gate"),
            WorkflowEdge("e7", "approve_gate", "output", "launch", conditionExpression = "exitCode == 0"),
            WorkflowEdge("e8", "approve_gate", "output", "aborted", conditionExpression = "exitCode != 0"),
            WorkflowEdge("e9", "launch", "output", "wait_fg"),
            WorkflowEdge("e10", "wait_fg", "output", "pilot"),
            WorkflowEdge("e11", "pilot", "output", "notify"),
            WorkflowEdge("e12", "notify", "output", "done"),
        ),
    )
}
