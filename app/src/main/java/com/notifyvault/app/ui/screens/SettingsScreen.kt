package com.notifyvault.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.notifyvault.app.service.SupportNotificationAccess
import com.notifyvault.app.ui.navigation.AppDestinations
import com.notifyvault.app.ui.theme.AppThemeMode
import com.notifyvault.app.ui.viewmodel.MessageViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    viewModel: MessageViewModel,
    navController: NavController,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAgeDeleteConfirm by remember { mutableStateOf<Int?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Secret Admin Portal state - REMOVED
    // val tapCount ... 

    // Admin unlocked fields state
    var backendUrlInput by remember(uiState.backendUrl) { mutableStateOf(uiState.backendUrl) }
    var accountEmailInput by remember(uiState.accountEmail) { mutableStateOf(uiState.accountEmail) }
    var devTextEditing by remember(uiState.developerText) { mutableStateOf(uiState.developerText) }
    var devUrlEditing by remember(uiState.developerUrl) { mutableStateOf(uiState.developerUrl) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(viewModel.exportMessagesAsJson().toByteArray(Charsets.UTF_8))
            }
        }
    }

    val oldest = uiState.oldestTimestamp
    val newest = uiState.newestTimestamp
    val storageEstimateKb = (uiState.storageEstimateBytes / 1024.0).coerceAtLeast(0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Account Section
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.isUserLoggedIn) {
                    Text(
                        text = "Logged in as:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.accountEmail,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.logoutUser()
                            navController.navigate(AppDestinations.LOGIN) {
                                popUpTo(0)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Log Out")
                    }
                } else {
                    Text(
                        text = "Not logged in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Notification Access",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AnimatedStatusBadge(uiState.notificationAccessGranted)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (uiState.notificationAccessGranted) {
                        "NotifyVault is connected and can read WhatsApp notifications exposed by Android."
                    } else {
                        "Notification Access is off. Enable it to capture supported notification content."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { SupportNotificationAccess.openNotificationAccessSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable / Manage Access")
                }
                if (!uiState.notificationAccessGranted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { SupportNotificationAccess.openAppInfoSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unlock Restricted Settings (App Info)")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Cloud Sync",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cloud Backup",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.cloudSyncEnabled,
                        onCheckedChange = { viewModel.setCloudSyncEnabled(it) }
                    )
                }
                Text(
                    text = "Connection: ${if (uiState.connectionOnline) "Online" else "Offline"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Last successful sync: ${if (uiState.lastSyncTimestamp > 0L) formatDate(uiState.lastSyncTimestamp) else "Not yet"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Pending messages: ${uiState.pendingSyncCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Failed messages: ${uiState.failedSyncCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Registered device: ${uiState.registeredDeviceName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.refreshCloudStatus() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sync Now")
                }
            }
        }

        if (false) { // Disabled config ui for normal users
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Admin Portal Active",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(onClick = { viewModel.logoutUser() }) {
                            Text("Logout Admin")
                        }
                    }

                    OutlinedTextField(
                        value = backendUrlInput,
                        onValueChange = { backendUrlInput = it },
                        label = { Text("Server Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = accountEmailInput,
                        onValueChange = { accountEmailInput = it },
                        label = { Text("Account Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = devTextEditing,
                        onValueChange = { devTextEditing = it },
                        label = { Text("Developer Credit Text") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = devUrlEditing,
                        onValueChange = { devUrlEditing = it },
                        label = { Text("Developer Website URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            viewModel.updateBackendUrl(backendUrlInput)
                            viewModel.updateAccountEmail(accountEmailInput)
                            viewModel.updateDeveloperInfo(devTextEditing, devUrlEditing)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save All Config")
                    }
                }
            }

            // Remote Monitoring Section (Visible only to Admin)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Connected Devices Monitoring",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "View live notifications and browse history from other registered devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    var selectedDeviceForHistory by remember { mutableStateOf<String?>(null) }
                    
                    val dummyDevices = listOf("Target Device 1 (S23)", "Target Device 2 (Pixel)")
                    dummyDevices.forEach { deviceName ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = deviceName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "● Syncing",
                                        color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { /* Not implemented locally */ },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 4.dp).height(16.dp)
                                        )
                                        Text("Notifications", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = { selectedDeviceForHistory = deviceName },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Default.PrivacyTip,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 4.dp).height(16.dp)
                                        )
                                        Text("Browse History", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    
                    if (selectedDeviceForHistory != null) {
                        AlertDialog(
                            onDismissRequest = { selectedDeviceForHistory = null },
                            title = { Text("Browse History: $selectedDeviceForHistory") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "Remote data sync is disabled for safety. Showing sample UI data only.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    val dummyHistory = listOf(
                                        "Google Search" to "https://www.google.com/search?q=notifyvault",
                                        "Wikipedia" to "https://en.wikipedia.org",
                                        "YouTube" to "https://m.youtube.com"
                                    )
                                    dummyHistory.forEach { (title, url) ->
                                        Column {
                                            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { selectedDeviceForHistory = null }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Monitoring",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Messaging apps monitoring",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = true, onCheckedChange = null)
                }
                Text(
                    text = "Current monitoring list: WhatsApp, Instagram, Snapchat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("${messages.size} saved messages", style = MaterialTheme.typography.bodyLarge)
                Text("Approx. ${"%.1f".format(storageEstimateKb)} KB local storage", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (oldest != null && newest != null) "Oldest: ${formatDate(oldest)} • Newest: ${formatDate(newest)}" else "No saved messages yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth(), enabled = messages.isNotEmpty()) {
                    Text("Delete all messages")
                }
                Spacer(modifier = Modifier.height(8.dp))
                listOf(7 to "Delete older than 7 days", 30 to "Delete older than 30 days", 90 to "Delete older than 90 days").forEach { (days, label) ->
                    Button(
                        onClick = { showAgeDeleteConfirm = days },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        enabled = messages.isNotEmpty()
                    ) {
                        Text(label)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export saved data as JSON")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    AppThemeMode.LIGHT to "Light",
                    AppThemeMode.DARK to "Dark",
                    AppThemeMode.SYSTEM to "System default"
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onThemeModeChange(mode) }
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = when (mode) {
                                AppThemeMode.LIGHT -> Icons.Default.LightMode
                                AppThemeMode.DARK -> Icons.Default.DarkMode
                                else -> Icons.Default.SystemUpdateAlt
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (themeMode == mode) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("NotifyVault", style = MaterialTheme.typography.bodyLarge)
                Text("Version 1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .clickable { navController.navigate(AppDestinations.PRIVACY) }
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Privacy",
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        // Footer Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "© 2026 NotifyVault",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Text(
                text = uiState.developerText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    val url = uiState.developerUrl
                    if (url.isNotBlank()) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }
                }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all saved messages?") },
            text = { Text("This removes all captured notifications stored locally on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllMessages()
                    showDeleteConfirm = false
                }) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAgeDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showAgeDeleteConfirm = null },
            title = { Text("Delete older messages?") },
            text = { Text("This permanently removes saved entries older than ${showAgeDeleteConfirm} days from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOlderThan(showAgeDeleteConfirm ?: 0)
                    showAgeDeleteConfirm = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAgeDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export local notification data?") },
            text = { Text("This file may contain sensitive notification content. Export only if you are comfortable storing it on your device or sharing it intentionally.") },
            confirmButton = {
                TextButton(onClick = {
                    exportLauncher.launch("notifyvault-export.json")
                    showExportDialog = false
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
}
