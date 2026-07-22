package io.github.untrustedguy.cardfarm.steam

import android.util.Log
import io.github.untrustedguy.cardfarm.data.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implements the card-farming policy on top of [SteamController].
 *
 * Strategy (mirrors ArchiSteamFarm's default "Simple" algorithm):
 *  - Fetch badge pages to find games with remaining card drops.
 *  - Idle one game at a time and re-check periodically.
 *  - When a game's drops hit zero it's removed; when all are done, farming stops.
 */
class FarmingEngine(
    private val controller: SteamController,
    private val session: SessionStore,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val TAG = "FarmingEngine"
        // Re-scan badges this often while farming (Steam drops are periodic).
        const val FARM_RECHECK_MS = 15 * 60 * 1000L
    }

    private val scraper = BadgeScraper()

    @Volatile private var farmJob: Job? = null
    @Volatile private var desiredState: FarmingState = FarmingState.Stopped

    /** Called by the controller once we're fully logged on. */
    fun onLoggedOn() {
        // Resume whatever we were doing before the process/connection died.
        when (val state = desiredState) {
            is FarmingState.CardFarming -> startCardFarming()
            is FarmingState.CustomIdle -> idleGames(state.appIds)
            FarmingState.Stopped -> if (session.wasFarming) startCardFarming()
        }
    }

    fun onDisconnected() {
        // Keep desiredState so we resume on reconnect, but cancel the active loop.
        farmJob?.cancel()
        farmJob = null
    }

    fun stop() {
        desiredState = FarmingState.Stopped
        session.wasFarming = false
        farmJob?.cancel()
        farmJob = null
        controller.playGames(emptyList())
        FarmRepository.farming.value = FarmingState.Stopped
        FarmRepository.statusText.value = "Idle"
    }

    fun idleGames(appIds: List<Int>) {
        if (appIds.isEmpty()) {
            stop()
            return
        }
        val activeAppIds = listOf(appIds.first())
        desiredState = FarmingState.CustomIdle(activeAppIds)
        session.wasFarming = false
        farmJob?.cancel()
        farmJob = null
        controller.playGames(activeAppIds)
        FarmRepository.farming.value = FarmingState.CustomIdle(activeAppIds)
        FarmRepository.statusText.value = "Idling game ${activeAppIds.first()}"
    }

    fun startCardFarming() {
        desiredState = FarmingState.CardFarming(emptyList())
        session.wasFarming = true
        farmJob?.cancel()
        farmJob = scope.launch(Dispatchers.IO) { farmLoop() }
    }

    fun refreshBadges() {
        scope.launch(Dispatchers.IO) {
            FarmRepository.refreshingBadges.value = true
            try {
                val badges = loadBadges()
                FarmRepository.badges.value = badges
            } catch (e: WebSessionExpiredException) {
                FarmRepository.postMessage("Web session expired — reconnecting may be needed.")
            } catch (e: Exception) {
                Log.e(TAG, "Badge refresh failed", e)
                FarmRepository.postMessage("Couldn't load badges: ${e.message}")
            } finally {
                FarmRepository.refreshingBadges.value = false
            }
        }
    }

    fun loadLibrary() {
        scope.launch(Dispatchers.IO) {
            FarmRepository.loadingLibrary.value = true
            try {
                val games = controller.fetchOwnedGames()
                FarmRepository.library.value = games.sortedByDescending { it.playtimeForeverMinutes }
            } catch (e: Exception) {
                Log.e(TAG, "Library load failed", e)
                FarmRepository.postMessage("Couldn't load library: ${e.message}")
            } finally {
                FarmRepository.loadingLibrary.value = false
            }
        }
    }

    private suspend fun farmLoop() {
        FarmRepository.statusText.value = "Scanning badges…"
        // delay() below is cancellable, so cancelling farmJob breaks the loop;
        // the desiredState guard stops it after an explicit stop()/idle switch.
        while (currentCoroutineContext().isActive && desiredState is FarmingState.CardFarming) {
            val badges = try {
                loadBadges().also { FarmRepository.badges.value = it }
            } catch (e: WebSessionExpiredException) {
                FarmRepository.postMessage("Web session expired — will retry.")
                delay(60_000)
                continue
            } catch (e: Exception) {
                Log.e(TAG, "Farm scan failed", e)
                FarmRepository.statusText.value = "Scan failed, retrying…"
                delay(60_000)
                continue
            }

            val farmable = badges.filter { it.dropsRemaining > 0 }
            if (farmable.isEmpty()) {
                controller.playGames(emptyList())
                FarmRepository.farming.value = FarmingState.Stopped
                FarmRepository.statusText.value = "All cards farmed! 🎉"
                session.wasFarming = false
                desiredState = FarmingState.Stopped
                FarmRepository.postMessage("Card farming complete — no drops remaining.")
                return
            }

            val activeGame = farmable.first()
            val activeAppIds = listOf(activeGame.appId)
            controller.playGames(activeAppIds)
            val totalDrops = farmable.sumOf { it.dropsRemaining }
            FarmRepository.farming.value = FarmingState.CardFarming(activeAppIds)
            FarmRepository.statusText.value =
                "Farming ${activeGame.name}, $totalDrops total drop(s) left"

            delay(FARM_RECHECK_MS)
        }
    }

    /** Mints a fresh web access token from the stored refresh token, then scrapes. */
    private suspend fun loadBadges(): List<BadgeGame> = withContext(Dispatchers.IO) {
        val steamId = controller.currentSteamId
            ?: throw IllegalStateException("Not logged on")
        val refreshToken = session.refreshToken
            ?: throw IllegalStateException("No refresh token")

        val accessToken = controller.generateWebAccessToken(steamId.convertToUInt64(), refreshToken)
        scraper.fetchBadges(steamId.convertToUInt64(), accessToken)
    }
}
