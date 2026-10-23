package top.wkbin.taixu.core.model.workflow

/** Persisted IDs share the HOST tool's virtual-screen primitives. Coordinates are always 0–1000. */
object VirtualScreenWorkflowActions {
    private fun field(key: String, label: String, hint: String = "", required: Boolean = false) =
        HostWorkflowField(key, label, hint, required, multiline = key == "text")

    private fun action(id: String, label: String, description: String, vararg fields: HostWorkflowField) =
        HostWorkflowActionDef(
            id, label, "Virtual Screen", HostWorkflowPrivilege.NONE, description,
            listOf(field("session", "Virtual Screen Session", "Leave empty to isolate per workflow; steps in the same run share screen")) + fields +
                field("wait_ms", "Post-action Wait (ms)", "Default 0; 0-600000, variables allowed"),
        )

    val all = listOf(
        action("virtual_screen_ensure", "Create Virtual Screen", "Create independent virtual screen, requires Shower service."),
        action("virtual_screen_launch", "Open App on Virtual Screen", "Launch app by package name without calling model.",
            field("package", "Package Name", "e.g. com.android.settings or \${PACKAGE}", true)),
        action("virtual_screen_click", "Virtual Screen Click", "0-1000 relative coordinates, independent of screenshot scaling.",
            field("x", "X (0-1000)", required = true), field("y", "Y (0-1000)", required = true)),
        action("virtual_screen_double_click", "Virtual Screen Double Click", "0-1000 relative coordinates.",
            field("x", "X (0-1000)", required = true), field("y", "Y (0-1000)", required = true)),
        action("virtual_screen_long_press", "Virtual Screen Long Press", "Coordinates and hold duration can be adjusted.",
            field("x", "X (0-1000)", required = true), field("y", "Y (0-1000)", required = true),
            field("duration_ms", "Hold Duration (ms)", "Default 800; 200-5000")),
        action("virtual_screen_swipe", "Virtual Screen Swipe", "Start and end points are 0-1000 coordinates.",
            field("x1", "Start X (0-1000)", required = true), field("y1", "Start Y (0-1000)", required = true),
            field("x2", "End X (0-1000)", required = true), field("y2", "End Y (0-1000)", required = true),
            field("duration_ms", "Gesture Duration (ms)", "Default 300; 50-5000")),
        action("virtual_screen_input_text", "Virtual Screen Append Text", "Appends to current focus, preserving surrounding whitespace.",
            field("text", "Input Text", "Supports multi-line or \${TEXT_1}", true)),
        action("virtual_screen_set_text", "Virtual Screen Replace Text", "Sends select-all and paste keys; target view must support it; empty text clears.",
            field("text", "Replacement Text", "Supports multi-line or \${TEXT_1}")),
        action("virtual_screen_key", "Virtual Screen Key Event", "Sends key event to virtual screen.",
            field("key", "Key", "back / home / enter / delete / paste / recents", true)),
        action("virtual_screen_wait", "Virtual Screen Wait", "Waits only, does not invoke model.",
            field("duration_ms", "Wait Duration (ms)", "Default 1000; 0-600000", true)),
        action("virtual_screen_close", "Close Virtual Screen", "Release screen session for this run."),
    )

    fun template(): WorkflowDefinition = PhoneOperationWorkflow.create(
        id = "virtual_screen_template", name = "Virtual Screen Operation Template",
        operations = listOf(
            PhoneWorkflowOperation("virtual_screen_launch", mapOf("package" to "\${PACKAGE}"), 900),
        ),
    ).copy(
        isBuiltin = true,
        description = "Clone and add click, swipe, input, and wait nodes; each step can be tuned independently. Replaying fixed steps does not call model; verify app UI before running.",
        defaultVariables = mapOf("PACKAGE" to "com.android.settings"),
    )
}
