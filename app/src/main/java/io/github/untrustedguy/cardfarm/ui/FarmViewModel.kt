package io.github.untrustedguy.cardfarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.untrustedguy.cardfarm.data.SessionStore
import io.github.untrustedguy.cardfarm.steam.BadgeGame
import io.github.untrustedguy.cardfarm.steam.ConnectionState
import io.github.untrustedguy.cardfarm.steam.FarmCommand
import io.github.untrustedguy.cardfarm.steam.FarmRepository
import io.github.untrustedguy.cardfarm.steam.FarmingState
import io.github.untrustedguy.cardfarm.steam.GuardRequest
import io.github.untrustedguy.cardfarm.steam.SteamFarmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * UI-facing state holder. It reflects [FarmRepository] flows and forwards user
 * actions to the [SteamFarmService], starting the service on demand.
 */
class FarmViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionStore(app)

    val connection: StateFlow<ConnectionState> = FarmRepository.connection
    val statusText: StateFlow<String> = FarmRepository.statusText
    val accountName: StateFlow<String?> = FarmRepository.accountName
    val badges: StateFlow<List<BadgeGame>> = FarmRepository.badges
    val farming: StateFlow<FarmingState> = FarmRepository.farming
    val guardRequest: StateFlow<GuardRequest?> = FarmRepository.guardRequest
    val refreshingBadges: StateFlow<Boolean> = FarmRepository.refreshingBadges
    val messages: SharedFlow<String> = FarmRepository.messages

    /** True if a saved session exists so we can offer an auto-login. */
    val hasSavedSession: Boolean get() = session.hasSession

    private fun ensureService() {
        SteamFarmService.start(getApplication())
    }

    fun login(username: String, password: String) {
        ensureService()
        FarmRepository.send(FarmCommand.Login(username.trim(), password))
    }

    fun autoConnect() {
        if (!session.hasSession) return
        ensureService()
        FarmRepository.send(FarmCommand.Connect)
    }

    fun submitGuardCode(code: String) {
        guardRequest.value?.future?.complete(code.trim())
    }

    fun cancelGuard() {
        guardRequest.value?.future?.completeExceptionally(
            CancellationExceptionCompat("User cancelled Steam Guard")
        )
    }

    fun startCardFarming() = FarmRepository.send(FarmCommand.StartFarming)
    fun stopIdling() = FarmRepository.send(FarmCommand.StopIdling)
    fun refreshBadges() = FarmRepository.send(FarmCommand.RefreshBadges)
    fun idleGames(appIds: List<Int>) = FarmRepository.send(FarmCommand.IdleGames(appIds))
    fun logout() = FarmRepository.send(FarmCommand.Logout)

    /** Parsed selection of app IDs from the custom-idle text field. */
    fun parseAppIds(raw: String): List<Int> =
        raw.split(',', ' ', '\n', ';')
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
}

/** Small shim so we don't import java.util directly at the call site. */
private class CancellationExceptionCompat(message: String) :
    java.util.concurrent.CancellationException(message)
