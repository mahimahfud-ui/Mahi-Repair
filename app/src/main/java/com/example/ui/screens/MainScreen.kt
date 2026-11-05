package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.models.ToolCategory
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.components.ExecutionDialog
import com.example.ui.components.ToolCard
import com.example.ui.theme.MahiBlack
import com.example.ui.theme.MahiCardStroke
import com.example.ui.theme.MahiCardSurface
import com.example.ui.theme.MahiRedDim
import com.example.ui.theme.MahiRedPrimary
import com.example.ui.theme.MahiSuccessGreen
import com.example.ui.theme.MahiSurfaceDark
import com.example.ui.theme.MahiTextMuted
import com.example.ui.theme.MahiTextPrimary
import com.example.ui.theme.MahiTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    viewModel: MainViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.syncStatusMessage) {
        state.syncStatusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onClearStatusMessage()
        }
    }

    Scaffold(
        containerColor = MahiBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MahiSurfaceDark,
                    titleContentColor = MahiTextPrimary
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF161820),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MahiRedPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_logo),
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Mahi Repair",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MahiTextPrimary
                                )
                            )
                            Text(
                                text = if (state.session.isAuthenticated) state.session.email else "Personal Android Toolkit",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MahiTextMuted,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    // Sync action
                    IconButton(
                        onClick = { viewModel.onSync() },
                        enabled = !state.isSyncing,
                        modifier = Modifier.testTag("sync_button")
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                color = MahiRedPrimary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                tint = MahiTextSecondary
                            )
                        }
                    }

                    // History action
                    IconButton(
                        onClick = { viewModel.setShowHistoryDialog(true) },
                        modifier = Modifier.testTag("history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = MahiTextSecondary
                        )
                    }

                    // Sign Out
                    if (state.session.isAuthenticated) {
                        IconButton(
                            onClick = { viewModel.onSignOut() },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Sign Out",
                                tint = MahiRedPrimary
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Status Bar: Cloud Sync & Timestamp
            Surface(
                color = Color(0xFF12131A),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MahiCardStroke),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MahiSuccessGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Repair tools",
                            color = MahiTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val syncTimeStr = if (state.lastSyncTime > 0) {
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(state.lastSyncTime))
                    } else "Never"

                    Text(
                        text = "Synced: $syncTimeStr",
                        color = MahiTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = { Text("Search repair tools...", color = MahiTextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MahiTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MahiTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MahiTextPrimary,
                        unfocusedTextColor = MahiTextPrimary,
                        focusedBorderColor = MahiRedPrimary,
                        unfocusedBorderColor = MahiCardStroke,
                        focusedContainerColor = MahiCardSurface,
                        unfocusedContainerColor = MahiCardSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("search_bar")
                )
            }

            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolCategory.entries.forEach { category ->
                    val isSelected = category == state.selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onSelectCategory(category) },
                        label = {
                            Text(
                                text = category.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MahiCardSurface,
                            labelColor = MahiTextSecondary,
                            selectedContainerColor = MahiRedPrimary,
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MahiCardStroke,
                            selectedBorderColor = MahiRedPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tool List or Empty State
            if (state.filteredTools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1E202A),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = MahiTextMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No repair tools found",
                            color = MahiTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (state.searchQuery.isNotBlank()) {
                                "No tools matched '${state.searchQuery}'."
                            } else {
                                "The Owner has not published any tools in this category yet."
                            },
                            color = MahiTextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("tools_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.filteredTools, key = { it.id }) { tool ->
                        ToolCard(
                            tool = tool,
                            onRun = { viewModel.onRunTool(it) }
                        )
                    }
                }
            }
        }
    }

    // Execution Dialog Modal
    ExecutionDialog(
        executionEvent = state.executionState,
        onSubmitUserAction = { viewModel.onSubmitUserAction(it) },
        onDismiss = { viewModel.onDismissExecution() }
    )

    // History Dialog Modal
    if (state.showHistoryDialog) {
        HistoryDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.setShowHistoryDialog(false) }
        )
    }

}
