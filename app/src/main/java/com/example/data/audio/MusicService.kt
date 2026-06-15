package com.example.data.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.models.SongEntity

class MusicService : Service() {

    companion object {
        private const val TAG = "MusicService"
        private const val CHANNEL_ID = "ad_music_player_channel"
        private const val NOTIFICATION_ID = 2026

        const val ACTION_PLAY = "com.example.music.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.music.ACTION_PAUSE"
        const val ACTION_PREV = "com.example.music.ACTION_PREV"
        const val ACTION_NEXT = "com.example.music.ACTION_NEXT"
        const val ACTION_STOP = "com.example.music.ACTION_STOP"

        fun updateNotification(context: Context, song: SongEntity, isPlaying: Boolean) {
            val intent = Intent(context, MusicService::class.java).apply {
                putExtra("title", song.title)
                putExtra("artist", song.artist)
                putExtra("isPlaying", isPlaying)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        val action = intent.action
        if (action != null) {
            handleNotificationAction(action)
        }

        val title = intent.getStringExtra("title") ?: "No track playing"
        val artist = intent.getStringExtra("artist") ?: "AD Music Player"
        val isPlaying = intent.getBooleanExtra("isPlaying", false)

        showNotification(title, artist, isPlaying)
        return START_STICKY
    }

    private fun handleNotificationAction(action: String) {
        val context = applicationContext
        when (action) {
            ACTION_PLAY, ACTION_PAUSE -> {
                AudioPlayerManager.togglePlayPause(context)
            }
            ACTION_PREV -> {
                AudioPlayerManager.previous(context)
            }
            ACTION_NEXT -> {
                AudioPlayerManager.next(context)
            }
            ACTION_STOP -> {
                AudioPlayerManager.stop()
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private fun showNotification(title: String, artist: String, isPlaying: Boolean) {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification Actions with PendingIntents
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pause",
                getServicePendingIntent(ACTION_PAUSE, 1)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Play",
                getServicePendingIntent(ACTION_PLAY, 1)
            )
        }

        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Previous",
            getServicePendingIntent(ACTION_PREV, 2)
        )

        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Next",
            getServicePendingIntent(ACTION_NEXT, 3)
        )

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_menu_close_clear_cancel, "Stop",
            getServicePendingIntent(ACTION_STOP, 4)
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play) // Standard system play icon as fallback
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun getServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AD Music Background Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls background music playback from AD Music Player"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Music service destroyed")
    }
}
