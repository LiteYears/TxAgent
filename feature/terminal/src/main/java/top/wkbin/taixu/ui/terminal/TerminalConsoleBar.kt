package top.wkbin.taixu.ui.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.wkbin.taixu.feature.terminal.R
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.SpotlightAnchor
import top.wkbin.taixu.ui.components.spotlightAnchor

private val ConsoleBarBg = Color(0xFF121721)
private val ConsoleCardBg = Color(0xFF1A212E)
private val ConsoleBorder = Color(0xFF2B3548)
private val ConsoleTextPrimary = Color(0xFFE6EDF3)
private val ConsoleTextSecondary = Color(0xFF8B949E)
private val ConsoleAccentCyan = Color(0xFF58A6FF)
private val ConsoleStatusGreen = Color(0xFF3FB950)
private val ConsoleStatusAmber = Color(0xFFD29922)

/**
 * Terminal top console bar: dedicated dark terminal chrome with generous padding,
 * active session status badge, segmented font zoom pill, and session switcher.
 */
@Composable
internal fun TerminalConsoleBar(
    onBack: (() -> Unit)?,
    onOpenNavigation: (() -> Unit)?,
    onOpenSessions: () -> Unit,
    sessionsAnchor: SpotlightAnchor?,
    modifier: Modifier = Modifier,
    sessionTitle: String? = null,
    distroLabel: String? = null,
    sessionIndex: String? = null,
    isAlive: Boolean = true,
    fontSizeSp: Int = 13,
    onZoomIn: (() -> Unit)? = null,
    onZoomOut: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().background(ConsoleBarBg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                onBack != null -> ConsoleKeyButton(
                    icon = RuntimeIconName.Back,
                    contentDescription = stringResource(R.string.terminal_back),
                    onClick = onBack,
                )
                onOpenNavigation != null -> ConsoleKeyButton(
                    icon = RuntimeIconName.Menu,
                    contentDescription = stringResource(R.string.terminal_open_navigation),
                    onClick = onOpenNavigation,
                )
            }

            // Active Session Terminal Tab Badge
            Surface(
                onClick = onOpenSessions,
                shape = RoundedCornerShape(8.dp),
                color = ConsoleCardBg,
                border = BorderStroke(1.dp, ConsoleBorder),
                modifier = Modifier
                    .height(36.dp)
                    .weight(1f, fill = false),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isAlive) ConsoleStatusGreen else ConsoleStatusAmber, CircleShape),
                    )

                    Text(
                        text = sessionTitle ?: stringResource(R.string.terminal_quick_main),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        ),
                        color = ConsoleTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (!distroLabel.isNullOrBlank()) {
                        Text(
                            text = distroLabel.lowercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = ConsoleTextSecondary,
                            maxLines = 1,
                        )
                    }

                    if (!sessionIndex.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ConsoleAccentCyan.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = sessionIndex,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = ConsoleAccentCyan,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Segmented Font Zoom Pill (- 13sp +)
            if (onZoomOut != null && onZoomIn != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ConsoleCardBg,
                    border = BorderStroke(1.dp, ConsoleBorder),
                    modifier = Modifier.height(36.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    ) {
                        ZoomStepButton(
                            label = "−",
                            description = stringResource(R.string.terminal_font_zoom_out),
                            onClick = onZoomOut,
                        )
                        Text(
                            text = "${fontSizeSp}sp",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                            ),
                            color = ConsoleTextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        ZoomStepButton(
                            label = "+",
                            description = stringResource(R.string.terminal_font_zoom_in),
                            onClick = onZoomIn,
                        )
                    }
                }
            }

            ConsoleKeyButton(
                icon = RuntimeIconName.Plus,
                contentDescription = stringResource(R.string.terminal_sessions),
                onClick = onOpenSessions,
                tint = ConsoleAccentCyan,
                modifier = sessionsAnchor?.let { Modifier.spotlightAnchor(it) } ?: Modifier,
            )
        }
        HorizontalDivider(color = ConsoleBorder.copy(alpha = 0.8f))
    }
}

@Composable
private fun ZoomStepButton(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
            color = ConsoleTextPrimary,
        )
    }
}

@Composable
private fun ConsoleKeyButton(
    icon: RuntimeIconName,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ConsoleTextPrimary,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = ConsoleCardBg,
        border = BorderStroke(1.dp, ConsoleBorder),
        modifier = modifier
            .size(36.dp)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            RuntimeIcon(icon, Modifier.size(18.dp), tint)
        }
    }
}
