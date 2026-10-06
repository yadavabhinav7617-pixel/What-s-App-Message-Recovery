package com.notifyvault.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notifyvault.app.data.CloudSyncSettings
import com.notifyvault.app.data.MessageEntity
import com.notifyvault.app.data.MessageRepository
import com.notifyvault.app.service.SupportNotificationAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HomeUiState(
    val notificationAccessGranted: Boolean = false,
    val messageCount: Int = 0,
    val oldestTimestamp: Long? = null,
    val newestTimestamp: Long? = null,
    val storageEstimateBytes: Long = 0L,
    val cloudSyncEnabled: Boolean = false,
    val connectionOnline: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
    val pendingSyncCount: Int = 0,
    val failedSyncCount: Int = 0,
    val registeredDeviceName: String = "This device",
    val backendUrl: String = "",
    val accountEmail: String = "",
    val isUserLoggedIn: Boolean = false,
    val isAdmin: Boolean = false,
    val developerText: String = "Developed by Abhinav",
    val developerUrl: String = "https://notifyvault-theta.vercel.app"
)

class MessageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MessageRepository.getInstance(application.applicationContext)

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private val _registeredUsersList = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val registeredUsersList: StateFlow<List<Pair<String, String>>> = _registeredUsersList.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                repository.getAllMessages().collect { list ->
                    _messages.value = list
                    val pending = try { repository.countPendingQueue() } catch (_: Exception) { 0 }
                    val failed = try { repository.countFailedQueue() } catch (_: Exception) { 0 }
                    _uiState.update {
                        it.copy(
                            messageCount = list.size,
                            oldestTimestamp = list.minOfOrNull { item -> item.capturedAt },
                            newestTimestamp = list.maxOfOrNull { item -> item.capturedAt },
                            storageEstimateBytes = estimateStorageBytes(list),
                            cloudSyncEnabled = CloudSyncSettings.isEnabled(getApplication<Application>().applicationContext),
                            pendingSyncCount = pending,
                            failedSyncCount = failed,
                            lastSyncTimestamp = CloudSyncSettings.getLastSyncTime(getApplication<Application>().applicationContext),
                            registeredDeviceName = CloudSyncSettings.getDeviceName(getApplication<Application>().applicationContext)
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        refreshNotificationAccess()
        refreshCloudStatus()
    }

    fun getMessageById(id: Long): MessageEntity? = _messages.value.firstOrNull { it.id == id }

    fun refreshNotificationAccess() {
        val granted = SupportNotificationAccess.isNotificationAccessEnabled(getApplication<Application>().applicationContext)
        _uiState.update { it.copy(notificationAccessGranted = granted) }
    }

    fun refreshCloudStatus() {
        val context = getApplication<Application>().applicationContext
        val currentEmail = CloudSyncSettings.getAccountEmail(context) ?: ""
        viewModelScope.launch {
            try {
                val pending = try { repository.countPendingQueue() } catch (_: Exception) { 0 }
                val failed = try { repository.countFailedQueue() } catch (_: Exception) { 0 }
                _uiState.update {
                    it.copy(
                        cloudSyncEnabled = CloudSyncSettings.isEnabled(context),
                        connectionOnline = true,
                        pendingSyncCount = pending,
                        failedSyncCount = failed,
                        lastSyncTimestamp = CloudSyncSettings.getLastSyncTime(context),
                        registeredDeviceName = CloudSyncSettings.getDeviceName(context),
                        backendUrl = CloudSyncSettings.getBackendUrl(context),
                        accountEmail = currentEmail,
                        isUserLoggedIn = CloudSyncSettings.getAuthToken(context) != null,
                        isAdmin = currentEmail.equals("admin@notifyvault.com", ignoreCase = true),
                        developerText = CloudSyncSettings.getDeveloperText(context),
                        developerUrl = CloudSyncSettings.getDeveloperUrl(context)
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun loginUserAsync(email: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()
        if (cleanEmail.isBlank() || cleanPass.isBlank()) return@withContext false

        val context = getApplication<Application>().applicationContext
        val baseUrl = CloudSyncSettings.getBackendUrl(context).trimEnd('/')

        // 1. Try Backend Admin Login (Vercel)
        try {
            val url = URL("$baseUrl/api/admin/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")

            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", cleanPass)
            }

            conn.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val token = json.optString("token", "server_token_${System.currentTimeMillis()}")
                
                CloudSyncSettings.setAuthToken(context, token)
                CloudSyncSettings.setAccountEmail(context, cleanEmail)
                _uiState.update { it.copy(isUserLoggedIn = true, isAdmin = true, accountEmail = cleanEmail) }
                return@withContext true
            }
        } catch (_: Exception) {
            // Network error or backend not reachable, fallback to local checks below
        }
        
        // 2. Admin Master Login Fallback (Hardcoded)
        if (cleanEmail.equals("admin@notifyvault.com", ignoreCase = true) && cleanPass == "admin123") {
            val dummyToken = "auth_admin_${System.currentTimeMillis()}"
            CloudSyncSettings.setAuthToken(context, dummyToken)
            CloudSyncSettings.setAccountEmail(context, cleanEmail)
            _uiState.update { it.copy(isUserLoggedIn = true, isAdmin = true, accountEmail = cleanEmail) }
            return@withContext true
        }

        // Allow any login with "admin" in email and "admin123" as password as a fallback admin
        if (cleanEmail.contains("admin", ignoreCase = true) && cleanPass == "admin123") {
            val dummyToken = "auth_admin_${System.currentTimeMillis()}"
            CloudSyncSettings.setAuthToken(context, dummyToken)
            CloudSyncSettings.setAccountEmail(context, cleanEmail)
            _uiState.update { it.copy(isUserLoggedIn = true, isAdmin = true, accountEmail = cleanEmail) }
            return@withContext true
        }

        // Standard User Login - Try Backend User Login (Vercel/MongoDB)
        try {
            val url = URL("$baseUrl/api/users/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")

            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", cleanPass)
            }

            conn.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val token = json.optString("token", "server_user_token_${System.currentTimeMillis()}")
                
                CloudSyncSettings.setAuthToken(context, token)
                CloudSyncSettings.setAccountEmail(context, cleanEmail)
                _uiState.update { it.copy(isUserLoggedIn = true, isAdmin = false, accountEmail = cleanEmail) }
                return@withContext true
            }
        } catch (_: Exception) {
            // Network error or backend not reachable
        }

        // Standard User Login - Fallback to local simulated DB if backend fails
        val usersJsonStr = CloudSyncSettings.getRegisteredUsers(context)
        try {
            val usersArray = JSONArray(usersJsonStr)
            for (i in 0 until usersArray.length()) {
                val userObj = usersArray.getJSONObject(i)
                if (userObj.optString("email") == cleanEmail && userObj.optString("password") == cleanPass) {
                    val dummyToken = "auth_user_${System.currentTimeMillis()}"
                    CloudSyncSettings.setAuthToken(context, dummyToken)
                    CloudSyncSettings.setAccountEmail(context, cleanEmail)
                    _uiState.update { it.copy(isUserLoggedIn = true, isAdmin = false, accountEmail = cleanEmail) }
                    return@withContext true
                }
            }
        } catch (_: Exception) {}

        return@withContext false
    }

    fun logoutUser() {
        val context = getApplication<Application>().applicationContext
        context.getSharedPreferences("notifyvault_cloud_sync", Context.MODE_PRIVATE).edit().remove("auth_token").remove("account_email").apply()
        _uiState.update { it.copy(isUserLoggedIn = false, isAdmin = false, accountEmail = "") }
    }
    
    // --- Admin User Management Methods ---

    fun loadRegisteredUsers() {
        val context = getApplication<Application>().applicationContext
        val baseUrl = CloudSyncSettings.getBackendUrl(context).trimEnd('/')
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/api/users")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/json")
                
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val usersArray = JSONArray(body)
                    val list = mutableListOf<Pair<String, String>>()
                    for (i in 0 until usersArray.length()) {
                        val userObj = usersArray.getJSONObject(i)
                        // Assume backend returns email and password fields, or default password if hashed
                        list.add(Pair(userObj.optString("email"), userObj.optString("password", "********")))
                    }
                    _registeredUsersList.value = list
                    return@launch
                }
            } catch (_: Exception) {}
            
            // Fallback to local if backend fails
            val usersJsonStr = CloudSyncSettings.getRegisteredUsers(context)
            val list = mutableListOf<Pair<String, String>>()
            try {
                val usersArray = JSONArray(usersJsonStr)
                for (i in 0 until usersArray.length()) {
                    val userObj = usersArray.getJSONObject(i)
                    list.add(Pair(userObj.optString("email"), userObj.optString("password")))
                }
            } catch (_: Exception) {}
            _registeredUsersList.value = list
        }
    }

    suspend fun createNewUser(email: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPass = pass.trim()
        if (cleanEmail.isBlank() || cleanPass.isBlank()) return@withContext false
        
        val context = getApplication<Application>().applicationContext
        val baseUrl = CloudSyncSettings.getBackendUrl(context).trimEnd('/')
        
        // 1. Try sending to Vercel/MongoDB Backend
        try {
            val url = URL("$baseUrl/api/users/register")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

            val payload = JSONObject().apply {
                put("email", cleanEmail)
                put("password", cleanPass)
            }

            conn.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode in 200..299) {
                loadRegisteredUsers()
                return@withContext true
            }
        } catch (_: Exception) {}
        
        // 2. Fallback to Local Storage
        val usersJsonStr = CloudSyncSettings.getRegisteredUsers(context)
        try {
            val usersArray = if (usersJsonStr.isNotBlank()) JSONArray(usersJsonStr) else JSONArray()
            // Check if exists locally
            for (i in 0 until usersArray.length()) {
                if (usersArray.getJSONObject(i).optString("email") == cleanEmail) {
                    return@withContext false // Already exists
                }
            }
            val newUser = JSONObject().apply {
                put("email", cleanEmail)
                put("password", cleanPass)
            }
            usersArray.put(newUser)
            CloudSyncSettings.setRegisteredUsers(context, usersArray.toString())
            loadRegisteredUsers()
            return@withContext true
        } catch (_: Exception) {
            return@withContext false
        }
    }

    suspend fun deleteUser(email: String): Boolean = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        val baseUrl = CloudSyncSettings.getBackendUrl(context).trimEnd('/')
        
        // 1. Try Backend
        try {
            val url = URL("$baseUrl/api/users")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            
            val payload = JSONObject().apply {
                put("email", email)
            }
            conn.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            
            if (conn.responseCode in 200..299) {
                loadRegisteredUsers()
                return@withContext true
            }
        } catch (_: Exception) {}
        
        // 2. Fallback Local
        val usersJsonStr = CloudSyncSettings.getRegisteredUsers(context)
        try {
            val usersArray = if (usersJsonStr.isNotBlank()) JSONArray(usersJsonStr) else JSONArray()
            val newArray = JSONArray()
            var found = false
            for (i in 0 until usersArray.length()) {
                val obj = usersArray.getJSONObject(i)
                if (obj.optString("email") == email) {
                    found = true
                } else {
                    newArray.put(obj)
                }
            }
            if (found) {
                CloudSyncSettings.setRegisteredUsers(context, newArray.toString())
                loadRegisteredUsers()
                return@withContext true
            }
        } catch (_: Exception) {}
        return@withContext false
    }

    suspend fun changeUserPassword(email: String, oldPass: String, newPass: String): Boolean = withContext(Dispatchers.IO) {
        val cleanNewPass = newPass.trim()
        if (cleanNewPass.isBlank()) return@withContext false
        
        val context = getApplication<Application>().applicationContext
        val baseUrl = CloudSyncSettings.getBackendUrl(context).trimEnd('/')
        
        // 1. Try Backend
        try {
            val url = URL("$baseUrl/api/users/password")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            
            val payload = JSONObject().apply {
                put("email", email)
                put("oldPassword", oldPass)
                put("newPassword", cleanNewPass)
            }
            conn.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            
            if (conn.responseCode in 200..299) {
                loadRegisteredUsers()
                return@withContext true
            }
        } catch (_: Exception) {}
        
        // 2. Fallback Local
        val usersJsonStr = CloudSyncSettings.getRegisteredUsers(context)
        try {
            val usersArray = if (usersJsonStr.isNotBlank()) JSONArray(usersJsonStr) else JSONArray()
            var updated = false
            for (i in 0 until usersArray.length()) {
                val obj = usersArray.getJSONObject(i)
                if (obj.optString("email") == email && obj.optString("password") == oldPass) {
                    obj.put("password", cleanNewPass)
                    updated = true
                    break
                }
            }
            if (updated) {
                CloudSyncSettings.setRegisteredUsers(context, usersArray.toString())
                loadRegisteredUsers()
                return@withContext true
            }
        } catch (_: Exception) {}
        return@withContext false
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        val context = getApplication<Application>().applicationContext
        CloudSyncSettings.setEnabled(context, enabled)
        refreshCloudStatus()
    }

    fun updateBackendUrl(url: String) {
        val context = getApplication<Application>().applicationContext
        CloudSyncSettings.setBackendUrl(context, url)
        refreshCloudStatus()
    }

    fun updateAccountEmail(email: String) {
        val context = getApplication<Application>().applicationContext
        CloudSyncSettings.setAccountEmail(context, email)
        refreshCloudStatus()
    }

    fun updateDeveloperInfo(text: String, url: String) {
        val context = getApplication<Application>().applicationContext
        CloudSyncSettings.setDeveloperText(context, text)
        CloudSyncSettings.setDeveloperUrl(context, url)
        refreshCloudStatus()
    }

    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch {
            repository.deleteMessage(message)
        }
    }

    fun deleteMessages(messages: List<MessageEntity>) {
        viewModelScope.launch {
            messages.forEach { repository.deleteMessage(it) }
        }
    }

    fun deleteAllMessages() {
        viewModelScope.launch {
            repository.deleteAllMessages()
        }
    }

    fun deleteOlderThan(days: Int) {
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
            repository.deleteOlderThan(cutoff)
        }
    }

    fun applyRetention(days: Int?) {
        if (days == null) return
        deleteOlderThan(days)
    }

    fun exportMessagesAsJson(): String {
        val data = _messages.value
        return buildString {
            append("[\n")
            data.forEachIndexed { index, message ->
                append(
                    "  {\"id\":${message.id},\"sender\":\"${escapeJson(message.sender)}\",\"messageText\":\"${escapeJson(message.messageText)}\",\"timestamp\":${message.timestamp},\"capturedAt\":${message.capturedAt},\"packageName\":\"${escapeJson(message.packageName)}\",\"notificationKey\":${if (message.notificationKey != null) "\"${escapeJson(message.notificationKey)}\"" else "null"},\"messageType\":${if (message.messageType != null) "\"${escapeJson(message.messageType)}\"" else "null"}}"
                )
                if (index < data.lastIndex) append(",")
                append("\n")
            }
            append("]")
        }
    }

    private fun estimateStorageBytes(messages: List<MessageEntity>): Long {
        return messages.sumOf { message ->
            (
                message.sender.toByteArray().size +
                    message.messageText.toByteArray().size +
                    (message.notificationKey?.toByteArray()?.size ?: 0) +
                    message.packageName.toByteArray().size +
                    (message.notificationTitle?.toByteArray()?.size ?: 0).toLong()
                ).toLong()
        }
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
