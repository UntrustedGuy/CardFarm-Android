package io.github.untrustedguy.cardfarm.steam

import android.util.Log
import io.github.untrustedguy.cardfarm.data.SessionStore
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGamesPlayed
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPlayerSteamclient.CPlayer_GetOwnedGames_Request
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesAuthSteamclient.CAuthentication_AllowedConfirmation
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesAuthSteamclient.EAuthSessionGuardType
import `in`.dragonbra.javasteam.rpc.service.Player
import `in`.dragonbra.javasteam.steam.authentication.AuthPollResult
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import `in`.dragonbra.javasteam.steam.authentication.CredentialsAuthSession
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.types.SteamID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * Owns the JavaSteam [SteamClient] and its callback loop. All Steam network
 * activity happens on the single [callbackThread]; public methods are safe to
 * call from any thread and post work onto it.
 *
 * The card-farming policy itself lives in [FarmingEngine]; this class only
 * handles connection, authentication and the low-level "play games" message.
 */
class SteamController(
    private val session: SessionStore,
    private val scope: CoroutineScope,
) {

    private companion object {
        const val TAG = "SteamController"
        // Distinct from any real client so Steam doesn't kick our own session.
        // 0x4641524D spells "FARM"; fits in a positive Int.
        const val LOGIN_ID = 0x4641524D
    }

    private val steamClient = SteamClient()
    private val manager = CallbackManager(steamClient)
    private val steamUser: SteamUser = steamClient.getHandler(SteamUser::class.java)!!
    private val unifiedMessages: SteamUnifiedMessages =
        steamClient.getHandler(SteamUnifiedMessages::class.java)!!
    private val playerService: Player by lazy { unifiedMessages.createService(Player::class.java) }
    private val subscriptions = mutableListOf<Closeable>()
    private val authenticator = UiAuthenticator()
    private val farmingEngine = FarmingEngine(this, session, scope)

    @Volatile private var running = false
    @Volatile private var callbackThread: Thread? = null

    /** Set when the user asked to log in with fresh credentials. */
    @Volatile private var pendingCredentials: Pair<String, String>? = null
    /** Set when reconnecting with a stored refresh token. */
    @Volatile private var pendingReconnect = false
    @Volatile private var intentionalDisconnect = false

    fun start() {
        if (running) return
        running = true
        subscriptions += manager.subscribe(ConnectedCallback::class.java, ::onConnected)
        subscriptions += manager.subscribe(DisconnectedCallback::class.java, ::onDisconnected)
        subscriptions += manager.subscribe(LoggedOnCallback::class.java, ::onLoggedOn)
        subscriptions += manager.subscribe(LoggedOffCallback::class.java, ::onLoggedOff)

        callbackThread = Thread({
            while (running) {
                try {
                    manager.runWaitCallbacks(1000L)
                } catch (t: Throwable) {
                    Log.e(TAG, "Callback loop error", t)
                }
            }
        }, "steam-callbacks").apply { isDaemon = true; start() }
    }

    fun shutdown() {
        running = false
        try {
            farmingEngine.stop()
            if (steamClient.isConnected) steamUser.logOff()
            steamClient.disconnect()
        } catch (t: Throwable) {
            Log.w(TAG, "Error during shutdown", t)
        }
        subscriptions.forEach { runCatching { it.close() } }
        subscriptions.clear()
        callbackThread = null
    }

    // ---- Public commands (thread-safe entry points) ------------------------

    fun loginWithCredentials(username: String, password: String) {
        pendingCredentials = username to password
        pendingReconnect = false
        intentionalDisconnect = false
        FarmRepository.connection.value = ConnectionState.CONNECTING
        FarmRepository.statusText.value = "Connecting to Steam…"
        connect()
    }

    fun reconnectWithSession() {
        if (!session.hasSession) {
            FarmRepository.postMessage("No saved session — please log in.")
            FarmRepository.connection.value = ConnectionState.OFFLINE
            return
        }
        pendingCredentials = null
        pendingReconnect = true
        intentionalDisconnect = false
        FarmRepository.accountName.value = session.accountName
        FarmRepository.connection.value = ConnectionState.CONNECTING
        FarmRepository.statusText.value = "Reconnecting…"
        connect()
    }

    fun logout() {
        intentionalDisconnect = true
        session.clearSession()
        farmingEngine.stop()
        FarmRepository.badges.value = emptyList()
        FarmRepository.library.value = emptyList()
        FarmRepository.accountName.value = null
        runCatching {
            if (steamClient.isConnected) steamUser.logOff()
            steamClient.disconnect()
        }
        FarmRepository.resetToOffline()
        FarmRepository.statusText.value = "Logged out"
    }

    fun startCardFarming() = farmingEngine.startCardFarming()
    fun idleGames(appIds: List<Int>) = farmingEngine.idleGames(appIds)
    fun stopIdling() = farmingEngine.stop()
    fun refreshBadges() = farmingEngine.refreshBadges()
    fun loadLibrary() = farmingEngine.loadLibrary()

    // ---- Internal ----------------------------------------------------------

    private fun connect() {
        runCatching {
            if (steamClient.isConnected) steamClient.disconnect()
        }
        steamClient.connect()
    }

    /**
     * Sends a ClientGamesPlayed message. An empty list stops idling; up to ~32
     * app IDs can be idled at once (Steam's limit), matching ASF behaviour.
     */
    fun playGames(appIds: List<Int>) {
        if (!steamClient.isConnected) return
        val request = ClientMsgProtobuf<CMsgClientGamesPlayed.Builder>(
            CMsgClientGamesPlayed::class.java,
            EMsg.ClientGamesPlayed,
        ).apply {
            appIds.forEach { appId ->
                body.addGamesPlayedBuilder().setGameId(appId.toLong())
            }
        }
        steamClient.send(request)
        Log.d(TAG, "Now playing ${appIds.size} game(s): $appIds")
    }

    val currentSteamId: SteamID?
        get() = steamClient.steamID

    /**
     * Fetches the account's full owned-games library via Steam's native
     * Player.GetOwnedGames unified-service RPC — sent over the existing Steam
     * connection, no HTTP request, no Web API key, no scraping. Blocking —
     * call from a background thread.
     */
    fun fetchOwnedGames(): List<OwnedGame> {
        val steamId = currentSteamId ?: throw IllegalStateException("Not logged on")
        val request = CPlayer_GetOwnedGames_Request.newBuilder()
            .setSteamid(steamId.convertToUInt64())
            .setIncludeAppinfo(true)
            .setIncludePlayedFreeGames(true)
            .build()

        val response = playerService.getOwnedGames(request).toFuture().get()
        if (response.result != EResult.OK) {
            throw IllegalStateException("GetOwnedGames failed: ${response.result}")
        }

        return response.body.gamesList.map { game ->
            OwnedGame(
                appId = game.appid,
                name = game.name,
                playtimeForeverMinutes = game.playtimeForever,
            )
        }
    }

    /**
     * Exchanges a long-lived refresh token for a short-lived web access token,
     * used as the steamLoginSecure cookie when scraping badge pages. Blocking —
     * call from a background thread.
     */
    fun generateWebAccessToken(steamId64: Long, refreshToken: String): String {
        val steamId = SteamID(steamId64)
        val result = steamClient.authentication
            .generateAccessTokenForApp(steamId, refreshToken, false).get()
        // Persist a rotated refresh token if Steam issued one.
        result.refreshToken.takeIf { it.isNotEmpty() }?.let { session.refreshToken = it }
        return result.accessToken
    }

    /**
     * Resolves Steam Guard for a credentials login.
     *
     * By default this just polls silently for the user to tap Approve in the
     * real Steam app — no code dialog is shown at all, which avoids the race
     * that was making the real Steam app choke (this app submitting a code
     * while a push confirmation was still pending on the same login session).
     *
     * The code dialog only appears if the push is explicitly *denied*
     * (EResult.AccessDenied) and the account also allows a code as a
     * fallback — so denying on the phone lets you finish by typing a code
     * instead of having to restart the whole login.
     */
    private fun resolveGuardAndPoll(authSession: CredentialsAuthSession): AuthPollResult {
        val allowsDeviceConfirmation = authSession.allowedConfirmations.any {
            it.confirmationType == EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceConfirmation
        }
        val codeConfirmation = authSession.allowedConfirmations.firstOrNull {
            it.confirmationType == EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceCode ||
                it.confirmationType == EAuthSessionGuardType.k_EAuthSessionGuardType_EmailCode
        }

        if (!allowsDeviceConfirmation) {
            // No phone push offered for this account — use the normal code flow.
            return authSession.pollingWaitForResult().getUnwrapped()
        }

        return try {
            pollSilently(authSession)
        } catch (e: AuthenticationException) {
            val wasDenied = e.result == EResult.AccessDenied
            if (!wasDenied || codeConfirmation == null) throw e

            Log.i(TAG, "Device confirmation denied — falling back to a manual code")
            FarmRepository.postMessage("Login denied on phone — enter your Steam Guard code instead.")
            resolveWithCode(authSession, codeConfirmation)
        }
    }

    /** Blocks, polling every couple seconds, until the phone push is approved/denied/expired. */
    private fun pollSilently(authSession: CredentialsAuthSession): AuthPollResult {
        while (true) {
            authSession.pollAuthSessionStatus().getUnwrapped()?.let { return it }
            Thread.sleep(2000L)
        }
    }

    /** Shows the code dialog and blocks until a valid code is submitted or the user cancels. */
    private fun resolveWithCode(
        authSession: CredentialsAuthSession,
        codeConfirmation: CAuthentication_AllowedConfirmation,
    ): AuthPollResult {
        val guardType = if (codeConfirmation.confirmationType == EAuthSessionGuardType.k_EAuthSessionGuardType_EmailCode) {
            GuardType.EMAIL_CODE
        } else {
            GuardType.DEVICE_CODE
        }
        var previousCodeWasIncorrect = false

        while (true) {
            val guardRequest = GuardRequest(
                type = guardType,
                email = codeConfirmation.associatedMessage.takeIf { guardType == GuardType.EMAIL_CODE },
                previousCodeWasIncorrect = previousCodeWasIncorrect,
            )
            FarmRepository.guardRequest.value = guardRequest

            val code = try {
                guardRequest.future.get()
            } catch (e: Exception) {
                throw AuthenticationException("Steam Guard cancelled")
            } finally {
                FarmRepository.guardRequest.value = null
            }

            try {
                authSession.sendSteamGuardCode(code, codeConfirmation.confirmationType).getUnwrapped()
            } catch (e: AuthenticationException) {
                Log.w(TAG, "Guard code rejected", e)
                previousCodeWasIncorrect = true
                continue
            }

            return pollSilently(authSession)
        }
    }

    private fun onConnected(callback: ConnectedCallback) {
        Log.i(TAG, "Connected to Steam")
        scope.launch(Dispatchers.IO) { authenticate() }
    }

    private fun authenticate() {
        FarmRepository.connection.value = ConnectionState.AUTHENTICATING
        try {
            val reconnect = pendingReconnect
            val creds = pendingCredentials

            val logOnDetails = LogOnDetails().apply { loginID = LOGIN_ID }

            if (reconnect) {
                FarmRepository.statusText.value = "Signing in…"
                logOnDetails.username = session.accountName.orEmpty()
                logOnDetails.accessToken = session.refreshToken
            } else if (creds != null) {
                FarmRepository.statusText.value = "Authenticating…"
                val details = AuthSessionDetails().apply {
                    username = creds.first
                    password = creds.second
                    persistentSession = true
                    guardData = session.guardData
                    authenticator = this@SteamController.authenticator
                }

                val authSession = steamClient.authentication
                    .beginAuthSessionViaCredentials(details).getUnwrapped()

                val pollResult = resolveGuardAndPoll(authSession)

                pollResult.newGuardData?.let { session.guardData = it }
                session.accountName = pollResult.accountName
                session.refreshToken = pollResult.refreshToken

                logOnDetails.username = pollResult.accountName
                logOnDetails.accessToken = pollResult.refreshToken
            } else {
                FarmRepository.postMessage("Nothing to log in with.")
                return
            }

            steamUser.logOn(logOnDetails)
        } catch (e: AuthenticationException) {
            Log.e(TAG, "Auth failed", e)
            FarmRepository.postMessage("Login failed: ${e.message}")
            FarmRepository.connection.value = ConnectionState.OFFLINE
            intentionalDisconnect = true
            runCatching { steamClient.disconnect() }
        } catch (e: Exception) {
            Log.e(TAG, "Auth error", e)
            FarmRepository.postMessage("Login error: ${e.message ?: e.javaClass.simpleName}")
            FarmRepository.connection.value = ConnectionState.OFFLINE
            intentionalDisconnect = true
            runCatching { steamClient.disconnect() }
        } finally {
            pendingCredentials = null
        }
    }

    private fun onLoggedOn(callback: LoggedOnCallback) {
        if (callback.result != EResult.OK) {
            Log.w(TAG, "Logon failed: ${callback.result} / ${callback.extendedResult}")
            when (callback.result) {
                EResult.InvalidPassword, EResult.AccessDenied, EResult.Expired -> {
                    // Stored refresh token is dead — force a fresh credential login.
                    if (pendingReconnect) {
                        session.clearSession()
                        FarmRepository.postMessage("Session expired. Please log in again.")
                    } else {
                        FarmRepository.postMessage("Login denied: ${callback.result}")
                    }
                    intentionalDisconnect = true
                    FarmRepository.resetToOffline()
                    runCatching { steamClient.disconnect() }
                }
                else -> {
                    FarmRepository.postMessage("Unable to log on: ${callback.result}")
                    // Non-fatal: let the auto-reconnect on disconnect retry.
                }
            }
            return
        }

        Log.i(TAG, "Logged on as ${session.accountName}")
        pendingReconnect = false
        callback.clientSteamID?.let { session.steamId64 = it.convertToUInt64() }
        FarmRepository.accountName.value = session.accountName
        FarmRepository.connection.value = ConnectionState.LOGGED_ON
        FarmRepository.statusText.value = "Signed in"

        farmingEngine.onLoggedOn()
    }

    private fun onLoggedOff(callback: LoggedOffCallback) {
        Log.i(TAG, "Logged off: ${callback.result}")
        FarmRepository.statusText.value = "Logged off: ${callback.result}"
    }

    private fun onDisconnected(callback: DisconnectedCallback) {
        Log.i(TAG, "Disconnected (userInitiated=${callback.isUserInitiated})")
        farmingEngine.onDisconnected()

        if (intentionalDisconnect || !running) {
            FarmRepository.connection.value = ConnectionState.OFFLINE
            return
        }

        // Unexpected drop: retry with backoff, resuming with the saved session.
        FarmRepository.connection.value = ConnectionState.CONNECTING
        FarmRepository.statusText.value = "Connection lost — reconnecting…"
        pendingReconnect = true
        pendingCredentials = null
        scope.launch(Dispatchers.IO) {
            try {
                Thread.sleep(3000L)
            } catch (_: InterruptedException) {
            }
            if (running && !intentionalDisconnect) connect()
        }
    }
}

/**
 * JavaSteam's auth methods (`pollAuthSessionStatus`, `sendSteamGuardCode`, …)
 * are built with kotlinx-coroutines' `future{}` builder and documented as
 * `@Throws(AuthenticationException::class)`. But `Future.get()` always wraps
 * whatever the underlying computation threw in [ExecutionException], per the
 * plain-Java `Future` contract — coroutine builders don't change that. A bare
 * `catch (e: AuthenticationException)` around a `.get()` call on one of these
 * futures never fires; the real exception is `e.cause`. Callers that need to
 * inspect the failure (e.g. distinguishing an explicit Steam Guard denial
 * from other errors) must unwrap through this instead of calling `.get()`.
 */
private fun <T> CompletableFuture<T>.getUnwrapped(): T = try {
    get()
} catch (e: ExecutionException) {
    throw e.cause as? AuthenticationException ?: e
}
