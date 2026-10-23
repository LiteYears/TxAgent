package top.wkbin.taixu.harness

import java.security.MessageDigest
import java.util.ArrayDeque
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration.Companion.milliseconds

/**
 * In-process API key round-robin scheduler and sliding window rate limiter.
 * Only stores SHA-256 hashes of keys and timestamps; never persists or exposes plaintext keys.
 */
internal class ApiKeyScheduler(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    data class Selection(val key: String?, val waitMillis: Long = 0L)

    private data class KeyState(
        val requestTimes: ArrayDeque<Long> = ArrayDeque(),
        var cooldownUntil: Long = 0L,
    )

    private val states = mutableMapOf<String, KeyState>()
    private val cursors = mutableMapOf<String, Int>()
    // Session -> Last successfully used key (LRU). Keeps upstream identity stable during normal sessions
    // to preserve prompt prefix caching, but allows dynamic rotation when needed.
    private val sessionKeys = LinkedHashMap<String, String>()

    @Synchronized
    fun select(
        keys: List<String>,
        rpmLimit: Int,
        excluded: Set<String> = emptySet(),
        sessionKey: String? = null,
    ): Selection {
        val normalized = keys.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalized.isEmpty() || normalized.all(excluded::contains)) return Selection(null)

        val now = nowMillis()
        val poolId = normalized.joinToString(separator = "|") { fingerprint(it) }
        // Sticky key preference: reuse previously successful key for session if still valid, not cooled down, and under limit.
        val sticky = sessionKey?.let { sessionKeys[it] }
        if (sticky != null && sticky in normalized && sticky !in excluded) {
            val state = states.getOrPut(fingerprint(sticky)) { KeyState() }
            if (state.cooldownUntil <= now) {
                pruneWindow(state, now)
                if (rpmLimit <= 0 || state.requestTimes.size < rpmLimit) {
                    state.requestTimes.addLast(now)
                    rememberSessionKey(sessionKey, sticky)
                    return Selection(sticky)
                }
            }
        }

        val start = (cursors[poolId] ?: 0).mod(normalized.size)
        var shortestWait = Long.MAX_VALUE

        repeat(normalized.size) { offset ->
            val index = (start + offset) % normalized.size
            val key = normalized[index]
            if (key in excluded) return@repeat
            val state = states.getOrPut(fingerprint(key)) { KeyState() }
            if (state.cooldownUntil > now) {
                shortestWait = minOf(shortestWait, state.cooldownUntil - now)
                return@repeat
            }

            pruneWindow(state, now)
            if (rpmLimit <= 0 || state.requestTimes.size < rpmLimit) {
                state.requestTimes.addLast(now)
                cursors[poolId] = (index + 1) % normalized.size
                rememberSessionKey(sessionKey, key)
                return Selection(key)
            }
            shortestWait = minOf(shortestWait, state.requestTimes.first() + WINDOW_MILLIS - now)
        }
        return Selection(null, shortestWait.takeIf { it != Long.MAX_VALUE }?.coerceAtLeast(1L) ?: 0L)
    }

    /** Record sticky session binding; prune oldest LRU entries beyond capacity. */
    private fun rememberSessionKey(sessionKey: String?, key: String) {
        if (sessionKey.isNullOrEmpty()) return
        sessionKeys.remove(sessionKey)
        sessionKeys[sessionKey] = key
        while (sessionKeys.size > MAX_SESSION_BINDINGS) {
            sessionKeys.remove(sessionKeys.keys.first())
        }
    }

    @Synchronized
    fun markRateLimited(key: String, retryAfterSeconds: Long?) {
        val retryMillis = (retryAfterSeconds ?: DEFAULT_COOLDOWN_SECONDS)
            .coerceIn(1L, MAX_COOLDOWN_SECONDS) * 1_000L
        val state = states.getOrPut(fingerprint(key)) { KeyState() }
        state.cooldownUntil = maxOf(state.cooldownUntil, nowMillis() + retryMillis)
    }

    private fun pruneWindow(state: KeyState, now: Long) {
        val cutoff = now - WINDOW_MILLIS
        while (state.requestTimes.isNotEmpty() && state.requestTimes.first() <= cutoff) {
            state.requestTimes.removeFirst()
        }
    }

    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val WINDOW_MILLIS = 60_000L
        const val DEFAULT_COOLDOWN_SECONDS = 60L
        const val MAX_COOLDOWN_SECONDS = 300L
        const val MAX_SESSION_BINDINGS = 200
    }
}

/**
 * Executes a request with rotated API keys.
 * Handles rate limits (429), quota limits, and authentication errors with transparent key rotation.
 * For NVIDIA NIM endpoints, keys rotate in balanced round-robin with instant failover on limits.
 */
internal suspend fun <T> executeWithRotatedApiKey(
    model: ModelConfig,
    scheduler: ApiKeyScheduler,
    sessionKey: String? = null,
    request: suspend (ModelConfig) -> T,
): T {
    val keys = model.apiKeys.ifEmpty { listOfNotNull(model.apiKey) }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
    if (keys.isEmpty()) return request(model.copy(apiKey = null))

    val isNvidia = model.provider.contains("nvidia", ignoreCase = true) ||
        model.baseUrl.contains("api.nvidia.com", ignoreCase = true)
    // For NVIDIA NIM with multiple keys, round-robin load balance across keys rather than pinning to one sticky key
    val effectiveSessionKey = if (isNvidia && keys.size > 1) null else sessionKey

    val excluded = mutableSetOf<String>()
    var lastRateLimit: LlmRateLimitException? = null
    var lastAuthOrQuotaException: Exception? = null

    while (true) {
        // Ensure coroutine is still active before trying the next key
        currentCoroutineContext().ensureActive()
        val selection = scheduler.select(keys, model.requestsPerMinutePerKey, excluded, effectiveSessionKey)
        val selectedKey = selection.key
        if (selectedKey == null) {
            if (excluded.size >= keys.size) {
                lastRateLimit?.let { throw it }
                lastAuthOrQuotaException?.let { throw it }
            }
            if (selection.waitMillis > 0L) {
                delay(selection.waitMillis.milliseconds)
                continue
            }
            throw lastRateLimit ?: lastAuthOrQuotaException ?: IllegalStateException("No available API Key for the current model")
        }
        try {
            return request(model.copy(apiKey = selectedKey))
        } catch (error: LlmRateLimitException) {
            if (!error.allowImmediateKeyRotation) {
                scheduler.markRateLimited(selectedKey, error.retryAfterSeconds)
                throw error
            }
            // If this was the only available key in the pool, do not cool it down so the upper layer can backoff retry
            if (keys.count { it !in excluded && it != selectedKey } == 0) throw error
            scheduler.markRateLimited(selectedKey, error.retryAfterSeconds)
            excluded += selectedKey
            lastRateLimit = error
        } catch (error: Exception) {
            val msg = error.message.orEmpty().lowercase()
            val isAuthOrQuotaFailure = isNvidia ||
                msg.contains("401") || msg.contains("402") || msg.contains("403") ||
                msg.contains("unauthorized") || msg.contains("invalid api key") ||
                msg.contains("invalid or unauthorized") || msg.contains("quota") ||
                msg.contains("balance") || msg.contains("credit") || msg.contains("rate limit")
            val hasAlternativeKey = keys.any { it !in excluded && it != selectedKey }
            if (isAuthOrQuotaFailure && hasAlternativeKey) {
                scheduler.markRateLimited(selectedKey, 120L)
                excluded += selectedKey
                lastAuthOrQuotaException = error
            } else {
                throw error
            }
        }
    }
}
