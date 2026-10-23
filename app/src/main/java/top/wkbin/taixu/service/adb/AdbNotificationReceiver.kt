package top.wkbin.taixu.service.adb

import org.koin.core.component.inject
import org.koin.core.component.KoinComponent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.datastore.RuntimePreferences
import top.wkbin.taixu.runtime.bridge.adb.EmbeddedAdbManager

/**
 * 接收来自系统通知栏的无线 ADB 操作：
 * 1. 提取 RemoteInput 输入的 6 位配对码并异步发起 TLS + SPAKE2 配对与连接；
 * 2. 处理直接连接、断开连接与关闭通知请求。
 */
class AdbNotificationReceiver : BroadcastReceiver(), KoinComponent {

    val embeddedAdbManager: EmbeddedAdbManager by inject()

    val adbNotificationManager: AdbNotificationManager by inject()

    val preferences: RuntimePreferences by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AdbNotificationManager.ACTION_PAIR_INPUT -> {
                val pairingCode = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(AdbNotificationManager.KEY_PAIRING_CODE)
                    ?.toString()
                    ?.trim()

                if (pairingCode.isNullOrBlank() || !pairingCode.matches(Regex("\\d{6}"))) {
                    adbNotificationManager.showFailed("Invalid pairing code: must be 6 digits, please retry")
                    return
                }

                // Immediately refresh to pairing in progress state
                adbNotificationManager.showPairingInProgress()
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        Log.i(TAG, "Received pairing code from notification, starting wireless ADB pairing…")
                        val result = embeddedAdbManager.pair(pairingCode)
                        result.fold(
                            onSuccess = {
                                Log.i(TAG, "Wireless ADB paired successfully via notification")
                            },
                            onFailure = { error ->
                                Log.w(TAG, "Wireless ADB pairing failed via notification", error)
                                adbNotificationManager.showFailed(error.message ?: "Wireless ADB pairing failed. Please verify pairing code and retry")
                            },
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            AdbNotificationManager.ACTION_CONNECT -> {
                adbNotificationManager.showConnectingInProgress()
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        Log.i(TAG, "Initiating wireless ADB connection from notification…")
                        val result = embeddedAdbManager.connect()
                        result.fold(
                            onSuccess = {
                                Log.i(TAG, "Wireless ADB connected successfully via notification")
                            },
                            onFailure = { error ->
                                Log.w(TAG, "Wireless ADB connection failed via notification", error)
                                adbNotificationManager.showFailed(error.message ?: "Wireless ADB connection failed. Please verify port or pair again")
                            },
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            AdbNotificationManager.ACTION_DISCONNECT -> {
                Log.i(TAG, "从通知栏请求断开无线 ADB")
                embeddedAdbManager.disconnect()
                adbNotificationManager.showPairingPrompt()
            }

            AdbNotificationManager.ACTION_DISMISS -> {
                adbNotificationManager.dismiss()
                scope.launch { preferences.setAdbNotificationEnabled(false) }
            }
        }
    }

    private companion object {
        const val TAG = "AdbNotifReceiver"
    }
}
