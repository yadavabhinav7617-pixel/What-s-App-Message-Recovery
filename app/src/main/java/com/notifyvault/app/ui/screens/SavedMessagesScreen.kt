package com.notifyvault.app.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.notifyvault.app.data.MessageEntity
import com.notifyvault.app.ui.navigation.AppDestinations
import com.notifyvault.app.ui.viewmodel.MessageViewModel
import java.util.Date

@Composable
fun SavedMessagesScreen(viewModel: MessageViewModel, navController: NavController) {
    val messages by viewModel.messages.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredMessages = messages.filter { message ->
        val matchesSearch = searchQuery.isBlank() ||
            message.sender.contains(searchQuery, ignoreCase = true) ||
            message.messageText.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            "Today" -> isWithinDays(message, 0)
            "This Week" -> isWithinDays(message, 7)
            "Older" -> !isWithinDays(message, 7)
            else -> true
        }
        matchesSearch && matchesFilter
    }

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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Messages",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${filteredMessages.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showSearch = !showSearch }) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Text("Search")
            }
            Button(onClick = { /* filtering handled in chips */ }) {
                Icon(Icons.Default.FilterList, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Text("Filter")
            }
        }

        AnimatedVisibility(visible = showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by sender or message") }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            listOf("All", "Today", "This Week", "Older").forEach { option ->
                val selected = selectedFilter == option
                Card(
                    modifier = Modifier.clickable { selectedFilter = option },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = option,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        if (filteredMessages.isEmpty()) {
            EmptyStateCard(
                title = "No matching messages",
                message = "Adjust your search or wait for new notification captures to appear here.",
                icon = Icons.Default.Search
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredMessages) { message ->
                    MessageRow(
                        message = message,
                        onClick = { navController.navigate(AppDestinations.messageDetailRoute(message.id)) },
                        onDelete = { viewModel.deleteMessage(message) }
                    )
                }
            }
        }

        if (messages.isNotEmpty()) {
            Button(
                onClick = { viewModel.deleteAllMessages() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete all messages")
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: MessageEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.sender.ifBlank { "Unknown sender" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = message.messageText.ifBlank { "Notification text unavailable" },
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = DateFormat.format("MMM d, yyyy • h:mm a", Date(message.timestamp)).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Button(onClick = onDelete, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 4.dp))
                Text("Delete")
            }
        }
    }
}

private fun isWithinDays(message: MessageEntity, daysAgo: Int): Boolean {
    val cutoff = System.currentTimeMillis() - (daysAgo * 24L * 60L * 60L * 1000L)
    return message.capturedAt >= cutoff
}
