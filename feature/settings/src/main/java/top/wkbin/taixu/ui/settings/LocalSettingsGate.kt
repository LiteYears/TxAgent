package top.wkbin.taixu.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.taixu.core.datastore.RemoteServerRepository
import top.wkbin.taixu.ui.components.*

/** Gate before constructing local ViewModels, including navigation/search/deep-link entry points. */
@Composable
internal fun LocalSettingsGate(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val repository = koinInject<RemoteServerRepository>()
    val active by repository.activeId.collectAsStateWithLifecycle()
    if (active == null) content()
    else Scaffold(topBar = { RuntimeTopBar(title = title, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NoticeBanner(text = "This feature manages on-device Linux and plugins. Switch back to a local Linux system to use it.")
            Text("Remote server toolchains can be managed via terminal or Agent commands.", style = MaterialTheme.typography.bodyMedium)
            RuntimeTextButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
fun LocalLlmScreen(onBack: () -> Unit, onOpenEngine: () -> Unit, viewModel: LocalLlmViewModel? = null) =
    LocalSettingsGate("本地 LLM", onBack) { LocalLlmContent(onBack, onOpenEngine, viewModel ?: koinViewModel()) }

@Composable
fun ToolDetailScreen(toolId: String, onBack: () -> Unit, onLaunchTerminal: (String) -> Unit,
    onStartAiHealing: (String, String, List<String>) -> Unit = { _, _, _ -> },
    onOpenPluginPage: (String) -> Unit = {}, viewModel: ToolDetailViewModel? = null) =
    LocalSettingsGate("Local Plugins", onBack) { LocalToolDetailContent(toolId, onBack, onLaunchTerminal, onStartAiHealing, onOpenPluginPage, viewModel ?: koinViewModel()) }

@Composable
fun CcSwitchScreen(onBack: () -> Unit, onLaunchTerminal: (String) -> Unit, onOpenBrowser: (String) -> Unit, viewModel: CcSwitchViewModel? = null) =
    LocalSettingsGate("Local Agent Hub", onBack) { LocalCcSwitchContent(onBack, onLaunchTerminal, onOpenBrowser, viewModel ?: koinViewModel()) }

@Composable
fun ToolCenterScreen(onBack: () -> Unit, onLaunchPty: (String) -> Unit, onOpenToolDetail: (String) -> Unit = {},
    onStartAiHealing: (String, String, List<String>) -> Unit = { _, _, _ -> }, viewModel: ToolCenterViewModel? = null) =
    LocalSettingsGate("Plugin & Toolchain Center", onBack) { LocalToolCenterContent(onBack, onLaunchPty, onOpenToolDetail, onStartAiHealing, viewModel ?: koinViewModel()) }
