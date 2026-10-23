package top.wkbin.taixu.core.model

/**
 * 太墟支持的系统运行与特权模式。
 */
enum class ExecutionMode(
    val id: String,
    val shortLabel: String,
    val title: String,
    val summary: String,
    val requiredPrivilege: String,
    val capabilities: List<String>,
) {
    PROOT(
        id = "PROOT",
        shortLabel = "PRoot",
        title = "PRoot User-Space Sandbox (Default)",
        summary = "Works out of the box with no special permissions. Emulates a Linux user-space environment via PRoot.",
        requiredPrivilege = "None (Standard app permissions)",
        capabilities = listOf(
            "100% Root-free full Debian / Ubuntu runtime",
            "Supports APT / APK package ecosystems",
            "Interactive PTY terminal & AI Agent toolchains",
        ),
    ),
    SHIZUKU(
        id = "SHIZUKU",
        shortLabel = "Shizuku",
        title = "Shizuku / ADB Elevated Mode",
        summary = "Grants ADB privileges via Shizuku to lift Android background limits and enable device-wide automation.",
        requiredPrivilege = "Shizuku Service Authorization (UID 2000 shell)",
        capabilities = listOf(
            "Bypass Android 12+ 32-process limit (Phantom Process Killer)",
            "Deep background keep-alive exemption",
            "Host UI & device automation (input / screencap / logcat)",
            "Bypass /sdcard/Android/data access restrictions",
        ),
    ),
    ROOT(
        id = "ROOT",
        shortLabel = "Root",
        title = "Root Native Performance Mode",
        summary = "Requests SU access to unlock 100% hardware performance with zero PRoot syscall interception overhead.",
        requiredPrivilege = "Root Access (Magisk / KernelSU / APatch / UID 0)",
        capabilities = listOf(
            "100% native Linux speed (zero syscall interception overhead)",
            "Native ext4 semantics (resolves perl/dpkg hardlink issues)",
            "Supports Docker / Podman containers & images",
            "Direct GPU/NPU node access (/dev/kgsl-3d0) for acceleration",
            "Bind privileged ports (80/443) and configure iptables",
        ),
    );

    companion object {
        /** 兼容历史持久化值：已下线的 ADB 等未知 id 会安全回落到 PROOT。 */
        fun fromId(id: String): ExecutionMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PROOT
    }
}
