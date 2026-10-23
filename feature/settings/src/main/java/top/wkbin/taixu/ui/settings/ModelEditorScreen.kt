package top.wkbin.taixu.ui.settings
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.settings.R
import org.koin.compose.viewmodel.koinViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import top.wkbin.taixu.ui.components.RuntimeFilledTextField
import top.wkbin.taixu.ui.components.RuntimeProviderCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import top.wkbin.taixu.ui.settings.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.AiModelProfileExport
import top.wkbin.taixu.core.tools.AgentProviderDefinition
import top.wkbin.taixu.core.tools.ProviderGroup
import top.wkbin.taixu.harness.ModelContextWindows
import top.wkbin.taixu.ui.components.ProviderBadge
import top.wkbin.taixu.ui.components.RuntimeAlertDialog
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeFilledTonalButton
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconButton
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedButton
import top.wkbin.taixu.ui.components.RuntimeSlider
import top.wkbin.taixu.ui.components.RuntimeSwitch
import top.wkbin.taixu.ui.components.RuntimeTextButton
import top.wkbin.taixu.ui.components.RuntimeTopBar
import java.util.Locale
import top.wkbin.taixu.core.tools.ProviderProtocol

/**
 * 模型编辑与连接测试全屏独立页面
 */
@Composable
fun ModelEditorScreen(
    modelId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SettingsViewModel? = previewOrNull { koinViewModel() },
) {
    val models by viewModel?.models?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val discovered by viewModel?.discoveredModels?.collectAsStateWithLifecycle() ?: previewStateOf(emptyList())
    val discovering by viewModel?.discoveringModels?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val discoveryError by viewModel?.modelDiscoveryError?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val testing by viewModel?.testingConnection?.collectAsStateWithLifecycle() ?: previewStateOf(false)
    val testResult by viewModel?.connectionResult?.collectAsStateWithLifecycle() ?: previewStateOf(null)
    val existing = models.firstOrNull { it.id == modelId }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var initialApiKey by remember(modelId) { mutableStateOf("") }
    LaunchedEffect(modelId, existing?.secretRef) {
        val secretRef = existing?.secretRef
        if (!secretRef.isNullOrBlank()) {
            initialApiKey = viewModel?.readModelApiKey(secretRef).orEmpty()
        }
    }
    // 保存被端点策略拒绝时留在编辑页并提示，避免静默丢档
    LaunchedEffect(Unit) {
        viewModel?.profileSaveError?.collect { error ->
            if (!error.isNullOrBlank()) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    var showImportJsonDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = if (existing == null) resolveLegacyString("新增模型档案") else resolveLegacyString("编辑模型档案"),
                onBack = onBack,
                actions = {
                    RuntimeIconButton(
                        onClick = { showImportJsonDialog = true },
                        contentDescription = "Fill from JSON",
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Code,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        ModelEditorContent(
            modifier = top.wkbin.taixu.ui.components.runtimeScrollViewport(padding), bottomPadding = padding.calculateBottomPadding(),
            modelId = modelId,
            existing = existing,
            initialApiKey = initialApiKey,
            providers = livePluginProviders(viewModel?.providerCatalog.orEmpty(), existing?.provider),
            discovered = discovered,
            discovering = discovering,
            discoveryError = discoveryError,
            testing = testing,
            testResult = testResult,
            discover = { provider, url, key -> viewModel?.discoverModels(provider, url, key) },
            test = { url, model, key, respApi, providerId -> viewModel?.testConnection(url, model, key, respApi, providerId) },
            save = { name, provider, modelsList, url, key, rpmLimit, temperature, maxTokens, topP, reasoningMode, reasoningEffort, toolCallMode, contextTokens, compactionKeepRecent, compactionReserve, customHeaders, pureChatMode, visionEnabled, imageGenerationEnabled, responseApiEnabled, promptCachingEnabled, promptCacheTtl1h ->
                val saved = viewModel?.saveModels(
                    id = modelId,
                    models = modelsList,
                    name = name,
                    provider = provider,
                    baseUrl = url,
                    apiKey = key,
                    requestsPerMinutePerKey = rpmLimit,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    topP = topP,
                    reasoningMode = reasoningMode,
                    reasoningEffort = reasoningEffort,
                    toolCallMode = toolCallMode,
                    contextTokens = contextTokens,
                    compactionKeepRecentTokens = compactionKeepRecent,
                    compactionReserveTokens = compactionReserve,
                    customHeaders = customHeaders,
                    pureChatMode = pureChatMode,
                    visionEnabled = visionEnabled,
                    imageGenerationEnabled = imageGenerationEnabled,
                    responseApiEnabled = responseApiEnabled,
                    promptCachingEnabled = promptCachingEnabled,
                    promptCacheTtl1h = promptCacheTtl1h,
                )
                if (saved == true) onSaved()
            },
            onFillFromJson = { jsonStr ->
                val result = viewModel?.parseProfilesFromJson(jsonStr)
                result?.fold(
                    onSuccess = { list ->
                        val item = list.firstOrNull()
                        if (item != null) {
                            Toast.makeText(context, "Configuration loaded from JSON", Toast.LENGTH_SHORT).show()
                            item
                        } else {
                            Toast.makeText(context, "No model configuration found", Toast.LENGTH_SHORT).show()
                            null
                        }
                    },
                    onFailure = { err ->
                        Toast.makeText(context, "Parse failed: ${err.message}", Toast.LENGTH_SHORT).show()
                        null
                    }
                )
            },
            showImportDialog = showImportJsonDialog,
            onDismissImportDialog = { showImportJsonDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelEditorContent(
    modifier: Modifier = Modifier, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    modelId: String?,
    existing: AiModelEntity?,
    initialApiKey: String = "",
    providers: List<AgentProviderDefinition>,
    discovered: List<String>,
    discovering: Boolean,
    discoveryError: String?,
    testing: Boolean,
    testResult: String?,
    discover: (String, String, String) -> Unit,
    test: (String, String, String, Boolean, String?) -> Unit,
    save: (String, String, List<String>, String, String, Int, Float?, Int?, Float?, String?, String?, String?, Int?, Int?, Int?, String, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onFillFromJson: (String) -> AiModelProfileExport?,
    showImportDialog: Boolean,
    onDismissImportDialog: () -> Unit,
) {
    val context = LocalContext.current
    // 全部表单字段迁移 rememberSaveable，旋转后输入内容不丢失
    var providerId by rememberSaveable(modelId) {
        mutableStateOf(providers.firstOrNull { it.name == existing?.provider }?.id ?: providers.first().id)
    }
    val provider = providers.firstOrNull { it.id == providerId } ?: providers.first()
    var showProviderPicker by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(modelId) { mutableStateOf(existing?.name.orEmpty()) }

    val existingModelList = remember(existing?.model) {
        existing?.model?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
    }
    // Set 不能直接进 Bundle：用 listSaver 保存为 List 再还原
    val stringSetSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
    var selectedModels by rememberSaveable(modelId, providerId, stateSaver = stringSetSaver) {
        mutableStateOf(
            if (existing != null) {
                existingModelList
            } else {
                provider.recommendedModels.take(1).toSet()
            }
        )
    }
    var customModelInput by rememberSaveable(modelId, providerId) { mutableStateOf("") }
    var modelSearchQuery by rememberSaveable(modelId, providerId) { mutableStateOf("") }
    var url by rememberSaveable(modelId) { mutableStateOf(existing?.baseUrl ?: provider.baseUrl) }
    // API Key 列表等复杂结构用 listSaver
    val stringListSaver = listSaver<List<String>, String>(save = { it.toList() }, restore = { it })
    var keyList by rememberSaveable(modelId, initialApiKey, stateSaver = stringListSaver) {
        mutableStateOf(
            initialApiKey.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
                .ifEmpty { listOf("") }
        )
    }
    val intSetSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })
    var revealedKeyIndices by rememberSaveable(stateSaver = intSetSaver) { mutableStateOf(setOf<Int>()) }
    var showBatchImportKeysDialog by rememberSaveable { mutableStateOf(false) }
    // Base URL 校验：必须以 http/https 开头
    val urlValid = url.trim().startsWith("http://") || url.trim().startsWith("https://")

    LaunchedEffect(initialApiKey) {
        if (keyList.all { it.isBlank() } && initialApiKey.isNotBlank()) {
            keyList = initialApiKey.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
                .ifEmpty { listOf("") }
        }
    }

    val combinedKey = remember(keyList) {
        keyList.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    // 已选模型必须始终出现在候选列表中：否则厂商下架后刷新接口与预设都拿不到该模型，
    // 它会隐身存在于 selectedModels 里，既看不到也无法取消
    val candidateModels = remember(discovered, provider, selectedModels) {
        (selectedModels + discovered + provider.recommendedModels).distinct().filter { it.isNotBlank() }
    }
    val filteredCandidateModels = remember(candidateModels, modelSearchQuery) {
        filterCandidateModels(candidateModels, modelSearchQuery)
    }

    /** 主流模型自动适配值；用户在输入框里填写的值仍然优先。 */
    val autoContextTokens = remember(selectedModels, customModelInput, provider.id) {
        val selected = selectedModels.firstOrNull()
            ?: customModelInput.substringBefore(',').trim().takeIf { it.isNotBlank() }
        selected?.let { modelId -> ModelContextWindows.resolve(modelId, provider.id) }
    }

    // 高级与推理参数
    var rpmLimitText by rememberSaveable(modelId) {
        mutableStateOf(existing?.requestsPerMinutePerKey?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var temperature by rememberSaveable(modelId) { mutableFloatStateOf(existing?.temperature ?: 0.7f) }
    var maxTokensText by rememberSaveable(modelId) { mutableStateOf(existing?.maxTokens?.toString().orEmpty()) }
    var contextTokensText by rememberSaveable(modelId) { mutableStateOf(existing?.contextTokens?.toString().orEmpty()) }
    val parsedMaxTokens = maxTokensText.trim().toIntOrNull()
    val parsedContextTokens = contextTokensText.trim().toIntOrNull()
    val effectiveContextTokens = parsedContextTokens ?: autoContextTokens
    val maxTokensInvalid = maxTokensText.isNotBlank() && (parsedMaxTokens == null || parsedMaxTokens <= 0)
    val contextTokensInvalid = contextTokensText.isNotBlank() &&
        (parsedContextTokens == null || parsedContextTokens <= 0)
    val tokenWindowInvalid = parsedMaxTokens != null && effectiveContextTokens != null &&
        parsedMaxTokens > effectiveContextTokens
    val tokenFieldsValid = !maxTokensInvalid && !contextTokensInvalid && !tokenWindowInvalid
    var compactionKeepRecentText by rememberSaveable(modelId) {
        mutableStateOf(existing?.compactionKeepRecentTokens?.toString().orEmpty())
    }
    var compactionReserveText by rememberSaveable(modelId) {
        mutableStateOf(existing?.compactionReserveTokens?.toString().orEmpty())
    }
    val rpmValid = isValidOptionalModelInteger(rpmLimitText, 0)
    val keepRecentValid = isValidOptionalModelInteger(compactionKeepRecentText, 1)
    val reserveValid = isValidOptionalModelInteger(compactionReserveText, 1)
    var topP by rememberSaveable(modelId) { mutableFloatStateOf(existing?.topP ?: 1.0f) }

    var reasoningModeText by rememberSaveable(modelId) { mutableStateOf(existing?.reasoningMode ?: "auto") }
    var reasoningEffortText by rememberSaveable(modelId) { mutableStateOf(existing?.reasoningEffort.orEmpty()) }
    var reasoningModeMenu by rememberSaveable { mutableStateOf(false) }

    // 功能开关
    var toolCallEnabled by rememberSaveable(modelId) {
        mutableStateOf(existing?.toolCallMode != "disabled")
    }
    var pureChatMode by rememberSaveable(modelId) {
        mutableStateOf(existing?.pureChatMode ?: false)
    }
    var visionEnabled by rememberSaveable(modelId) {
        mutableStateOf(existing?.visionEnabled ?: true)
    }
    var imageGenerationEnabled by rememberSaveable(modelId) {
        mutableStateOf(existing?.imageGenerationEnabled ?: false)
    }
    var responseApiEnabled by rememberSaveable(modelId) {
        mutableStateOf(existing?.responseApiEnabled ?: false)
    }
    var promptCachingEnabled by rememberSaveable(modelId) {
        mutableStateOf(existing?.promptCachingEnabled ?: true)
    }
    var promptCacheTtl1h by rememberSaveable(modelId) {
        mutableStateOf(existing?.promptCacheTtl1h ?: false)
    }

    var customHeaders by rememberSaveable(modelId) { mutableStateOf(existing?.customHeaders.orEmpty()) }

    // 折叠区域控制
    var reasoningSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedSectionExpanded by rememberSaveable { mutableStateOf(false) }

    // 从 JSON 填充表单逻辑
    fun applyProfile(profile: AiModelProfileExport) {
        val matchedProvider = providers.firstOrNull { it.id.equals(profile.provider, ignoreCase = true) || it.name.equals(profile.provider, ignoreCase = true) }
        if (matchedProvider != null) {
            providerId = matchedProvider.id
        }
        if (profile.name.isNotBlank()) name = profile.name
        if (profile.baseUrl.isNotBlank()) url = profile.baseUrl
        val importedKeys = (profile.apiKeys + listOfNotNull(profile.apiKey))
            .flatMap { it.lineSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (importedKeys.isNotEmpty()) keyList = importedKeys
        val importedModels = profile.model.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (importedModels.isNotEmpty()) {
            selectedModels = importedModels.toSet()
        }
        if (profile.requestsPerMinutePerKey > 0) rpmLimitText = profile.requestsPerMinutePerKey.toString()
        profile.temperature?.let { temperature = it }
        profile.maxTokens?.let { maxTokensText = it.toString() }
        profile.contextTokens?.let { contextTokensText = it.toString() }
        if (profile.schemaVersion >= 2 || profile.compactionKeepRecentTokens != null) compactionKeepRecentText = profile.compactionKeepRecentTokens?.toString().orEmpty()
        if (profile.schemaVersion >= 2 || profile.compactionReserveTokens != null) compactionReserveText = profile.compactionReserveTokens?.toString().orEmpty()
        profile.topP?.let { topP = it }
        profile.reasoningMode?.let { reasoningModeText = it }
        profile.reasoningEffort?.let { reasoningEffortText = it }
        toolCallEnabled = profile.toolCallMode != "disabled"
        pureChatMode = profile.pureChatMode
        visionEnabled = profile.visionEnabled
        imageGenerationEnabled = profile.imageGenerationEnabled
        responseApiEnabled = profile.responseApiEnabled
        promptCachingEnabled = profile.promptCachingEnabled
        promptCacheTtl1h = profile.promptCacheTtl1h
        if (profile.customHeaders.isNotBlank()) customHeaders = profile.customHeaders
    }

    if (showBatchImportKeysDialog) {
        BatchImportKeysDialog(
            onDismiss = { showBatchImportKeysDialog = false },
            onConfirm = { batchText ->
                val existingKeys = keyList.map { it.trim() }.filter { it.isNotEmpty() }
                val importedKeys = batchText.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .filter { it !in existingKeys }
                    .toList()
                if (importedKeys.isEmpty()) {
                    Toast.makeText(context, "No new keys found", Toast.LENGTH_SHORT).show()
                } else {
                    keyList = (existingKeys + importedKeys).ifEmpty { listOf("") }
                    revealedKeyIndices = emptySet()
                    Toast.makeText(context, "Imported ${importedKeys.size} keys", Toast.LENGTH_SHORT).show()
                    showBatchImportKeysDialog = false
                }
            },
        )
    }

    if (showImportDialog) {
        QuickImportJsonDialog(
            onDismiss = onDismissImportDialog,
            onConfirm = { jsonText ->
                val profile = onFillFromJson(jsonText)
                if (profile != null) {
                    applyProfile(profile)
                    onDismissImportDialog()
                }
            },
        )
    }

    if (showProviderPicker) {
        ProviderPickerDialog(
            providers = providers,
            currentProviderId = providerId,
            onDismiss = { showProviderPicker = false },
            onSelect = { option ->
                providerId = option.id
                url = option.baseUrl
                if (existing == null) {
                    selectedModels = option.recommendedModels.take(1).toSet()
                    customModelInput = ""
                }
                showProviderPicker = false
            },
        )
    }

    val compactFieldShape = TextFieldDefaults.shape
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = top.wkbin.taixu.ui.components.runtimeContentPadding(horizontal = 16.dp, top = 16.dp, bottom = 32.dp + bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The preset card is one selection target, leaving the full name room to wrap.
        item {
            RuntimeProviderCard(
                providerId = provider.id, name = provider.name,
                description = if (provider.id.startsWith("plugin.")) "Installed Local Plugins" else when (provider.group) {
                    ProviderGroup.OFFICIAL -> "Official"
                    ProviderGroup.CHINA -> "CN Providers"
                    ProviderGroup.AGGREGATOR -> "Aggregators"
                    ProviderGroup.LOCAL -> "Local"
                    ProviderGroup.CUSTOM -> "自定义"
                } + " · " + if (provider.protocol == ProviderProtocol.ANTHROPIC) "Claude Messages API" else "OpenAI-Compatible API",
                onClick = { showProviderPicker = true },
            )
        }

        // ---- 2. 基础连接卡片 (Connection Info) ----
        item {
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(SettingsDensity.cardPadding),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Globe, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (provider.id.startsWith("plugin.")) "Profile Info" else "Endpoint & Connection",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // 档案名称
                    RuntimeFilledTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("档案名称") },
                        placeholder = { Text(if (selectedModels.isNotEmpty()) selectedModels.first() else provider.name) },
                        singleLine = true,
                        shape = compactFieldShape,
                        colors = fieldColors,
                        leadingIcon = {
                            RuntimeIcon(RuntimeIconName.Model, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                    )

                    // External providers use installed MCP metadata rather than a user URL.
                    if (provider.id.startsWith("plugin.")) LocalPluginProviderNotice() else RuntimeFilledTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Base URL") },
                        placeholder = {
                            Text(
                                if (provider.protocol == ProviderProtocol.ANTHROPIC) {
                                    "https://api.anthropic.com/v1 or proxy endpoint"
                                } else {
                                    "https://api.openai.com/v1"
                                }
                            )
                        },
                        isError = url.isNotBlank() && !urlValid,
                        supportingText = if (url.isNotBlank() && !urlValid) {
                            { Text("Endpoint URL must start with http:// or https://") }
                        } else null,
                        singleLine = true,
                        shape = compactFieldShape,
                        colors = fieldColors,
                        leadingIcon = {
                            RuntimeIcon(RuntimeIconName.Globe, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            RuntimeIconButton(
                                onClick = { discover(providerId, url, combinedKey) },
                                enabled = !discovering && url.isNotBlank(),
                            ) {
                                if (discovering) {
                                    RuntimeCircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    RuntimeIcon(
                                        name = RuntimeIconName.Download,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (url.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        // ---- 3. API 凭证与密钥池 ----
        if (!provider.id.startsWith("plugin.") || combinedKey.isNotBlank()) item {
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(SettingsDensity.cardPadding),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Key, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                            Text(
                                text = "API Key Pool",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = "${keyList.count { it.isNotBlank() }} configured",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // Key 列表
                    keyList.forEachIndexed { index, currentKey ->
                        val isRevealed = revealedKeyIndices.contains(index)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RuntimeFilledTextField(
                                value = currentKey,
                                onValueChange = { newVal ->
                                    keyList = keyList.toMutableList().also { it[index] = newVal }
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text("Key ${if (keyList.size > 1) "#${index + 1}" else ""}") },
                                placeholder = { Text("sk-...") },
                                singleLine = true,
                                shape = compactFieldShape,
                                colors = fieldColors,
                                visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    if (currentKey.isNotEmpty()) {
                                        RuntimeIconButton(
                                            onClick = {
                                                revealedKeyIndices = if (isRevealed) revealedKeyIndices - index else revealedKeyIndices + index
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .size(28.dp),
                                            contentDescription = if (isRevealed) "Hide Key" else "Show Key",
                                        ) {
                                            RuntimeIcon(
                                                name = if (isRevealed) RuntimeIconName.VisibilityOff else RuntimeIconName.Visibility,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                            )

                            if (keyList.size > 1) {
                                RuntimeIconButton(
                                    onClick = {
                                        keyList = keyList.toMutableList().also { it.removeAt(index) }
                                        revealedKeyIndices = revealedKeyIndices.mapNotNull {
                                            when {
                                                it < index -> it
                                                it > index -> it - 1
                                                else -> null
                                            }
                                        }.toSet()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(36.dp),
                                    contentDescription = "Remove Key",
                                ) {
                                    RuntimeIcon(
                                        name = RuntimeIconName.Close,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }

                    // 添加备用 Key / 批量导入
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RuntimeFilledTonalButton(
                            onClick = { keyList = keyList + "" },
                            modifier = Modifier.weight(1f).widthIn(min = 140.dp).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuntimeIcon(RuntimeIconName.Plus, Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                                Text("Fallback Key", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        RuntimeOutlinedButton(
                            onClick = { showBatchImportKeysDialog = true },
                            modifier = Modifier.weight(1f).widthIn(min = 140.dp).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuntimeIcon(RuntimeIconName.Download, Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                                Text("Batch Import", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    Text(
                        text = "Keys are encrypted in Android Keystore and never stored in plaintext.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- 4. 模型选择与定制 ----
        item {
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(SettingsDensity.cardPadding),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val totalSelectedCount = (selectedModels + customModelInput.split(",").map { it.trim() }).filter { it.isNotBlank() }.distinct().size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Brain, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Model Selection",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        if (totalSelectedCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = "$totalSelectedCount selected",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    if (candidateModels.isNotEmpty()) {
                        val candidateScrollState = rememberScrollState()
                        val candidateCount = candidateModels.size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Available Models (Tap to multi-select)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (candidateCount > 20) {
                                Text(
                                    text = "$candidateCount total",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        RuntimeFilledTextField(
                            value = modelSearchQuery,
                            onValueChange = { modelSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search model name or ID") },
                            singleLine = true,
                            shape = compactFieldShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            leadingIcon = {
                                RuntimeIcon(
                                    name = RuntimeIconName.Search,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingIcon = {
                                if (modelSearchQuery.isNotEmpty()) {
                                    RuntimeIconButton(
                                        onClick = { modelSearchQuery = "" },
                                        contentDescription = "Clear search",
                                    ) {
                                        RuntimeIcon(
                                            name = RuntimeIconName.Close,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            supportingText = {
                                if (modelSearchQuery.isNotBlank()) {
                                    Text("Found ${filteredCandidateModels.size} / $candidateCount models")
                                }
                            },
                        )

                        // 流式布局自动横向换行；限制最大高度，模型过多时区域内部滚动，避免撑爆单 item 导致底部黑屏
                        if (filteredCandidateModels.isEmpty()) {
                            Text(
                                text = "No matching models; clear search or enter model ID manually below",
                                modifier = Modifier.padding(vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(candidateScrollState),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                filteredCandidateModels.forEach { option ->
                                    val isSelected = selectedModels.contains(option)
                                    Surface(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        ),
                                        modifier = Modifier.clickable {
                                            selectedModels = if (isSelected) selectedModels - option else selectedModels + option
                                        },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            if (isSelected) {
                                                RuntimeIcon(
                                                    name = RuntimeIconName.Check,
                                                    modifier = Modifier.size(13.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            Text(
                                                text = option,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    fontSize = 12.sp,
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 自定义模型输入
                    RuntimeFilledTextField(
                        value = customModelInput,
                        onValueChange = { customModelInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Model ID (Optional, comma-separated)") },
                        placeholder = { Text("E.g. gpt-4.5-preview, claude-3-7-sonnet") },
                        singleLine = true,
                        shape = compactFieldShape,
                        colors = fieldColors,
                    )
                }
            }
        }

        // ---- 5. 推理与上下文调优 (Collapsible) ----
        item {
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(SettingsDensity.cardPadding),
                onClick = { reasoningSectionExpanded = !reasoningSectionExpanded },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Speed, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Inference & Context Tuning",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                "Temperature, Max Tokens, Context Window, and Reasoning Mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RuntimeIcon(
                            name = RuntimeIconName.ChevronDown,
                            modifier = Modifier.size(18.dp).rotate(if (reasoningSectionExpanded) 180f else 0f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    AnimatedVisibility(visible = reasoningSectionExpanded) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // Temperature 滑块
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Temperature", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                                        Text(
                                            text = String.format("%.2f", temperature),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                RuntimeSlider(
                                    value = temperature,
                                    onValueChange = { temperature = it },
                                    valueRange = 0f..2.0f,
                                    steps = 20,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PresetChip("0.2 Precise", temperature == 0.2f) { temperature = 0.2f }
                                    PresetChip("0.7 Balanced", temperature == 0.7f) { temperature = 0.7f }
                                    PresetChip("1.2 Creative", temperature == 1.2f) { temperature = 1.2f }
                                }
                            }

                            // Max Tokens 与 Context Tokens 双列并排
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RuntimeFilledTextField(
                                    value = maxTokensText,
                                    onValueChange = { maxTokensText = it.filter(Char::isDigit) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Max Tokens") },
                                    placeholder = { Text("8000") },
                                    singleLine = true,
                                    isError = maxTokensInvalid || tokenWindowInvalid,
                                    supportingText = if (maxTokensInvalid) {
                                        { Text("Enter a positive integer or leave empty for default") }
                                    } else if (tokenWindowInvalid) {
                                        { Text("Cannot exceed context window limit") }
                                    } else null,
                                    shape = compactFieldShape,
                                    colors = fieldColors,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                RuntimeFilledTextField(
                                    value = contextTokensText,
                                    onValueChange = { contextTokensText = it.filter(Char::isDigit) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Context Window") },
                                    placeholder = { Text(autoContextTokens?.let(::formatContextWindow) ?: "Auto if empty") },
                                    singleLine = true,
                                    isError = contextTokensInvalid || tokenWindowInvalid,
                                    supportingText = if (contextTokensInvalid) {
                                        { Text("Enter a positive integer or leave empty for auto") }
                                    } else if (tokenWindowInvalid) {
                                        { Text("不能大于当前上下文上限（自动 ${autoContextTokens?.let(::formatContextWindow) ?: "全局预算"}）") }
                                    } else if (contextTokensText.isBlank() && autoContextTokens != null) {
                                        { Text("Auto-detected if empty: ${formatContextWindow(autoContextTokens)}") }
                                    } else if (contextTokensText.isBlank()) {
                                        { Text("Uses global fallback budget if empty") }
                                    } else null,
                                    shape = compactFieldShape,
                                    colors = fieldColors,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                            }

                            // Top P 滑块
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Top P (Nucleus Sampling)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                                        Text(
                                            text = String.format("%.2f", topP),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                RuntimeSlider(
                                    value = topP,
                                    onValueChange = { topP = it },
                                    valueRange = 0f..1.0f,
                                    steps = 10,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            // 推理思考模式
                            ExposedDropdownMenuBox(
                                expanded = reasoningModeMenu,
                                onExpandedChange = { reasoningModeMenu = !reasoningModeMenu },
                            ) {
                                RuntimeFilledTextField(
                                    value = when (reasoningModeText) {
                                        "enabled" -> "Enable Extended Thinking / Deep Reasoning"
                                        else -> "跟随模型默认"
                                    },
                                    onValueChange = {},
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                    readOnly = true,
                                    label = { Text("推理思考模式") },
                                    shape = compactFieldShape,
                                    colors = fieldColors,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(reasoningModeMenu) },
                                )
                                ExposedDropdownMenu(
                                    expanded = reasoningModeMenu,
                                    onDismissRequest = { reasoningModeMenu = false },
                                ) {
                                    DropdownMenuItem(text = { Text("跟随模型默认") }, onClick = {
                                        reasoningModeText = "auto"
                                        reasoningModeMenu = false
                                    })
                                    DropdownMenuItem(text = { Text("Enable Extended Thinking / Deep Reasoning") }, onClick = {
                                        reasoningModeText = "enabled"
                                        reasoningModeMenu = false
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- 6. 高级特性与网络配置 (Collapsible) ----
        item {
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(SettingsDensity.cardPadding),
                onClick = { advancedSectionExpanded = !advancedSectionExpanded },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Shield, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Advanced Capabilities & Network",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                "Tool Call, Vision, Responses API, RPM Limit, and Custom Headers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RuntimeIcon(
                            name = RuntimeIconName.ChevronDown,
                            modifier = Modifier.size(18.dp).rotate(if (advancedSectionExpanded) 180f else 0f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    AnimatedVisibility(visible = advancedSectionExpanded) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 单 Key 每分钟上限
                            RuntimeFilledTextField(
                                value = rpmLimitText,
                                onValueChange = { rpmLimitText = it },
                                isError = !rpmValid,
                                supportingText = { if (!rpmValid) Text(stringResource(R.string.model_integer_nonnegative)) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Per-Key Rate Limit (RPM)") },
                                placeholder = { Text("0 for unlimited") },
                                singleLine = true,
                                shape = compactFieldShape,
                                colors = fieldColors,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )

                            // 每模型压缩预算覆盖（对齐 pi compaction.modelOverrides）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RuntimeFilledTextField(
                                    value = compactionKeepRecentText,
                                    onValueChange = { compactionKeepRecentText = it },
                                    isError = !keepRecentValid,
                                    supportingText = { if (!keepRecentValid) Text(stringResource(R.string.model_integer_positive)) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Compaction Retain Tokens") },
                                    placeholder = { Text("20000") },
                                    singleLine = true,
                                    shape = compactFieldShape,
                                    colors = fieldColors,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                RuntimeFilledTextField(
                                    value = compactionReserveText,
                                    onValueChange = { compactionReserveText = it },
                                    isError = !reserveValid,
                                    supportingText = { if (!reserveValid) Text(stringResource(R.string.model_integer_positive)) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Response Reserve Tokens") },
                                    placeholder = { Text("16384") },
                                    singleLine = true,
                                    shape = compactFieldShape,
                                    colors = fieldColors,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                            }
                            Text(
                                "Overrides compaction token retention and response reserve for this model; leave empty for global default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // 特性开关列表
                            EditorToggleRow(
                                title = "支持函数调用 (Tool Call)",
                                subtitle = "Enable sandbox command and tool-calling loop",
                                checked = toolCallEnabled,
                                onCheckedChange = { toolCallEnabled = it },
                            )

                            EditorToggleRow(
                                title = "Multimodal Vision",
                                subtitle = "Send images directly to the model",
                                checked = visionEnabled,
                                onCheckedChange = { visionEnabled = it },
                            )

                            EditorToggleRow(
                                title = "Image Generation",
                                subtitle = "Allow model responses to return generated images",
                                checked = imageGenerationEnabled,
                                onCheckedChange = { imageGenerationEnabled = it },
                            )

                            EditorToggleRow(
                                title = "Use Responses API",
                                subtitle = "Use /v1/responses instead of chat/completions",
                                checked = responseApiEnabled,
                                onCheckedChange = { responseApiEnabled = it },
                            )

                            EditorToggleRow(
                                title = "Claude Prompt Caching",
                                subtitle = "Inject Anthropic prompt cache breakpoints to save up to 90% input tokens",
                                checked = promptCachingEnabled,
                                onCheckedChange = { promptCachingEnabled = it },
                            )

                            EditorToggleRow(
                                title = "Prompt Cache 1-Hour TTL",
                                subtitle = "Extend cache lifetime to 1 hour (when supported by provider)",
                                checked = promptCacheTtl1h,
                                onCheckedChange = { promptCacheTtl1h = it },
                            )

                            EditorToggleRow(
                                title = "Raw Diagnostic Mode",
                                subtitle = "Omit system prompt and tool definitions",
                                checked = pureChatMode,
                                onCheckedChange = { pureChatMode = it },
                            )

                            // 自定义请求头
                            RuntimeFilledTextField(
                                value = customHeaders,
                                onValueChange = { customHeaders = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Custom HTTP Headers (Multi-line Key: Value)") },
                                placeholder = { Text("X-Custom-Header: value\nHTTP-Referer: https://taixu.app") },
                                minLines = 2,
                                maxLines = 4,
                                shape = compactFieldShape,
                                colors = fieldColors,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }
            }
        }

        // ---- 7. 连通性测试与错误提示 ----
        item {
            val testModelTarget = selectedModels.firstOrNull() ?: customModelInput.trim()

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeOutlinedButton(
                    onClick = { test(url, testModelTarget, combinedKey, responseApiEnabled, providerId) },
                    enabled = !testing && url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    if (testing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RuntimeCircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Testing…")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RuntimeIcon(RuntimeIconName.Play, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                            Text("测试连接")
                        }
                    }
                }

                discoveryError?.let {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Alert, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                testResult?.let { resultMsg ->
                    val isSuccess = resultMsg == "连接成功"
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSuccess) successStatusColor().copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RuntimeIcon(
                                name = if (isSuccess) RuntimeIconName.Check else RuntimeIconName.Alert,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSuccess) successStatusColor() else MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = resultMsg,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSuccess) successStatusColor() else MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // ---- 8. 底部保存按钮 ----
        item {
            val effectiveModels = remember(selectedModels, customModelInput) {
                val customList = customModelInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                (selectedModels + customList).filter { it.isNotBlank() }.distinct()
            }
            val parsedCompactionKeepRecent = compactionKeepRecentText.trim().toIntOrNull()?.takeIf { it > 0 }
            val parsedCompactionReserve = compactionReserveText.trim().toIntOrNull()?.takeIf { it > 0 }
            val parsedRpmLimit = rpmLimitText.trim().toIntOrNull() ?: 0

            val buttonText = if (effectiveModels.size > 1) {
                "Save ${effectiveModels.size} Selected Models"
            } else {
                "Save Model Profile"
            }

            RuntimeButton(
                onClick = {
                    save(
                        name.trim(),
                        provider.name,
                        effectiveModels,
                        url.trim(),
                        combinedKey,
                        parsedRpmLimit,
                        temperature,
                        parsedMaxTokens,
                        topP,
                        reasoningModeText.takeIf { it != "auto" },
                        reasoningEffortText.ifBlank { null },
                        if (toolCallEnabled) "native" else "disabled",
                        parsedContextTokens,
                        parsedCompactionKeepRecent,
                        parsedCompactionReserve,
                        customHeaders,
                        pureChatMode,
                        visionEnabled,
                        imageGenerationEnabled,
                        responseApiEnabled,
                        promptCachingEnabled,
                        promptCacheTtl1h,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = effectiveModels.isNotEmpty() && urlValid && tokenFieldsValid && rpmValid && keepRecentValid && reserveValid,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.Check, Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary)
                    Text(buttonText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatContextWindow(tokens: Int): String = when {
    tokens <= 0 -> "0"
    tokens % 1_000_000 == 0 -> "${tokens / 1_000_000}M"
    tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
    tokens % 1_000 == 0 -> "${tokens / 1_000}K"
    else -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
}

internal fun filterCandidateModels(models: List<String>, query: String): List<String> {
    val normalizedQuery = query.trim()
    return if (normalizedQuery.isEmpty()) {
        models
    } else {
        models.filter { it.contains(normalizedQuery, ignoreCase = true) }
    }
}
@Composable
private fun BatchImportKeysDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val parsedCount = remember(text) {
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().count()
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Key, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                Text("Batch Import API Keys", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Paste multiple API keys, one per line:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    placeholder = { Text("sk-xxxxxxxxxxxxxxxx\nsk-yyyyyyyyyyyyyyyy\nsk-zzzzzzzzzzzzzzzz") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(10.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RuntimeOutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            if (clipText.isNotBlank()) {
                                text = clipText
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text("Paste from Clipboard", style = MaterialTheme.typography.labelMedium)
                    }
                    if (parsedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = "$parsedCount keys detected",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            RuntimeButton(
                onClick = { onConfirm(text) },
                enabled = parsedCount > 0,
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            RuntimeTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun QuickImportJsonDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Code, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                Text("Fill Model Profile from JSON", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Paste a TaiXu model export or OpenAI-compatible JSON to populate the form:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    placeholder = { Text("{\n  \"name\": \"DeepSeek-V4\",\n  \"provider\": \"DeepSeek\",\n  \"baseUrl\": \"https://api.deepseek.com/v1\",\n  \"model\": \"deepseek-v4-flash\"\n}") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(10.dp),
                )
                RuntimeOutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (clipText.isNotBlank()) {
                            text = clipText
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Paste from Clipboard")
                }
            }
        },
        confirmButton = {
            RuntimeButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text("Parse & Fill")
            }
        },
        dismissButton = {
            RuntimeTextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ProviderPickerDialog(
    providers: List<AgentProviderDefinition>,
    currentProviderId: String,
    onDismiss: () -> Unit,
    onSelect: (AgentProviderDefinition) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf<ProviderGroup?>(null) }

    val filteredProviders = remember(providers, searchQuery, selectedGroup) {
        providers.filter { item ->
            val matchesGroup = selectedGroup == null || item.group == selectedGroup
            val matchesSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.id.contains(searchQuery, ignoreCase = true) ||
                item.baseUrl.contains(searchQuery, ignoreCase = true)
            matchesGroup && matchesSearch
        }
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Sparkles, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                Text("Select Provider Preset", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search provider name or endpoint...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        RuntimeIcon(RuntimeIconName.Search, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            RuntimeIconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp),
                            ) {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )

                // 分类 Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderFilterChip(
                        label = "All",
                        selected = selectedGroup == null,
                        onClick = { selectedGroup = null },
                    )
                    ProviderFilterChip(
                        label = "Official",
                        selected = selectedGroup == ProviderGroup.OFFICIAL,
                        onClick = { selectedGroup = ProviderGroup.OFFICIAL },
                    )
                    ProviderFilterChip(
                        label = "CN Providers",
                        selected = selectedGroup == ProviderGroup.CHINA,
                        onClick = { selectedGroup = ProviderGroup.CHINA },
                    )
                    ProviderFilterChip(
                        label = "Aggregators",
                        selected = selectedGroup == ProviderGroup.AGGREGATOR,
                        onClick = { selectedGroup = ProviderGroup.AGGREGATOR },
                    )
                    ProviderFilterChip(
                        label = "Local",
                        selected = selectedGroup == ProviderGroup.LOCAL,
                        onClick = { selectedGroup = ProviderGroup.LOCAL },
                    )
                    ProviderFilterChip(
                        label = "自定义",
                        selected = selectedGroup == ProviderGroup.CUSTOM,
                        onClick = { selectedGroup = ProviderGroup.CUSTOM },
                    )
                }

                // 列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (filteredProviders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No matching providers found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(filteredProviders, key = { it.id }) { option ->
                            val isSelected = option.id == currentProviderId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                },
                                border = if (isSelected) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(option)
                                    },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ProviderBadge(providerIdOrName = option.id, size = 22.dp)
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = option.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                            ) {
                                                Text(
                                                    text = when (option.group) {
                                                        ProviderGroup.OFFICIAL -> "Official"
                                                        ProviderGroup.CHINA -> "CN Providers"
                                                        ProviderGroup.AGGREGATOR -> "Aggregators"
                                                        ProviderGroup.LOCAL -> "Local"
                                                        ProviderGroup.CUSTOM -> "自定义"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            text = option.baseUrl.ifBlank { "Requires custom Base URL" },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    if (isSelected) {
                                        RuntimeIcon(
                                            name = RuntimeIconName.Check,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            RuntimeTextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun ProviderFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
            ),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
