package top.wkbin.taixu.ui.iteration
import top.wkbin.taixu.ui.components.previewOrNull
import top.wkbin.taixu.ui.components.previewStateOf
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.feature.custom_iteration.R
import top.wkbin.taixu.iteration.engine.TaiXuDevBuildCoordinator
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeOutlinedTextField
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.SectionHeader
@Composable
fun CustomIterationScreen(
    onBack: () -> Unit,
    onNavigateToChat: (prefillPrompt: String) -> Unit,
    viewModel: CustomIterationViewModel? = previewOrNull { koinViewModel() }
) {
    val uiState by viewModel?.uiState?.collectAsStateWithLifecycle() ?: previewStateOf(CustomIterationViewModel.UiState())
    val buildState by viewModel?.buildState?.collectAsStateWithLifecycle() ?: previewStateOf(top.wkbin.taixu.iteration.engine.TaiXuDevBuildCoordinator.BuildStatus.Idle)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar(title = "Custom Iteration (Experimental)", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(top.wkbin.taixu.ui.components.runtimeScrollViewport(innerPadding))
                .verticalScroll(rememberScrollState()).padding(bottom = innerPadding.calculateBottomPadding())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero card
            RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Fully customize your own TaiXuDev",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "TaiXu is an open-source infrastructure dedicated to building a mobile Linux runtime and autonomous agents. With Custom Iteration, you can use TaiXu AI on your phone to modify TaiXu itself, automatically build an independent TaiXuDev APK via GitHub Actions, and collaborate with community developers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }

            // Feature list
            SectionHeader(title = "Core Capabilities", subtitle = "Prepare once, continuously collaborate")

            IterationFeatureItem(
                title = "Isolated Iteration Workspace",
                description = "Independent path ~/custom_taixu, completely isolated from host environment.",
                badgeColor = MaterialTheme.colorScheme.primary
            )

            IterationFeatureItem(
                title = "Dedicated Agent Skill Specification",
                description = "Built-in taixu-custom-iteration Skill, strictly adhering to multi-module, Compose, and security rules.",
                badgeColor = MaterialTheme.colorScheme.secondary
            )

            IterationFeatureItem(
                title = "GitHub Actions Cloud CI Build",
                description = "Eliminates tedious local compilation and automatically generates standalone TaiXuDev (top.wkbin.taixu.dev) APK.",
                badgeColor = MaterialTheme.colorScheme.tertiary
            )

            IterationFeatureItem(
                title = "Dual Package Coexistence & Open Source PRs",
                description = "Dev and release packages coexist seamlessly; submit standard PRs to community once verified.",
                badgeColor = MaterialTheme.colorScheme.primary
            )

            // Error notice banner
            if (uiState.errorMessage != null) {
                NoticeBanner(
                    text = uiState.errorMessage ?: "",
                    isError = true,
                    onDismiss = { viewModel?.dismissError() }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Launch button
            RuntimeButton(
                onClick = {
                    viewModel?.startCustomIteration { prompt ->
                        onNavigateToChat(prompt)
                    }
                },
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (uiState.isBusy) {
                    RuntimeCircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Preparing iteration workspace…")
                } else {
                    Text(
                        text = "Start Custom Iteration",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Cloud build section: trigger taixudev-build.yml and track status
            SectionHeader(
                title = stringResource(R.string.iteration_build_section_title),
                subtitle = stringResource(R.string.iteration_build_section_subtitle),
            )

            var branchText by remember { mutableStateOf("main") }

            RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RuntimeOutlinedTextField(
                        value = branchText,
                        onValueChange = { branchText = it },
                        label = { Text(stringResource(R.string.iteration_build_branch_label)) },
                        singleLine = true,
                        enabled = !isBuildPipelineActive(buildState),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = describeBuildStatus(buildState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    if (buildState is TaiXuDevBuildCoordinator.BuildStatus.Failed) {
                        NoticeBanner(
                            text = (buildState as TaiXuDevBuildCoordinator.BuildStatus.Failed).error,
                            isError = true,
                            onDismiss = { viewModel?.dismissBuildStatus() }
                        )
                    }

                    RuntimeButton(
                        onClick = { viewModel?.startCloudBuild(branchText) },
                        enabled = !isBuildPipelineActive(buildState),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isBuildPipelineActive(buildState)) {
                            RuntimeCircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.iteration_build_in_progress))
                        } else {
                            Text(
                                text = stringResource(R.string.iteration_build_trigger),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Whether the build pipeline is active (prohibits duplicate triggers). */
private fun isBuildPipelineActive(state: TaiXuDevBuildCoordinator.BuildStatus): Boolean =
    when (state) {
        is TaiXuDevBuildCoordinator.BuildStatus.Dispatching,
        is TaiXuDevBuildCoordinator.BuildStatus.Running,
        is TaiXuDevBuildCoordinator.BuildStatus.Downloading,
        -> true
        else -> false
    }

/** Renders the build status machine to a user-readable single-line description. */
@Composable
private fun describeBuildStatus(state: TaiXuDevBuildCoordinator.BuildStatus): String = when (state) {
    TaiXuDevBuildCoordinator.BuildStatus.Idle -> stringResource(R.string.iteration_build_idle)
    is TaiXuDevBuildCoordinator.BuildStatus.Dispatching ->
        stringResource(R.string.iteration_build_dispatching, state.branch)
    is TaiXuDevBuildCoordinator.BuildStatus.Running ->
        stringResource(R.string.iteration_build_running, state.runId.toString(), state.statusText)
    is TaiXuDevBuildCoordinator.BuildStatus.Downloading -> stringResource(R.string.iteration_build_downloading)
    is TaiXuDevBuildCoordinator.BuildStatus.Success ->
        stringResource(R.string.iteration_build_success, state.apkPath, state.sha256.take(16))
    is TaiXuDevBuildCoordinator.BuildStatus.Failed ->
        stringResource(R.string.iteration_build_failed, state.error)
}

@Composable
private fun IterationFeatureItem(
    title: String,
    description: String,
    badgeColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
