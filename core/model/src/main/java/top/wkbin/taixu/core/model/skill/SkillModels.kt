package top.wkbin.taixu.core.model.skill

import kotlinx.serialization.Serializable

/**
 * 技能所申请的系统权限边界。
 */
@Serializable
enum class SkillPermission(
    val id: String,
    val label: String,
    val description: String,
    val isHighRisk: Boolean,
) {
    EXEC_COMMAND(
        id = "exec_command",
        label = "Command Execution",
        description = "Allows executing shell commands or bundled scripts inside the Linux PRoot sandbox",
        isHighRisk = true,
    ),
    NETWORK(
        id = "network",
        label = "Network Access",
        description = "Allows making network requests to external or LAN hosts",
        isHighRisk = true,
    ),
    FILE_WRITE(
        id = "file_write",
        label = "Filesystem Write",
        description = "Allows creating, modifying, or deleting files in the workspace or sandbox",
        isHighRisk = false,
    ),
    BROWSER_AUTOMATION(
        id = "browser_automation",
        label = "Browser Control",
        description = "Allows controlling the built-in headless browser for rendering, snapshots, and DOM inspection",
        isHighRisk = true,
    ),
    BACKGROUND_SERVICE(
        id = "background_service",
        label = "Background Daemons",
        description = "Allows registering and running persistent background services in the sandbox",
        isHighRisk = false,
    ),
    SYSTEM_PROBE(
        id = "system_probe",
        label = "System Inspection",
        description = "Allows inspecting sandbox CPU, memory usage, and running processes",
        isHighRisk = false,
    );

    companion object {
        fun fromIdOrNull(id: String): SkillPermission? =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }
}

/**
 * 技能包标准元数据声明（对应 SKILL.md Frontmatter 或 manifest.json）。
 */
@Serializable
data class SkillManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String,
    val author: String = "TaiXu Community",
    val license: String? = "Apache-2.0",
    val icon: String = "Sparkles",
    val category: String = "General",
    val tags: List<String> = emptyList(),
    val minTaiXuVersion: String = "1.0.0",
    val compatibleRuntimes: List<String> = listOf("debian", "proot"),
    val permissions: List<SkillPermission> = emptyList(),
    val requiredTools: List<String> = emptyList(),
    val triggerCommand: String? = null,
    val homepage: String? = null,
    val repository: String? = null,
)

/**
 * 声明式提示词模板解构集合（对标 PalmClaw 标准）。
 */
@Serializable
data class PromptTemplateBundle(
    val skillMd: String? = null,
    val agentMd: String? = null,
    val soulMd: String? = null,
    val toolsMd: String? = null,
    val userMd: String? = null,
    val memoryMd: String? = null,
    val extraFiles: Map<String, String> = emptyMap(),
) {
    /**
     * 智能组装生成系统提示词 (System Prompt)。
     */
    fun composeSystemPrompt(resourceGuestPath: String? = null): String = buildString {
        // 1. 技能入口与总纲
        skillMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append(it)
            append("\n\n")
        }

        // 2. 角色与执行指引 (AGENT)
        agentMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【核心角色与指令 (AGENT)】\n")
            append(it)
            append("\n\n")
        }

        // 3. 人格心智与沟通风格 (SOUL)
        soulMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【人格边界与风格 (SOUL)】\n")
            append(it)
            append("\n\n")
        }

        // 4. 工具使用约定 (TOOLS)
        toolsMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【工具使用规范 (TOOLS)】\n")
            append(it)
            append("\n\n")
        }

        // 5. 用户上下文契约 (USER)
        userMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【用户交互契约 (USER)】\n")
            append(it)
            append("\n\n")
        }

        // 6. 长期记忆与初始事实切片 (MEMORY)
        memoryMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【初始化事实记忆 (MEMORY)】\n")
            append(it)
            append("\n\n")
        }

        // 7. 沙箱物理资源目录挂载提示
        if (!resourceGuestPath.isNullOrBlank()) {
            append("### 【Skill 资源与沙箱挂载】\n")
            append("资源物理路径：`$resourceGuestPath`\n")
            append("如需调用该 Skill 附带的 scripts/ 脚本或 references/ 资产，请在此目录下执行。\n")
        }
    }.trim()
}

/**
 * 完整解构后的技能包模型。
 */
data class SkillPackage(
    val manifest: SkillManifest,
    val templates: PromptTemplateBundle,
    val scripts: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val rawFiles: Map<String, ByteArray> = emptyMap(),
)

/**
 * 静态安全审查等级。
 */
@Serializable
enum class AuditLevel(val severity: Int, val label: String) {
    SAFE(0, "Safe"),
    INFO(1, "Info"),
    WARNING(2, "Warning"),
    DANGER(3, "High Risk"),
    BLOCKED(4, "Blocked");
}

/**
 * 安全审查发现项。
 */
@Serializable
data class AuditFinding(
    val level: AuditLevel,
    val ruleId: String,
    val title: String,
    val detail: String,
    val targetFile: String? = null,
    val line: Int? = null,
    val snippet: String? = null,
)

/**
 * 完整的静态安全审查报告。
 */
@Serializable
data class SecurityAuditReport(
    val level: AuditLevel,
    val findings: List<AuditFinding> = emptyList(),
    val declaredPermissions: Set<SkillPermission> = emptySet(),
    val detectedPermissions: Set<SkillPermission> = emptySet(),
    val undeclaredPermissions: Set<SkillPermission> = emptySet(),
) {
    val isBlocked: Boolean get() = level == AuditLevel.BLOCKED
    val isSafe: Boolean get() = level == AuditLevel.SAFE
    val hasWarnings: Boolean get() = level >= AuditLevel.WARNING
}

/**
 * 兼容性评估等级。
 */
@Serializable
enum class CompatibilityLevel(val label: String) {
    COMPATIBLE("Compatible"),
    PARTIALLY_COMPATIBLE("Partially Compatible (Missing optional dependencies)"),
    INCOMPATIBLE("Incompatible");
}

/**
 * 技能兼容性评估报告。
 */
@Serializable
data class SkillCompatibilityResult(
    val level: CompatibilityLevel,
    val missingTools: List<String> = emptyList(),
    val unsupportedRuntimes: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
) {
    val isCompatible: Boolean get() = level != CompatibilityLevel.INCOMPATIBLE
}

/**
 * ClawHub 在线技能市场条目简报。
 */
@Serializable
data class ClawHubMarketItem(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val icon: String = "Sparkles",
    val tags: List<String> = emptyList(),
    val category: String = "General",
    val downloadUrl: String,
    val stars: Int = 0,
    val isInstalled: Boolean = false,
    val requiredTools: List<String> = emptyList(),
    val permissions: List<SkillPermission> = emptyList(),
)

/**
 * ClawHub 技能详情。
 */
@Serializable
data class ClawHubMarketDetail(
    val item: ClawHubMarketItem,
    val readmeMarkdown: String? = null,
    val license: String? = "Apache-2.0",
    val templateSummary: List<String> = emptyList(),
    val sampleScripts: List<String> = emptyList(),
)
