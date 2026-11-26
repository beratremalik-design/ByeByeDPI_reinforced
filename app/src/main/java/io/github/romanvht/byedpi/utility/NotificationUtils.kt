package io.github.romanvht.byedpi.utility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.activities.MainActivity
import io.github.romanvht.byedpi.activities.TestActivity
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.PAUSE_ACTION
import io.github.romanvht.byedpi.data.RESUME_ACTION
import io.github.romanvht.byedpi.data.STOP_ACTION
import io.github.romanvht.byedpi.receiver.AutopilotReceiver
import io.github.romanvht.byedpi.receiver.ServiceActionReceiver

private const val AUTOPILOT_CHANNEL = "Autopilot"

/** Идентификатор уведомления-предложения автоподбора (D2). */
const val AUTOPILOT_NOTIFICATION_ID = 5

/**
 * D2: уведомление «Автоподбор нашёл стратегию — применить?».
 * Показывается, когда автопилот не может применить стратегию молча
 * (у пользователя своя стратегия или UI-режим).
 */
fun showAutopilotProposal(context: Context, display: String, command: String) {
    registerNotificationChannel(context, AUTOPILOT_CHANNEL, R.string.autopilot_channel)
    val manager = context.getSystemService(NotificationManager::class.java) ?: return

    val applyIntent = Intent(context, AutopilotReceiver::class.java)
        .setAction(AutopilotReceiver.ACTION_APPLY)
        .putExtra(AutopilotReceiver.EXTRA_COMMAND, command)
    val declineIntent = Intent(context, AutopilotReceiver::class.java)
        .setAction(AutopilotReceiver.ACTION_DECLINE)

    // U2: в тексте уведомления — только условный код стратегии (A1..C10),
    // сама последовательность параметров не раскрывается.
    val notification = NotificationCompat.Builder(context, AUTOPILOT_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.autopilot_proposal_title))
        .setContentText(display)
        .setStyle(NotificationCompat.BigTextStyle()
            .bigText(context.getString(R.string.autopilot_proposal_text) + "\n\n" + display))
        .addAction(0, context.getString(R.string.autopilot_apply),
            PendingIntent.getBroadcast(
                context, 0, applyIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .addAction(0, context.getString(R.string.autopilot_decline),
            PendingIntent.getBroadcast(
                context, 1, declineIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .setContentIntent(
            PendingIntent.getActivity(
                context, 0,
                Intent(context, TestActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .setAutoCancel(true)
        .build()
    manager.notify(AUTOPILOT_NOTIFICATION_ID, notification)
}

fun registerNotificationChannel(context: Context, id: String, @StringRes name: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val channel = NotificationChannel(
            id,
            context.getString(name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.enableLights(false)
        channel.enableVibration(false)
        channel.setShowBadge(false)

        manager.createNotificationChannel(channel)
    }
}

fun createConnectionNotification(
    context: Context,
    channelId: String,
    @StringRes title: Int,
    @StringRes content: Int,
    mode: Mode,
): Notification =
    NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setSilent(true)
        .setContentTitle(context.getString(title))
        .setContentText(context.getString(content))
        .addAction(0, context.getString(R.string.service_pause_btn),
            PendingIntent.getBroadcast(
                context,
                mode.ordinal,
                Intent(context, ServiceActionReceiver::class.java).setAction(PAUSE_ACTION)
                    .putExtra(ServiceActionReceiver.EXTRA_MODE, mode.name)
                    .putExtra(ServiceActionReceiver.EXTRA_PID, Process.myPid()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .addAction(0, context.getString(R.string.service_stop_btn),
            PendingIntent.getBroadcast(
                context,
                mode.ordinal,
                Intent(context, ServiceActionReceiver::class.java).setAction(STOP_ACTION)
                    .putExtra(ServiceActionReceiver.EXTRA_MODE, mode.name)
                    .putExtra(ServiceActionReceiver.EXTRA_PID, Process.myPid()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .build()

fun createPauseNotification(
    context: Context,
    channelId: String,
    @StringRes title: Int,
    @StringRes content: Int,
    mode: Mode,
): Notification =
    NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_notification)
        .setSilent(true)
        .setContentTitle(context.getString(title))
        .setContentText(context.getString(content))
        .addAction(0, context.getString(R.string.service_start_btn),
            PendingIntent.getBroadcast(
                context,
                mode.ordinal,
                Intent(context, ServiceActionReceiver::class.java).setAction(RESUME_ACTION)
                    .putExtra(ServiceActionReceiver.EXTRA_MODE, mode.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
        .build()
