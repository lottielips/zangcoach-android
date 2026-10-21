package nl.zangcoach.studio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Zolang er muziek speelt, houdt deze dienst de app wakker met een melding
 * ("Zangcoach speelt"). Zo stopt Android de muziek niet als het scherm uitgaat.
 */
class PlaybackService : Service() {

    companion object {
        private const val CHANNEL = "spelen"
        private const val ID = 7

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, PlaybackService::class.java))
            } catch (_: Exception) { }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PlaybackService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Muziek speelt", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Zangcoach speelt")
            .setContentText("Tik om terug te gaan naar je nummer")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(ID, n)
            }
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }
}
