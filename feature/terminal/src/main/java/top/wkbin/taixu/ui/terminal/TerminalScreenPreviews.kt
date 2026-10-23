package top.wkbin.taixu.ui.terminal

import androidx.compose.runtime.Composable
import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import top.wkbin.taixu.ui.components.FoldablePreviews
import top.wkbin.taixu.ui.components.RuntimePreview

@FoldablePreviews
@Preview(name = "Narrow · Large Font", widthDp = 360, heightDp = 800, fontScale = 1.3f, locale = "en")
@Preview(name = "Dark · English", widthDp = 412, heightDp = 892, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "en")
@Composable
private fun TerminalScreenPreview() {
    RuntimePreview {
        TerminalScreen(onBack = { })
    }
}
