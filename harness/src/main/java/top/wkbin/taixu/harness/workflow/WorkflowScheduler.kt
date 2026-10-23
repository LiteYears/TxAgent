package top.wkbin.taixu.harness.workflow

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.wkbin.taixu.core.model.workflow.FailurePolicy
import top.wkbin.taixu.core.model.workflow.NodeExecutionOutput
import top.wkbin.taixu.core.model.workflow.NodeRunStatus
import top.wkbin.taixu.core.model.workflow.WorkflowApprovalDecision
import top.wkbin.taixu.core.model.workflow.WorkflowApprovalRequest
import top.wkbin.taixu.core.model.workflow.WorkflowApprovalTimeout
import top.wkbin.taixu.core.model.workflow.WorkflowDefinition
import top.wkbin.taixu.core.model.workflow.WorkflowNode
import top.wkbin.taixu.core.model.workflow.WorkflowNodeType
import top.wkbin.taixu.core.model.workflow.WorkflowNodeRunState
import top.wkbin.taixu.core.model.workflow.WorkflowRunStatus
import top.wkbin.taixu.core.model.workflow.WorkflowRuntimeContext
import top.wkbin.taixu.core.model.workflow.WorkflowRuntimeState
import top.wkbin.taixu.core.model.workflow.WorkflowValidator
import top.wkbin.taixu.core.model.workflow.WorkflowExpansions
import top.wkbin.taixu.core.model.workflow.WorkflowExpansion
import top.wkbin.taixu.runtime.virtualdisplay.PhoneTaskState

class WorkflowRunHandle internal constructor(
    val state: StateFlow<WorkflowRuntimeState>,
    val approvalRequest: StateFlow<WorkflowApprovalRequest?>,
    private val cancelAction: () -> Unit,
    private val decisionAction: (String, WorkflowApprovalDecision) -> Boolean,
    private val settledAction: () -> Boolean,
) {
    internal val settled: Boolean get() = settledAction()
    fun cancel() = cancelAction()
    fun decide(nodeId: String, approved: Boolean, variables: Map<String, String> = emptyMap(), reviewToken: String? = null): Boolean =
        decisionAction(nodeId, WorkflowApprovalDecision(approved, variables, reviewToken))
}

class WorkflowScheduler(
    executors: Set<@JvmSuppressWildcards NodeExecutor>,
    private val approvalBroker: WorkflowApprovalBroker,
    private val virtualScreenRuns: VirtualScreenWorkflowRuns? = null,
) {
    private val executorMap = executors
        .flatMap { executor -> executor.supportedTypes.map { it to executor } }
        .also { pairs -> require(pairs.map { it.first }.distinct().size == pairs.size) { "Multiple executors registered for the same workflow node type" } }
        .toMap()

    fun execute(
        definition: WorkflowDefinition,
        initialVariables: Map<String, String>,
        workspacePath: String,
        scope: CoroutineScope,
        checkpoint: (suspend (WorkflowRuntimeState) -> Unit)? = null,
        restored: WorkflowRuntimeState? = null,
        environmentId: String? = null,
    ): WorkflowRunHandle {
        require(restored == null || restored.definition == definition) { "Workflow definition cannot be replaced during recovery" }
        val executionId = restored?.executionId ?: "wf_${UUID.randomUUID()}"
        val mutableState = MutableStateFlow(restored ?: WorkflowRuntimeState.initial(executionId, definition).copy(
            journalVersion = if (checkpoint == null) 0 else 1, runtimeEnvironmentId = environmentId,
        ))
        val journal = checkpoint?.let { WorkflowJournalRecorder(mutableState, it) }
        val approvalRequest = approvalBroker.currentRequestFor(executionId)
        val jobRef = AtomicReference<Job?>()
        val cancelled = AtomicBoolean(false)
        val settled = AtomicBoolean(false)
        val job = scope.launch {
            runWorkflow(executionId, definition, initialVariables, workspacePath, mutableState, journal, restored != null)
        }
        jobRef.set(job)
        if (cancelled.get()) job.cancel(CancellationException("User cancelled workflow"))
        job.invokeOnCompletion { cause ->
            approvalBroker.cancelExecution(executionId)
            if (cause is CancellationException) mutableState.update { current ->
                if (current.status in setOf(WorkflowRunStatus.SUCCESS, WorkflowRunStatus.FAILED, WorkflowRunStatus.CANCELLED)) current
                else current.copy(status = WorkflowRunStatus.CANCELLED, finishedAt = System.currentTimeMillis())
            }
            settled.set(true)
        }
        return WorkflowRunHandle(
            state = mutableState.asStateFlow(),
            approvalRequest = approvalRequest,
            cancelAction = {
                approvalBroker.cancelExecution(executionId)
                cancelled.set(true)
                jobRef.get()?.cancel(CancellationException("User cancelled workflow"))
            },
            decisionAction = { nodeId, decision -> approvalBroker.decide(executionId, nodeId, decision) },
            settledAction = settled::get,
        )
    }

    private suspend fun runWorkflow(
        executionId: String,
        definition: WorkflowDefinition,
        initialVariables: Map<String, String>,
        workspacePath: String,
        state: MutableStateFlow<WorkflowRuntimeState>,
        journal: WorkflowJournalRecorder?,
        restoring: Boolean,
    ) {
        val startedAt = if (restoring) state.value.startedAt ?: System.currentTimeMillis() else System.currentTimeMillis()
        val validation = WorkflowValidator.validate(definition)
        if (validation.isNotEmpty()) {
            state.update { it.copy(status = WorkflowRunStatus.FAILED, startedAt = startedAt, finishedAt = startedAt, error = validation.joinToString { issue -> issue.message }) }
            return
        }
        var context = if (restoring) requireNotNull(state.value.context) else WorkflowRuntimeContext(
            executionId = executionId,
            workflowId = definition.id,
            workspacePath = workspacePath,
            globalVariables = definition.defaultVariables + initialVariables,
        )
        state.update { current ->
            current.copy(
                status = WorkflowRunStatus.RUNNING,
                startedAt = startedAt,
                nodeStates = current.nodeStates.mapValues { (id, value) ->
                    context.nodeOutputs[id]?.let { WorkflowNodeRunState(it.status, "Reusing saved output", it) }
                        ?: value.copy(status = NodeRunStatus.PENDING, output = null)
                },
                context = context,
            )
        }

        try {
            journal?.checkpoint()
            virtualScreenRuns?.begin(executionId, currentCoroutineContext()[Job]!!)
            val abort = dispatchWorkflowDag(state, context) { node, nodeContext -> executeNode(node, nodeContext, state, journal) }
            context = requireNotNull(state.value.context)
            if (abort != null) {
                state.update { current -> current.copy(status = WorkflowRunStatus.FAILED, finishedAt = System.currentTimeMillis(), error = abort,
                    nodeStates = current.nodeStates.mapValues { (_, value) ->
                        if (value.status in ACTIVE_STATUSES) value.copy(status = NodeRunStatus.CANCELLED, progressMessage = "Cancelled due to preceding node failure") else value
                    }) }
                return
            }

            val hasFailure = context.nodeOutputs.values.any { it.status == NodeRunStatus.FAILED }
            state.update {
                it.copy(
                    status = if (hasFailure) WorkflowRunStatus.FAILED else WorkflowRunStatus.SUCCESS,
                    context = context,
                    finishedAt = System.currentTimeMillis(),
                    error = context.nodeOutputs.values.firstOrNull { output -> output.status == NodeRunStatus.FAILED }?.error,
                )
            }
        } catch (cancelled: CancellationException) {
            state.update { current ->
                current.copy(
                    status = WorkflowRunStatus.CANCELLED,
                    finishedAt = System.currentTimeMillis(),
                    nodeStates = current.nodeStates.mapValues { (_, nodeState) ->
                        if (nodeState.status in ACTIVE_STATUSES) nodeState.copy(status = NodeRunStatus.CANCELLED) else nodeState
                    },
                )
            }
            throw cancelled
        } catch (error: Throwable) {
            state.update { current -> current.copy(status = WorkflowRunStatus.FAILED, finishedAt = System.currentTimeMillis(),
                error = error.message ?: error::class.java.simpleName,
                nodeStates = current.nodeStates.mapValues { (_, nodeState) ->
                    if (nodeState.status in ACTIVE_STATUSES) nodeState.copy(status = NodeRunStatus.CANCELLED) else nodeState
                }) }
        } finally {
            virtualScreenRuns?.end(executionId, when (state.value.status) {
                WorkflowRunStatus.SUCCESS -> PhoneTaskState.COMPLETED
                WorkflowRunStatus.CANCELLED -> PhoneTaskState.CANCELLED
                else -> PhoneTaskState.FAILED
            })
        }
    }

    private suspend fun executeNode(
        node: WorkflowNode,
        context: WorkflowRuntimeContext,
        state: MutableStateFlow<WorkflowRuntimeState>,
        journal: WorkflowJournalRecorder?,
    ): NodeExecutionOutput {
        val executor = executorMap[node.type]
            ?: return NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = 127, error = "No executor registered for node type ${node.type}")
                .also { updateNode(state, node.id, it.status, it.error.orEmpty(), it) }
        var attempts = 0
        val maxAttempts = if (node.failurePolicy == FailurePolicy.RETRY_ONCE) 2 else 1
        var output: NodeExecutionOutput
        do {
            var expansion: WorkflowExpansion? = null
            attempts++
            updateNode(state, node.id, NodeRunStatus.RUNNING, if (attempts > 1) "Retrying" else "Executing")
            journal?.started(node.id)
            val started = System.nanoTime()
            val timeoutMs = if (node.type == WorkflowNodeType.HUMAN_APPROVAL) {
                WorkflowApprovalTimeout.milliseconds(node.config[WorkflowApprovalTimeout.CONFIG_KEY])
            } else {
                node.timeoutSeconds * 1_000L
            }
            val onProgress: suspend (NodeRunStatus, String) -> Unit = { status, message ->
                updateNode(state, node.id, status, message)
                state.update { current ->
                    val waiting = current.nodeStates.values.any { it.status == NodeRunStatus.WAITING_APPROVAL }
                    current.copy(status = if (waiting) WorkflowRunStatus.WAITING_APPROVAL else WorkflowRunStatus.RUNNING)
                }
            }
            output = try {
                val action: suspend () -> NodeExecutionOutput = {
                    if (WorkflowExpansions.SOURCE_KEY in node.config) {
                        check(journal != null) { "Dynamic expansion requires persistent execution journal" }
                        approveExpansion(node, context, state.value.definition, approvalBroker, onProgress).let {
                            expansion = it.patch
                            it.output
                        }
                    } else executor.execute(node, context, onProgress)
                }
                if (timeoutMs == null) {
                    action()
                } else {
                    withTimeoutOrNull(timeoutMs) { action() }
                        ?: NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = 124, error = "Node timed out (${timeoutMs / 1_000}s)")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = -1, error = error.message ?: error::class.java.simpleName)
            }
            if (output.durationMs == 0L) output = output.copy(durationMs = (System.nanoTime() - started) / 1_000_000L)
            if (output.status != NodeRunStatus.SUCCESS) expansion = null
            journal?.settled(node.id, output, expansion)
        } while (output.status == NodeRunStatus.FAILED && attempts < maxAttempts)

        if (output.status == NodeRunStatus.FAILED && node.failurePolicy == FailurePolicy.ASK_USER) {
            updateNode(state, node.id, NodeRunStatus.WAITING_APPROVAL, "Execution failed, awaiting decision to retry")
            state.update { current ->
                val waiting = current.nodeStates.values.any { it.status == NodeRunStatus.WAITING_APPROVAL }
                current.copy(status = if (waiting) WorkflowRunStatus.WAITING_APPROVAL else WorkflowRunStatus.RUNNING)
            }
            val decision = approvalBroker.await(
                WorkflowApprovalRequest(context.executionId, node.id, "Retry \"${node.title}\"?", output.error.orEmpty()),
            )
            if (decision.approved) return executeNode(node.copy(failurePolicy = FailurePolicy.ABORT), context, state, journal)
        }
        updateNode(state, node.id, output.status, output.error ?: output.textOutput.lineSequence().lastOrNull().orEmpty(), output)
        state.update { current ->
            val waiting = current.nodeStates.values.any { it.status == NodeRunStatus.WAITING_APPROVAL }
            current.copy(status = if (waiting) WorkflowRunStatus.WAITING_APPROVAL else WorkflowRunStatus.RUNNING)
        }
        return output
    }

    private fun updateNode(
        state: MutableStateFlow<WorkflowRuntimeState>,
        nodeId: String,
        status: NodeRunStatus,
        message: String,
        output: NodeExecutionOutput? = null,
    ) {
        state.update { current ->
            current.copy(
                nodeStates = current.nodeStates + (nodeId to WorkflowNodeRunState(status, message, output ?: current.nodeStates[nodeId]?.output)),
                context = if (output == null) current.context else current.context?.withOutput(nodeId, output),
            )
        }
    }

    private fun WorkflowRuntimeContext.withOutput(nodeId: String, output: NodeExecutionOutput) = copy(
        globalVariables = globalVariables + output.variables,
        nodeOutputs = nodeOutputs + (nodeId to output),
    )

    private companion object {
        val ACTIVE_STATUSES = setOf(NodeRunStatus.PENDING, NodeRunStatus.RUNNING, NodeRunStatus.STREAMING, NodeRunStatus.WAITING_APPROVAL)
    }
}
