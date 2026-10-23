package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

/**
 * 🧩 MCP 能力仓库（MCP Capability Catalog）
 *
 * 把「有哪些 MCP」「怎么装」「怎么更」拆成三层，本项目只承担**精选层**：
 * - 发现层：`punkpeye/awesome-mcp-servers` —— 只用来人工挑选候选，条目里没有包名/版本/校验和，不能程序化消费；
 * - 安装 / 更新层：npm registry（走 `registry.npmmirror.com`）与 PyPI —— 唯一能提供 version + 校验和 + tarball 的来源；
 * - 精选层：本文件的条目 + 开发套件（[BuiltinPluginBundles]）的组件生命周期。
 *
 * 三条硬约束（见 docs/MCP_CATALOG.md）：
 * 1. **禁止 `@latest`**：一律固定版本号，否则安装不可复现、更新不可审计；
 * 2. **安装期落盘校验，运行期不联网现拉**：元数据里的 `integrity` 会在装配期与 registry 比对，
 *    不一致直接中止；运行时只通过 `run` 子命令直调已落盘的全局包，避免 `discoverTools()` 冷启动拉包超时；
 * 3. **更新是显式动作**：catalog 只声明出厂 pin，升级由用户点「检查更新」触发，写入落位标记即刻生效。
 *
 * 关于版本从哪里读——**落位标记是唯一真相**，`/opt/taixu/mcp/<id>.ok` 记录实际安装的
 * `NAME/VERSION/SPEC/INTEGRITY`。运行参数因此**不携带版本号**（否则「升级」只改了全局安装，
 * 进程仍按代码里的旧 pin 拉起，升级会退化成假动作）；代码里的 [McpCatalogEntry.pinnedVersion]
 * 只表示出厂装配的默认值，以及「重新安装」时的回落点。
 *
 * 本文件是纯数据 + 纯函数：不碰文件系统、不执行命令，安装与探针都交给开发套件已有的
 * `PluginComponent.postInstallSteps` / `checkCommand` 机制。
 */
@Serializable
enum class McpCatalogChannel {
    /** npm 通道：`npm install -g <pkg>@<pin>`，校验 `dist.integrity`。 */
    NPM,

    /** PyPI 通道：需要 python3-pip 与独立 venv，当前未启用（见 [McpCatalogEntry.available]）。 */
    PYPI,
}

/**
 * 一条可安装的 MCP 能力。
 *
 * `id` 与 `McpServerConfig.id` 一一对应：安装完成后由仓库层把对应服务置为「已安装 + 启用一次」，
 * 因此这里不额外引入第二套标识。
 */
@Serializable
data class McpCatalogEntry(
    val id: String,
    val name: String,
    val summary: String,
    val channel: McpCatalogChannel,
    /** 完整包规格（含固定版本），例如 `open-websearch@2.2.0`、`@colbymchenry/codegraph@1.6.2`。 */
    val packageSpec: String,
    /** 包名（不含版本），卸载时需要。 */
    val packageName: String,
    val pinnedVersion: String,
    /** registry 提供的 `dist.integrity`（sha512）。装配期必须与它逐字相等，否则中止。 */
    val integrity: String,
    /** 本地版本记录；探针同时核验实际包与入口，启动不询问 registry。 */
    val markerPath: String,
    /** 该 MCP 服务自身需要的启动参数（例如 CodeGraph 的 `serve --mcp`）。版本由落位标记决定，不写在这里。 */
    val serverArgs: List<String> = emptyList(),
    val runtimeEnv: Map<String, String> = emptyMap(),
    /** 提供该能力的开发套件组件 id；安装该组件即代表能力已就绪。 */
    val installComponentId: String,
    /** 组件级依赖（例如 Node 运行时）。 */
    val dependsOnComponentIds: List<String> = emptyList(),
    /** 需要用户凭证的能力不可一键安装，保持 [available]=false。 */
    val available: Boolean,
    val unavailableReason: String = "",
    val risk: String = "low",
    /** 需要 per-workspace 索引（如 CodeGraph）：装好 ≠ 可用，首轮使用前要建索引。 */
    val requiresWorkspaceIndex: Boolean = false,
    val notes: String = "",
) {
    /** 装配步骤：交给 `PluginComponent.postInstallSteps` 执行。 */
    val installSteps: List<String>
        get() = listOf(
            "/bin/sh ${McpCatalog.SCRIPT_PATH} install $id $packageSpec '$integrity'",
        )

    /** 卸载命令：交给 `PluginComponent.postUninstallSteps`（受白名单正则约束，不能带 shell 元字符）。 */
    val uninstallCommand: String
        get() = "npm uninstall -g $packageName >/dev/null 2>&1 || true"

    /** 状态探针：交给 `PluginComponent.checkCommand`，返回 0 视为已就绪。 */
    val probeCommand: String
        get() = "/bin/sh ${McpCatalog.SCRIPT_PATH} probe $id $packageName >/dev/null 2>&1"

    fun serverPreset(): McpServerConfig = McpServerConfig(
        id = id, name = name, description = componentDescription,
        command = launchCommand, args = launchArgs, env = runtimeEnv,
        isEnabled = false, isBuiltin = true, requiresInstall = true,
        installComponentId = installComponentId, builtinRisk = risk,
    )

    /**
     * 启动命令：npm 通道由装配脚本按落位标记解析实际版本后 `exec` 全局包的可执行入口。
     * 刻意不经过 `npx`，也不在参数里带版本号，见文件头的「落位标记是唯一真相」。
     */
    val launchCommand: String
        get() = if (channel == McpCatalogChannel.NPM) "/bin/sh" else "python3"

    /** 启动参数：npm 通道为 [McpCatalog.SCRIPT_PATH] 的 `run` 子命令 + 该服务自身的参数。 */
    val launchArgs: List<String>
        get() = if (channel == McpCatalogChannel.NPM) {
            listOf(McpCatalog.SCRIPT_PATH, "run", id, packageName) + serverArgs
        } else {
            serverArgs
        }

    /** 更新检查命令：读落位标记里的已装版本，再对比镜像上的最新版本。 */
    val checkUpdateCommand: String
        get() = "/bin/sh ${McpCatalog.SCRIPT_PATH} check $id $packageName"

    /** 一键升级命令：以镜像上的最新版本覆盖安装，并重写落位标记。 */
    val upgradeCommand: String
        get() = "/bin/sh ${McpCatalog.SCRIPT_PATH} upgrade $id $packageName"

    /** 是否为可一键安装的 npm 能力。 */
    val installable: Boolean
        get() = available && channel == McpCatalogChannel.NPM

    /** 能力仓库里的组件名（带风险与索引提示，避免用户在 UI 里只看名字）。 */
    val componentDescription: String
        get() = buildString {
            append(summary)
            append(". Risk level: $risk; pinned version: $pinnedVersion")
            if (installable) append("; check for updates in installation dialog; reinstall to revert to pinned")
            if (requiresWorkspaceIndex) append("; requires workspace indexing after installation")
            if (notes.isNotBlank()) append(". ").append(notes)
        }
}

/**
 * Update check result for a capability (runtime state, not persisted).
 *
 * [installedVersion] comes from marker file, [latestVersion] from registry mirror.
 * Update available only when both are present and distinct.
 */
data class McpUpdateStatus(
    val id: String,
    val installedVersion: String = "",
    val latestVersion: String = "",
    val error: String = "",
) {
    val updateAvailable: Boolean
        get() = error.isBlank() && installedVersion.isNotBlank() &&
            latestVersion.isNotBlank() && installedVersion != latestVersion

    /** Single-line readable conclusion for UI. */
    val summary: String
        get() = when {
            error.isNotBlank() -> error
            installedVersion.isBlank() -> "Not installed"
            latestVersion.isBlank() -> "Upstream version unknown"
            updateAvailable -> "Update available to v$latestVersion"
            else -> "Up to date v$installedVersion"
        }
}

/**
 * Curated catalog of MCP capabilities.
 */
object McpCatalog {
    /** Dev suite bundle ID for MCP catalog. */
    const val BUNDLE_ID = "mcp-catalog"

    /** Absolute path to setup script in sandbox. */
    const val SCRIPT_PATH = "/opt/taixu/scripts/setup_mcp_catalog.sh"

    /** Marker directory for installation status. */
    const val MARKER_DIR = "/opt/taixu/mcp"

    private fun marker(id: String) = "$MARKER_DIR/$id.ok"

    val entries: List<McpCatalogEntry> = listOf(
        McpCatalogEntry(
            id = "mcp_websearch",
            name = "Web Search (Open-WebSearch)",
            summary = "API-key-free multi-engine web search and page content extraction",
            channel = McpCatalogChannel.NPM,
            packageSpec = "open-websearch@2.2.0",
            packageName = "open-websearch",
            pinnedVersion = "2.2.0",
            integrity = "sha512-Wv2iZqknbNGNMqD9YQ7+gfgc0FFIFg303HfbysRDDbbob9vamCp/99ltP3EIMO/kskWERU9cFpDV3tO1sdnPTw==",
            markerPath = marker("mcp_websearch"),
            runtimeEnv = mapOf("MODE" to "stdio", "DEFAULT_SEARCH_ENGINE" to "baidu"),
            installComponentId = "mcp_websearch",
            dependsOnComponentIds = listOf("nodejs-core"),
            available = true,
            risk = "medium",
            notes = "Content extraction uses request mode. Pages requiring browser rendering or cookies may fail.",
        ),
        McpCatalogEntry(
            id = "mcp_codegraph",
            name = "CodeGraph Code Knowledge Graph",
            summary = "tree-sitter parsing + SQLite index, providing symbol search, call hierarchy, and impact analysis",
            channel = McpCatalogChannel.NPM,
            packageSpec = "@colbymchenry/codegraph@1.6.2",
            packageName = "@colbymchenry/codegraph",
            pinnedVersion = "1.6.2",
            integrity = "sha512-hODH/mGnCf3IUMs6rzktv/zbecJIKJfdZAYHZ9mqecb0G7ZkHaA8CZafDVnswANFEL1YFcG+lfAwc5SIXzUH6Q==",
            markerPath = marker("mcp_codegraph"),
            serverArgs = listOf("serve", "--mcp"),
            installComponentId = "mcp_codegraph",
            dependsOnComponentIds = listOf("nodejs-core"),
            available = true,
            risk = "medium",
            requiresWorkspaceIndex = true,
            notes = "better-sqlite3 is a native module; falls back to WASM on ARM64 if prebuilt binary misses",
        ),
        McpCatalogEntry(
            id = "mcp_memory",
            name = "Knowledge Graph Memory",
            summary = "Cross-session persistent entity/relation knowledge graph with local JSON storage",
            channel = McpCatalogChannel.NPM,
            packageSpec = "@modelcontextprotocol/server-memory@1.0.0",
            packageName = "@modelcontextprotocol/server-memory",
            pinnedVersion = "1.0.0",
            integrity = "sha512-UDMtMrw2xeI16mcn7O4fesp9Pf3hYh91thuU5KSz0BxKqwnTwEytZKjHfkrqxxmKp0r4QHoiEkMV99UmadmODw==",
            markerPath = marker("mcp_memory"),
            runtimeEnv = mapOf("MEMORY_FILE_PATH" to "/opt/taixu/data/mcp-memory/memory.json"),
            installComponentId = "mcp_memory",
            dependsOnComponentIds = listOf("nodejs-core"),
            available = true,
            risk = "low",
            notes = "Separation of concerns from project memory: model-managed knowledge graph, does not write back to project memory",
        ),
        McpCatalogEntry(
            id = "mcp_sequential_thinking",
            name = "Sequential Thinking",
            summary = "Explicit multi-step reasoning chain, pure computation with no side effects",
            channel = McpCatalogChannel.NPM,
            packageSpec = "@modelcontextprotocol/server-sequential-thinking@1.0.0",
            packageName = "@modelcontextprotocol/server-sequential-thinking",
            pinnedVersion = "1.0.0",
            integrity = "sha512-ykTmeS4Eejs+FESDrD73mDBNYCkb+cCZJ1MWuv7/vwucbVgGfEbBL5HVt2nVw0/vNF8jz+o9sO/WmxxfXt0J6w==",
            markerPath = marker("mcp_sequential_thinking"),
            installComponentId = "mcp_sequential_thinking",
            dependsOnComponentIds = listOf("nodejs-core"),
            available = true,
            risk = "low",
            notes = "No I/O; can serve as an approval-free read-only capability",
        ),
        McpCatalogEntry(
            id = "mcp_context7",
            name = "Context7 Library Docs Retrieval",
            summary = "Fetch latest official doc snippets by library name to reduce SDK hallucinations",
            channel = McpCatalogChannel.NPM,
            packageSpec = "@upstash/context7-mcp@4.3.0",
            packageName = "@upstash/context7-mcp",
            pinnedVersion = "4.3.0",
            integrity = "sha512-fuDt5nWjE5JkAPLjU0m+m0LlJphxyegAvAmcwHoA5jrzAhKo/rVV0RFA8TOwODGdDVdt/nPxNslkxeDIDp/rPA==",
            markerPath = marker("mcp_context7"),
            installComponentId = "mcp_context7",
            dependsOnComponentIds = listOf("nodejs-core"),
            available = true,
            risk = "medium",
            notes = "Depends on external SaaS endpoint; network failure degrades gracefully without blocking session",
        ),
        McpCatalogEntry(
            id = "mcp_github",
            name = "GitHub Platform Integration",
            summary = "Issues, PRs, search, and repository file operations",
            channel = McpCatalogChannel.NPM,
            packageSpec = "@modelcontextprotocol/server-github@2025.4.8",
            packageName = "@modelcontextprotocol/server-github",
            pinnedVersion = "2025.4.8",
            integrity = "sha512-8N43bQw9MlUB0piTZHK2JMh8kYPKxH57d4Z7Wb8PS4by2MkZ0FzI5xPImg3xumpev82VZw2VWHQJJJYp+WkwEw==",
            markerPath = marker("mcp_github"),
            runtimeEnv = mapOf("GITHUB_PERSONAL_ACCESS_TOKEN" to "<vault-ref>"),
            installComponentId = "mcp_github",
            dependsOnComponentIds = listOf("nodejs-core"),
            available = false,
            unavailableReason = "Requires GitHub personal access token; available after credential vault integration",
            risk = "high",
        ),
        // ---------- PyPI Channel (currently disabled) ----------
        McpCatalogEntry(
            id = "mcp_fetch",
            name = "Fetch Web Scraping (Official)",
            summary = "Converts HTML from a given URL to Markdown and returns body content",
            channel = McpCatalogChannel.PYPI,
            packageSpec = "mcp-server-fetch==2026.10.10",
            packageName = "mcp-server-fetch",
            pinnedVersion = "2026.10.10",
            integrity = "",
            markerPath = marker("mcp_fetch"),
            serverArgs = listOf("-m", "mcp_server_fetch"),
            installComponentId = "mcp_fetch",
            available = false,
            unavailableReason = "pip channel not enabled (requires python3-pip and isolated venvs)",
            risk = "low",
        ),
        McpCatalogEntry(
            id = "mcp_time",
            name = "Time Zone Tools (Official)",
            summary = "Timezone conversion and current time",
            channel = McpCatalogChannel.PYPI,
            packageSpec = "mcp-server-time==2026.10.10",
            packageName = "mcp-server-time",
            pinnedVersion = "2026.10.10",
            integrity = "",
            markerPath = marker("mcp_time"),
            serverArgs = listOf("-m", "mcp_server_time"),
            installComponentId = "mcp_time",
            available = false,
            unavailableReason = "pip channel not enabled (requires python3-pip and isolated venvs)",
            risk = "low",
        ),
        McpCatalogEntry(
            id = "mcp_pandoc",
            name = "Pandoc Document Conversion",
            summary = "Format conversion between Office, Markdown, HTML, PDF, etc.",
            channel = McpCatalogChannel.PYPI,
            packageSpec = "mcp-pandoc==0.11.1",
            packageName = "mcp-pandoc",
            pinnedVersion = "0.11.1",
            integrity = "",
            markerPath = marker("mcp_pandoc"),
            serverArgs = listOf("-m", "mcp_pandoc"),
            installComponentId = "mcp_pandoc",
            available = false,
            unavailableReason = "pip channel not enabled, and requires in-sandbox pandoc binary",
            risk = "low",
        ),
    )

    /** 只包含可一键安装的 npm 能力：开发套件为每条生成一个组件。 */
    val installable: List<McpCatalogEntry> = entries.filter { it.installable }

    private val byId: Map<String, McpCatalogEntry> = entries.associateBy { it.id }

    fun find(id: String): McpCatalogEntry? = byId[id]

    /**
     * 已安装组件集合 → 就绪的 MCP 服务 id。
     * 安装状态以组件探针为唯一来源，不再单独持久化一个「是否已安装」字段，
     * 避免出现「数据库说装了、文件其实不在」这种会阻塞启动的第二真相。
     */
    fun readyServerIds(installedComponentIds: Set<String>): Set<String> =
        entries.filter { it.installComponentId in installedComponentIds }.map { it.id }.toSet()

    /** 由 catalog 条目生成开发套件组件（安装 / 探针 / 卸载全部复用既有流水线）。 */
    fun component(entry: McpCatalogEntry): PluginComponent = PluginComponent(
        id = entry.installComponentId,
        name = entry.name,
        description = entry.componentDescription,
        isRequired = false,
        aptPackages = listOf("nodejs", "npm"),
        postInstallSteps = entry.installSteps,
        checkCommand = entry.probeCommand,
        dependsOn = entry.dependsOnComponentIds,
        purgeFiles = listOf(entry.markerPath),
        postUninstallSteps = listOf(entry.uninstallCommand),
    )
}
