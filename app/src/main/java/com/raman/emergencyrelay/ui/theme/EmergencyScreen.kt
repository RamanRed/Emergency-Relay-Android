package com.raman.emergencyrelay.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raman.emergencyrelay.model.EmergencyCode
import com.raman.emergencyrelay.model.EmergencyMessage

/* Human-readable name for a received emergency code. */
private fun EmergencyCode.label(): String = when (this) {
    EmergencyCode.MEDICAL -> "Medical"
    EmergencyCode.FIRE -> "Fire"
    EmergencyCode.POLICE -> "Police"
    EmergencyCode.ACCIDENT -> "Accident"
    EmergencyCode.TRAPPED -> "Trapped"
    EmergencyCode.MISSING_PERSON -> "Missing person"
    EmergencyCode.GENERAL_SOS -> "General SOS"
}

@Composable
fun EmergencyScreen(
    status: String,
    relayEnabled: Boolean,
    pendingCount: Int,
    receivedMessages: List<EmergencyMessage>,
    onSendEmergency: (EmergencyCode, Int) -> Unit,
    onRelayToggle: (Boolean) -> Unit,
    onCall: (String) -> Unit
) {

    var selectedTab by remember { mutableIntStateOf(0) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var pendingRelayEnable by remember { mutableStateOf(false) }

    if (showConsentDialog) {
        ConsentDialog(
            onConfirm = {
                showConsentDialog = false
                if (pendingRelayEnable) onRelayToggle(true)
                pendingRelayEnable = false
            },
            onDismiss = {
                showConsentDialog = false
                pendingRelayEnable = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ER",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Emergency Relay",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Stay connected when it matters",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    status = status,
                    pendingCount = pendingCount,
                    onSendEmergency = onSendEmergency,
                    onCall = onCall
                )
                1 -> ReceivedScreen(messages = receivedMessages)
                2 -> RelayScreen(
                    relayEnabled = relayEnabled,
                    onRelayToggle = { enabled ->
                        if (enabled) {
                            pendingRelayEnable = true
                            showConsentDialog = true
                        } else {
                            onRelayToggle(false)
                        }
                    }
                )
            }
        }

        // Bottom navigation
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavigationItem("Home", selectedTab == 0) { selectedTab = 0 }
            NavigationItem(
                if (receivedMessages.isEmpty()) "Received"
                else "Received (${receivedMessages.size})",
                selectedTab == 1
            ) { selectedTab = 1 }
            NavigationItem("Relay", selectedTab == 2) { selectedTab = 2 }
        }
    }
}


/* ------------------------------------------------------------------ */
/* HOME — SOS wheel                                                   */
/* ------------------------------------------------------------------ */

@Composable
private fun HomeScreen(
    status: String,
    pendingCount: Int,
    onSendEmergency: (EmergencyCode, Int) -> Unit,
    onCall: (String) -> Unit
) {
    // null = centre (General SOS); 0..5 = a ring wedge
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val selected = if (selectedIndex == null) SOS_CENTER else SOS_TYPES[selectedIndex!!]

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Hold the centre to send an SOS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Or hold a type on the ring to be specific",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            SosWheel(
                selectedIndex = selectedIndex,
                onPreview = { idx -> selectedIndex = idx },
                onSend = { idx ->
                    val t = if (idx == null) SOS_CENTER else SOS_TYPES[idx]
                    EmergencyCode.fromCode(t.code)?.let { code ->
                        onSendEmergency(code, t.severity)
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Readout of the current selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "WILL SEND",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selected.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Severity ${selected.severity}  •  hold to send",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Current status shown as a friendly pill. Replaced by
            // the full lifecycle timeline once the SOS state machine
            // lands.
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Queued indicator
            if (pendingCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = if (pendingCount == 1)
                            "1 message is saved and will be delivered when a connection is found."
                        else
                            "$pendingCount messages are saved and will be delivered when a connection is found.",
                        modifier = Modifier.padding(14.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Call for help (always available). Opens the dialer.
            OutlinedButton(
                onClick = { onCall("112") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Call 112 — all emergencies", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("How it works", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tries the internet first. If there's no path, it hands your alert to a nearby phone and keeps retrying — even after the app is closed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}


/* ------------------------------------------------------------------ */
/* CONSENT DIALOG                                                     */
/* ------------------------------------------------------------------ */

@Composable
private fun ConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable Emergency Relay?") },
        text = {
            Column {
                Text(
                    "Relay Mode lets your phone help forward emergency messages from other participating users."
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("When enabled:", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "• Your phone may discover nearby participating devices.\n\n" +
                            "• It may receive emergency messages from this app.\n\n" +
                            "• It may temporarily forward them to another participating device.\n\n" +
                            "• Relay is voluntary and can be turned off at any time."
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No access to your contacts, SMS, microphone or camera is needed for relay."
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("I understand & continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


/* ------------------------------------------------------------------ */
/* RECEIVED                                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun ReceivedScreen(
    messages: List<EmergencyMessage>
) {
    if (messages.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "No messages yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Emergency messages carried or forwarded through this phone will appear here.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            item {
                Text(
                    "Received emergencies",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Messages carried or forwarded through this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(messages) { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "!",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(message.emergencyCode.label(), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "Severity ${message.severity}  •  Hops ${message.hopCount}  •  TTL ${message.ttl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "From ${message.sourceId.take(8)}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}


/* ------------------------------------------------------------------ */
/* RELAY                                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun RelayScreen(
    relayEnabled: Boolean,
    onRelayToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            "Relay mode",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Allow this phone to help forward emergency messages from nearby participating users.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (relayEnabled) "Relay mode ON" else "Relay mode OFF",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        if (relayEnabled)
                            "Your phone can carry and forward emergency messages."
                        else
                            "Your phone is not participating as a relay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = relayEnabled, onCheckedChange = { onRelayToggle(it) })
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Your control & privacy",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Relay is voluntary — turn it off anytime. You can't read the messages you carry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )
            }
        }
    }
}


/* ------------------------------------------------------------------ */
/* BOTTOM NAVIGATION                                                  */
/* ------------------------------------------------------------------ */

@Composable
private fun NavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
