package io.github.untrustedguy.cardfarm.steam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.untrustedguy.cardfarm.MainActivity
import io.github.untrustedguy.cardfarm.R
import io.github.untrustedguy.cardfarm.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the Steam connection alive while the app is
 * backgrounded. It owns the [SteamController] and drains [FarmRepository.commands].
 */
class SteamFarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var controller: SteamController
    private lateinit var session: SessionStore

    override fun onCreate() {
        super.onCreate()
        session = SessionStore(this)
        controller = SteamController(session, scope)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting…", "CardFarm"))
        controller.start()
        observeState()
        drainCommands()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                controller.logout()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun drainCommands() {
        scope.launch {
            for (command in FarmRepository.commands) {
                when (command) {
                    is FarmCommand.Login -> controller.loginWithCredentials(command.username, command.password)
                    is FarmCommand.Connect -> controller.reconnectWithSession()
                    is FarmCommand.StartFarming -> controller.startCardFarming()
                    is FarmCommand.IdleGames -> controller.idleGames(command.appIds)
                    is FarmCommand.StopIdling -> controller.stopIdling()
                    is FarmCommand.RefreshBadges -> controller.refreshBadges()
                    is FarmCommand.LoadLibrary -> controller.loadLibrary()
                    is FarmCommand.Logout -> {
                        controller.logout()
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun observeState() {
        // Keep the ongoing notification in sync with connection + farming state.
        scope.launch {
            FarmRepository.statusText.collectLatest { status ->
                val title = when (FarmRepository.connection.value) {
                    ConnectionState.LOGGED_ON -> FarmRepository.accountName.value ?: "CardFarm"
                    else -> "CardFarm"
                }
                updateNotification(status.ifBlank { "Idle" }, title)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_farming),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent farming status"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, title: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SteamFarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_cards)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, "Sign out", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String, title: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text, title))
    }

    override fun onDestroy() {
        controller.shutdown()
        scope.cancel()
        // Explicitly drop the ongoing notification so it doesn't linger after
        // sign-out / service stop on some OEM builds.
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "farming_status"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "io.github.untrustedguy.cardfarm.STOP"

        fun start(context: Context) {
            val intent = Intent(context, SteamFarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
