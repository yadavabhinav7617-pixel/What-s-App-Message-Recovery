package com.notifyvault.app.ui.screens

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.notifyvault.app.data.MessageEntity
import java.util.Date

@Composable
fun DetailScreen(message: MessageEntity?, navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Message details",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        if (message == null) {
            EmptyStateCard(
                title = "Message unavailable",
                message = "This saved notification could not be loaded from the local database.",
                icon = Icons.Default.Delete
            )
            return
        }

        Card(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.sender.ifBlank { "Unknown sender" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    InfoChip(
                        text = message.packageName.substringAfterLast('.').ifBlank { "App" },
                        icon = Icons.AutoMirrored.Outlined.Chat,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = message.messageText.ifBlank { "Notification text unavailable" },
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            val clip = android.content.ClipData.newPlainText("message", message.messageText)
                            clipboard?.setPrimaryClip(clip)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 6.dp))
                        Text("Copy")
                    }
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message.messageText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share message"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 6.dp))
                        Text("Share")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Captured information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                DetailRow(label = "Captured", value = DateFormat.format("MMM d, yyyy • h:mm a", Date(message.capturedAt)).toString())
                DetailRow(label = "Original notification", value = DateFormat.format("MMM d, yyyy • h:mm a", Date(message.timestamp)).toString())
                DetailRow(label = "Source app", value = message.packageName)
                DetailRow(label = "Capture status", value = if (message.messageText.isNotBlank()) "Stored locally" else "Text unavailable")
                if (!message.notificationTitle.isNullOrBlank()) {
                    DetailRow(label = "Notification title", value = message.notificationTitle)
                }
                if (message.notificationKey != null) {
                    DetailRow(label = "Notification key", value = message.notificationKey)
                }
            }
        }

        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to messages")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
