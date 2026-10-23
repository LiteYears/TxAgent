package top.wkbin.taixu.ui.workspace

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import top.wkbin.taixu.ui.components.FoldablePreviews
import top.wkbin.taixu.ui.components.RuntimePreview

@FoldablePreviews
@Preview(name = "Narrow · Large Font", widthDp = 360, heightDp = 800, fontScale = 1.3f, locale = "en")
@Preview(name = "Dark · English", widthDp = 412, heightDp = 892, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "en")
@Composable
private fun WorkspaceScreenPreview() {
    RuntimePreview {
        LocalWorkspaceScreen(onNavigate = { _ -> }, onOpenExplorer = { _ -> }, onOpenTerminal = { _ -> })
    }
}

@FoldablePreviews
@Preview(name = "Narrow · Large Font", widthDp = 360, heightDp = 800, fontScale = 1.3f, locale = "en")
@Preview(name = "Dark · English", widthDp = 412, heightDp = 892, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "en")
@Composable
private fun RemoteWorkspaceScreenPreview() {
    RuntimePreview {
        RemoteWorkspaceScreen(serverId = "preview", onBack = {})
    }
}
