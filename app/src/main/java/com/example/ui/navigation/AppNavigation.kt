package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.EditorViewModel

enum class Screen {
    PROJECTS,
    TEMPLATES,
    PREMIUM,
    EDITOR,
    EXPORT,
    IMPORT,
    PLAYER,
    CAPTIONS,
    SETTINGS,
    ONBOARDING
}

@Composable
fun AppNavigation(
    viewModel: EditorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentScreen by remember { mutableStateOf(Screen.PROJECTS) }
    var previousScreen by remember { mutableStateOf(Screen.PROJECTS) }

    fun navigateTo(screen: Screen) {
        previousScreen = currentScreen
        currentScreen = screen
    }

    // Handle back button on sub-screens
    if (currentScreen != Screen.PROJECTS && currentScreen != Screen.TEMPLATES && currentScreen != Screen.PREMIUM) {
        BackHandler {
            currentScreen = if (currentScreen == Screen.EDITOR || currentScreen == Screen.SETTINGS || currentScreen == Screen.ONBOARDING) {
                Screen.PROJECTS
            } else if (currentScreen == Screen.EXPORT || currentScreen == Screen.IMPORT || currentScreen == Screen.CAPTIONS || currentScreen == Screen.PLAYER) {
                Screen.EDITOR
            } else {
                previousScreen
            }
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            if (currentScreen == Screen.PROJECTS || currentScreen == Screen.TEMPLATES || currentScreen == Screen.PREMIUM) {
                Surface(
                    color = BackgroundElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = BackgroundElevated,
                        contentColor = PrimaryPurpleVariant,
                        tonalElevation = 0.dp,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == Screen.PROJECTS,
                            onClick = { currentScreen = Screen.PROJECTS },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = "Projetos"
                                )
                            },
                            label = {
                                Text(
                                    text = "Projetos",
                                    fontSize = 12.sp,
                                    fontWeight = if (currentScreen == Screen.PROJECTS) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryPurpleVariant,
                                selectedTextColor = PrimaryPurpleVariant,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor = PrimaryPurpleSoft
                            ),
                            modifier = Modifier.testTag("tab_projects")
                        )

                        NavigationBarItem(
                            selected = currentScreen == Screen.TEMPLATES,
                            onClick = { currentScreen = Screen.TEMPLATES },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Modelos"
                                )
                            },
                            label = {
                                Text(
                                    text = "Modelos",
                                    fontSize = 12.sp,
                                    fontWeight = if (currentScreen == Screen.TEMPLATES) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryPurpleVariant,
                                selectedTextColor = PrimaryPurpleVariant,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor = PrimaryPurpleSoft
                            ),
                            modifier = Modifier.testTag("tab_templates")
                        )

                        NavigationBarItem(
                            selected = currentScreen == Screen.PREMIUM,
                            onClick = { currentScreen = Screen.PREMIUM },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Diamond,
                                    contentDescription = "Premium"
                                )
                            },
                            label = {
                                Text(
                                    text = "Premium",
                                    fontSize = 12.sp,
                                    fontWeight = if (currentScreen == Screen.PREMIUM) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GoldPremium,
                                selectedTextColor = GoldPremium,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor = GoldPremium.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.testTag("tab_premium")
                        )
                    }
                }
            }
        },
        snackbarHost = {
            uiState.feedbackMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryPurpleVariant),
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = msg,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.PROJECTS -> ProjectsScreen(
                    viewModel = viewModel,
                    onNavigateToEditor = { navigateTo(Screen.EDITOR) },
                    onNavigateToImport = { navigateTo(Screen.IMPORT) },
                    onNavigateToSettings = { navigateTo(Screen.SETTINGS) },
                    onNavigateToPremium = { navigateTo(Screen.PREMIUM) }
                )
                Screen.TEMPLATES -> TemplatesScreen(
                    viewModel = viewModel,
                    onNavigateToEditor = { navigateTo(Screen.EDITOR) },
                    onNavigateToPremium = { navigateTo(Screen.PREMIUM) }
                )
                Screen.PREMIUM -> PremiumScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateTo(Screen.PROJECTS) }
                )
                Screen.EDITOR -> EditorScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateTo(Screen.PROJECTS) },
                    onNavigateToExport = { navigateTo(Screen.EXPORT) },
                    onNavigateToImport = { navigateTo(Screen.IMPORT) },
                    onNavigateToCaptions = { navigateTo(Screen.CAPTIONS) }
                )
                Screen.EXPORT -> ExportScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateTo(Screen.EDITOR) },
                    onNavigateToPlayer = { navigateTo(Screen.PLAYER) },
                    onNavigateToPremium = { navigateTo(Screen.PREMIUM) }
                )
                Screen.IMPORT -> ImportScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateTo(Screen.EDITOR) }
                )
                Screen.PLAYER -> PlayerScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateTo(Screen.EDITOR) }
                )
                Screen.CAPTIONS -> CaptionsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateTo(Screen.EDITOR) },
                    onNavigateToPremium = { navigateTo(Screen.PREMIUM) }
                )
                Screen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateTo(Screen.PROJECTS) }
                )
                Screen.ONBOARDING -> OnboardingScreen(
                    onFinishOnboarding = { navigateTo(Screen.PROJECTS) }
                )
            }
        }
    }
}
