package top.wkbin.taixu.ui.workflow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import top.wkbin.taixu.core.model.workflow.WorkflowDefinitionImport

/** Preserve every result value; formatting must not prevent a complete file transfer. */
internal object WorkflowResultDocument {
    private val pretty = Json { prettyPrint = true }

    fun encode(result: JsonObject): String {
        val compact = result.toString()
        require(compact.length <= WorkflowDefinitionImport.MAX_CHARACTERS) { "Result JSON exceeds file export limit" }
        val formatted = pretty.encodeToString(JsonObject.serializer(), result)
        return formatted.takeIf { it.length <= WorkflowDefinitionImport.MAX_CHARACTERS } ?: compact
    }

    fun pages(raw: String, pageSize: Int = 6000): List<String> {
        require(pageSize >= 2) { "Page size must be at least 2" }
        if (raw.isEmpty()) return listOf("")
        return buildList {
            var start = 0
            while (start < raw.length) {
                var end = (start.toLong() + pageSize).coerceAtMost(raw.length.toLong()).toInt()
                if (end < raw.length && raw[end - 1].isHighSurrogate() && raw[end].isLowSurrogate()) end--
                add(raw.substring(start, end))
                start = end
            }
        }
    }
}
