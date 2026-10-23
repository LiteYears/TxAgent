package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentPlugin(
    val id: String,
    val name: String,
    val description: String,
    val version: String = "1.0.0",
    val author: String = "TaiXu Core Team",
    val permissions: List<String> = emptyList(),
    val iconName: String = "Package",
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = true,
)

object BuiltinPlugins {
    val presets: List<AgentPlugin> = listOf(
        AgentPlugin(
            id = "proot_health_probe",
            name = "PRoot Sandbox Diagnostic Probe",
            description = "Probes Linux PRoot virtual filesystem, process, and memory states in real time to assist with diagnostics",
            version = "1.0.0",
            author = "TaiXu System",
            permissions = listOf("Run System State Probe", "Collect PRoot Diagnostic Metrics"),
            iconName = "Terminal",
            isEnabled = true,
            isBuiltin = true,
        ),
        AgentPlugin(
            id = "destructive_cmd_interceptor",
            name = "High-Risk Command Interceptor",
            description = "Validates and intercepts destructive commands (rm -rf /, mkfs, dd) before execution to protect the sandbox and workspace",
            version = "1.1.0",
            author = "Security Lab",
            permissions = listOf("Command Safety Audit", "Intercept High-Risk Operations"),
            iconName = "Alert",
            isEnabled = true,
            isBuiltin = true,
        ),
        AgentPlugin(
            id = "web_search_augment",
            name = "Web Search & Docs Index Booster",
            description = "Enriches Agent context with external developer docs, Debian package indexes, and troubleshooting guides",
            version = "0.9.0",
            author = "Community",
            permissions = listOf("Network Access", "Knowledge Base Indexing"),
            iconName = "Globe",
            isEnabled = false,
            isBuiltin = true,
        ),
        AgentPlugin(
            id = "host_bridge",
            name = "Host Bridge · Built-in ADB",
            description = "Access host capabilities from the sandbox via 127.0.0.1:7980: APK install and privileged shell execution via built-in Wireless ADB, Shizuku, or Root.",
            version = "1.0.0",
            author = "TaiXu System",
            permissions = listOf(
                "Loopback Network Listener (127.0.0.1)",
                "Install Unknown Apps (REQUEST_INSTALL_PACKAGES)",
                "Wireless Debugging Pairing & Connection (Android 11+)",
            ),
            iconName = "Cable",
            isEnabled = true,
            isBuiltin = true,
        ),
    )
}
