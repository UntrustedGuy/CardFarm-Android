package io.github.untrustedguy.cardfarm

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.untrustedguy.cardfarm.steam.ConnectionState
import io.github.untrustedguy.cardfarm.ui.DashboardScreen
import io.github.untrustedguy.cardfarm.ui.FarmViewModel
import io.github.untrustedguy.cardfarm.ui.LoginScreen
import io.github.untrustedguy.cardfarm.ui.SteamGuardDialog
import io.github.untrustedguy.cardfarm.ui.theme.CardFarmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardFarmTheme {
                CardFarmRoot()
            }
        }
    }
}

@Composable
private fun CardFarmRoot(viewModel: FarmViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val statusText by viewModel.statusText.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val farming by viewModel.farming.collectAsStateWithLifecycle()
    val guardRequest by viewModel.guardRequest.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshingBadges.collectAsStateWithLifecycle()

    // Ask for notification permission (Android 13+) so the FGS notice shows.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — the service still runs regardless */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Auto-resume a saved session on cold start.
        if (viewModel.hasSavedSession && connection == ConnectionState.OFFLINE) {
            viewModel.autoConnect()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val loggedOn = connection == ConnectionState.LOGGED_ON

        if (loggedOn) {
            DashboardScreen(
                accountName = accountName,
                connection = connection,
                statusText = statusText,
                farming = farming,
                badges = badges,
                refreshing = refreshing,
                onStartFarming = viewModel::startCardFarming,
                onStopIdling = viewModel::stopIdling,
                onRefresh = viewModel::refreshBadges,
                onIdleGames = viewModel::idleGames,
                onParseAppIds = viewModel::parseAppIds,
                onLogout = viewModel::logout,
            )
        } else {
            LoginScreen(
                connection = connection,
                statusText = statusText,
                hasSavedSession = viewModel.hasSavedSession,
                onLogin = viewModel::login,
                onAutoConnect = viewModel::autoConnect,
                modifier = Modifier.padding(padding),
            )
        }
    }

    guardRequest?.let { request ->
        SteamGuardDialog(
            request = request,
            onSubmit = viewModel::submitGuardCode,
            onCancel = viewModel::cancelGuard,
        )
    }
}
