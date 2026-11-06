package com.example.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.RepairHistoryEntity
import com.example.domain.models.RepairTool
import com.example.ui.MainUiState
import com.example.ui.components.ExecutionDialog
import com.example.ui.theme.MahiAccent
import com.example.ui.theme.MahiAccentDim
import com.example.ui.theme.MahiBlack
import com.example.ui.theme.MahiCardStroke
import com.example.ui.theme.MahiCardSurface
import com.example.ui.theme.MahiRedPrimary
import com.example.ui.theme.MahiSuccessGreen
import com.example.ui.theme.MahiSuccessDim
import com.example.ui.theme.MahiTextMuted
import com.example.ui.theme.MahiTextPrimary
import com.example.ui.theme.MahiTextSecondary
import com.example.ui.theme.MahiWarningAmber
import com.example.ui.theme.MahiWarningDim
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AppSection(val label: String) {
    HOME("Home"), TOOLS("Tools"), HISTORY("History"), SETTINGS("Settings")
}

private data class DeviceSnapshot(
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val batteryPercent: Int?,
    val storageUsedPercent: Int?,
    val storageFreeLabel: String,
    val internetAvailable: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onRunRepair: () -> Unit,
    onRetryAvailability: () -> Unit,
    onSelectTool: (String) -> Unit,
    onDismissToolPicker: () -> Unit,
    onSubmitUserAction: (Boolean) -> Unit,
    onDismissExecution: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onCancelPermission: () -> Unit,
    onSignOut: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val desktop = screenWidth >= 1024
    val tablet = screenWidth >= 640
    val context = LocalContext.current
    val device = remember(context) { readDeviceSnapshot(context) }

    var sectionName by rememberSaveable { mutableStateOf(AppSection.HOME.name) }
    var selectedToolId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var historyFilter by rememberSaveable { mutableStateOf("All") }
    val section = runCatching { AppSection.valueOf(sectionName) }.getOrDefault(AppSection.HOME)
    val selectedTool = state.tools.firstOrNull { it.id == selectedToolId }

    Scaffold(
        containerColor = MahiBlack,
        bottomBar = {
            if (!desktop) {
                NavigationBar(
                    containerColor = Color(0xFF0B1220),
                    contentColor = MahiTextSecondary,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    AppSection.entries.forEach { item ->
                        NavigationBarItem(
                            selected = section == item && selectedTool == null,
                            onClick = { selectedToolId = null; sectionName = item.name },
                            icon = { Icon(item.icon(), contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 11.sp) },
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        Row(Modifier.fillMaxSize().padding(scaffoldPadding)) {
            if (desktop) {
                NavigationRail(
                    containerColor = Color(0xFF0A1120),
                    contentColor = MahiTextSecondary,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 18.dp, bottom = 24.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(15.dp),
                                color = MahiAccentDim,
                                border = BorderStroke(1.dp, MahiAccent.copy(alpha = 0.3f)),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_logo),
                                    contentDescription = "Mahi Repair",
                                    modifier = Modifier.padding(5.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("MAHI", color = MahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                ) {
                    AppSection.entries.forEach { item ->
                        NavigationRailItem(
                            selected = section == item && selectedTool == null,
                            onClick = { selectedToolId = null; sectionName = item.name },
                            icon = { Icon(item.icon(), contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 11.sp) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                val maxWidth = when {
                    desktop -> 1180.dp
                    tablet -> 940.dp
                    else -> 680.dp
                }
                when {
                    selectedTool != null -> ToolDetailPage(
                        tool = selectedTool,
                        deviceApi = Build.VERSION.SDK_INT,
                        maxWidth = maxWidth,
                        onBack = { selectedToolId = null },
                        onRun = { onSelectTool(selectedTool.id) },
                        isPreparing = state.isPreparingRepair
                    )
                    section == AppSection.HOME -> HomePage(
                        state, device, maxWidth,
                        onOpenTools = { sectionName = AppSection.TOOLS.name },
                        onOpenHistory = { sectionName = AppSection.HISTORY.name },
                        onRunRepair = onRunRepair,
                        onRetryAvailability = onRetryAvailability,
                        onSelectTool = { selectedToolId = it }
                    )
                    section == AppSection.TOOLS -> ToolsPage(
                        tools = state.tools,
                        isLoading = state.isToolsLoading,
                        maxWidth = maxWidth,
                        columns = when { screenWidth >= 1024 -> 3; tablet -> 2; else -> 1 },
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        selectedCategory = selectedCategory,
                        onCategoryChange = { selectedCategory = it },
                        onSelectTool = { selectedToolId = it },
                        onRefresh = onRetryAvailability
                    )
                    section == AppSection.HISTORY -> HistoryPage(
                        history = state.history,
                        filter = historyFilter,
                        onFilterChange = { historyFilter = it },
                        maxWidth = maxWidth
                    )
                    else -> SettingsPage(
                        state = state,
                        device = device,
                        maxWidth = maxWidth,
                        onRefresh = onRetryAvailability,
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }

    if (state.isToolPickerOpen) {
        AlertDialog(
            onDismissRequest = onDismissToolPicker,
            title = { Text("Choose a repair", color = MahiTextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(state.tools, key = { it.id }) { tool ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectTool(tool.id) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolMonogram(tool, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tool.name, color = MahiTextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("${tool.category.title} · v${tool.version}", color = MahiTextMuted, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MahiTextMuted)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismissToolPicker) { Text("CLOSE", color = MahiAccent) } },
            containerColor = MahiCardSurface,
            titleContentColor = MahiTextPrimary,
            textContentColor = MahiTextSecondary
        )
    }

    ExecutionDialog(
        executionEvent = state.executionState,
        onSubmitUserAction = onSubmitUserAction,
        onDismiss = onDismissExecution,
        onRequestPermission = onRequestPermission,
        onCancelPermission = onCancelPermission
    )
}

@Composable
private fun HomePage(
    state: MainUiState,
    device: DeviceSnapshot,
    maxWidth: Dp,
    onOpenTools: () -> Unit,
    onOpenHistory: () -> Unit,
    onRunRepair: () -> Unit,
    onRetryAvailability: () -> Unit,
    onSelectTool: (String) -> Unit
) {
    PageFrame(maxWidth) {
        PageHeading("YOUR REPAIR HUB", "Everything running smoothly?", "A clearer view of your device and the tools available to help.")
        Spacer(Modifier.height(20.dp))
        DeviceOverviewCard(device)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(Modifier.weight(1f), "Battery", device.batteryPercent?.let { "${it}%" } ?: "—", "Current level") {
                Icon(Icons.Default.BatteryFull, null, tint = MahiAccent)
            }
            MetricCard(Modifier.weight(1f), "Storage", device.storageUsedPercent?.let { "${it}%" } ?: "—", device.storageFreeLabel) {
                Icon(Icons.Default.Storage, null, tint = MahiAccent)
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionHeading("Repair tools", "View all", onOpenTools)
        Spacer(Modifier.height(10.dp))
        when {
            state.isToolsLoading && state.tools.isEmpty() -> LoadingCard("Checking your authorized repair library…")
            state.tools.isEmpty() -> EmptyCard(
                "No repair tools available",
                state.repairStatusMessage ?: "Connect to the internet and refresh your authorized tool list.",
                if (state.canRetryAvailability) "Try again" else null,
                onRetryAvailability
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.tools.take(3).forEach { tool -> ToolRowCard(tool) { onSelectTool(tool.id) } }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionHeading("Recent activity", "Open history", onOpenHistory)
        Spacer(Modifier.height(10.dp))
        if (state.history.isEmpty()) {
            EmptyCard("Your activity will appear here", "Completed repair runs are saved securely on this device.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.history.take(3).forEach { HistoryRow(it) }
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryActionButton(
            label = if (state.tools.isEmpty()) "Refresh repair library" else "Choose a repair",
            enabled = if (state.tools.isEmpty()) state.canRetryAvailability && !state.isToolsLoading
                else !state.isToolsLoading && !state.isPreparingRepair,
            loading = state.isToolsLoading || state.isPreparingRepair,
            onClick = if (state.tools.isEmpty()) onRetryAvailability else onRunRepair,
            modifier = Modifier.testTag("run_repair_button")
        )
        Text(
            state.repairStatusMessage.orEmpty(),
            color = MahiTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp).testTag("repair_status")
        )
    }
}

@Composable
private fun ToolsPage(
    tools: List<RepairTool>,
    isLoading: Boolean,
    maxWidth: Dp,
    columns: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    onSelectTool: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val categories = listOf("All") + tools.map { it.category.title }.distinct().sorted()
    val filtered = tools.filter { tool ->
        (selectedCategory == "All" || tool.category.title == selectedCategory) &&
            (searchQuery.isBlank() || tool.name.contains(searchQuery, true) ||
                tool.description.contains(searchQuery, true) || tool.category.title.contains(searchQuery, true))
    }
    PageFrame(maxWidth) {
        PageHeading("VERIFIED LIBRARY", "Repair tools", "Only releases authorized for your account and this device are shown.")
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth().testTag("search_bar"),
            singleLine = true,
            placeholder = { Text("Search repair tools…", color = MahiTextMuted) },
            leadingIcon = { Icon(Icons.Default.Build, null, tint = MahiTextMuted) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MahiTextPrimary,
                unfocusedTextColor = MahiTextPrimary,
                focusedBorderColor = MahiAccent,
                unfocusedBorderColor = MahiCardStroke,
                focusedContainerColor = MahiCardSurface,
                unfocusedContainerColor = MahiCardSurface
            )
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategoryChange(category) },
                    label = { Text(category) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            isLoading && tools.isEmpty() -> LoadingCard("Loading verified tools…")
            filtered.isEmpty() -> EmptyCard(
                if (tools.isEmpty()) "Your library is empty" else "No matching tools",
                if (tools.isEmpty()) "Refresh to retrieve tools assigned to your account." else "Try another search or category.",
                if (tools.isEmpty()) "Refresh library" else null,
                onRefresh
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filtered.chunked(columns).forEach { rowTools ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowTools.forEach { tool -> Box(Modifier.weight(1f)) { ToolRowCard(tool) { onSelectTool(tool.id) } } }
                        repeat((columns - rowTools.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolDetailPage(
    tool: RepairTool,
    deviceApi: Int,
    maxWidth: Dp,
    onBack: () -> Unit,
    onRun: () -> Unit,
    isPreparing: Boolean
) {
    PageFrame(maxWidth) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Default.ArrowBack, null, tint = MahiAccent)
            Spacer(Modifier.width(6.dp))
            Text("Back to tools", color = MahiAccent)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolMonogram(tool, 58.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("REPAIR TOOL", color = MahiTextMuted, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                Text(tool.name, color = MahiTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${tool.category.title} · Version ${tool.version}", color = MahiTextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        AppCard {
            Text("Overview", color = MahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(
                tool.description.ifBlank { "Verified repair workflow provided by the service." },
                color = MahiTextSecondary, fontSize = 14.sp, lineHeight = 21.sp
            )
            Divider(color = MahiCardStroke)
            MetadataRow("Minimum Android", "API ${tool.minAndroidSdk}")
            MetadataRow("Your Android", "API $deviceApi")
            MetadataRow("Release version", tool.version)
            MetadataRow("Availability", if (tool.enabled) "Authorized" else "Unavailable")
        }
        Spacer(Modifier.height(14.dp))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = MahiAccent)
                Spacer(Modifier.width(8.dp))
                Text("Secure execution", color = MahiTextPrimary, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "The signed instructions are fetched and verified again before execution. Any required permission or confirmation is requested by Android and the repair engine.",
                color = MahiTextSecondary, fontSize = 13.sp, lineHeight = 20.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryActionButton("Review & start repair", tool.enabled && !isPreparing, isPreparing, onRun)
    }
}

@Composable
private fun HistoryPage(
    history: List<RepairHistoryEntity>,
    filter: String,
    onFilterChange: (String) -> Unit,
    maxWidth: Dp
) {
    val filters = listOf("All", "SUCCESS", "PARTIAL", "FAILED", "CANCELLED")
    val filtered = history.filter { filter == "All" || it.result.equals(filter, true) }
    LazyColumn(
        modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PageHeading("ON-DEVICE AUDIT", "Repair history", "A record of repair runs completed with this account.") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { value ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { onFilterChange(value) },
                        label = { Text(if (value == "All") value else value.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyCard(
                    if (history.isEmpty()) "No repair runs yet" else "No matching results",
                    if (history.isEmpty()) "After a repair completes, its result and diagnosis will be saved here."
                    else "Choose another status filter to see more activity."
                )
            }
        } else {
            items(filtered, key = { it.id }) { HistoryDetailCard(it) }
        }
    }
}

@Composable
private fun SettingsPage(
    state: MainUiState,
    device: DeviceSnapshot,
    maxWidth: Dp,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    PageFrame(maxWidth) {
        PageHeading("PREFERENCES & ACCOUNT", "Settings", "Your account, device and local synchronization status.")
        Spacer(Modifier.height(18.dp))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MahiAccentDim, modifier = Modifier.size(46.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Security, null, tint = MahiAccent) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Signed-in account", color = MahiTextMuted, fontSize = 12.sp)
                    Text(state.session.email, color = MahiTextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(state.session.role.replaceFirstChar { it.uppercase() }, color = MahiTextSecondary, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AppCard {
            Text("Device & sync", color = MahiTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            MetadataRow("Device", device.model)
            MetadataRow("Android", "${device.androidVersion} · API ${device.apiLevel}")
            MetadataRow("Internet", if (device.internetAvailable) "Available" else "Not connected")
            MetadataRow("Authorized tools", state.tools.size.toString())
            MetadataRow("Last catalog sync", formatSyncTime(state.lastSyncTimestamp))
            MetadataRow("Reports waiting to upload", state.pendingReportCount.toString())
            Button(
                onClick = onRefresh,
                enabled = !state.isToolsLoading && !state.isPreparingRepair,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MahiAccent)
            ) {
                if (state.isToolsLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh repair library", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = MahiAccent)
                Spacer(Modifier.width(8.dp))
                Text("Privacy & safety", color = MahiTextPrimary, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Repair instructions are signed and checked before use. Session credentials remain encrypted by Android Keystore. Completed reports are retained locally until the server acknowledges them.",
                color = MahiTextSecondary, fontSize = 13.sp, lineHeight = 20.sp
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MahiRedPrimary.copy(alpha = 0.13f),
                contentColor = MahiRedPrimary
            )
        ) {
            Icon(Icons.Default.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Sign out", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PageFrame(maxWidth: Dp, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            content = content
        )
    }
}

@Composable
private fun PageHeading(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(eyebrow, color = MahiAccent, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Text(title, color = MahiTextPrimary, fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MahiTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun DeviceOverviewCard(device: DeviceSnapshot) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF172A4D), Color(0xFF111B30), Color(0xFF111827)))
        ).padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Devices, null, tint = MahiAccent, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("THIS DEVICE", color = Color(0xFF9EB9EC), fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                    Text(device.model, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Android ${device.androidVersion} · API ${device.apiLevel}", color = Color(0xFFB8C6DE), fontSize = 12.sp)
                }
                Surface(shape = CircleShape, color = if (device.internetAvailable) MahiSuccessDim else MahiWarningDim) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (device.internetAvailable) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            null,
                            tint = if (device.internetAvailable) MahiSuccessGreen else MahiWarningAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (device.internetAvailable) "Online" else "Offline",
                            color = if (device.internetAvailable) MahiSuccessGreen else MahiWarningAmber,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Divider(color = Color.White.copy(alpha = 0.09f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeviceMetric(Modifier.weight(1f), "Battery", device.batteryPercent?.let { "${it}%" } ?: "Unavailable", device.batteryPercent?.div(100f))
                DeviceMetric(Modifier.weight(1f), "Storage used", device.storageUsedPercent?.let { "${it}%" } ?: "Unavailable", device.storageUsedPercent?.div(100f))
            }
            if (device.storageFreeLabel.isNotBlank()) {
                Text(device.storageFreeLabel, color = Color(0xFF9AA7C2), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun DeviceMetric(modifier: Modifier, label: String, value: String, progress: Float?) {
    Column(modifier) {
        Text(label, color = Color(0xFF9AA7C2), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (progress != null) {
            Box(
                Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight()
                        .clip(CircleShape).background(MahiAccent)
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    caption: String,
    icon: @Composable () -> Unit
) {
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, color = MahiTextSecondary, fontSize = 11.sp)
                Text(value, color = MahiTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(caption, color = MahiTextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHeading(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = MahiTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text(actionLabel, color = MahiAccent, fontSize = 12.sp)
            Icon(Icons.Default.ArrowForward, null, tint = MahiAccent, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun ToolRowCard(tool: RepairTool, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
        border = BorderStroke(1.dp, MahiCardStroke)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolMonogram(tool, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tool.name, color = MahiTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (tool.isNew) {
                        Spacer(Modifier.width(6.dp))
                        MiniBadge("NEW", MahiAccent, MahiAccentDim)
                    }
                }
                Text("${tool.category.title} · v${tool.version}", color = MahiTextMuted, fontSize = 11.sp)
                if (tool.description.isNotBlank()) {
                    Text(tool.description, color = MahiTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Default.ChevronRight, "View ${tool.name}", tint = MahiTextMuted)
        }
    }
}

@Composable
private fun ToolMonogram(tool: RepairTool, size: Dp) {
    val monogram = tool.name.split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MahiAccentDim,
        border = BorderStroke(1.dp, MahiAccent.copy(alpha = 0.18f)),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(monogram.ifBlank { "MR" }, color = MahiAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun HistoryRow(record: RepairHistoryEntity) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.toolName, color = MahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(formatHistoryTime(record.timestamp), color = MahiTextMuted, fontSize = 11.sp)
            }
            ResultBadge(record.result)
        }
    }
}

@Composable
private fun HistoryDetailCard(record: RepairHistoryEntity) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.toolName, color = MahiTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Version ${record.toolVersion}", color = MahiTextMuted, fontSize = 11.sp)
            }
            ResultBadge(record.result)
        }
        Text(
            record.diagnosis.ifBlank { "No diagnosis was recorded for this run." },
            color = MahiTextSecondary, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = MahiTextMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(formatHistoryTime(record.timestamp), color = MahiTextMuted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(record.durationMs), color = MahiTextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ResultBadge(result: String) {
    val (label, foreground, background) = when (result.uppercase(Locale.ROOT)) {
        "SUCCESS" -> Triple("Passed", MahiSuccessGreen, MahiSuccessDim)
        "PARTIAL" -> Triple("Attention", MahiWarningAmber, MahiWarningDim)
        "FAILED" -> Triple("Failed", MahiRedPrimary, MahiRedPrimary.copy(alpha = 0.13f))
        else -> Triple("Cancelled", MahiTextSecondary, Color(0xFF253047))
    }
    MiniBadge(label, foreground, background)
}

@Composable
private fun MiniBadge(label: String, foreground: Color, background: Color) {
    Surface(shape = CircleShape, color = background) {
        Text(label, color = foreground, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
        border = BorderStroke(1.dp, MahiCardStroke)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MahiTextSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = MahiTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MahiAccent, disabledContainerColor = MahiAccent.copy(alpha = 0.28f))
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = MahiAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(message, color = MahiTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyCard(title: String, message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    AppCard {
        Surface(shape = CircleShape, color = MahiAccentDim, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Info, null, tint = MahiAccent) }
        }
        Text(title, color = MahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(message, color = MahiTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        if (actionLabel != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(actionLabel, color = MahiAccent) }
        }
    }
}

private fun AppSection.icon() = when (this) {
    AppSection.HOME -> Icons.Default.Home
    AppSection.TOOLS -> Icons.Default.Build
    AppSection.HISTORY -> Icons.Default.History
    AppSection.SETTINGS -> Icons.Default.Settings
}

private fun readDeviceSnapshot(context: Context): DeviceSnapshot {
    val battery = runCatching {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }.getOrNull()
    val storage = runCatching {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val percent = if (total > 0L) (((total - free).toDouble() / total) * 100).toInt().coerceIn(0, 100) else null
        percent to "${formatBytes(free)} free of ${formatBytes(total)}"
    }.getOrNull()
    val internet = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
    val model = listOf(Build.MANUFACTURER, Build.MODEL).map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(" ").ifBlank { "Android device" }

    return DeviceSnapshot(
        model = model,
        androidVersion = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "Unknown",
        apiLevel = Build.VERSION.SDK_INT,
        batteryPercent = battery,
        storageUsedPercent = storage?.first,
        storageFreeLabel = storage?.second.orEmpty(),
        internetAvailable = internet
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do {
        value /= 1024.0
        index++
    } while (value >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", value, units[index.coerceAtMost(units.lastIndex)])
}

private fun formatHistoryTime(timestamp: Long): String =
    runCatching {
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
    }.getOrDefault("Saved locally")

private fun formatSyncTime(timestamp: Long): String =
    if (timestamp <= 0L) "Not synced yet" else formatHistoryTime(timestamp)

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1000L
    return if (seconds < 60L) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}
