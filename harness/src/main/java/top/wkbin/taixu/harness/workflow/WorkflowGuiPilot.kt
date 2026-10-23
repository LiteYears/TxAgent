package top.wkbin.taixu.harness.workflow

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.core.model.ExecutionMode
import top.wkbin.taixu.core.model.workflow.NodeExecutionOutput
import top.wkbin.taixu.core.model.workflow.NodeRunStatus
import top.wkbin.taixu.harness.ApiMessage
import top.wkbin.taixu.harness.ProviderClient
import top.wkbin.taixu.runtime.gui.HostGuiController
import top.wkbin.taixu.runtime.gui.ScreenObservation
import top.wkbin.taixu.runtime.gui.ScrollDirection
import top.wkbin.taixu.runtime.gui.WorkflowGuiHudBridge
import top.wkbin.taixu.runtime.privilege.PrivilegeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import top.wkbin.taixu.runtime.gui.GuiNode

/**
 * Workflow-native GUI loop: observe → LLM returns one JSON action → execute.
 * Avoids SubagentLaneRunner (MCP discovery + approval dead-ends) so progress shows in the workflow UI.
 */
class WorkflowGuiPilot(
    private val gui: HostGuiController,
    private val privilegeManager: PrivilegeManager,
    private val providerClient: ProviderClient,
    private val hud: WorkflowGuiHudBridge,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun run(
        goal: String,
        targetPackage: String?,
        modelId: String?,
        modelVariant: String?,
        maxSteps: Int = 18,
        onProgress: suspend (String) -> Unit,
    ): NodeExecutionOutput {
        val info = privilegeManager.getPrivilegeInfo()
        if (info.mode == ExecutionMode.PROOT || !info.modeActive) {
            return NodeExecutionOutput(
                status = NodeRunStatus.FAILED,
                exitCode = 13,
                error = "GUI pilot requires authorized Shizuku or Root",
            )
        }
        val trimmedGoal = goal.trim()
        if (trimmedGoal.isBlank()) {
            return NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = 2, error = "GUI_GOAL is empty")
        }

        val log = StringBuilder()
        fun appendLog(line: String) {
            log.appendLine(line)
            Log.i(TAG, line)
        }

        appendLog("Goal: $trimmedGoal")
        if (!targetPackage.isNullOrBlank()) {
            onProgress("Opening $targetPackage")
            gui.launchApp(targetPackage).onFailure {
                appendLog("Launch failed: ${it.message}")
            }.onSuccess {
                appendLog(it)
            }
            gui.waitForForeground(targetPackage, 15_000).onSuccess { appendLog(it) }
            delay(1_200)
        }

        val model = providerClient.resolveConfigured(modelId, modelVariant)
            .copy(pureChatMode = true, dynamicMcpTools = emptyList())
        appendLog("Model: ${model.name} / ${model.model} (Pure decision JSON)")

        var doneReason: String? = null
        var failedReason: String? = null
        var steps = 0

        for (step in 1..maxSteps.coerceIn(1, 40)) {
            coroutineContext.ensureActive()
            if (hud.isStopRequested()) {
                throw CancellationException("User cancelled GUI pilot")
            }
            steps = step
            onProgress("Step $step: Observing screen...")
            hud.thinking("Step $step: Observing screen...")
            val observation = gui.observeScreen(onlyInteractive = true).getOrElse { err ->
                appendLog("Observation failed: ${err.message}")
                failedReason = "screen_observe failed: ${err.message}"
                break
            }
            appendLog("Foreground=${observation.packageName} nodes=${observation.nodes.size}")
            onProgress("Step $step: Requesting model decision...")
            hud.thinking("Step $step: Model deciding...", "${observation.nodes.size} nodes")

            val decisionText = runCatching {
                providerClient.chat(
                    model,
                    listOf(
                        ApiMessage(role = "system", content = SYSTEM_PROMPT),
                        ApiMessage(
                            role = "user",
                            content = buildUserPrompt(trimmedGoal, targetPackage, observation, step, maxSteps),
                        ),
                    ),
                ).content.orEmpty()
            }.getOrElse { err ->
                appendLog("Model call failed: ${err.message}")
                failedReason = "Model call failed: ${err.message}"
                break
            }

            if (hud.isStopRequested()) {
                throw CancellationException("User cancelled GUI pilot")
            }

            val action = parseAction(decisionText)
            if (action == null) {
                appendLog("Unable to parse decision: ${decisionText.take(400)}")
                onProgress("Step $step: Decision parsing failed, retrying")
                hud.thinking("Step $step: Decision parsing failed, retrying")
                delay(600)
                continue
            }

            val kind = action.string("action")?.lowercase().orEmpty()
            val reason = action.string("reason").orEmpty()
            appendLog("Decision=$kind reason=$reason")
            onProgress("Step $step: Executing $kind")
            hud.thinking("Step $step: Executing $kind", reason)

            when (kind) {
                "done" -> {
                    doneReason = reason.ifBlank { "Model completed goal" }
                    break
                }
                "fail" -> {
                    failedReason = reason.ifBlank { "Model reported failure" }
                    break
                }
                "click_text" -> {
                    val needle = action.string("text").orEmpty()
                    if (needle.isBlank()) {
                        appendLog("click_text missing text")
                        continue
                    }
                    if (needle.length >= 4 && trimmedGoal.contains(needle) &&
                        observation.nodes.none { it.text.contains(needle) || it.contentDesc.contains(needle) }
                    ) {
                        appendLog("'$needle' looks like message text rather than an on-screen control; please use screen_input_text")
                        continue
                    }
                    val hit = observation.nodes
                        .filter { node ->
                            listOf(node.text, node.contentDesc, node.resourceId)
                                .any { it.contains(needle, ignoreCase = true) }
                        }
                        .sortedWith(
                            compareByDescending<GuiNode> { it.text.equals(needle, true) }
                                .thenByDescending { it.clickable }
                                .thenByDescending { it.text.length.coerceAtMost(needle.length * 2) },
                        )
                        .firstOrNull()
                    if (hit == null) {
                        appendLog("Text '$needle' not found on screen")
                        continue
                    }
                    if ((hit.text.equals("Back", true) || hit.contentDesc.contains("Back", true)) &&
                        !trimmedGoal.contains("Back", true) &&
                        observation.nodes.any { it.editable || it.className.contains("EditText", true) }
                    ) {
                        appendLog("Ignoring 'Back' button (input field present; staying on page)")
                        continue
                    }
                    gui.click(hit.centerX, hit.centerY).fold(
                        onSuccess = { appendLog("$it <- '$needle'") },
                        onFailure = { appendLog("Click failed: ${it.message}") },
                    )
                }
                "screen_click" -> {
                    val x = action.int("x")
                    val y = action.int("y")
                    if (x == null || y == null) {
                        appendLog("screen_click missing coordinates")
                        continue
                    }
                    gui.click(x, y).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Click failed: ${it.message}") },
                    )
                }
                "screen_swipe" -> {
                    val x1 = action.int("x1")
                    val y1 = action.int("y1")
                    val x2 = action.int("x2")
                    val y2 = action.int("y2")
                    if (listOf(x1, y1, x2, y2).any { it == null }) {
                        appendLog("screen_swipe missing coordinates")
                        continue
                    }
                    gui.swipe(x1!!, y1!!, x2!!, y2!!, action.int("duration_ms")?.toLong() ?: 300L).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Swipe failed: ${it.message}") },
                    )
                }
                "screen_input_text" -> {
                    val text = action.string("text").orEmpty()
                    if (text.isBlank()) {
                        appendLog("screen_input_text missing text")
                        continue
                    }
                    val editable = observation.nodes.firstOrNull { it.editable }
                        ?: observation.nodes.firstOrNull {
                            it.className.contains("EditText", ignoreCase = true) ||
                                it.contentDesc.contains("input", ignoreCase = true) ||
                                it.text.contains("input", ignoreCase = true)
                        }
                    if (editable != null) {
                        gui.click(editable.centerX, editable.centerY).onSuccess {
                            appendLog("Focusing input field center=(${editable.centerX},${editable.centerY})")
                        }
                        delay(250)
                    }
                    gui.inputText(text).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Input failed: ${it.message}") },
                    )
                }
                "screen_scroll" -> {
                    val dir = when (action.string("direction")?.lowercase()) {
                        "up" -> ScrollDirection.UP
                        "down" -> ScrollDirection.DOWN
                        "left" -> ScrollDirection.LEFT
                        "right" -> ScrollDirection.RIGHT
                        else -> {
                            appendLog("screen_scroll missing valid direction")
                            continue
                        }
                    }
                    gui.scroll(dir).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Scroll failed: ${it.message}") },
                    )
                }
                "screen_double_click" -> {
                    val x = action.int("x")
                    val y = action.int("y")
                    if (x == null || y == null) {
                        appendLog("screen_double_click missing coordinates")
                        continue
                    }
                    gui.doubleClick(x, y).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Double click failed: ${it.message}") },
                    )
                }
                "screen_long_press" -> {
                    val x = action.int("x")
                    val y = action.int("y")
                    if (x == null || y == null) {
                        appendLog("screen_long_press missing coordinates")
                        continue
                    }
                    gui.longPress(x, y, action.int("duration_ms")?.toLong() ?: 800L).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Long press failed: ${it.message}") },
                    )
                }
                "screen_key" -> {
                    val key = action.string("key").orEmpty()
                    if (key.isBlank()) {
                        appendLog("screen_key missing key")
                        continue
                    }
                    gui.sendKey(key).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Key press failed: ${it.message}") },
                    )
                }
                "app_launch" -> {
                    val pkg = action.string("package").orEmpty().ifBlank { targetPackage.orEmpty() }
                    if (pkg.isBlank()) {
                        appendLog("app_launch missing package")
                        continue
                    }
                    gui.launchApp(pkg).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Launch failed: ${it.message}") },
                    )
                }
                else -> appendLog("Unknown action: $kind")
            }
            gui.awaitUiSettled()
        }

        val stepBudget = maxSteps.coerceIn(1, 40)
        val exhausted = failedReason == null && doneReason == null && steps >= stepBudget
        val effectiveFailure = failedReason
            ?: if (exhausted) "Step budget exhausted without reaching goal (model did not report completion within $stepBudget steps)" else null
        val success = effectiveFailure == null
        val summary = buildString {
            appendLine(if (doneReason != null) "✔ $doneReason" else if (effectiveFailure != null) "✘ $effectiveFailure" else "Reached maximum steps $maxSteps")
            appendLine()
            append(log.toString().trimEnd())
        }
        onProgress(if (success) "GUI pilot finished" else "GUI pilot incomplete")
        return NodeExecutionOutput(
            status = if (success) NodeRunStatus.SUCCESS else NodeRunStatus.FAILED,
            exitCode = if (success) 0 else 1,
            textOutput = summary,
            error = effectiveFailure,
            variables = mapOf(
                "GUI_PILOT_STEPS" to steps.toString(),
                "GUI_PILOT_DONE" to (doneReason != null).toString(),
            ),
        )
    }

    private fun parseAction(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull
            ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun buildUserPrompt(
        goal: String,
        targetPackage: String?,
        observation: ScreenObservation,
        step: Int,
        maxSteps: Int,
    ): String = buildString {
        appendLine("## Goal")
        appendLine(goal)
        if (!targetPackage.isNullOrBlank()) appendLine("Target package: $targetPackage")
        appendLine("Progress: Step $step / $maxSteps")
        appendLine()
        appendLine("## Current Screen")
        append(observation.toAgentSummary(maxNodes = 60))
        appendLine()
        appendLine("Output only one valid JSON object. To input text: focus the input field first, then use screen_input_text; then use click_text for action buttons.")
    }

    private companion object {
        const val TAG = "TaiXu-GuiPilot"
        val SYSTEM_PROMPT = """
You are an Android GUI Pilot agent. Advance the user's goal based on the screen accessibility tree.
Output exactly one JSON object with fields:
- action: click_text | screen_click | screen_double_click | screen_long_press | screen_swipe | screen_scroll | screen_input_text | screen_key | app_launch | done | fail
- text: string for click_text / screen_input_text
- x,y / x1,y1,x2,y2,duration_ms: coordinate actions
- direction: up|down|left|right (screen_scroll)
- key: back|home|recents|enter|delete|paste
- package: package name for app_launch
- reason: brief explanation

Rules:
1. Prefer click_text. Text must come from existing nodes on screen (e.g. buttons, items). Never use the message body as click_text.
2. For text input: use screen_input_text. For long lists: use screen_scroll.
3. Once in chat with an input box: do not click Back; use screen_input_text -> click_text "Send".
4. For payment or fund transfer screens: output {"action":"fail","reason":"Payment required"}.
5. When the goal is completed: output {"action":"done","reason":"Goal completed"}.
6. Do not output Markdown or any text outside the single JSON object.
        """.trimIndent()
    }
}
