package top.wkbin.taixu.ui.settings.permission

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import top.wkbin.taixu.ui.components.RuntimeIconName

/**
 * 厂商保活引导步骤模型
 */
data class PermissionStep(
    val title: String,
    val description: String,
    val tip: String? = null,
)

/**
 * 品牌定义与识别模型
 */
enum class OemBrand(val id: String, val label: String, val osName: String) {
    XIAOMI("xiaomi", "Xiaomi / Redmi", "HyperOS / MIUI"),
    HUAWEI("huawei", "Huawei / Honor", "HarmonyOS / MagicOS"),
    VIVO("vivo", "vivo / iQOO", "OriginOS / Funtouch"),
    OPPO("oppo", "OPPO / OnePlus / realme", "ColorOS / RealmeUI"),
    MEIZU("meizu", "Meizu", "Flyme"),
    SAMSUNG("samsung", "Samsung", "One UI"),
    GENERIC("generic", "AOSP / Other OEMs", "AOSP / Android");

    companion object {
        fun detect(): OemBrand {
            val manufacturer = Build.MANUFACTURER?.lowercase().orEmpty()
            val brand = Build.BRAND?.lowercase().orEmpty()
            val fingerprint = Build.FINGERPRINT?.lowercase().orEmpty()
            val hardware = Build.HARDWARE?.lowercase().orEmpty()
            val combined = "$manufacturer $brand $fingerprint $hardware"

            return when {
                combined.contains("xiaomi") || combined.contains("redmi") ||
                    combined.contains("poco") || combined.contains("miui") -> XIAOMI
                combined.contains("huawei") || combined.contains("honor") ||
                    combined.contains("emui") || combined.contains("magic") -> HUAWEI
                combined.contains("vivo") || combined.contains("iqoo") ||
                    combined.contains("funtouch") || combined.contains("origin") -> VIVO
                combined.contains("oppo") || combined.contains("realme") ||
                    combined.contains("oneplus") || combined.contains("coloros") -> OPPO
                combined.contains("meizu") || combined.contains("flyme") -> MEIZU
                combined.contains("samsung") -> SAMSUNG
                else -> GENERIC
            }
        }
    }
}

/**
 * 保活与权限专题枚举
 */
enum class KeepaliveTopic(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: RuntimeIconName,
) {
    AUTOSTART(
        id = "autostart",
        title = "Autostart & Background Wakeup",
        subtitle = "Allow the system to launch the sandbox and Agent service on boot or in the background",
        icon = RuntimeIconName.Cpu,
    ),
    BATTERY_UNRESTRICTED(
        id = "battery_unrestricted",
        title = "Unrestricted Battery Usage",
        subtitle = "Exempt from battery saver and Doze mode to prevent PRoot processes from freezing when screen is off",
        icon = RuntimeIconName.Battery,
    ),
    LOCK_RECENTS(
        id = "lock_recents",
        title = "Lock Recents Task Card",
        subtitle = "Lock TaiXu in the Recent Apps list to prevent one-tap clear from killing foreground services",
        icon = RuntimeIconName.Shield,
    ),
    FLOATING_OVERLAY(
        id = "floating_overlay",
        title = "Floating Window & Background Pop-ups",
        subtitle = "Allow build completion or Agent interactive notifications and windows while running in background",
        icon = RuntimeIconName.Terminal,
    ),
    NOTIFICATION(
        id = "notification",
        title = "Notifications & Persistent Service",
        subtitle = "Keep the foreground keep-alive notification visible to ensure top process priority for Linux runtime",
        icon = RuntimeIconName.Chat,
    ),
}

/**
 * 厂商保活与权限引导知识库仓储
 */
object PermissionGuideRepository {

    fun getSteps(brand: OemBrand, topic: KeepaliveTopic): List<PermissionStep> {
        return when (topic) {
            KeepaliveTopic.AUTOSTART -> when (brand) {
                OemBrand.XIAOMI -> listOf(
                    PermissionStep("Open App Settings", "Open System [Settings] -> [Apps] -> [Permissions] -> [Autostart]."),
                    PermissionStep("Enable TaiXu Autostart", "Find [TaiXu] in the app list and toggle the Autostart switch ON."),
                    PermissionStep("Allow Associated Wakeup", "Open TaiXu details and enable [Allow associated startup / Wake by other apps] (recommended on HyperOS)."),
                )
                OemBrand.HUAWEI -> listOf(
                    PermissionStep("Open App Launch Management", "Open [Optimizer / System Manager] -> [App Launch]."),
                    PermissionStep("Switch to Manage Manually", "Find [TaiXu], disable Auto-manage, and enable all 3 toggles: [Auto-launch], [Secondary launch], and [Run in background]."),
                )
                OemBrand.VIVO -> listOf(
                    PermissionStep("Open Autostart Permission", "Go to System [Settings] -> [Apps & Permissions] -> [Permission management] -> [Permissions] -> [Autostart]."),
                    PermissionStep("Enable TaiXu Autostart", "Find [TaiXu] and allow Autostart."),
                    PermissionStep("Allow High Background Power Usage", "In [Settings] -> [Battery] -> [High background power consumption], allow TaiXu to keep running in the background."),
                )
                OemBrand.OPPO -> listOf(
                    PermissionStep("Open Autostart Management", "Go to System [Settings] -> [Apps] -> [Auto-launch]."),
                    PermissionStep("Allow Auto-launch & Associated Launch", "Find [TaiXu] and enable both [Auto-launch] and [Associated launch]."),
                )
                OemBrand.MEIZU -> listOf(
                    PermissionStep("Open Background Management", "Go to [Phone Manager] -> [Permissions] -> [Background management]."),
                    PermissionStep("Keep Running in Background", "Set [TaiXu] to [Allow running in background] or [Keep awake in background]."),
                )
                OemBrand.SAMSUNG -> listOf(
                    PermissionStep("Check Background Usage Limits", "Go to [Settings] -> [Battery] -> [Background usage limits]."),
                    PermissionStep("Remove from Sleeping Apps", "Ensure TaiXu is not in [Deep sleeping apps] and add it to [Never auto sleeping apps]."),
                )
                OemBrand.GENERIC -> listOf(
                    PermissionStep("Check System Startup Permissions", "In App Info settings, check for any Autostart or Startup permissions and allow them."),
                )
            }
            KeepaliveTopic.BATTERY_UNRESTRICTED -> when (brand) {
                OemBrand.XIAOMI -> listOf(
                    PermissionStep("App Info Battery Saver", "Long-press the TaiXu home screen icon -> [App info] -> scroll to [Battery saver]."),
                    PermissionStep("Set to No Restrictions", "Select [No restrictions] (does not restrict background activity; best for PRoot builds and long Agent runs)."),
                )
                OemBrand.HUAWEI -> listOf(
                    PermissionStep("Ignore Battery Optimization", "Go to [Settings] -> [Apps] -> [Permissions] -> top-right menu [Special access] -> [Battery optimization]."),
                    PermissionStep("Set to Don't Allow", "Filter by [All apps], find [TaiXu], and set to [Don't allow]."),
                )
                OemBrand.VIVO -> listOf(
                    PermissionStep("Allow High Background Power", "Go to [Settings] -> [Battery] -> [Background power consumption management]."),
                    PermissionStep("Enable High Power Background Run", "Find [TaiXu] and select [High background power usage]."),
                )
                OemBrand.OPPO -> listOf(
                    PermissionStep("Battery Usage Management", "Long-press TaiXu icon -> [App info] -> [Battery usage]."),
                    PermissionStep("Enable All Background Options", "Enable [Allow background activity] and [Allow auto-launch], and disable deep power saving optimizations."),
                )
                OemBrand.SAMSUNG -> listOf(
                    PermissionStep("Battery Usage Mode", "Go to System [Settings] -> [Apps] -> [TaiXu] -> [Battery]."),
                    PermissionStep("Set to Unrestricted", "Switch from default [Optimized] to [Unrestricted]."),
                )
                else -> listOf(
                    PermissionStep("Ignore Battery Optimization", "Open App Info -> [Battery] -> set to [Unrestricted] or ignore battery optimizations."),
                )
            }
            KeepaliveTopic.LOCK_RECENTS -> listOf(
                PermissionStep("Open Recent Apps Overview", "Swipe up and hold from the bottom of the screen (or tap the Recents button) to open Recent Apps."),
                PermissionStep("Lock the TaiXu Card", "Long-press or pull down the TaiXu card and tap the lock 🔒 icon so it is not killed when clearing recents."),
            )
            KeepaliveTopic.FLOATING_OVERLAY -> listOf(
                PermissionStep("Enable Display Over Other Apps", "Go to [App info] -> [Advanced] -> [Display over other apps] and toggle it to [Allowed]."),
                PermissionStep("Display Pop-up Windows in Background", "On Xiaomi/vivo ROMs, open [Permissions] and enable [Display pop-up windows while running in the background]."),
            )
            KeepaliveTopic.NOTIFICATION -> listOf(
                PermissionStep("Allow Notifications & Foreground Service", "Ensure TaiXu has notification permission so it can run a persistent foreground service for PRoot and Agent tasks."),
            )
        }
    }

    /**
     * 检查某项权限在当前系统中的状态
     */
    fun checkStatus(context: Context, topic: KeepaliveTopic): Boolean {
        return when (topic) {
            KeepaliveTopic.BATTERY_UNRESTRICTED -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
            KeepaliveTopic.NOTIFICATION -> {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
            KeepaliveTopic.FLOATING_OVERLAY -> {
                Settings.canDrawOverlays(context)
            }
            KeepaliveTopic.AUTOSTART,
            KeepaliveTopic.LOCK_RECENTS -> {
                // Android 无公开 API 直接读取 OEM 私有自启动/最近任务加锁状态
                false
            }
        }
    }

    /**
     * 构建跳转至该专题设置页的 Intent 列表（按优先级降级尝试）
     */
    fun buildLaunchIntents(context: Context, brand: OemBrand, topic: KeepaliveTopic): List<Intent> {
        val pkg = context.packageName
        return when (topic) {
            KeepaliveTopic.AUTOSTART -> buildAutostartIntents(pkg, brand)
            KeepaliveTopic.BATTERY_UNRESTRICTED -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                appDetailsIntent(pkg),
            )
            KeepaliveTopic.FLOATING_OVERLAY -> listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")),
                appDetailsIntent(pkg),
            )
            KeepaliveTopic.NOTIFICATION -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                },
                appDetailsIntent(pkg),
            )
            KeepaliveTopic.LOCK_RECENTS -> listOf(
                // 最近任务加锁不需要跳转，用户直接呼出多任务即可
                appDetailsIntent(pkg),
            )
        }
    }

    private fun buildAutostartIntents(pkg: String, brand: OemBrand): List<Intent> {
        return when (brand) {
            OemBrand.XIAOMI -> listOf(
                intentFor("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                intentFor("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"),
                intentFor("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
                appDetailsIntent(pkg),
            )
            OemBrand.HUAWEI -> listOf(
                intentFor("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                intentFor("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                intentFor("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                appDetailsIntent(pkg),
            )
            OemBrand.VIVO -> listOf(
                intentFor("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                intentFor("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                intentFor("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                appDetailsIntent(pkg),
            )
            OemBrand.OPPO -> listOf(
                intentFor("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                intentFor("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                intentFor("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                appDetailsIntent(pkg),
            )
            OemBrand.MEIZU -> listOf(
                intentFor("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
                intentFor("com.meizu.safe", "com.meizu.safe.security.AppSecActivity"),
                appDetailsIntent(pkg),
            )
            OemBrand.SAMSUNG -> listOf(
                appDetailsIntent(pkg),
            )
            OemBrand.GENERIC -> listOf(
                appDetailsIntent(pkg),
            )
        }
    }

    private fun intentFor(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun appDetailsIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
