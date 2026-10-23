package top.wkbin.taixu.harness

import android.content.Context
import top.wkbin.taixu.core.database.HarnessSessionEntity
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import top.wkbin.taixu.harness.metrics.RunMetrics
import top.wkbin.taixu.harness.session.SessionTreeStore
import top.wkbin.taixu.harness.compaction.CompactionManager
import top.wkbin.taixu.harness.effects.RetryPolicy
import top.wkbin.taixu.harness.operation.OperationCoordinator
import top.wkbin.taixu.harness.events.AgentEventLogger
import top.wkbin.taixu.harness.projection.SessionMessageProjector
import top.wkbin.taixu.harness.projection.SessionStateMirrors
import top.wkbin.taixu.harness.session.ApiContextAssembler
import kotlin.time.Duration.Companion.milliseconds
import top.wkbin.taixu.harness.diagnostics.RequestDiagnosticsStore

/**
 * 本地流式增量处理（消息投影 / 持久化 / UI 派生）失败。
 *
 * 与网络 [IOException] 严格隔离：这类异常不是网络故障，绝不能触发整轮网络重发，
 * 否则会把半截内容、重复文字或残缺 JSON 污染到后续上下文。
 */
internal class StreamChunkHandlingException(message: String, cause: Throwable) : Exception(message, cause)

/** Wraps streaming callbacks: local processing errors convert to [StreamChunkHandlingException] while cancellation passes through. */
internal inline fun <T> withinStreamHandling(block: () -> T): T = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    throw StreamChunkHandlingException(throwable.message ?: "Streaming chunk handling failed", throwable)
}

/** 模型能力选择、流式请求重试及助手回复持久化；不持有会话调度状态。 */
class HarnessProviderRunner(
    private val providerClient: ProviderClient,
    private val requestDiagnostics: RequestDiagnosticsStore,
    private val messageStore: SessionTreeStore,
    private val operationCoordinator: OperationCoordinator,
    private val stateMirrors: SessionStateMirrors,
    private val messageProjector: SessionMessageProjector,
    private val toolMountResolver: McpToolMountResolver,
    private val agentEventLogger: AgentEventLogger,
    private val contextAssembler: ApiContextAssembler,
    private val compactionManager: CompactionManager,
    private val context: Context,
) {
    suspend fun clearDiagnostics(sessionId: String) = requestDiagnostics.removeSessionDurably(sessionId)

    /**
     * 解析本轮有效模型：用户 @ 过的 MCP 服务会把启用工具挂载进 provider tools（会话内稳定），
     * 其余能力仍走 use_capability；挂载与能力事件逻辑在 [McpToolMountResolver]，这里保持薄委托。
     */
    suspend fun resolveEffectiveModel(sessId: String, model: ModelConfig): ModelConfig =
        toolMountResolver.resolve(sessId, messageProjector.messagesFlow(sessId).value, model)

    /** 流式调用 + 限流/网络退避重试。恢复不了的失败以 Failed 终态返回；取消与超过重试上限的原样抛出 */
    suspend fun callProviderWithRetry(
        sessId: String,
        model: ModelConfig,
        sessionEntity: HarnessSessionEntity?,
        sessionWorkspace: String,
        operationId: String,
        assistantId: String,
        assistantAt: Long,
        round: Int,
        startedAt: Long,
        retryPolicy: RetryPolicy,
        metrics: RunMetrics,
    ): TurnProviderOutcome {
        val streamText = StreamBuffer()
        val streamReasoning = StreamBuffer(maxChars = ProviderClient.MAX_STREAM_REASONING_CHARS)
        var streamed: ChatResult? = null
        // 干净快照基线：每次网络重发前彻底清空本轮缓冲区并删除流式气泡，
        // 避免半截字符/残缺 JSON 拼接到下一次重试的增量之后污染上下文。
        fun resetStreamBaseline() {
            streamText.clear()
            streamReasoning.clear()
            messageProjector.remove(sessId, assistantId)
        }
        suspend fun settleFailedStream() {
            stateMirrors.setThinkingLive(sessId, false)
            if (streamText.length > 0) {
                persistAssistant(
                    sessId, assistantId, assistantAt, streamText.toString(),
                    streamReasoning.toString().ifBlank { null },
                    totalMs = now() - startedAt, operationId = operationId, round = round,
                )
            } else {
                messageProjector.remove(sessId, assistantId)
            }
        }

        var requestModel = model
        var outputBudgetReduced = false
        var outputLimitRecoveries = 0
        var netRetry = 0
        val governance = top.wkbin.taixu.harness.diagnostics.ContextGovernanceTrace()
        suspend fun assembleFor(requestModel: ModelConfig) = contextAssembler.assemble(
            sessId = sessId,
            model = requestModel,
            workspacePath = sessionWorkspace,
            projectTypeOverride = sessionEntity?.projectType.orEmpty(),
            thinkingMode = stateMirrors.requestThinkingMode(sessId),
            sessionRunMode = sessionEntity?.runMode, trace = governance,
        )
        var requestMessages = assembleFor(model)
        var imageStripped = false
        var overflowRecovered = false
        // 与请求构造同口径：messages + provider 可见 tools schema（仅 NATIVE 模式独立于 messages）。
        // 漏掉 schema 会低估真实输入规模，余量被吃光后以 400/413 溢出。
        val toolSchemaTokens =
            if (model.capabilities.nativeTools) {
                ContextWindowPolicy.estimateToolDefinitionTokens(ProviderClient.buildDynamicTools(model))
            } else {
                0
            }
        val estimatedRequestTokens = estimateRetryRequestTokens(requestMessages) + toolSchemaTokens
        val maxNetworkRetries = maxNetworkRetriesFor(estimatedRequestTokens, retryPolicy.maxRetries)
        val maxAttempts = maxNetworkRetries + 1
        if (maxNetworkRetries < retryPolicy.maxRetries) {
            agentEventLogger.log(sessId, "LargeContextRetryPolicy", "Estimated input ~$estimatedRequestTokens tokens, large context network retry limit is $maxNetworkRetries attempts")
        }
        var requestAttempt = 0
        while (streamed == null) {
            try {
                agentEventLogger.logModelAttempt(sessId, requestModel, requestMessages.size, netRetry + 1, maxAttempts)
                stateMirrors.setStatus(sessId, "Waiting for initial model response (${netRetry + 1}/$maxAttempts)")
                operationCoordinator.providerIntent(
                    operationId = operationId,
                    effectId = assistantId,
                    round = round,
                    attempt = netRetry + 1,
                    maxAttempts = maxAttempts,
                )
                streamed = providerClient.chatStream(
                    requestModel,
                    requestMessages,
                    onRequest = { protocol, body, bytes, secrets ->
                        requestDiagnostics.record(sessId, operationId, round, ++requestAttempt, protocol, body, bytes, secrets, governance.report(bytes))
                    },
                    onReasoning = { chunk ->
                        withinStreamHandling {
                            if (chunk.isNotEmpty()) stateMirrors.recordModelOutput(sessId)
                            streamReasoning.append(chunk)
                            stateMirrors.setThinkingLive(sessId, true)
                            stateMirrors.recordThinkingObserved(sessId)
                            streamReasoning.publishIfDue(now(), ProviderClient.STREAM_PUBLISH_INTERVAL_MS)?.let {
                                messageProjector.streamReasoning(sessId, assistantId, assistantAt, it)
                            }
                        }
                    },
                    onToolProgress = { progress ->
                        withinStreamHandling {
                            stateMirrors.recordModelOutput(sessId)
                            stateMirrors.setThinkingLive(sessId, false)
                            stateMirrors.setStatus(sessId, progress.statusText())
                        }
                    },
                    sessionKey = sessId,
                ) { chunk ->
                    withinStreamHandling {
                        if (chunk.isNotEmpty()) stateMirrors.recordModelOutput(sessId)
                        stateMirrors.setStatus(sessId, "Responding")
                        streamText.append(chunk)
                        streamText.publishSnapshotIfDue(now(), ProviderClient.STREAM_PUBLISH_INTERVAL_MS)?.let {
                            messageProjector.streamText(sessId, assistantId, assistantAt, it)
                        }
                    }
                }
                // Completed streaming; unconditionally flush the full response
                withinStreamHandling {
                    if (streamReasoning.length > 0) {
                        messageProjector.streamReasoning(sessId, assistantId, assistantAt, streamReasoning.toString())
                    }
                    if (streamText.length > 0) {
                        messageProjector.streamText(sessId, assistantId, assistantAt, streamText.snapshot())
                    }
                }
            } catch (cancellation: CancellationException) {
                persistCancelledPartial(sessId, assistantId, assistantAt, streamText, streamReasoning, startedAt, operationId, round)
                withContext(NonCancellable) {
                    agentEventLogger.log(sessId, "Cancelled", "User cancelled execution, retained ${streamText.length} generated characters")
                }
                throw cancellation
            } catch (handling: StreamChunkHandlingException) {
                stateMirrors.setThinkingLive(sessId, false)
                agentEventLogger.log(
                    sessId,
                    "StreamChunkHandling",
                    "Streaming chunk processing failed (local exception, network retry prohibited): ${handling.message}",
                    handling,
                )
                if (streamText.length > 0) {
                    persistAssistant(
                        sessId,
                        assistantId,
                        assistantAt,
                        streamText.toString(),
                        streamReasoning.toString().ifBlank { null },
                        totalMs = now() - startedAt,
                        operationId = operationId,
                        round = round,
                    )
                } else {
                    messageProjector.remove(sessId, assistantId)
                }
                return TurnProviderOutcome.Failed(
                    "Streaming response handling failed (local exception, not retried): ${friendly(handling.cause ?: handling)}",
                )
            } catch (rateLimit: LlmRateLimitException) {
                currentCoroutineContext().ensureActive()
                if (rateLimit.quotaExhausted) {
                    stateMirrors.setThinkingLive(sessId, false)
                    agentEventLogger.log(sessId, "QuotaExhausted", rateLimit.message.orEmpty(), rateLimit)
                    messageProjector.remove(sessId, assistantId)
                    val detail = rateLimit.message?.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()
                    return TurnProviderOutcome.Failed("Model provider quota exhausted, cannot continue. Please recharge, switch models, or update your API Key.$detail")
                }
                netRetry++
                if (netRetry > maxNetworkRetries) {
                    settleFailedStream()
                    throw rateLimit
                }
                metrics.streamRetry()
                stateMirrors.setThinkingLive(sessId, false)
                val waitSeconds = rateLimit.retryAfterSeconds ?: (netRetry * RETRY_BACKOFF_SEC).coerceAtMost(60L)
                stateMirrors.setStatus(sessId, "Request rate limited. Retrying automatically in ${waitSeconds}s ($netRetry/$maxNetworkRetries)")
                agentEventLogger.log(sessId, "RateLimitRetry", "Rate limit backoff ${waitSeconds}s, retry $netRetry/$maxNetworkRetries", rateLimit)
                resetStreamBaseline()
                for (remaining in waitSeconds downTo 1L) {
                    currentCoroutineContext().ensureActive()
                    stateMirrors.setStatus(sessId, "Request rate limited. Retrying automatically in ${remaining}s ($netRetry/$maxNetworkRetries)")
                    delay(1000L.milliseconds)
                }
            } catch (outputLimit: LlmOutputLimitException) {
                currentCoroutineContext().ensureActive()
                stateMirrors.setThinkingLive(sessId, false)
                val recovered = if (outputLimitRecoveries < OUTPUT_LIMIT_MAX_RECOVERIES && !outputBudgetReduced) {
                    outputLimitRecoveries++
                    stateMirrors.setStatus(sessId, "Output token limit reached, adjusting budget and retrying")
                    recoverFromOutputLimit(sessId, requestModel, requestMessages, toolSchemaTokens,
                        compactionManager, agentEventLogger)
                } else null
                if (recovered != null) {
                    requestModel = recovered
                    agentEventLogger.log(sessId, "OutputLimitRecovery",
                        "Output truncated, reserving ${recovered.maxTokens} tokens and regenerating", outputLimit)
                    metrics.streamRetry()
                    resetStreamBaseline()
                    requestMessages = assembleFor(requestModel)
                        .map { if (imageStripped) it.copy(imageUrls = emptyList()) else it }
                    continue
                }
                settleFailedStream()
                return TurnProviderOutcome.Failed("Model output reached token limit and could not recover automatically. Please increase output budget or split the task and retry.")
            } catch (invalidOutputTokens: LlmInvalidOutputTokensException) {
                currentCoroutineContext().ensureActive()
                if (outputBudgetReduced) throw invalidOutputTokens
                outputBudgetReduced = true
                val current = ContextWindowPolicy.normalizeOutputTokens(requestModel.maxTokens, 8_192)
                val reduced = (current / 2).coerceAtLeast(1)
                requestModel = requestModel.copy(maxTokens = reduced)
                agentEventLogger.log(
                    sessId,
                    "OutputBudgetReduction",
                    "Provider rejected output budget, reduced strictly to $reduced and retrying",
                    invalidOutputTokens,
                )
                resetStreamBaseline()
            } catch (contextOverflow: LlmContextOverflowException) {
                currentCoroutineContext().ensureActive()
                stateMirrors.setThinkingLive(sessId, false)
                if (!overflowRecovered) {
                    overflowRecovered = true
                    stateMirrors.setStatus(sessId, "Context overflow, performing emergency compression and retrying")
                    agentEventLogger.log(
                        sessId,
                        "ContextOverflowRecovery",
                        "Detected context overflow, executing emergency compression: ${contextOverflow.message}",
                        contextOverflow,
                    )
                    val recovered = recoverFromContextOverflow(sessId, model, compactionManager, agentEventLogger)
                    if (recovered != null) {
                        agentEventLogger.log(
                            sessId,
                            "ContextOverflowRecovery",
                            "Emergency compression completed: folded ${recovered.first} messages, kept ${recovered.second} messages, reassembling and retrying",
                        )
                        resetStreamBaseline()
                        requestMessages = assembleFor(requestModel)
                        continue
                    }
                }
                val pendingImages = requestMessages.sumOf { it.imageUrls.size }
                if (!imageStripped && pendingImages > 0) {
                    imageStripped = true
                    requestMessages = requestMessages.map { it.copy(imageUrls = emptyList()) }
                    agentEventLogger.log(
                        sessId,
                        "ContextOverflowImageStrip",
                        "Still overflowing after compression, stripping $pendingImages images and retrying",
                        contextOverflow,
                    )
                    resetStreamBaseline()
                    continue
                }
                settleFailedStream()
                agentEventLogger.log(
                    sessId,
                    "ContextOverflowUnrecoverable",
                    "Context overflow cannot be recovered via compression or image stripping",
                    contextOverflow,
                )
                return TurnProviderOutcome.Failed(
                    "Context exceeds model window and could not be recovered automatically. " +
                        "You can ask the model to manually compress history or switch to a model with a larger context window.",
                )
            } catch (empty: LlmEmptyResponseException) {
                currentCoroutineContext().ensureActive()
                if (!overflowRecovered && shouldCompactEmptyResponse(
                        estimateRetryRequestTokens(requestMessages) + toolSchemaTokens, requestModel,
                    )
                ) {
                    overflowRecovered = true
                    stateMirrors.setThinkingLive(sessId, false)
                    stateMirrors.setStatus(sessId, "Long context returned empty response, compressing history and retrying")
                    val recovered = recoverFromContextOverflow(sessId, requestModel, compactionManager, agentEventLogger)
                    agentEventLogger.log(sessId, "EmptyResponseContextRecovery",
                        "Long context empty response compression: ${recovered ?: "No collapsible history"}", empty)
                    if (recovered != null) {
                        metrics.streamRetry()
                        resetStreamBaseline()
                        requestMessages = assembleFor(requestModel)
                        continue
                    }
                }
                requestMessages = stripEmptyResponseImages(++netRetry, EMPTY_RESPONSE_MAX_RETRIES, requestMessages, sessId, agentEventLogger, empty)
                if (netRetry > EMPTY_RESPONSE_MAX_RETRIES) {
                    stateMirrors.setThinkingLive(sessId, false)
                    messageProjector.remove(sessId, assistantId)
                    agentEventLogger.log(sessId, "EmptyModelResponse", empty.message.orEmpty(), empty)
                    return TurnProviderOutcome.Failed(
                        (if (overflowRecovered) "Attempted automatic context compression, but model still returned an empty response.\n" else "") +
                            empty.message.orEmpty().ifBlank { ProviderClient.EMPTY_RESPONSE_MESSAGE },
                    )
                }
                metrics.streamRetry()
                stateMirrors.setThinkingLive(sessId, false)
                stateMirrors.setStatus(sessId, "Model returned empty response, retrying ($netRetry/$EMPTY_RESPONSE_MAX_RETRIES)")
                agentEventLogger.log(sessId, "EmptyModelResponseRetry", "Empty response attempt $netRetry, max retries $EMPTY_RESPONSE_MAX_RETRIES")
                resetStreamBaseline()
                delay(retryPolicy.delayForRetry(netRetry).milliseconds)
            } catch (io: IOException) {
                if (!currentCoroutineContext().isActive) {
                    persistCancelledPartial(sessId, assistantId, assistantAt, streamText, streamReasoning, startedAt, operationId, round)
                    withContext(NonCancellable) {
                        agentEventLogger.log(sessId, "Cancelled", "User cancelled execution, retained ${streamText.length} generated characters")
                    }
                }
                currentCoroutineContext().ensureActive()
                io.firstResponseTimeoutOrNull()?.let { return failFirstResponseTimeout(sessId, assistantId, it) }
                netRetry++
                val transient = isTransientFailure(io)
                val retryBudget = effectiveRetryBudget(maxNetworkRetries, io)
                agentEventLogger.log(
                    sessId,
                    "NetworkRetry",
                    "Network interrupted, failure $netRetry (retry limit $retryBudget" +
                        (if (transient) ", transient fault exempt from large-context reduction" else "") +
                        ", retrying in ${retryPolicy.delayForRetry(netRetry)}ms): ${io.message}",
                    io,
                )
                if (netRetry > retryBudget) {
                    settleFailedStream()
                    throw io
                }
                metrics.streamRetry()
                stateMirrors.setThinkingLive(sessId, false)
                stateMirrors.setStatus(sessId, "Network interrupted, automatically retrying (failure $netRetry, limit $retryBudget)")
                resetStreamBaseline()
                delay(retryPolicy.delayForRetry(netRetry).milliseconds)
            } catch (throwable: Throwable) {
                stateMirrors.setThinkingLive(sessId, false)
                val lowerMsg = throwable.message.orEmpty().lowercase()
                val pendingImages = requestMessages.sumOf { it.imageUrls.size }
                if (!imageStripped && pendingImages > 0 &&
                    ("do not support image" in lowerMsg || "does not support image" in lowerMsg ||
                        "image input" in lowerMsg || "supports image" in lowerMsg ||
                        "image not supported" in lowerMsg ||
                        "不支持图片" in lowerMsg || "不支持图像" in lowerMsg ||
                        "图片输入" in lowerMsg || "图像输入" in lowerMsg)
                ) {
                    imageStripped = true
                    requestMessages = requestMessages.map { it.copy(imageUrls = emptyList()) }
                    agentEventLogger.log(
                        sessId, "VisionFallback",
                        "Model does not support image input, stripped $pendingImages images and retrying", throwable,
                    )
                    resetStreamBaseline()
                    continue
                }
                agentEventLogger.log(sessId, "ModelError", "LLM call failed: ${throwable.message}", throwable)
                settleFailedStream()
                return TurnProviderOutcome.Failed(friendly(throwable))
            }
        }
        // 仅在本轮请求真正成功后记录估算值，与 HarnessLoop 里 recordUsage 的计量轮次对齐
        metrics.recordEstimatedInput(estimateRetryRequestTokens(requestMessages) + toolSchemaTokens)
        messageProjector.endStreaming(sessId)
        return TurnProviderOutcome.Success(streamed, streamText.toString())
    }

    /** 落库助手回复或原生输出锚点；两者都没有时只结算 usage。 */
    suspend fun persistAssistantOutput(
        sessId: String,
        assistantId: String,
        assistantAt: Long,
        round: Int,
        startedAt: Long,
        operationId: String,
        result: ChatResult,
        effectiveModel: ModelConfig,
        displayText: String,
        hasToolCalls: Boolean,
    ) {
        if (displayText.isNotEmpty() || result.responsesTurn != null) {
            persistAssistant(
                sessId,
                assistantId,
                assistantAt,
                displayText,
                result.reasoningContent,
                totalMs = if (!hasToolCalls) now() - startedAt else null,
                reasoningMs = result.reasoningMs,
                responsesTurn = result.responsesTurn,
                operationId = operationId,
                round = round,
                usage = result.usage,
                model = effectiveModel,
            )
        } else {
            val usageEntity = result.usage.takeIf { it.hasData }?.let {
                operationCoordinator.usageEntity(
                    sessionId = sessId,
                    operationId = operationId,
                    entryId = null,
                    provider = effectiveModel.provider,
                    modelId = effectiveModel.model,
                    usage = it,
                )
            }
            operationCoordinator.providerSettled(operationId, null, usage = usageEntity, round = round)
        }
    }

    private suspend fun persistAssistant(
        sessId: String,
        id: String,
        createdAt: Long,
        text: String,
        reasoning: String? = null,
        totalMs: Long? = null,
        reasoningMs: Long? = null,
        operationId: String? = null,
        round: Int = 0,
        usage: ChatUsage? = null,
        model: ModelConfig? = null,
        responsesTurn: ResponsesTurn? = null,
    ) {
        val message = AssistantText(
            id = id,
            createdAt = createdAt,
            text = text,
            reasoning = reasoning,
            totalMs = totalMs,
            reasoningMs = reasoningMs,
            responsesTurn = responsesTurn,
            modelId = model?.model,
            providerId = model?.provider,
            promptTokens = usage?.inputTokens?.takeIf { it > 0 }?.toInt(),
            completionTokens = usage?.outputTokens?.takeIf { it > 0 }?.toInt(),
            cachedTokens = usage?.cacheReadTokens?.takeIf { it > 0 }?.toInt(),
        )
        if (operationId != null) {
            val usageEntity = usage?.takeIf { it.hasData }?.let {
                operationCoordinator.usageEntity(
                    sessionId = sessId,
                    operationId = operationId,
                    entryId = id,
                    provider = model?.provider,
                    modelId = model?.model,
                    usage = it,
                )
            }
            operationCoordinator.providerSettled(operationId, message, usage = usageEntity, round = round)
        } else {
            messageStore.append(sessId, message)
        }
        messageProjector.publishPersisted(sessId, message)
    }

    /** 用户主动停止时保留已流式内容；协程已取消，落库需 NonCancellable，失败退回删除半截气泡。 */
    private suspend fun persistCancelledPartial(
        sessId: String,
        assistantId: String,
        assistantAt: Long,
        streamText: StreamBuffer,
        streamReasoning: StreamBuffer,
        startedAt: Long,
        operationId: String,
        round: Int,
    ) {
        val keptChars = streamText.length
        stateMirrors.setThinkingLive(sessId, false)
        withContext(NonCancellable) {
            if (keptChars > 0) {
                runCatching {
                    persistAssistant(
                        sessId, assistantId, assistantAt,
                        streamText.toString(),
                        streamReasoning.toString().ifBlank { null },
                        totalMs = now() - startedAt,
                        operationId = operationId,
                        round = round,
                    )
                }.onFailure { messageProjector.remove(sessId, assistantId) }
            } else {
                messageProjector.remove(sessId, assistantId)
            }
        }
    }
    private fun now(): Long = System.currentTimeMillis()
    private fun friendly(throwable: Throwable): String =
        throwable.message?.take(200) ?: throwable::class.simpleName.orEmpty()

    /** 首个响应看门狗到点：不按网络中断重试，直接给出与上游错误区分开的说明。 */
    private suspend fun failFirstResponseTimeout(
        sessId: String,
        assistantId: String,
        error: FirstResponseTimeoutException,
    ): TurnProviderOutcome.Failed {
        stateMirrors.setThinkingLive(sessId, false)
        messageProjector.remove(sessId, assistantId)
        val seconds = (error.waitedMs / 1_000L).toInt().coerceAtLeast(1)
        val text = context.getString(R.string.harness_first_response_timeout, seconds)
        agentEventLogger.log(sessId, "FirstResponseTimeout", text, error)
        return TurnProviderOutcome.Failed(text)
    }

    companion object {
        private const val OUTPUT_LIMIT_MAX_RECOVERIES = 2
        private const val EMPTY_RESPONSE_MAX_RETRIES = 2

        internal fun emergencyFoldBudget(model: ModelConfig): Int = providerEmergencyFoldBudget(model)
        internal fun maxNetworkRetriesFor(estimatedRequestTokens: Int, configuredRetries: Int): Int =
            providerMaxNetworkRetriesFor(estimatedRequestTokens, configuredRetries)
        internal fun effectiveRetryBudget(largeContextRetries: Int, throwable: Throwable): Int =
            providerEffectiveRetryBudget(largeContextRetries, throwable)
        internal fun isTransientFailure(throwable: Throwable): Boolean = providerIsTransientFailure(throwable)

        const val RETRY_BACKOFF_MS = 1_000L
        const val RETRY_BACKOFF_SEC = 2L
    }
}
