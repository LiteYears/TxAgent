package top.wkbin.taixu.ui.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val KeyDockBg = Color(0xFF121721)
private val KeyDockBorder = Color(0xFF2B3548)
private val KeycapStandardBg = Color(0xFF1A212E)
private val KeycapControlBg = Color(0xFF20293A)
private val KeycapActiveBg = Color(0xFF0E2942)
private val KeycapStandardBorder = Color(0xFF2C374B)
private val KeycapActiveBorder = Color(0xFF58A6FF)
private val KeycapTextPrimary = Color(0xFFE6EDF3)
private val KeycapTextControl = Color(0xFF9ECBFF)
private val KeycapTextInterrupt = Color(0xFFFF7B72)
private val KeycapTextActive = Color(0xFF79C0FF)

/**
 * Two-row hardware-style terminal extra keys dock with authentic dark terminal palette
 * and generous tactile spacing.
 */
@Composable
internal fun TerminalExtraKeys(bridge: TaiXuTerminalBridge, hapticsEnabled: Boolean) {
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KeyDockBg),
    ) {
        HorizontalDivider(color = KeyDockBorder.copy(alpha = 0.8f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row 1: ESC TAB / - ~ HOME ▲ END
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ExtraKey("ESC", Modifier.weight(1f), isControl = true, hapticsEnabled = hapticsEnabled) {
                    bridge.sendEscape()
                }
                ExtraKey("TAB", Modifier.weight(1f), isControl = true, hapticsEnabled = hapticsEnabled) {
                    bridge.sendTab()
                }
                ExtraKey("/", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                    bridge.sendString("/")
                }
                ExtraKey("-", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                    bridge.sendString("-")
                }
                ExtraKey("~", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                    bridge.sendString("~")
                }
                ExtraKey("HOME", Modifier.weight(1f), isControl = true, hapticsEnabled = hapticsEnabled) {
                    bridge.sendHome()
                }
                ExtraKey("▲", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                    bridge.sendArrowUp()
                }
                ExtraKey("END", Modifier.weight(1f), isControl = true, hapticsEnabled = hapticsEnabled) {
                    bridge.sendEnd()
                }
            }
            // Row 2: CTRL ALT | ^C ^D ◀ ▼ ▶
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ExtraKey(
                    "CTRL",
                    Modifier.weight(1f),
                    isActive = ctrlActive,
                    isControl = true,
                    hapticsEnabled = hapticsEnabled,
                ) {
                    bridge.toggleControlKey()
                    ctrlActive = bridge.isControlKeyActive()
                }
                ExtraKey(
                    "ALT",
                    Modifier.weight(1f),
                    isActive = altActive,
                    isControl = true,
                    hapticsEnabled = hapticsEnabled,
                ) {
                    bridge.toggleAltKey()
                    altActive = bridge.isAltKeyActive()
                }
                ExtraKey("|", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                    bridge.sendString("|")
                }
                ExtraKey("^C", Modifier.weight(1f), isInterrupt = true, hapticsEnabled = hapticsEnabled) {
                    bridge.sendSigInt()
                }
                ExtraKey("^D", Modifier.weight(1f), isInterrupt = true, hapticsEnabled = hapticsEnabled) {
                    bridge.sendEof()
                }
                ExtraKey("◀", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                    bridge.sendArrowLeft()
                }
                ExtraKey("▼", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                    bridge.sendArrowDown()
                }
                ExtraKey("▶", Modifier.weight(1f), hapticsEnabled = hapticsEnabled) {
                    bridge.sendArrowRight()
                }
            }
        }
    }
}

@Composable
private fun ExtraKey(
    label: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isControl: Boolean = false,
    isInterrupt: Boolean = false,
    hapticsEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val bgColor = when {
        isActive -> KeycapActiveBg
        isControl || isInterrupt -> KeycapControlBg
        else -> KeycapStandardBg
    }
    val borderColor = if (isActive) KeycapActiveBorder else KeycapStandardBorder
    val textColor = when {
        isActive -> KeycapTextActive
        isInterrupt -> KeycapTextInterrupt
        isControl -> KeycapTextControl
        else -> KeycapTextPrimary
    }

    Surface(
        onClick = {
            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.then(
            if (label == "CTRL" || label == "ALT") Modifier.semantics { selected = isActive } else Modifier,
        ),
        shape = RoundedCornerShape(7.dp),
        color = bgColor,
        border = BorderStroke(if (isActive) 1.2.dp else 0.8.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = if (label.length >= 4) 11.5.sp else 13.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isActive || isControl || isInterrupt) FontWeight.Bold else FontWeight.SemiBold,
                ),
                color = textColor,
                maxLines = 1,
            )
        }
    }
}
