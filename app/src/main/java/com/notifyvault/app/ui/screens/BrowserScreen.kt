package com.notifyvault.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.notifyvault.app.data.BrowserHistoryEntity
import com.notifyvault.app.data.CloudSyncSettings
import com.notifyvault.app.data.MessageEntity
import com.notifyvault.app.data.MessageRepository
import com.notifyvault.app.ui.viewmodel.MessageViewModel
import com.notifyvault.app.work.CloudSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import java.util.Date

data class WebTab(
    val id: String = java.util.UUID.randomUUID().toString(),
    var url: String = "",
    var title: String = "Start Page",
    var webView: WebView? = null
)

data class QuickShortcut(
    val name: String,
    val url: String,
    val iconBg: Color,
    val label: String
)

private val QUICK_SHORTCUTS = listOf(
    QuickShortcut("WhatsApp", "https://web.whatsapp.com", Color(0xFF25D366), "WA"),
    QuickShortcut("Instagram", "https://www.instagram.com", Color(0xFFE1306C), "IG"),
    QuickShortcut("Snapchat", "https://web.snapchat.com", Color(0xFFFFFC00), "SC"),
    QuickShortcut("YouTube", "https://m.youtube.com", Color(0xFFFF0000), "YT"),
    QuickShortcut("Facebook", "https://m.facebook.com", Color(0xFF1877F2), "FB"),
    QuickShortcut("Google", "https://www.google.com", Color(0xFF4285F4), "G"),
    QuickShortcut("Wikipedia", "https://www.wikipedia.org", Color(0xFF6366F1), "W"),
    QuickShortcut("News", "https://news.google.com", Color(0xFF10B981), "N")
)

class NotifyVaultWebBridge(
    private val context: Context,
    private val repository: MessageRepository
) {
    @JavascriptInterface
    fun onWebNotificationCaptured(sender: String, messageText: String, sourceApp: String) {
        val cleanSender = sender.trim().ifBlank { "In-App Web" }
        val cleanMessage = messageText.trim()
        if (cleanMessage.isBlank()) return

        val pkg = when (sourceApp.lowercase()) {
            "instagram" -> "com.instagram.android"
            "snapchat" -> "com.snapchat.android"
            else -> "com.notifyvault.app.browser"
        }

        val entity = MessageEntity(
            sender = cleanSender,
            conversationName = cleanSender,
            messageText = cleanMessage,
            timestamp = System.currentTimeMillis(),
            packageName = pkg,
            capturedAt = System.currentTimeMillis(),
            sourceType = if (pkg.contains("instagram")) "instagram-web" else if (pkg.contains("snapchat")) "snapchat-web" else "web-browser"
        )

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.insertMessage(entity)
                CloudSyncScheduler.enqueueNow(context)
            } catch (_: Exception) {
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(viewModel: MessageViewModel) {
    val context = LocalContext.current
    val repository = remember { MessageRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    val tabs = remember { mutableStateListOf(WebTab()) }
    var activeTabIndex by remember { mutableStateOf(0) }
    val currentTab = tabs.getOrNull(activeTabIndex) ?: tabs.first()

    var urlInput by remember(currentTab.url) { mutableStateOf(currentTab.url) }
    var progress by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showTabSheet by remember { mutableStateOf(false) }

    val historyList by repository.getAllBrowserHistory().collectAsState(initial = emptyList())

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
    ) {
        // Modern Pill Address Bar & Navigation Toolbar
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            if (currentTab.url.isBlank()) {
                                // Already on start page
                            } else if (currentTab.webView?.canGoBack() == true) {
                                currentTab.webView?.goBack()
                            } else {
                                currentTab.url = ""
                                urlInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }

                    IconButton(
                        onClick = { currentTab.webView?.goForward() },
                        enabled = currentTab.webView?.canGoForward() == true
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }

                    // Modern Pill Search / URL Bar
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Search or type URL", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = {
                            if (currentTab.url.startsWith("https://")) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Secure",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            if (urlInput.isNotBlank()) {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                IconButton(onClick = { currentTab.webView?.reload() }) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Reload",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val formatted = formatUrl(urlInput)
                            urlInput = formatted
                            currentTab.url = formatted
                            currentTab.webView?.loadUrl(formatted)
                        })
                    )

                    // Modern Tab Switcher Button [ N ]
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { showTabSheet = true }
                    ) {
                        Text(
                            text = "${tabs.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Home & History Buttons
                    IconButton(onClick = {
                        currentTab.url = ""
                        urlInput = ""
                    }) {
                        Icon(Icons.Default.Home, contentDescription = "Start Page")
                    }

                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                }

                if (isLoading && progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .padding(top = 4.dp)
                    )
                }
            }
        }

        // Web Content / Modern Start Page View
        Box(modifier = Modifier.weight(1f)) {
            if (currentTab.url.isBlank()) {
                // Modern Chrome/Edge-style Start Page
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "NotifyVault Web",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Fast, Secure & Private In-App Browsing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Quick Access Shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    // 4x2 Shortcuts Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(QUICK_SHORTCUTS) { shortcut ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    urlInput = shortcut.url
                                    currentTab.url = shortcut.url
                                    currentTab.webView?.loadUrl(shortcut.url)
                                }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(shortcut.iconBg.copy(alpha = 0.2f))
                                        .border(1.dp, shortcut.iconBg.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Text(
                                        text = shortcut.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = shortcut.iconBg
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = shortcut.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Recently Visited Pages Section
                    Text(
                        text = "Recently Visited on This Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    if (historyList.isEmpty()) {
                        Text(
                            text = "No recent browsing history recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(historyList.take(6)) { item ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val targetUrl = item.url
                                            urlInput = targetUrl
                                            currentTab.url = targetUrl
                                            currentTab.webView?.loadUrl(targetUrl)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title.ifBlank { "Web Page" },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = DateFormat.format("h:mm a", Date(item.timestamp)).toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Render Active Web Page inside WebView
                AndroidView(
                    factory = { ctx ->
                        val webView = WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.setSupportMultipleWindows(false)
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                            addJavascriptInterface(NotifyVaultWebBridge(ctx, repository), "NotifyVaultBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    url?.let {
                                        urlInput = it
                                        currentTab.url = it
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    progress = 100
                                    val title = view?.title ?: "Web Page"
                                    currentTab.title = title
                                    url?.let { pageUrl ->
                                        urlInput = pageUrl
                                        currentTab.url = pageUrl
                                        val devId = CloudSyncSettings.getDeviceId(ctx)
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                repository.insertBrowserHistory(
                                                    BrowserHistoryEntity(
                                                        title = title,
                                                        url = pageUrl,
                                                        timestamp = System.currentTimeMillis(),
                                                        deviceId = devId
                                                    )
                                                )
                                                CloudSyncScheduler.enqueueNow(ctx)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }

                                    // Inject Web Notification & DM Observer Script for Instagram & Snapchat
                                    val script = """
                                        (function() {
                                            if (window.__nv_injected) return;
                                            window.__nv_injected = true;
                                            
                                            function checkMessages() {
                                                try {
                                                    if (window.location.hostname.includes('instagram.com')) {
                                                        var nodes = document.querySelectorAll('[role="aria-label"], [data-testid="message-item"]');
                                                        nodes.forEach(function(n) {
                                                            if (n.innerText && n.innerText.length > 2 && !n.__nv_seen) {
                                                                n.__nv_seen = true;
                                                                window.NotifyVaultBridge.onWebNotificationCaptured("Instagram Direct", n.innerText.substring(0, 150), "instagram");
                                                            }
                                                        });
                                                    } else if (window.location.hostname.includes('snapchat.com')) {
                                                        var snaps = document.querySelectorAll('.chat-item, [data-testid="chat-message"]');
                                                        snaps.forEach(function(s) {
                                                            if (s.innerText && s.innerText.length > 2 && !s.__nv_seen) {
                                                                s.__nv_seen = true;
                                                                window.NotifyVaultBridge.onWebNotificationCaptured("Snapchat Chat", s.innerText.substring(0, 150), "snapchat");
                                                            }
                                                        });
                                                    }
                                                } catch(e) {}
                                            }
                                            setInterval(checkMessages, 4000);
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(script, null)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    progress = newProgress
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (!title.isNullOrBlank()) {
                                        currentTab.title = title
                                    }
                                }
                            }

                            loadUrl(currentTab.url)
                        }
                        currentTab.webView = webView
                        webView
                    },
                    update = { webView ->
                        if (currentTab.webView != webView) {
                            currentTab.webView = webView
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Modern Tab Switcher Sheet
    if (showTabSheet) {
        ModalBottomSheet(onDismissRequest = { showTabSheet = false }) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Open Tabs (${tabs.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        val newTab = WebTab()
                        tabs.add(newTab)
                        activeTabIndex = tabs.lastIndex
                        urlInput = newTab.url
                        showTabSheet = false
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab")
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(tabs.size) { idx ->
                        val tab = tabs[idx]
                        val isSelected = idx == activeTabIndex
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeTabIndex = idx
                                    urlInput = tab.url
                                    showTabSheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tab.title.ifBlank { "Start Page" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tab.url.ifBlank { "notifyvault://start" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (tabs.size > 1) {
                                    IconButton(onClick = {
                                        tabs.removeAt(idx)
                                        if (activeTabIndex >= tabs.size) {
                                            activeTabIndex = tabs.lastIndex
                                        }
                                        urlInput = tabs[activeTabIndex].url
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Tab")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Browsing History Sheet
    if (showHistorySheet) {
        ModalBottomSheet(onDismissRequest = { showHistorySheet = false }) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Device Browsing History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            repository.clearBrowserHistory()
                        }
                    }) {
                        Text("Clear History")
                    }
                }

                if (historyList.isEmpty()) {
                    Text("No browsing history recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(historyList) { item ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val targetUrl = item.url
                                        urlInput = targetUrl
                                        currentTab.url = targetUrl
                                        currentTab.webView?.loadUrl(targetUrl)
                                        showHistorySheet = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item.title.ifBlank { "Web Page" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(item.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        DateFormat.format("MMM d, yyyy • h:mm a", Date(item.timestamp)).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }
    if (trimmed.contains(".") && !trimmed.contains(" ")) {
        return "https://$trimmed"
    }
    return "https://www.google.com/search?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
}
