package top.wkbin.taixu.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.harness.TaskDelivery
import top.wkbin.taixu.harness.FileArtifactOrigin
import top.wkbin.taixu.harness.task.DeliveryArtifactKind
import java.text.DateFormat
import java.util.Date
import top.wkbin.taixu.harness.task.DeliveryGap
import top.wkbin.taixu.harness.task.ObservationState
import top.wkbin.taixu.ui.chat.artifact.ArtifactCard
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeTextButton

/** A transcript-bound delivery record, with observed execution separated from goal verification. */
@Composable
internal fun TaskDeliveryCard(
    message: TaskDelivery,
    workspace: String = "",
    onOpenFile: ((projectName: String, relativePath: String) -> Unit)? = null,
) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var showAll by rememberSaveable(message.id) { mutableStateOf(false) }
    val report = message.report
    RuntimeCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header summary row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(
                        stringResource(R.string.delivery_title),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.delivery_counts, report.observations.size, report.artifacts.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                RuntimeTextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        stringResource(if (expanded) R.string.delivery_collapse else R.string.delivery_expand),
                        maxLines = 1
                    )
                }
            }

            // Universal Artifact Download Section:
            // Displayed whenever artifacts are produced by the task
            if (report.artifacts.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.delivery_artifacts_section),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    report.artifacts.forEach { artifact ->
                        ArtifactCard(
                            artifact = artifact,
                            workspace = workspace,
                            onOpenFile = onOpenFile,
                        )
                    }
                }
            }

            // Full Execution Audit (collapsible)
            if (expanded) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                Text(
                    stringResource(R.string.delivery_unverified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                report.gaps.filter { it != DeliveryGap.GOAL_NOT_VERIFIED }.forEach { gap ->
                    Text(stringResource(gapLabel(gap)), style = MaterialTheme.typography.bodySmall)
                }
                val observations = if (showAll) report.observations else report.observations.take(8)
                observations.forEach { observation ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            observation.toolName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(stringResource(stateLabel(observation.state)), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                if (report.observations.size > 8 && !showAll) {
                    RuntimeTextButton(onClick = { showAll = true }) {
                        Text(stringResource(R.string.delivery_show_all), maxLines = 1)
                    }
                }

                // Cryptographic hash audit
                if (report.artifacts.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.delivery_hash_at_creation),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    report.artifacts.forEach { artifact ->
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        artifact.path,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        stringResource(kindLabel(artifact.kind)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                artifact.observedAt?.let {
                                    Text(
                                        stringResource(
                                            R.string.delivery_observed_at,
                                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    artifact.sha256,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.delivery_artifact_version),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun kindLabel(kind: DeliveryArtifactKind): Int = when (kind) {
    DeliveryArtifactKind.OUTPUT_LOG -> R.string.delivery_kind_log
    DeliveryArtifactKind.APK -> R.string.delivery_kind_apk
    DeliveryArtifactKind.IMAGE -> R.string.delivery_kind_image
    DeliveryArtifactKind.FILE -> R.string.delivery_kind_file
}
private fun originLabel(origin: FileArtifactOrigin): Int = when (origin) {
    FileArtifactOrigin.NEW_AFTER_COMMAND -> R.string.delivery_origin_new
    FileArtifactOrigin.CHANGED_AFTER_COMMAND -> R.string.delivery_origin_changed
    FileArtifactOrigin.UNCHANGED_AFTER_COMMAND -> R.string.delivery_origin_unchanged
    FileArtifactOrigin.OBSERVED_AFTER_COMMAND -> R.string.delivery_origin_observed
    FileArtifactOrigin.AFTER_DOWNLOAD -> R.string.delivery_origin_download
    FileArtifactOrigin.IMAGE_READ -> R.string.delivery_origin_image_read
}

private fun stateLabel(state: ObservationState): Int = when (state) {
    ObservationState.SUCCEEDED -> R.string.delivery_tool_succeeded
    ObservationState.FAILED -> R.string.delivery_tool_failed
    ObservationState.OUTCOME_UNKNOWN -> R.string.delivery_tool_unknown
    ObservationState.WAITING_APPROVAL -> R.string.delivery_tool_waiting
    ObservationState.UNSETTLED -> R.string.delivery_tool_unsettled
}
private fun gapLabel(gap: DeliveryGap): Int = when (gap) {
    DeliveryGap.GOAL_NOT_VERIFIED -> R.string.delivery_unverified
    DeliveryGap.NO_TOOL_EVIDENCE -> R.string.delivery_no_evidence
    DeliveryGap.HISTORY_INCOMPLETE -> R.string.delivery_history_incomplete
    DeliveryGap.UNKNOWN_OUTCOME -> R.string.delivery_unknown_outcome
    DeliveryGap.PENDING_RESULT -> R.string.delivery_pending_result
    DeliveryGap.FAILED_TOOL -> R.string.delivery_failed_tool
    DeliveryGap.INCOMPLETE_ARTIFACT -> R.string.delivery_incomplete_artifact
    DeliveryGap.INVALID_ARTIFACT -> R.string.delivery_invalid_artifact
}
