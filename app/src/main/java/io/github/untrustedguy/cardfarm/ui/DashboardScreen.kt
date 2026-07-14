package io.github.untrustedguy.cardfarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.untrustedguy.cardfarm.steam.BadgeGame
import io.github.untrustedguy.cardfarm.steam.ConnectionState
import io.github.untrustedguy.cardfarm.steam.FarmingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    accountName: String?,
    connection: ConnectionState,
    statusText: String,
    farming: FarmingState,
    badges: List<BadgeGame>,
    refreshing: Boolean,
    onStartFarming: () -> Unit,
    onStopIdling: () -> Unit,
    onRefresh: () -> Unit,
    onIdleGames: (List<Int>) -> Unit,
    onParseAppIds: (String) -> List<Int>,
    onOpenLibrary: () -> Unit,
    onLogout: () -> Unit,
) {
    var showCustomIdle by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            accountName ?: "CardFarm",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            connection.label(),
                            style = MaterialTheme.typography.labelSmall,
                            color = connection.color(),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.Default.SportsEsports, contentDescription = "Your games")
                    }
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh badges")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StatusBanner(statusText, farming, badges)

            FarmControls(
                farming = farming,
                onStartFarming = onStartFarming,
                onStopIdling = onStopIdling,
                onCustomIdle = { showCustomIdle = true },
            )

            BadgeSection(
                badges = badges,
                refreshing = refreshing,
                farming = farming,
                onRefresh = onRefresh,
                onIdleSingle = { onIdleGames(listOf(it)) },
            )
        }
    }

    if (showCustomIdle) {
        CustomIdleDialog(
            onParseAppIds = onParseAppIds,
            onConfirm = {
                onIdleGames(it)
                showCustomIdle = false
            },
            onDismiss = { showCustomIdle = false },
        )
    }
}

@Composable
private fun StatusBanner(
    statusText: String,
    farming: FarmingState,
    badges: List<BadgeGame>,
) {
    val remainingDrops = badges.sumOf { it.dropsRemaining }
    val gamesWithDrops = badges.count { it.dropsRemaining > 0 }
    val isActive = farming !is FarmingState.Stopped

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = statusText.ifBlank { "Idle" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatBlock("$remainingDrops", "Card drops left")
                StatBlock("$gamesWithDrops", "Games to farm")
                StatBlock("${badges.size}", "Badges")
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FarmControls(
    farming: FarmingState,
    onStartFarming: () -> Unit,
    onStopIdling: () -> Unit,
    onCustomIdle: () -> Unit,
) {
    val isFarming = farming is FarmingState.CardFarming
    val isIdling = farming is FarmingState.CustomIdle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (farming is FarmingState.Stopped) {
            Button(
                onClick = onStartFarming,
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Farm cards")
            }
            OutlinedButton(
                onClick = onCustomIdle,
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Default.Timer, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Idle games")
            }
        } else {
            Button(
                onClick = onStopIdling,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isFarming) "Stop farming" else "Stop idling")
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BadgeSection(
    badges: List<BadgeGame>,
    refreshing: Boolean,
    farming: FarmingState,
    onRefresh: () -> Unit,
    onIdleSingle: (Int) -> Unit,
) {
    val activeIds = farming.activeAppIds.toSet()

    Box(Modifier.fillMaxSize()) {
        if (badges.isEmpty() && !refreshing) {
            EmptyBadges(onRefresh)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(badges, key = { it.appId }) { badge ->
                    BadgeRow(
                        badge = badge,
                        isActive = badge.appId in activeIds,
                        onIdle = { onIdleSingle(badge.appId) },
                    )
                }
            }
        }

        if (refreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun BadgeRow(
    badge: BadgeGame,
    isActive: Boolean,
    onIdle: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = badge.capsuleUrl,
                contentDescription = badge.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(92.dp)
                    .height(35.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    badge.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatChip(Icons.Default.Style, "${badge.dropsRemaining} drops")
                    if (badge.playtime.isNotBlank()) {
                        StatChip(Icons.Default.Timer, badge.playtime)
                    }
                }
            }
            if (isActive) {
                Badge(containerColor = MaterialTheme.colorScheme.secondary) { Text("Idling") }
            } else if (badge.dropsRemaining > 0) {
                IconButton(onClick = onIdle) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Idle this game",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBadges(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Style,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No badges loaded yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Tap refresh to scan your Steam library for card drops.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Refresh badges")
        }
    }
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.OFFLINE -> "Offline"
    ConnectionState.CONNECTING -> "Connecting…"
    ConnectionState.AUTHENTICATING -> "Authenticating…"
    ConnectionState.LOGGED_ON -> "Online"
}

@Composable
private fun ConnectionState.color() = when (this) {
    ConnectionState.LOGGED_ON -> MaterialTheme.colorScheme.secondary
    ConnectionState.OFFLINE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}
