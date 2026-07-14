package io.github.untrustedguy.cardfarm.steam

import java.util.concurrent.CompletableFuture

/** High-level connection state of the Steam client. */
enum class ConnectionState {
    OFFLINE,
    CONNECTING,
    AUTHENTICATING,
    LOGGED_ON,
}

/** What the client is currently doing with games. */
sealed class FarmingState {
    data object Stopped : FarmingState()

    /** Automatically farming card drops, ASF-style. */
    data class CardFarming(val appIds: List<Int>) : FarmingState()

    /** Idling a fixed, user-chosen set of games. */
    data class CustomIdle(val appIds: List<Int>) : FarmingState()

    val activeAppIds: List<Int>
        get() = when (this) {
            is Stopped -> emptyList()
            is CardFarming -> appIds
            is CustomIdle -> appIds
        }
}

/** A game on the badge page. */
data class BadgeGame(
    val appId: Int,
    val name: String,
    val dropsRemaining: Int,
    val playtime: String,
) {
    val capsuleUrl: String
        get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/capsule_184x69.jpg"
}

/** A game the account owns — full library, not just games with card drops. */
data class OwnedGame(
    val appId: Int,
    val name: String,
    val playtimeForeverMinutes: Int,
) {
    val capsuleUrl: String
        get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/capsule_184x69.jpg"

    val playtimeLabel: String
        get() = when {
            playtimeForeverMinutes <= 0 -> "Never played"
            playtimeForeverMinutes < 60 -> "${playtimeForeverMinutes}m"
            else -> "${playtimeForeverMinutes / 60}h ${playtimeForeverMinutes % 60}m"
        }
}

enum class GuardType { DEVICE_CODE, EMAIL_CODE }

/**
 * A pending Steam Guard prompt. The UI completes [future] with the code the
 * user typed; the auth flow blocks on it.
 */
class GuardRequest(
    val type: GuardType,
    val email: String?,
    val previousCodeWasIncorrect: Boolean,
    /** True when the account can *also* be approved via a push in the real Steam app. */
    val canApproveOnPhone: Boolean = false,
) {
    val future: CompletableFuture<String> = CompletableFuture()
}

/** Commands sent from the UI to the foreground service. */
sealed class FarmCommand {
    /** Fresh login with credentials. */
    data class Login(val username: String, val password: String) : FarmCommand()

    /** Reconnect using the stored refresh token. */
    data object Connect : FarmCommand()

    data object StartFarming : FarmCommand()
    data class IdleGames(val appIds: List<Int>) : FarmCommand()
    data object StopIdling : FarmCommand()
    data object RefreshBadges : FarmCommand()
    data object LoadLibrary : FarmCommand()
    data object Logout : FarmCommand()
}
