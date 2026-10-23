package top.wkbin.taixu.ui.home

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import top.wkbin.taixu.ui.components.FoldablePreviews
import top.wkbin.taixu.ui.components.RuntimePreview

@FoldablePreviews
@Preview(name = "Compact · Large text", widthDp = 360, heightDp = 800, fontScale = 1.3f, locale = "en")
@Preview(name = "Dark · English", widthDp = 412, heightDp = 892, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "en")
@Composable
private fun HomeScreenPreview() {
    RuntimePreview {
        LocalHomeScreen(onNavigate = { _ -> }, onOpenTerminal = { })
    }
}

@FoldablePreviews
@Preview(name = "Compact · Large text", widthDp = 360, heightDp = 800, fontScale = 1.3f, locale = "en")
@Preview(name = "Dark · English", widthDp = 412, heightDp = 892, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "en")
@Composable
private fun RemoteHomeScreenPreview() {
    RuntimePreview {
        RemoteHomeScreen(serverId = "preview", onNavigate = { _ -> })
    }
}
