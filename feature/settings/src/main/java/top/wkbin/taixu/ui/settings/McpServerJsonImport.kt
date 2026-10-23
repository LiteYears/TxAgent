package top.wkbin.taixu.ui.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.taixu.core.model.McpAuthMode
import top.wkbin.taixu.core.model.McpServerConfig
import top.wkbin.taixu.core.model.McpTransportType
import top.wkbin.taixu.core.model.validateMcpHttpHeaders
import java.util.UUID

internal fun parseMcpHeaders(raw: String): Result<Map<String, String>> = runCatching {
    if (raw.isBlank()) return@runCatching emptyMap()
    val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }
        .getOrElse { error("Headers must be a JSON object") }
    parseHeaderObject(obj)
}

private fun parseHeaderObject(obj: JsonObject): Map<String, String> = obj.mapValues { (_, value) ->
    require(value is JsonPrimitive && value.isString) { "Header values must be strings" }
    value.content
}.also(::validateMcpHttpHeaders)

/** 解析标准 mcpServers 或单个服务；不回显含凭据的原始 JSON 解析错误。 */
internal fun parseMcpJson(rawJson: String): Result<List<McpServerConfig>> = runCatching {
    if (rawJson.isBlank()) error("请输入 JSON 内容")
    val root = runCatching { Json.parseToJsonElement(rawJson.trim()).jsonObject }
        .getOrElse { error("Please enter a valid MCP JSON object") }
    val entries = if ("mcpServers" in root) root.getValue("mcpServers").jsonObject.entries.map { it.key to it.value.jsonObject }
        else listOf((root["name"]?.jsonPrimitive?.contentOrNull ?: "custom_mcp") to root)
    entries.map { (name, obj) ->
        fun text(key: String) = obj[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val url = text("url")
        val command = text("command")
        require(command.isNotBlank() || url.isNotBlank()) { "配置中必须包含 command 或 url 字段" }
        val remote = url.isNotBlank()
        val headers = obj["headers"]?.let {
            val headerObject = it as? JsonObject ?: error("Headers must be a JSON object")
            parseHeaderObject(headerObject)
        }.orEmpty()
        val authMode = if (text("authMode").equals("OAUTH", true)) McpAuthMode.OAUTH else McpAuthMode.NONE
        require(authMode != McpAuthMode.OAUTH || headers.keys.none { it.equals("Authorization", true) }) {
            "Custom Authorization header and OAuth cannot be enabled simultaneously"
        }
        McpServerConfig(
            id = "custom_" + UUID.randomUUID(),
            name = name,
            description = text("description").ifBlank { if (remote) "Remote HTTP Server: $url" else "Local Stdio: $command" },
            transportType = if (remote) McpTransportType.SSE else McpTransportType.STDIO,
            command = command,
            args = obj["args"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            env = obj["env"]?.jsonObject?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty(),
            serverUrl = url,
            headers = headers,
            authMode = authMode,
            oauthClientId = text("oauthClientId"),
            oauthAuthorizationEndpoint = text("oauthAuthorizationEndpoint"),
            oauthTokenEndpoint = text("oauthTokenEndpoint"),
            oauthScope = text("oauthScope"),
            oauthResource = text("oauthResource"),
        )
    }.also { require(it.isNotEmpty()) { "未找到有效的 MCP 服务配置" } }

}.recoverCatching {
    error("Invalid MCP configuration format. Check field types, headers, and OAuth config.")
}
