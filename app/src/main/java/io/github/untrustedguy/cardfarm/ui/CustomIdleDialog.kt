package io.github.untrustedguy.cardfarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CustomIdleDialog(
    onParseAppIds: (String) -> List<Int>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    val parsed = onParseAppIds(raw)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Idle specific games") },
        text = {
            Column {
                Text(
                    "Enter Steam App IDs to idle, separated by commas or spaces. " +
                        "You can find an App ID in a game's store URL.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    label = { Text("App IDs") },
                    placeholder = { Text("e.g. 730, 440, 570") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (parsed.isEmpty()) "No valid App IDs yet"
                    else "${parsed.size} game(s): ${parsed.joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (parsed.size > 32) {
                    Text(
                        "Steam idles at most 32 games at once — extras are ignored.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsed) },
                enabled = parsed.isNotEmpty(),
            ) { Text("Start idling") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
