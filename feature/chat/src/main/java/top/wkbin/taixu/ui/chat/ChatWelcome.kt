package top.wkbin.taixu.ui.chat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.runtime.ProjectType
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.feature.chat.R
import top.wkbin.taixu.ui.components.*

@Composable
internal fun EmptyChatGuidance(
    workspaceProject: WorkspaceProject? = null,
    onboardingPrivilege: OnboardingPrivilege? = null,
    onSelectCommand: (SlashCommandItem) -> Unit,
) {
    val context = LocalContext.current
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour) {
        when (hour) {
            in 5..11 -> "Good morning, what project would you like to build today?"
            in 12..17 -> "Good afternoon, ready to pair program with AI?"
            else -> "Good evening, let's bring your ideas to life."
        }
    }

    val quickCommands = remember(workspaceProject) {
        when (workspaceProject?.projectType) {
            ProjectType.ANDROID -> listOf(
                SlashCommandItem("/android-check", context.getString(R.string.chat_android_check), context.getString(R.string.chat_android_check_description), context.getString(R.string.chat_android_check_prompt), RuntimeIconName.Check),
                SlashCommandItem("/android-build-install", context.getString(R.string.chat_android_build), context.getString(R.string.chat_android_build_description), context.getString(R.string.chat_android_build_prompt), RuntimeIconName.Play),
                SlashCommandItem("/android-debug", context.getString(R.string.chat_android_debug), context.getString(R.string.chat_android_debug_description), context.getString(R.string.chat_android_debug_prompt), RuntimeIconName.Alert),
                SlashCommandItem("/reverse-analyze", context.getString(R.string.chat_reverse_analyze), context.getString(R.string.chat_reverse_analyze_description), context.getString(R.string.chat_reverse_analyze_prompt), RuntimeIconName.Search),
            )
            ProjectType.FLUTTER -> listOf(
                SlashCommandItem("/flutter-check", context.getString(R.string.chat_flutter_check), context.getString(R.string.chat_flutter_check_description), context.getString(R.string.chat_flutter_check_prompt), RuntimeIconName.Check),
                SlashCommandItem("/flutter-build-install", context.getString(R.string.chat_flutter_build), context.getString(R.string.chat_flutter_build_description), context.getString(R.string.chat_flutter_build_prompt), RuntimeIconName.Play),
                SlashCommandItem("/flutter-debug", context.getString(R.string.chat_flutter_debug), context.getString(R.string.chat_flutter_debug_description), context.getString(R.string.chat_flutter_debug_prompt), RuntimeIconName.Alert),
                SlashCommandItem("/init", context.getString(R.string.chat_command_init), context.getString(R.string.chat_command_init_description), context.getString(R.string.chat_command_init_description), RuntimeIconName.Code),
            )
            ProjectType.REVERSE -> listOf(
                SlashCommandItem("/reverse-analyze", context.getString(R.string.chat_reverse_analyze), context.getString(R.string.chat_reverse_analyze_description), context.getString(R.string.chat_reverse_analyze_prompt), RuntimeIconName.Search),
                SlashCommandItem("/reverse-decode", context.getString(R.string.chat_reverse_decode), context.getString(R.string.chat_reverse_decode_description), context.getString(R.string.chat_reverse_decode_prompt), RuntimeIconName.Code),
                SlashCommandItem("/android-check", context.getString(R.string.chat_android_check), context.getString(R.string.chat_android_check_description), context.getString(R.string.chat_android_check_prompt), RuntimeIconName.Check),
                SlashCommandItem("/git", context.getString(R.string.chat_command_git), context.getString(R.string.chat_command_git_description), "git status", RuntimeIconName.GitBranch),
            )
            else -> defaultBentoCommands(context)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Hero 品牌与问候徽章
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaiXuBrandBadge(size = 48.dp)
                Text(
                    text = greeting, modifier = chatGreetingModifier(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 2. Linux 沙箱与工作区实时微胶囊
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = "Linux Sandbox Ready",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    RuntimeIcon(
                        name = RuntimeIconName.Folder,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = workspaceProject?.name ?: "Default Global Sandbox",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 3. 核心场景快捷动作列表 (统一优雅卡片)
            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = top.wkbin.taixu.ui.theme.chatBackdropSurface(),
                shape = RoundedCornerShape(20.dp),
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    quickCommands.forEachIndexed { index, cmd ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCommand(cmd) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                RuntimeIcon(
                                    name = cmd.icon,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = cmd.label,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = cmd.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            RuntimeIcon(
                                name = RuntimeIconName.ChevronRight,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }

            onboardingPrivilege?.takeUnless { it == OnboardingPrivilege.SANDBOX }?.let { privilege ->
                Text(
                    text = stringResource(
                        when (privilege) {
                            OnboardingPrivilege.SANDBOX -> R.string.chat_onboarding_sandbox
                            OnboardingPrivilege.SANDBOX_UNLOCKABLE -> R.string.chat_onboarding_unlockable
                            OnboardingPrivilege.SHIZUKU_READY -> R.string.chat_onboarding_shizuku
                            OnboardingPrivilege.ROOT_READY -> R.string.chat_onboarding_root
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}


private fun defaultBentoCommands(context: Context): List<SlashCommandItem> = listOf(
    SlashCommandItem("/init", "Init Project", "Wizard-based initialization of Android / Flutter / Web templates", "Please list available project templates and help me create a new project", RuntimeIconName.Code),
    SlashCommandItem("/terminal", "Sandbox Terminal", "Execute commands and build in Linux PRoot sandbox", "Please check the current development environment and toolchain in the Linux sandbox", RuntimeIconName.Terminal),
    SlashCommandItem("/doctor", "Doctor & Healing", "One-click diagnosis of SDK, build scripts, and dependencies", "Please run a diagnostic on the current dev environment and fix potential issues", RuntimeIconName.Wrench),
    SlashCommandItem("/explore", "Pair Audit", "Inspect project structure, analyze build errors, and refactor", "Please inspect current workspace project structure and analyze key logic", RuntimeIconName.Search),
)
