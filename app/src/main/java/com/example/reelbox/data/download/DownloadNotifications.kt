package com.example.reelbox.data.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.reelbox.MainActivity
import com.example.reelbox.R

object DownloadNotifications {
    const val CHANNEL_ID = "downloads"

    /** Creates the channel on Android 8.0+; a no-op on older versions, which have no channels. */
    fun ensureChannel(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        if (nm.getNotificationChannelCompat(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Downloads")
                    .setDescription("Progress of titles being downloaded for offline viewing")
                    .build(),
            )
        }
    }

    fun idFor(mediaId: String): Int = 1000 + (mediaId.hashCode() and 0x7fff)

    fun progress(context: Context, label: String, done: Long, total: Long): Notification {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val pct = if (total > 0) (done * 100 / total).toInt() else 0
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Downloading $label")
            .setContentText("$pct%")
            .setProgress(100, pct, total <= 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }
}
