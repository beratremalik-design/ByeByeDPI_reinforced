package io.github.romanvht.byedpi.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.util.Log
import io.github.romanvht.byedpi.utility.AUTOPILOT_NOTIFICATION_ID
import io.github.romanvht.byedpi.utility.AutopilotManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * D2: обработка кнопок уведомления-предложения автоподбора.
 * Показывается только когда автопилот не вправе молча менять стратегию
 * (пользователь использует свою). «Применить» — явное согласие.
 */
class AutopilotReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AutopilotReceiver"
        const val ACTION_APPLY = "io.github.romanvht.byedpi.AUTOPILOT_APPLY"
        const val ACTION_DECLINE = "io.github.romanvht.byedpi.AUTOPILOT_DECLINE"
        const val EXTRA_COMMAND = "command"
    }

    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(AUTOPILOT_NOTIFICATION_ID)

        when (intent.action) {
            ACTION_APPLY -> {
                val command = intent.getStringExtra(EXTRA_COMMAND) ?: return
                val pending = goAsync()
                CoroutineScope(Dispatchers.Main.immediate).launch {
                    try {
                        AutopilotManager.applyFromProposal(
                            context.applicationContext, command
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to apply autopilot proposal", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_DECLINE -> Log.i(TAG, "Autopilot proposal declined")
        }
    }
}
