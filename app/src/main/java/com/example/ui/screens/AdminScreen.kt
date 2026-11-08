package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.AdminAnalyticsData
import com.example.data.remote.AdminApiClient
import com.example.data.remote.AdminAuditLogItem
import com.example.data.remote.AdminDeviceItem
import com.example.data.remote.AdminMemberItem
import com.example.data.remote.AdminRecentRunDto
import com.example.data.remote.AdminToolItem
import com.example.data.remote.CreateToolRequest
import com.example.data.remote.GeminiAdminData
import com.example.data.remote.InviteMemberRequest
import com.example.ui.theme.MahiAccent
import com.example.ui.theme.MahiAccentDim
import com.example.ui.theme.MahiBlack
import com.example.ui.theme.MahiCardStroke
import com.example.ui.theme.MahiCardSurface
import com.example.ui.theme.MahiRedPrimary
import com.example.ui.theme.MahiSuccessDim
import com.example.ui.theme.MahiSuccessGreen
import com.example.ui.theme.MahiTextMuted
import com.example.ui.theme.MahiTextPrimary
import com.example.ui.theme.MahiTextSecondary
import com.example.ui.theme.MahiWarningAmber
import com.example.ui.theme.MahiWarningDim
import kotlinx.coroutines.launch

private enum class AdminTab(val title: String, val icon: ImageVector) {
    OVERVIEW("Dashboard", Icons.Default.Analytics),
    TOOLS("Tools", Icons.Default.Build),
    MEMBERS("Members", Icons.Default.SupervisorAccount),
    AI_ASSISTANT("AI Admin", Icons.Default.AutoAwesome),
    SETTINGS("Security", Icons.Default.Settings)
}

data class ChatMessage(
    val id: String,
    val sender: String, // "user" or "gemini"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val executedActions: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    userEmail: String,
    authToken: String,
    onExitAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isTablet = screenWidth >= 640
    val maxContentWidth = if (isTablet) 980.dp else 680.dp
    val scope = rememberCoroutineScope()
    val adminClient = remember { com.example.data.remote.SupabaseOwnerAdminApiClient() }

    // PIN Unlock State (Verified server-side via PBKDF2)
    var isUnlocked by rememberSaveable { mutableStateOf(false) }
    var enteredPin by rememberSaveable { mutableStateOf("") }
    var pinError by rememberSaveable { mutableStateOf<String?>(null) }
    var isVerifyingPin by rememberSaveable { mutableStateOf(false) }

    // Admin Data State
    var selectedTab by rememberSaveable { mutableIntStateOf(AdminTab.OVERVIEW.ordinal) }
    var analyticsData by remember { mutableStateOf<AdminAnalyticsData?>(null) }
    var toolsList by remember { mutableStateOf<List<AdminToolItem>>(emptyList()) }
    var membersList by remember { mutableStateOf<List<AdminMemberItem>>(emptyList()) }
    var devicesList by remember { mutableStateOf<List<AdminDeviceItem>>(emptyList()) }
    var auditLogs by remember { mutableStateOf<List<AdminAuditLogItem>>(emptyList()) }
    var isLoadingData by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    // AI Chat State
    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage(
                id = "welcome",
                sender = "gemini",
                text = "Owner dashboard is connected to Supabase. Gemini actions are disabled until a production AI provider is configured; use the Tools and Members tabs for secured administration."
            )
        )
    }
    var chatInput by rememberSaveable { mutableStateOf("") }
    var isAILoading by remember { mutableStateOf(false) }

    // Dialog States
    var showCreateToolDialog by rememberSaveable { mutableStateOf(false) }
    var editingTool by remember { mutableStateOf<AdminToolItem?>(null) }
    var showInviteMemberDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePinDialog by rememberSaveable { mutableStateOf(false) }

    fun applyDashboard(snapshot: com.example.data.remote.AdminDashboardSnapshot) {
        analyticsData = snapshot.analytics
        toolsList = snapshot.tools
        membersList = snapshot.members
        devicesList = snapshot.devices
        auditLogs = snapshot.auditLogs
    }

    // One authenticated request loads the complete Owner dashboard.
    fun refreshAdminData() {
        if (!isUnlocked || authToken.isBlank()) return
        scope.launch {
            isLoadingData = true
            statusFeedback = null
            adminClient.getDashboard(authToken)
                .onSuccess { applyDashboard(it) }
                .onFailure { statusFeedback = it.message ?: "Owner dashboard refresh failed." }
            isLoadingData = false
        }
    }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            refreshAdminData()
        }
    }

    if (!isUnlocked) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MahiBlack)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
                border = BorderStroke(1.dp, MahiCardStroke)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MahiAccentDim,
                        border = BorderStroke(1.dp, MahiAccent.copy(alpha = 0.3f)),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MahiAccent, modifier = Modifier.size(32.dp))
                        }
                    }
                    Text(
                        "OWNER SESSION VERIFICATION",
                        color = MahiAccent,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Secure Owner Console",
                        color = MahiTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Supabase verifies this signed-in account on the server. Only an active Owner profile can open the console; Member accounts are rejected.",
                        color = MahiTextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    if (pinError != null) {
                        Text(pinError.orEmpty(), color = MahiRedPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = {
                            if (!isVerifyingPin && authToken.isNotBlank()) {
                                isVerifyingPin = true
                                pinError = null
                                scope.launch {
                                    adminClient.getDashboard(authToken)
                                        .onSuccess {
                                            applyDashboard(it)
                                            isUnlocked = true
                                        }
                                        .onFailure {
                                            pinError = it.message ?: "Owner verification failed."
                                        }
                                    isVerifyingPin = false
                                }
                            }
                        },
                        enabled = authToken.isNotBlank() && !isVerifyingPin,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MahiAccent)
                    ) {
                        if (isVerifyingPin) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Verify Owner & Open Console", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(onClick = onExitAdmin) {
                        Text("Return to User Panel", color = MahiTextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
        return
    }

    // --- MAIN ADMIN PANEL ---
    Scaffold(
        containerColor = MahiBlack,
        topBar = {
            Surface(
                color = Color(0xFF0B1324),
                border = BorderStroke(1.dp, MahiCardStroke)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onExitAdmin) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MahiAccent)
                            }
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AdminPanelSettings, null, tint = MahiAccent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("MAHI REPAIR OWNER", color = MahiAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                }
                                Text("Governance & Live Control", color = MahiTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { refreshAdminData() }, enabled = !isLoadingData) {
                                if (isLoadingData) {
                                    CircularProgressIndicator(color = MahiAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MahiTextSecondary)
                                }
                            }
                            OutlinedButton(
                                onClick = { isUnlocked = false },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MahiRedPrimary),
                                border = BorderStroke(1.dp, MahiRedPrimary.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Lock", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MahiAccent,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MahiAccent,
                                height = 2.5.dp
                            )
                        },
                        divider = {},
                        edgePadding = 0.dp
                    ) {
                        AdminTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(tab.title, fontSize = 13.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                selectedContentColor = MahiAccent,
                                unselectedContentColor = MahiTextSecondary
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (AdminTab.entries[selectedTab]) {
                AdminTab.OVERVIEW -> AdminOverviewPage(
                    analytics = analyticsData,
                    maxWidth = maxContentWidth,
                    onOpenTools = { selectedTab = AdminTab.TOOLS.ordinal },
                    onOpenMembers = { selectedTab = AdminTab.MEMBERS.ordinal },
                    onOpenAI = { selectedTab = AdminTab.AI_ASSISTANT.ordinal }
                )
                AdminTab.TOOLS -> AdminToolsPage(
                    tools = toolsList,
                    maxWidth = maxContentWidth,
                    authToken = authToken,
                    onNewTool = { showCreateToolDialog = true },
                    onEdit = { editingTool = it },
                    onPublish = { toolId ->
                        scope.launch {
                            adminClient.publishTool(toolId, authToken).onSuccess {
                                statusFeedback = "Tool signed with RSA-3072 and published!"
                                refreshAdminData()
                            }
                        }
                    },
                    onDisable = { toolId ->
                        scope.launch {
                            adminClient.disableTool(toolId, authToken).onSuccess {
                                statusFeedback = "Tool disabled."
                                refreshAdminData()
                            }
                        }
                    }
                )
                AdminTab.MEMBERS -> AdminMembersPage(
                    members = membersList,
                    devices = devicesList,
                    maxWidth = maxContentWidth,
                    authToken = authToken,
                    onInvite = { showInviteMemberDialog = true },
                    onToggleMemberStatus = { memberId, newStatus ->
                        scope.launch {
                            adminClient.setMemberStatus(memberId, newStatus, authToken).onSuccess {
                                refreshAdminData()
                            }
                        }
                    },
                    onRevokeDevice = { deviceId ->
                        scope.launch {
                            adminClient.revokeDevice(deviceId, authToken).onSuccess {
                                refreshAdminData()
                            }
                        }
                    }
                )
                AdminTab.AI_ASSISTANT -> AdminAIAssistantPage(
                    messages = chatMessages,
                    input = chatInput,
                    isAILoading = isAILoading,
                    maxWidth = maxContentWidth,
                    onInputChange = { chatInput = it },
                    onSendMessage = { query, confirmed ->
                        if (query.isNotBlank() && !isAILoading) {
                            val userMsg = ChatMessage(id = "user_${System.currentTimeMillis()}", sender = "user", text = query)
                            chatMessages.add(userMsg)
                            chatInput = ""
                            isAILoading = true
                            scope.launch {
                                adminClient.sendGeminiInstruction(query, confirmed, authToken)
                                    .onSuccess { res ->
                                        val executedSummary = res.executedActions.map { it.action }
                                        val geminiMsg = ChatMessage(
                                            id = "gemini_${System.currentTimeMillis()}",
                                            sender = "gemini",
                                            text = res.text,
                                            executedActions = executedSummary,
                                            requiresConfirmation = res.requiresConfirmation,
                                            confirmationPrompt = res.confirmationPrompt
                                        )
                                        chatMessages.add(geminiMsg)
                                        isAILoading = false
                                        refreshAdminData()
                                    }
                                    .onFailure { err ->
                                        val errorMsg = ChatMessage(
                                            id = "err_${System.currentTimeMillis()}",
                                            sender = "gemini",
                                            text = "Error executing instruction: ${err.message}"
                                        )
                                        chatMessages.add(errorMsg)
                                        isAILoading = false
                                    }
                            }
                        }
                    }
                )
                AdminTab.SETTINGS -> AdminSettingsPage(
                    auditLogs = auditLogs,
                    maxWidth = maxContentWidth,
                    onChangePin = { statusFeedback = "Owner access is verified through the active Supabase session; the legacy PIN service is retired." }
                )
            }
        }
    }

    // --- EDIT TOOL DIALOG ---
    editingTool?.let { selectedTool ->
        var editedName by rememberSaveable(selectedTool.id) { mutableStateOf(selectedTool.name) }
        var editedDescription by rememberSaveable(selectedTool.id) { mutableStateOf(selectedTool.description) }
        var isSavingEdit by rememberSaveable(selectedTool.id) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSavingEdit) editingTool = null },
            title = { Text("Edit Draft Tool", color = MahiTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Published releases remain unchanged. Publish a new version after updating the draft.", color = MahiTextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Tool Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editedDescription,
                        onValueChange = { editedDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = editedName.isNotBlank() && !isSavingEdit,
                    onClick = {
                        isSavingEdit = true
                        scope.launch {
                            adminClient.updateTool(
                                selectedTool.id,
                                com.example.data.remote.UpdateToolRequest(
                                    name = editedName.trim(),
                                    description = editedDescription.trim(),
                                    category = selectedTool.category.lowercase()
                                ),
                                authToken
                            ).onSuccess {
                                statusFeedback = "Draft updated. Publish to create a new signed release."
                                editingTool = null
                                refreshAdminData()
                            }.onFailure {
                                statusFeedback = it.message ?: "Draft update failed."
                            }
                            isSavingEdit = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MahiAccent)
                ) {
                    if (isSavingEdit) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Save Draft")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isSavingEdit) editingTool = null }) {
                    Text("Cancel", color = MahiTextMuted)
                }
            },
            containerColor = MahiCardSurface
        )
    }

    // --- CREATE TOOL DIALOG ---
    if (showCreateToolDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        var slug by rememberSaveable { mutableStateOf("") }
        var category by rememberSaveable { mutableStateOf("connectivity") }
        var description by rememberSaveable { mutableStateOf("") }
        var problemDescription by rememberSaveable { mutableStateOf("") }
        var estimatedSeconds by rememberSaveable { mutableStateOf("60") }
        var isSubmitting by rememberSaveable { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showCreateToolDialog = false },
            title = { Text("Create New Repair Tool", color = MahiTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (slug.isBlank() || slug.startsWith(name.take(3).lowercase())) {
                                slug = it.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                            }
                        },
                        label = { Text("Tool Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = slug,
                        onValueChange = { slug = it.lowercase().replace(" ", "-") },
                        label = { Text("Slug Identifier") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = problemDescription,
                        onValueChange = { problemDescription = it },
                        label = { Text("Problem Diagnosed") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = estimatedSeconds,
                        onValueChange = { if (it.all { c -> c.isDigit() }) estimatedSeconds = it },
                        label = { Text("Estimated Duration (seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && slug.isNotBlank() && !isSubmitting) {
                            isSubmitting = true
                            scope.launch {
                                adminClient.createTool(
                                    CreateToolRequest(
                                        name = name.trim(),
                                        slug = slug.trim(),
                                        category = category,
                                        description = description.trim(),
                                        draftRecipe = mapOf(
                                            "schemaVersion" to 1,
                                            "toolId" to slug.trim(),
                                            "title" to name.trim(),
                                            "version" to 1,
                                            "category" to category,
                                            "minAndroidApi" to 26,
                                            "minAppVersion" to "1.0.0",
                                            "estimatedSeconds" to (estimatedSeconds.toIntOrNull() ?: 60),
                                            "changelog" to problemDescription.trim().ifBlank { "Initial draft" },
                                            "steps" to listOf(
                                                mapOf(
                                                    "id" to "initial-diagnostic",
                                                    "action" to "CHECK_INTERNET",
                                                    "params" to emptyMap<String, Any?>(),
                                                    "risk" to "read_only",
                                                    "requiresConfirmation" to false
                                                )
                                            )
                                        )
                                    ),
                                    authToken
                                ).onSuccess {
                                    showCreateToolDialog = false
                                    refreshAdminData()
                                }
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = name.isNotBlank() && slug.isNotBlank() && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MahiAccent)
                ) {
                    if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Create Tool")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateToolDialog = false }) { Text("Cancel", color = MahiTextMuted) }
            },
            containerColor = MahiCardSurface
        )
    }

    // --- INVITE MEMBER DIALOG ---
    if (showInviteMemberDialog) {
        var email by rememberSaveable { mutableStateOf("") }
        var displayName by rememberSaveable { mutableStateOf("") }
        var isSubmitting by rememberSaveable { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showInviteMemberDialog = false },
            title = { Text("Invite Member", color = MahiTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim().lowercase() },
                        label = { Text("Member Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        "Invited accounts are always created as Members. Owner access is never granted through Member invitations.",
                        color = MahiTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (email.contains("@") && !isSubmitting) {
                            isSubmitting = true
                            scope.launch {
                                adminClient.inviteMember(
                                    InviteMemberRequest(email = email, role = "member", displayName = displayName),
                                    authToken
                                ).onSuccess {
                                    showInviteMemberDialog = false
                                    refreshAdminData()
                                }
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = email.contains("@") && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = MahiAccent)
                ) {
                    if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Send Invitation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInviteMemberDialog = false }) { Text("Cancel", color = MahiTextMuted) }
            },
            containerColor = MahiCardSurface
        )
    }


}

// ==========================================
// --- TAB 1: OVERVIEW & DASHBOARD ---
// ==========================================
@Composable
private fun AdminOverviewPage(
    analytics: AdminAnalyticsData?,
    maxWidth: Dp,
    onOpenTools: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenAI: () -> Unit
) {
    Column(
        Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Action Bar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenAI,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MahiAccent)
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ask Gemini", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onOpenTools,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MahiCardStroke)
            ) {
                Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Manage Tools", fontSize = 12.sp)
            }
        }

        // Metrics Grid
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricStatCard(Modifier.weight(1f), "Total Runs", analytics?.totalRuns?.toString() ?: "—", "All-time repairs")
            MetricStatCard(Modifier.weight(1f), "Success Rate", analytics?.let { "${it.successRate.toInt()}%" } ?: "—", "Passed repairs")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricStatCard(Modifier.weight(1f), "Active Members", analytics?.activeMembersCount?.toString() ?: "—", "Registered users")
            MetricStatCard(Modifier.weight(1f), "Active Devices", analytics?.activeDevicesCount?.toString() ?: "—", "Verified devices")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricStatCard(Modifier.weight(1f), "Total Tools", analytics?.totalTools?.toString() ?: "—", "In catalog")
            MetricStatCard(Modifier.weight(1f), "Published", analytics?.publishedTools?.toString() ?: "—", "Signed & live")
        }

        Spacer(Modifier.height(8.dp))
        Text("Recent Repair Runs", color = MahiTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        if (analytics?.recentRuns.isNullOrEmpty()) {
            AdminEmptyCard("No repair runs recorded yet", "Repair executions by connected members will appear here in real-time.")
        } else {
            analytics?.recentRuns?.take(8)?.forEach { run ->
                AdminRunRowCard(run)
            }
        }
    }
}

// ==========================================
// --- TAB 2: TOOLS MANAGEMENT ---
// ==========================================
@Composable
private fun AdminToolsPage(
    tools: List<AdminToolItem>,
    maxWidth: Dp,
    authToken: String,
    onNewTool: () -> Unit,
    onEdit: (AdminToolItem) -> Unit,
    onPublish: (String) -> Unit,
    onDisable: (String) -> Unit
) {
    var filterStatus by rememberSaveable { mutableStateOf("ALL") }
    val filtered = tools.filter {
        filterStatus == "ALL" || it.status.equals(filterStatus, ignoreCase = true)
    }

    Column(
        Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Repair Tools", color = MahiTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${tools.size} tools defined", color = MahiTextSecondary, fontSize = 12.sp)
            }
            Button(
                onClick = onNewTool,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MahiAccent)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Tool", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Filters
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL", "PUBLISHED", "DRAFT", "DISABLED").forEach { st ->
                FilterChip(
                    selected = filterStatus == st,
                    onClick = { filterStatus = st },
                    label = { Text(if (st == "ALL") "All" else st.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        if (filtered.isEmpty()) {
            AdminEmptyCard("No tools match filter", "Create a new tool or switch status filter.")
        } else {
            filtered.forEach { tool ->
                AdminToolCard(
                    tool = tool,
                    onEdit = { onEdit(tool) },
                    onPublish = { onPublish(tool.id) },
                    onDisable = { onDisable(tool.id) }
                )
            }
        }
    }
}

@Composable
private fun AdminToolCard(
    tool: AdminToolItem,
    onEdit: () -> Unit,
    onPublish: () -> Unit,
    onDisable: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
        border = BorderStroke(1.dp, MahiCardStroke)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tool.name, color = MahiTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        AdminStatusBadge(tool.status)
                    }
                    Text("${tool.category} · slug: ${tool.slug} · v${tool.version}", color = MahiTextMuted, fontSize = 11.sp)
                }
            }

            if (tool.description.isNotBlank()) {
                Text(tool.description, color = MahiTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Divider(color = MahiCardStroke)

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Audience: ${tool.audience.replace('_', ' ')}", color = MahiTextMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Edit", fontSize = 11.sp)
                    }
                    if (tool.status != "PUBLISHED") {
                        Button(
                            onClick = onPublish,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MahiSuccessGreen)
                        ) {
                            Icon(Icons.Default.Publish, null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sign & Publish", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (tool.status != "DISABLED") {
                        OutlinedButton(
                            onClick = onDisable,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MahiWarningAmber),
                            border = BorderStroke(1.dp, MahiWarningAmber.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Block, null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Disable", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// --- TAB 3: MEMBERS & DEVICES ---
// ==========================================
@Composable
private fun AdminMembersPage(
    members: List<AdminMemberItem>,
    devices: List<AdminDeviceItem>,
    maxWidth: Dp,
    authToken: String,
    onInvite: () -> Unit,
    onToggleMemberStatus: (String, String) -> Unit,
    onRevokeDevice: (String) -> Unit
) {
    var subTab by rememberSaveable { mutableStateOf("MEMBERS") }

    Column(
        Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = subTab == "MEMBERS",
                    onClick = { subTab = "MEMBERS" },
                    label = { Text("Members (${members.size})") }
                )
                FilterChip(
                    selected = subTab == "DEVICES",
                    onClick = { subTab = "DEVICES" },
                    label = { Text("Devices (${devices.size})") }
                )
            }
            if (subTab == "MEMBERS") {
                Button(
                    onClick = onInvite,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MahiAccent)
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Invite", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (subTab == "MEMBERS") {
            members.forEach { m ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
                    border = BorderStroke(1.dp, MahiCardStroke)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = MahiAccentDim, modifier = Modifier.size(38.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    m.email.take(2).uppercase(),
                                    color = MahiAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.displayName.ifBlank { m.email }, color = MahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                AdminStatusBadge(m.role.uppercase())
                            }
                            Text(m.email, color = MahiTextSecondary, fontSize = 11.sp)
                        }
                        if (m.role != "owner") {
                            TextButton(
                                onClick = {
                                    val nextStatus = if (m.status == "active") "suspended" else "active"
                                    onToggleMemberStatus(m.id, nextStatus)
                                }
                            ) {
                                Text(
                                    if (m.status == "active") "Suspend" else "Activate",
                                    color = if (m.status == "active") MahiWarningAmber else MahiSuccessGreen,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            devices.forEach { d ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
                    border = BorderStroke(1.dp, MahiCardStroke)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = MahiAccentDim, modifier = Modifier.size(38.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Devices, null, tint = MahiAccent, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(d.deviceLabel.ifBlank { "Android Device" }, color = MahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                AdminStatusBadge(if (d.revokedAt != null) "REVOKED" else "ACTIVE")
                            }
                            Text("User: ${d.userEmail ?: d.userId} · Android ${d.androidVersion}", color = MahiTextSecondary, fontSize = 11.sp)
                        }
                        if (d.revokedAt == null) {
                            OutlinedButton(
                                onClick = { onRevokeDevice(d.id) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MahiRedPrimary),
                                border = BorderStroke(1.dp, MahiRedPrimary.copy(alpha = 0.4f))
                            ) {
                                Text("Revoke", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// --- TAB 4: GEMINI AI ADMIN ASSISTANT ---
// ==========================================
@Composable
private fun AdminAIAssistantPage(
    messages: List<ChatMessage>,
    input: String,
    isAILoading: Boolean,
    maxWidth: Dp,
    onInputChange: (String) -> Unit,
    onSendMessage: (String, Boolean) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        Modifier
            .widthIn(max = maxWidth)
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Chat History
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(msg = msg, onConfirmAction = { onSendMessage(it, true) })
            }
            if (isAILoading) {
                item {
                    Row(
                        Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = MahiAccent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Gemini is analyzing platform state…", color = MahiTextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        // Suggestions Row
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "List failed repair runs",
                "Create DNS flush repair tool",
                "Show active member count",
                "Publish all draft tools"
            ).forEach { prompt ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MahiCardSurface,
                    border = BorderStroke(1.dp, MahiCardStroke),
                    modifier = Modifier.clickable { onSendMessage(prompt, false) }
                ) {
                    Text(
                        prompt,
                        color = MahiTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Input Field
        Row(
            Modifier
                .fillMaxWidth()
                .background(MahiCardSurface, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, MahiCardStroke), RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Gemini to manage tools, inspect runs…", color = MahiTextMuted, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = MahiTextPrimary,
                    unfocusedTextColor = MahiTextPrimary
                ),
                keyboardActions = KeyboardActions(onDone = { onSendMessage(input, false) })
            )
            IconButton(
                onClick = { onSendMessage(input, false) },
                enabled = input.isNotBlank() && !isAILoading
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (input.isNotBlank() && !isAILoading) MahiAccent else MahiTextMuted
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    onConfirmAction: (String) -> Unit
) {
    val isUser = msg.sender == "user"
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 380.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MahiAccent else Color(0xFF131D33)
            ),
            border = if (isUser) null else BorderStroke(1.dp, MahiCardStroke)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MahiAccent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Gemini AI Admin", color = MahiAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(msg.text, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)

                if (msg.executedActions.isNotEmpty()) {
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MahiSuccessGreen, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Executed: ${msg.executedActions.joinToString(", ")}", color = MahiSuccessGreen, fontSize = 10.sp)
                    }
                }

                if (msg.requiresConfirmation && msg.confirmationPrompt != null) {
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Text(msg.confirmationPrompt, color = MahiWarningAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = { onConfirmAction(msg.confirmationPrompt) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MahiWarningAmber)
                    ) {
                        Text("Confirm Action", color = MahiBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// --- TAB 5: SETTINGS & AUDIT LOGS ---
// ==========================================
@Composable
private fun AdminSettingsPage(
    auditLogs: List<AdminAuditLogItem>,
    maxWidth: Dp,
    onChangePin: () -> Unit
) {
    Column(
        Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
            border = BorderStroke(1.dp, MahiCardStroke)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = MahiAccent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Administrative Security PIN", color = MahiTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Protects server-side governance and Gemini actions.", color = MahiTextMuted, fontSize = 11.sp)
                    }
                }
                Text("Stored securely with PBKDF2-SHA512 (100,000 iterations) and timing attack protection.", color = MahiTextSecondary, fontSize = 12.sp)
                Button(
                    onClick = onChangePin,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MahiAccent)
                ) {
                    Text("Change Security PIN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Platform Audit Trail", color = MahiTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        if (auditLogs.isEmpty()) {
            AdminEmptyCard("No audit events recorded yet", "Platform mutations and governance events will appear here.")
        } else {
            auditLogs.take(15).forEach { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
                    border = BorderStroke(1.dp, MahiCardStroke)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(log.action, color = MahiTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(log.createdAt.take(19).replace('T', ' '), color = MahiTextMuted, fontSize = 10.sp)
                        }
                        Text("Target: ${log.targetType} ${log.targetId ?: ""} · Actor: ${log.actorId.take(12)}", color = MahiTextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// --- REUSABLE ADMIN ATOMS ---
// ==========================================

@Composable
private fun MetricStatCard(modifier: Modifier, title: String, value: String, subtitle: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
        border = BorderStroke(1.dp, MahiCardStroke)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = MahiTextMuted, fontSize = 11.sp)
            Text(value, color = MahiTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MahiTextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun AdminRunRowCard(run: AdminRecentRunDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
        border = BorderStroke(1.dp, MahiCardStroke)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(run.toolName ?: run.toolId, color = MahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("User: ${run.userEmail ?: "Member"} · ${run.startedAt.take(19).replace('T', ' ')}", color = MahiTextMuted, fontSize = 11.sp)
            }
            AdminStatusBadge(run.result)
        }
    }
}

@Composable
private fun AdminStatusBadge(status: String) {
    val (color, bg) = when (status.uppercase()) {
        "PUBLISHED", "SUCCESS", "ACTIVE" -> MahiSuccessGreen to MahiSuccessDim
        "DRAFT", "PARTIAL" -> MahiWarningAmber to MahiWarningDim
        "DISABLED", "FAILED", "REVOKED" -> MahiRedPrimary to MahiRedPrimary.copy(alpha = 0.15f)
        else -> MahiTextSecondary to Color(0xFF222C42)
    }
    Surface(shape = CircleShape, color = bg) {
        Text(
            status.uppercase(),
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AdminEmptyCard(title: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MahiCardSurface),
        border = BorderStroke(1.dp, MahiCardStroke)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = MahiTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(message, color = MahiTextSecondary, fontSize = 12.sp)
        }
    }
}
