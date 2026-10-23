package top.wkbin.taixu.core.model.workflow

/** Privilege floor required by a host workflow action. */
enum class HostWorkflowPrivilege {
    /** App Context / HostBridge only — works without Shizuku/Root. */
    NONE,
    /** Needs Shizuku or Root effective mode. */
    PRIVILEGED,
}

data class HostWorkflowField(
    val key: String,
    val label: String,
    val hint: String = "",
    val required: Boolean = false,
    val multiline: Boolean = false,
)

data class HostWorkflowActionDef(
    val id: String,
    val label: String,
    val category: String,
    val privilege: HostWorkflowPrivilege,
    val description: String = "",
    val fields: List<HostWorkflowField> = emptyList(),
)

/**
 * Catalog of first-class host actions exposed by workflow [HOST_ACTION] nodes.
 * Keep IDs stable — they are persisted in node config.
 */
object HostWorkflowActions {
    val all: List<HostWorkflowActionDef> = listOf(
        HostWorkflowActionDef(
            id = "status",
            label = "Privilege Status",
            category = "Diagnostics",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Report active PRoot / Shizuku / Root mode and authorization status",
        ),
        HostWorkflowActionDef(
            id = "health",
            label = "Host Bridge Health Check",
            category = "Diagnostics",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Probe taixu-host bridge and wireless ADB health status",
        ),
        HostWorkflowActionDef(
            id = "device_status",
            label = "Device Status Snapshot",
            category = "Diagnostics",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Battery / Network / Foreground Activity / Storage summary",
        ),
        HostWorkflowActionDef(
            id = "logcat",
            label = "Capture Logcat",
            category = "Diagnostics",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Capture recent system logcat logs (filterable by tag)",
            fields = listOf(
                HostWorkflowField("tag", "Tag (Optional)", "e.g. TaiXu"),
                HostWorkflowField("tail_lines", "Lines", "Default 200"),
            ),
        ),
        HostWorkflowActionDef(
            id = "install-apk",
            label = "Install APK",
            category = "Apps",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Install APK via host bridge (wireless ADB or package installer)",
            fields = listOf(
                HostWorkflowField("artifactFrom", "Artifact Source Node ID", "Leave empty to use APK_PATH"),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_launch",
            label = "Launch App",
            category = "Apps",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Launch app by package name; falls back to monkey without launcher activity",
            fields = listOf(HostWorkflowField("package", "Package Name", "e.g. com.android.settings", required = true)),
        ),
        HostWorkflowActionDef(
            id = "app_force_stop",
            label = "Force Stop App",
            category = "Apps",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "am force-stop target package name",
            fields = listOf(HostWorkflowField("package", "Package Name", required = true)),
        ),
        HostWorkflowActionDef(
            id = "app_clear_data",
            label = "Clear App Data",
            category = "Apps",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm clear (destructive operation, manual approval recommended)",
            fields = listOf(HostWorkflowField("package", "Package Name", required = true)),
        ),
        HostWorkflowActionDef(
            id = "app_freeze",
            label = "Freeze / Disable App",
            category = "Apps",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm disable-user",
            fields = listOf(
                HostWorkflowField("package", "Package Name", required = true),
                HostWorkflowField("user", "User ID", "Default 0"),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_unfreeze",
            label = "Unfreeze / Enable App",
            category = "Apps",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm enable",
            fields = listOf(
                HostWorkflowField("package", "Package Name", required = true),
                HostWorkflowField("user", "User ID", "Default 0"),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_grant_permission",
            label = "Grant Runtime Permission",
            category = "Apps",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm grant specified permission",
            fields = listOf(
                HostWorkflowField("package", "Package Name", required = true),
                HostWorkflowField("permission", "Permission Name", "e.g. android.permission.CAMERA", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_revoke_permission",
            label = "Revoke Runtime Permission",
            category = "Apps",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm revoke specified permission",
            fields = listOf(
                HostWorkflowField("package", "Package Name", required = true),
                HostWorkflowField("permission", "Permission Name", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "wait_foreground",
            label = "Wait for Foreground App",
            category = "Apps",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Poll until target package enters foreground or times out",
            fields = listOf(
                HostWorkflowField("package", "Package Name", required = true),
                HostWorkflowField("timeoutSeconds", "Timeout (s)", "Default 15"),
            ),
        ),
        HostWorkflowActionDef(
            id = "send_broadcast",
            label = "Send Broadcast",
            category = "Intent",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Send explicit/implicit broadcast; protected broadcasts use privileged am broadcast",
            fields = listOf(
                HostWorkflowField("intentAction", "Action", "e.g. android.intent.action.BOOT_COMPLETED", required = true),
                HostWorkflowField("package", "Target Package (Optional)"),
                HostWorkflowField("component", "Component (Optional)", "pkg/.Receiver"),
                HostWorkflowField("extras", "Extras (Multiline key=value)", "flag:bool=true\ncount:int=1", multiline = true),
                HostWorkflowField("preferShell", "Force Privileged Shell", "true/false, default auto"),
            ),
        ),
        HostWorkflowActionDef(
            id = "start_activity",
            label = "Start Activity",
            category = "Intent",
            privilege = HostWorkflowPrivilege.NONE,
            description = "am start / startActivity: Component, Action, Data URI, Extras",
            fields = listOf(
                HostWorkflowField("component", "Component", "pkg/.Activity"),
                HostWorkflowField("intentAction", "Action", "e.g. android.intent.action.VIEW"),
                HostWorkflowField("dataUri", "Data URI", "https://... or content://..."),
                HostWorkflowField("mimeType", "MIME (Optional)"),
                HostWorkflowField("extras", "Extras (Multiline)", multiline = true),
                HostWorkflowField("preferShell", "Force Privileged Shell", "true/false"),
            ),
        ),
        HostWorkflowActionDef(
            id = "start_service",
            label = "Start Service",
            category = "Intent",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "am startservice / start-foreground-service",
            fields = listOf(
                HostWorkflowField("component", "Component", "pkg/.Service", required = true),
                HostWorkflowField("intentAction", "Action (Optional)"),
                HostWorkflowField("extras", "Extras (Multiline)", multiline = true),
                HostWorkflowField("foreground", "Foreground Service", "true/false, default false"),
            ),
        ),
        HostWorkflowActionDef(
            id = "settings_get",
            label = "Get System Setting",
            category = "System",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "settings get system/secure/global",
            fields = listOf(
                HostWorkflowField("namespace", "Namespace", "system / secure / global", required = true),
                HostWorkflowField("key", "Key", required = true),
                HostWorkflowField("outputVariable", "Output Variable", "Default SETTINGS_VALUE"),
            ),
        ),
        HostWorkflowActionDef(
            id = "settings_put",
            label = "Put System Setting",
            category = "System",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "settings put; system namespace prioritizes ContentResolver",
            fields = listOf(
                HostWorkflowField("namespace", "Namespace", "system / secure / global", required = true),
                HostWorkflowField("key", "Key", required = true),
                HostWorkflowField("value", "Value", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "airplane_mode",
            label = "Airplane Mode",
            category = "System",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Toggle airplane mode and broadcast status change",
            fields = listOf(HostWorkflowField("enabled", "Enabled", "true / false", required = true)),
        ),
        HostWorkflowActionDef(
            id = "wifi_set",
            label = "Wi-Fi Switch",
            category = "System",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "cmd wifi set-wifi-enabled",
            fields = listOf(HostWorkflowField("enabled", "Enabled", "true / false", required = true)),
        ),
        HostWorkflowActionDef(
            id = "volume_set",
            label = "Set Media Volume",
            category = "System",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Set STREAM_MUSIC volume (typically 0-15)",
            fields = listOf(HostWorkflowField("value", "Volume", "e.g. 8", required = true)),
        ),
        HostWorkflowActionDef(
            id = "screen_observe",
            label = "Observe Screen",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "uiautomator dump: foreground app and view hierarchy summary",
            fields = listOf(HostWorkflowField("onlyInteractive", "Interactive Only", "true/false, default true")),
        ),
        HostWorkflowActionDef(
            id = "screen_click",
            label = "Click Coordinate",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Fallback: accessibility gesture -> cmd input -> bin input",
            fields = listOf(
                HostWorkflowField("x", "X", required = true),
                HostWorkflowField("y", "Y", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_double_click",
            label = "Double-Click Coordinate",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            fields = listOf(
                HostWorkflowField("x", "X", required = true),
                HostWorkflowField("y", "Y", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_long_press",
            label = "Long-Press Coordinate",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            fields = listOf(
                HostWorkflowField("x", "X", required = true),
                HostWorkflowField("y", "Y", required = true),
                HostWorkflowField("durationMs", "Duration (ms)", "Default 800"),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_swipe",
            label = "Swipe Screen",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            fields = listOf(
                HostWorkflowField("x1", "Start X", required = true),
                HostWorkflowField("y1", "Start Y", required = true),
                HostWorkflowField("x2", "End X", required = true),
                HostWorkflowField("y2", "End Y", required = true),
                HostWorkflowField("durationMs", "Duration (ms)", "Default 300"),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_scroll",
            label = "Scroll Screen",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Scroll by direction (up/down/left/right), mapped to swipe internally",
            fields = listOf(
                HostWorkflowField("direction", "Direction", "up / down / left / right", required = true),
                HostWorkflowField("distanceRatio", "Distance Ratio", "0.15-0.8, default 0.45"),
                HostWorkflowField("durationMs", "Duration (ms)", "Default 350"),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_input_text",
            label = "Paste / Input Text",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Text input via clipboard + paste; automatic fallback to Ctrl+V / ASCII input text",
            fields = listOf(HostWorkflowField("text", "Text", required = true)),
        ),
        HostWorkflowActionDef(
            id = "screen_key",
            label = "Send Key Event",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "back/home/recents prioritizes accessibility global action, then keyevent",
            fields = listOf(HostWorkflowField("key", "Key", "back/home/recents/enter/delete/paste/power", required = true)),
        ),
        HostWorkflowActionDef(
            id = "screen_capture",
            label = "Capture Screen",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            fields = listOf(HostWorkflowField("path", "Save Path", "/sdcard/Download/taixu-screen.png", required = true)),
        ),
        HostWorkflowActionDef(
            id = "gui_pilot",
            label = "Agent GUI Pilot Loop",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Local loop: observe screen -> model outputs step JSON -> click/input. Bypasses subagent lane.",
            fields = listOf(
                HostWorkflowField("goal", "Goal", "Optional, reads GUI_GOAL by default"),
                HostWorkflowField("package", "Target Package", "Optional, reads TARGET_PACKAGE by default"),
                HostWorkflowField("maxSteps", "Max Steps", "Default 18"),
            ),
        ),
        HostWorkflowActionDef(
            id = "toast",
            label = "Show Toast",
            category = "Interaction",
            privilege = HostWorkflowPrivilege.NONE,
            fields = listOf(HostWorkflowField("text", "Content", required = true)),
        ),
        HostWorkflowActionDef(
            id = "vibrate",
            label = "Vibrate",
            category = "Interaction",
            privilege = HostWorkflowPrivilege.NONE,
            fields = listOf(HostWorkflowField("durationMs", "Duration (ms)", "Default 200")),
        ),
        HostWorkflowActionDef(
            id = "clipboard_set",
            label = "Set Clipboard",
            category = "Interaction",
            privilege = HostWorkflowPrivilege.NONE,
            fields = listOf(HostWorkflowField("text", "Text", required = true)),
        ),
        HostWorkflowActionDef(
            id = "clipboard_get",
            label = "Get Clipboard",
            category = "Interaction",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Result written to CLIPBOARD variable",
        ),
        HostWorkflowActionDef(
            id = "notification",
            label = "Send Notification",
            category = "Interaction",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Post a TaiXu workflow notification in status bar",
            fields = listOf(
                HostWorkflowField("title", "Title", required = true),
                HostWorkflowField("text", "Text", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "shell",
            label = "Privileged Shell",
            category = "Advanced",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Execute arbitrary host shell under Shizuku/Root (high risk)",
            fields = listOf(HostWorkflowField("command", "Command", "e.g. dumpsys window | head", required = true, multiline = true)),
        ),
        HostWorkflowActionDef(
            id = "cmd",
            label = "cmd Service Call",
            category = "Advanced",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Execute /system/bin/cmd <service> ...",
            fields = listOf(
                HostWorkflowField("service", "Service Name", "e.g. wifi / activity / package", required = true),
                HostWorkflowField("args", "Arguments", "set-wifi-enabled enabled", multiline = true),
            ),
        ),
    ) + VirtualScreenWorkflowActions.all

    fun find(id: String): HostWorkflowActionDef? = all.firstOrNull { it.id == id }

    fun grouped(): Map<String, List<HostWorkflowActionDef>> = all.groupBy { it.category }
}
