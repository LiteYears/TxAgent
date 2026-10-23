package top.wkbin.taixu.harness.mcp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.McpTransportType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpHttpTransportTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `tools call transport failure is not retried`() = runBlocking {
        val toolsCalls = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                return when (payload.getValue("method").jsonPrimitive.content) {
                    "initialize" -> MockResponse().setBody(
                        """{"jsonrpc":"2.0","id":"${payload.getValue("id").jsonPrimitive.content}","result":{"protocolVersion":"$MCP_PROTOCOL_VERSION"}}""",
                    )
                    "notifications/initialized" -> MockResponse().setResponseCode(202)
                    "tools/call" -> {
                        toolsCalls.incrementAndGet()
                        MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                    }
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val transport = McpHttpTransport(
            OkHttpClient(),
            Json { ignoreUnknownKeys = true },
            AppLogger(context) { it },
        )
        val config = McpServerConfig(
            id = "side-effecting",
            name = "side-effecting",
            transportType = McpTransportType.SSE,
            serverUrl = server.url("/mcp").toString(),
        )

        val failure = runCatching {
            transport.execute(config, "write_record", buildJsonObject {})
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("未自动重试"))
        assertEquals(1, toolsCalls.get())
    }
    @Test
    fun `custom headers cover handshake discovery calls and credential rotation`() = runBlocking {
        val received = mutableListOf<Pair<String, String?>>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                val method = payload.getValue("method").jsonPrimitive.content
                synchronized(received) { received += method to request.getHeader("Authorization") }
                assertEquals("test-key", request.getHeader("X-Api-Key"))
                if (method == "notifications/initialized") return MockResponse().setResponseCode(202)
                val result = when (method) {
                    "initialize" -> """{"protocolVersion":"$MCP_PROTOCOL_VERSION"}"""
                    "tools/list" -> """{"tools":[]}"""
                    else -> """{"content":[{"type":"text","text":"ok"}]}"""
                }
                return MockResponse().setBody("""{"jsonrpc":"2.0","id":"${payload.getValue("id").jsonPrimitive.content}","result":$result}""")
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val transport = McpHttpTransport(OkHttpClient(), Json { ignoreUnknownKeys = true }, AppLogger(context) { it })
        val config = McpServerConfig("headers", "Headers", transportType = McpTransportType.SSE,
            serverUrl = server.url("/mcp").toString(),
            headers = mapOf("Authorization" to "Bearer fake-one", "X-Api-Key" to "test-key"))
        try {
            transport.discover(config)
            assertTrue(transport.execute(config, "sample", buildJsonObject {}).first)
            transport.discover(config.copy(headers = config.headers + ("Authorization" to "Bearer fake-two")))
            assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/call",
                "initialize", "notifications/initialized", "tools/list"), received.map { it.first })
            assertEquals(List(4) { "Bearer fake-one" } + List(3) { "Bearer fake-two" }, received.map { it.second })
        } finally { transport.closeSession(config.id) }
    }

    @Test
    fun `missing and invalid custom header credentials fail cleanly without leaking secrets`() = runBlocking {
        val secretToken = "Bearer super-secret-mcp-token-xyz987"
        val loggedMessages = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val auth = request.getHeader("Authorization")
                if (auth.isNullOrBlank()) {
                    return MockResponse().setResponseCode(401).setBody("Missing credentials")
                }
                if (auth != "Bearer valid-token") {
                    return MockResponse().setResponseCode(403).setBody("Invalid credentials")
                }
                val payload = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                val method = payload.getValue("method").jsonPrimitive.content
                if (method == "notifications/initialized") return MockResponse().setResponseCode(202)
                val result = when (method) {
                    "initialize" -> """{"protocolVersion":"$MCP_PROTOCOL_VERSION"}"""
                    "tools/list" -> """{"tools":[]}"""
                    else -> """{"content":[{"type":"text","text":"ok"}]}"""
                }
                return MockResponse().setBody("""{"jsonrpc":"2.0","id":"${payload.getValue("id").jsonPrimitive.content}","result":$result}""")
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = AppLogger(context) { raw ->
            val redacted = raw.replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer [REDACTED]")
            synchronized(loggedMessages) { loggedMessages += redacted }
            redacted
        }
        val transport = McpHttpTransport(OkHttpClient(), Json { ignoreUnknownKeys = true }, logger)
        val baseConfig = McpServerConfig(
            id = "auth-check",
            name = "AuthCheck",
            transportType = McpTransportType.SSE,
            serverUrl = server.url("/mcp").toString(),
        )
        try {
            // 1. Missing credentials -> 401 failure
            val missingErr = runCatching { transport.discover(baseConfig) }.exceptionOrNull()
            assertTrue(missingErr != null)
            transport.closeSession(baseConfig.id)

            // 2. Invalid credentials -> 403 failure
            val invalidConfig = baseConfig.copy(headers = mapOf("Authorization" to secretToken))
            val invalidErr = runCatching { transport.discover(invalidConfig) }.exceptionOrNull()
            assertTrue(invalidErr != null)
            assertTrue(loggedMessages.none { it.contains("super-secret-mcp-token-xyz987") })
            transport.closeSession(baseConfig.id)

            // 3. Valid credentials after rotation -> success
            val validConfig = baseConfig.copy(headers = mapOf("Authorization" to "Bearer valid-token"))
            transport.discover(validConfig)
            assertTrue(transport.execute(validConfig, "sample", buildJsonObject {}).first)
        } finally {
            transport.closeSession(baseConfig.id)
        }
    }
}
