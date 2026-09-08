package com.mannam.goldpet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class WalkNotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "walk_widget"
        const val NOTIFICATION_ID = 9998
    }

    // Cached PendingIntents — created once, reused every second
    private val contentIntent: PendingIntent
    private val peePendingIntent: PendingIntent
    private val poopPendingIntent: PendingIntent
    private val photoPendingIntent: PendingIntent
    private val stopPendingIntent: PendingIntent

    // Cached builder + layouts to prevent flickering on every-second updates
    private val cachedCollapsedLayout: RemoteViews  // contentView (collapsed)
    private val cachedLayout: RemoteViews            // bigContentView (expanded)
    private val cachedBuilder: NotificationCompat.Builder

    init {
        createChannel()
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // NEW_TASK brings existing task to foreground; SINGLE_TOP delivers
        // to onNewIntent() instead of recreating the Activity.
        // Avoid CLEAR_TOP — it destroys and recreates the Activity when
        // taskAffinity="" (Flutter default), causing full app restart.
        val intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("goldpet://walk")
                flags = intentFlags
            },
            piFlags
        )
        peePendingIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("goldpet://spot/pee")
                flags = intentFlags
            },
            piFlags
        )
        poopPendingIntent = PendingIntent.getActivity(
            context, 2,
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("goldpet://spot/poop")
                flags = intentFlags
            },
            piFlags
        )
        photoPendingIntent = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("goldpet://spot/photo")
                flags = intentFlags
            },
            piFlags
        )
        stopPendingIntent = PendingIntent.getActivity(
            context, 4,
            Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("goldpet://walk/stop")
                flags = intentFlags
            },
            piFlags
        )

        // Build collapsed layout (metrics only, no action buttons)
        cachedCollapsedLayout = RemoteViews(context.packageName, R.layout.notification_walk_collapsed)

        // Build expanded layout (full widget with action buttons)
        cachedLayout = RemoteViews(context.packageName, R.layout.notification_walk).apply {
            setOnClickPendingIntent(R.id.btn_pee, peePendingIntent)
            setOnClickPendingIntent(R.id.btn_poop, poopPendingIntent)
            setOnClickPendingIntent(R.id.btn_photo, photoPendingIntent)
        }

        cachedBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(cachedCollapsedLayout)
            .setCustomBigContentView(cachedLayout)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "종료", stopPendingIntent)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "산책 위젯",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "산책 중 위치 추적 및 위젯"
                setShowBadge(false)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /** Build the initial notification for startForeground() */
    fun buildInitial(): Notification {
        return cachedBuilder.build()
    }

    fun show(time: String, distance: String, duration: String, calories: String) {
        // Update expanded layout
        cachedLayout.setTextViewText(R.id.walk_time, time)
        cachedLayout.setTextViewText(R.id.walk_distance, distance)
        cachedLayout.setTextViewText(R.id.walk_duration, duration)
        cachedLayout.setTextViewText(R.id.walk_calories, calories)

        // Update collapsed layout
        cachedCollapsedLayout.setTextViewText(R.id.walk_time, time)
        cachedCollapsedLayout.setTextViewText(R.id.walk_distance, distance)
        cachedCollapsedLayout.setTextViewText(R.id.walk_duration, duration)
        cachedCollapsedLayout.setTextViewText(R.id.walk_calories, calories)

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, cachedBuilder.build())
    }

    fun cancel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
    }
}
