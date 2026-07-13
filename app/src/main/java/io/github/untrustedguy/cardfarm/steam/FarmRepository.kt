package io.github.untrustedguy.cardfarm.steam

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide bridge between the UI and [SteamFarmService]. The service is
 * the single writer of the state flows; the UI observes them and pushes
 * [FarmCommand]s through [commands].
 */
object FarmRepository {

    val connection = MutableStateFlow(ConnectionState.OFFLINE)
    val statusText = MutableStateFlow("")
    val accountName = MutableStateFlow<String?>(null)
    val badges = MutableStateFlow<List<BadgeGame>>(emptyList())
    val farming = MutableStateFlow<FarmingState>(FarmingState.Stopped)
    val guardRequest = MutableStateFlow<GuardRequest?>(null)
    val refreshingBadges = MutableStateFlow(false)

    /** One-shot error/info messages surfaced as snackbars. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Buffered so commands sent before the service starts are not lost. */
    val commands = Channel<FarmCommand>(Channel.UNLIMITED)

    fun send(command: FarmCommand) {
        commands.trySend(command)
    }

    fun postMessage(message: String) {
        messages.tryEmit(message)
    }

    fun resetToOffline() {
        connection.value = ConnectionState.OFFLINE
        farming.value = FarmingState.Stopped
        guardRequest.value = null
        refreshingBadges.value = false
    }
}
