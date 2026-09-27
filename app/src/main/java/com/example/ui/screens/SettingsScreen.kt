package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.EditorViewModel

@Composable
fun SettingsScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    var hardwareAcc by remember { mutableStateOf(true) }
    var cacheCleaned by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Configurações",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // General
            Text("Geral", color = PrimaryPurpleVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingRow(
                        icon = Icons.Default.Language,
                        title = "Idioma",
                        value = "Português (Brasil)"
                    )
                    Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                    SettingRow(
                        icon = Icons.Default.DarkMode,
                        title = "Tema",
                        value = "Escuro Studio"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Performance & Render
            Text("Renderização & Hardware", color = PrimaryPurpleVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = TextSecondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Aceleração por Hardware", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Renderização rápida via GPU", color = TextTertiary, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = hardwareAcc,
                            onCheckedChange = { hardwareAcc = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryPurple, checkedTrackColor = PrimaryPurpleSoft)
                        )
                    }

                    Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = TextSecondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Limpar Cache Temporário", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(if (cacheCleaned) "Cache limpo: 0 MB" else "Tamanho em disco: 138 MB", color = TextTertiary, fontSize = 11.sp)
                            }
                        }
                        TextButton(
                            onClick = {
                                cacheCleaned = true
                                viewModel.setFeedback("Cache limpo com sucesso!")
                            },
                            modifier = Modifier.testTag("clear_cache_button")
                        ) {
                            Text("Limpar", color = PrimaryPurpleVariant, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // About
            Text("Sobre o Aplicativo", color = PrimaryPurpleVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingRow(
                        icon = Icons.Default.Info,
                        title = "Versão",
                        value = "1.0.0 (Build 2026)"
                    )
                    Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                    SettingRow(
                        icon = Icons.Default.Shield,
                        title = "Política de Privacidade",
                        value = "Ler termos"
                    )
                    Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                    SettingRow(
                        icon = Icons.Default.Headphones,
                        title = "Suporte & Contato",
                        value = "support@boti.dev"
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text(text = value, color = TextTertiary, fontSize = 13.sp)
    }
}
