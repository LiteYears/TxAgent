package top.wkbin.taixu.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import top.wkbin.taixu.core.datastore.RemoteServerRepository
import top.wkbin.taixu.runtime.remote.RemoteSystemService
import top.wkbin.taixu.runtime.remote.RemoteSystemInspection
import top.wkbin.taixu.ui.components.*
import java.util.Locale

@Composable
fun HomeScreen(onNavigate: (MainDestination) -> Unit, onOpenTerminal: () -> Unit,
    onOpenToolCenter: () -> Unit = {}, viewModel: HomeViewModel? = null, onBack: () -> Unit = { onNavigate(MainDestination.Agent) }) {
    val repository = koinInject<RemoteServerRepository>()
    val active by repository.activeId.collectAsStateWithLifecycle()
    val servers by repository.servers.collectAsStateWithLifecycle()
    if (active == null) LocalHomeScreen(onNavigate, onOpenTerminal, onOpenToolCenter, viewModel ?: org.koin.compose.viewmodel.koinViewModel(), onBack)
    else key(active, servers.firstOrNull { it.id == active }) { RemoteHomeScreen(checkNotNull(active), onNavigate, onBack = onBack) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteHomeScreen(serverId: String, onNavigate: (MainDestination) -> Unit, service: RemoteSystemService? = previewOrNull { koinInject() }, onBack: () -> Unit = { onNavigate(MainDestination.Agent) }) {
    var refresh by remember { mutableIntStateOf(0) }
    val target = service?.let { runCatching { it.server(serverId) }.getOrNull() }
    val state by remember(serverId, refresh, service) {
        service?.observe(serverId, target) ?: kotlinx.coroutines.flow.flowOf(RemoteSystemInspection(loading = false))
    }.collectAsStateWithLifecycle(RemoteSystemInspection(loading = service != null))
    val metrics = state.metrics
    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RuntimeTopBar(title = androidx.compose.ui.res.stringResource(top.wkbin.taixu.feature.home.R.string.home_dashboard_title), onBack = onBack, scrollBehavior = scrollBehavior, actions = {
            RuntimeIconButton(onClick = { refresh++ }, contentDescription = "Refresh") { RuntimeIcon(RuntimeIconName.Refresh) }
        }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().then(top.wkbin.taixu.ui.components.runtimeScrollViewport(padding)).verticalScroll(rememberScrollState()).padding(bottom = padding.calculateBottomPadding()).padding(runtimeContentPadding(horizontal = 16.dp, top = 16.dp, bottom = 16.dp))
            .padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Main System Overview", style = MaterialTheme.typography.titleMedium)
            SystemOverviewCard(target?.name ?: "Server", target?.let { "${it.username}@${it.host}:${it.port}" }.orEmpty(),
                target?.workspace.orEmpty(), state.loading, state.error, metrics?.let { listOf(
                    "Distribution" to it.distribution, "Architecture" to it.architecture,
                    "Memory" to "${it.memoryUsedMb} / ${it.memoryTotalMb} MB",
                    "Storage" to String.format(Locale.ROOT, "%.1f / %.1f GB", it.diskUsedGb, it.diskTotalGb),
                    "Load (1/5/15 min)" to it.load, "Processes" to it.processCount.toString(),
                    "Uptime" to "${it.uptimeSeconds / 3600} hours", "Kernel" to it.kernel,
                ) }.orEmpty())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RuntimeButton(modifier = Modifier.weight(1f), onClick = { onNavigate(MainDestination.Agent) }) { Text("Open Mind") }
                RuntimeFilledTonalButton(modifier = Modifier.weight(1f), onClick = { onNavigate(MainDestination.Workspace) }) { Text("Open Workshop") }
            }
            Text("Metrics collected from active server, refreshed every 15s. Workspace matches active server.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
