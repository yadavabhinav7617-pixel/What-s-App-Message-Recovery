package com.notifyvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.notifyvault.app.ui.navigation.AppDestinations
import com.notifyvault.app.ui.viewmodel.MessageViewModel

@Composable
fun AdminDashboardScreen(
    viewModel: MessageViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val registeredUsers by viewModel.registeredUsersList.collectAsState()

    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Edit User Password State
    var userToEdit by remember { mutableStateOf<String?>(null) }
    var oldPassInput by remember { mutableStateOf("") }
    var newPassInput by remember { mutableStateOf("") }
    var passActionMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadRegisteredUsers()
    }

    // Force redirect if a standard user manually reaches here
    LaunchedEffect(uiState.isAdmin) {
        if (!uiState.isAdmin) {
            navController.navigate(AppDestinations.HOME) {
                popUpTo(AppDestinations.ADMIN_DASHBOARD) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Admin Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Button(onClick = { 
                viewModel.logoutUser() 
                navController.navigate(AppDestinations.LOGIN) {
                    popUpTo(0)
                }
            }) {
                Text("Log Out")
            }
        }

        Text(
            text = "Welcome, ${uiState.accountEmail}. You are an Administrator.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Create New User Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Provision New User Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it; actionMessage = null },
                    label = { Text("User Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; actionMessage = null },
                    label = { Text("Initial Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Button(
                    onClick = {
                        scope.launch {
                            val success = viewModel.createNewUser(newEmail, newPassword)
                            if (success) {
                                actionMessage = "User successfully created."
                                newEmail = ""
                                newPassword = ""
                            } else {
                                actionMessage = "Failed to create user. It may already exist or input is invalid."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Account")
                }
                
                if (actionMessage != null) {
                    Text(
                        text = actionMessage!!,
                        color = if (actionMessage!!.contains("successfully")) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Registered Users List (${registeredUsers.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (registeredUsers.isEmpty()) {
            Text("No users provisioned yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(registeredUsers) { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Email: ${user.first}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                Text("Pass: ${user.second}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                IconButton(onClick = { 
                                    userToEdit = user.first
                                    oldPassInput = ""
                                    newPassInput = ""
                                    passActionMessage = null
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Change Password", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { 
                                    scope.launch { viewModel.deleteUser(user.first) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete User", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Change Password Dialog
    if (userToEdit != null) {
        AlertDialog(
            onDismissRequest = { userToEdit = null },
            title = { Text("Change Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Change password for $userToEdit", style = MaterialTheme.typography.bodyMedium)
                    
                    OutlinedTextField(
                        value = oldPassInput,
                        onValueChange = { oldPassInput = it; passActionMessage = null },
                        label = { Text("Old Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = newPassInput,
                        onValueChange = { newPassInput = it; passActionMessage = null },
                        label = { Text("New Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (passActionMessage != null) {
                        Text(
                            text = passActionMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val success = viewModel.changeUserPassword(userToEdit!!, oldPassInput, newPassInput)
                        if (success) {
                            userToEdit = null
                        } else {
                            passActionMessage = "Failed. Check if the old password is correct."
                        }
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}