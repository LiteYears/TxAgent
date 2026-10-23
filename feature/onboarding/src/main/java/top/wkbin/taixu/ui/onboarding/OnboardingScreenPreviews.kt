package top.wkbin.taixu.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import top.wkbin.taixu.ui.components.FoldablePreviews
import top.wkbin.taixu.ui.components.RuntimePreview

@FoldablePreviews
@Composable
private fun OnboardingScreenPreview() {
    RuntimePreview {
        OnboardingScreen()
    }
}

@FoldablePreviews
@Composable
private fun OnboardingSystemPagePreview() {
    RuntimePreview {
        OnboardingScreen(previewPage = 0)
    }
}

@Preview(name = "Welcome · Dark", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, locale = "zh")
@Preview(name = "Welcome · Large Font", widthDp = 320, heightDp = 640, fontScale = 1.5f, locale = "zh")
@Preview(name = "Welcome · English", widthDp = 360, heightDp = 800, locale = "en")
@Composable
private fun OnboardingWelcomeVariantsPreview() {
    RuntimePreview { OnboardingScreen() }
}

@FoldablePreviews
@Preview(name = "Model Setup · Narrow Large Font", widthDp = 320, heightDp = 720, fontScale = 1.3f, locale = "zh")
@Preview(name = "Model Setup · Dark", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, locale = "zh")
@Preview(name = "Model setup · English", widthDp = 360, heightDp = 800, locale = "en")
@Composable
private fun OnboardingModelPagePreview() {
    RuntimePreview {
        OnboardingScreen(previewPage = 2)
    }
}

@FoldablePreviews
@Preview(name = "Permissions · Narrow Large Font", widthDp = 320, heightDp = 720, fontScale = 1.3f, locale = "zh")
@Preview(name = "Permissions · Dark", widthDp = 360, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, locale = "zh")
@Preview(name = "Permissions · English", widthDp = 360, heightDp = 800, locale = "en")
@Composable
private fun OnboardingPermissionPagePreview() {
    RuntimePreview { OnboardingScreen(previewPage = 1) }
}

@Preview(name = "Disclaimer · Narrow Large Font", widthDp = 320, heightDp = 720, fontScale = 1.3f, locale = "zh")
@Preview(name = "Disclaimer · Dark", widthDp = 360, heightDp = 800,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, locale = "en")
@Composable
private fun OnboardingDisclaimerPreview() {
    RuntimePreview { OnboardingScreen(previewPage = -2) }
}
