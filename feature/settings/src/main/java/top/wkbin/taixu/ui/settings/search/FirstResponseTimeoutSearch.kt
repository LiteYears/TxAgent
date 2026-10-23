package top.wkbin.taixu.ui.settings.search

import top.wkbin.taixu.ui.components.RuntimeIconName

internal val firstResponseTimeoutSearchItem = SettingsSearchItem(
    id = "agent_first_response_timeout",
    title = "First Response Timeout",
    subtitle = "Timeout for first model response defaults to 90s, can be increased or disabled.",
    category = SettingsSearchCategory.AGENT,
    target = SettingsSearchTarget.AGENT_EXECUTION,
    icon = RuntimeIconName.Alarm,
    keywords = listOf("timeout", "first response", "90", "wait", "response"),
    breadcrumb = "Agent & AI Models > Agent Execution > First Response Timeout",
)
