package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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

/**
 * Tela 14: CONFIGURAÇÕES - Refinada conforme layout app.png
 */
@Composable
fun SettingsScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPremium: () -> Unit = {}
) {
    var autoSave by remember { mutableStateOf(true) }
    var cacheSizeMb by remember { mutableStateOf("42.8 MB") }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

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
                    fontSize = 20.sp,
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Seção Geral
            Text(
                text = "Geral",
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SettingItemRow(
                        title = "Idioma",
                        value = "Português (Brasil)",
                        onClick = { viewModel.setFeedback("Idioma padrão: Português (Brasil)") }
                    )
                    HorizontalDivider(color = BorderSubtle)
                    SettingItemRow(
                        title = "Tema",
                        value = "Escuro Profundo (OLED)",
                        onClick = { viewModel.setFeedback("Tema escuro otimizado ativo") }
                    )
                    HorizontalDivider(color = BorderSubtle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Salvar automaticamente",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Salva o projeto a cada edição",
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = autoSave,
                            onCheckedChange = {
                                autoSave = it
                                viewModel.setFeedback(if (it) "Salvamento automático ativado" else "Salvamento automático desativado")
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SuccessGreen,
                                uncheckedThumbColor = TextTertiary,
                                uncheckedTrackColor = SurfaceElevated
                            )
                        )
                    }
                    HorizontalDivider(color = BorderSubtle)
                    SettingItemRow(
                        title = "Limpar Cache Temporário",
                        value = cacheSizeMb,
                        onClick = {
                            if (cacheSizeMb != "0 MB") {
                                cacheSizeMb = "0 MB"
                                viewModel.setFeedback("Cache limpo! Espaço em disco liberado.")
                            } else {
                                viewModel.setFeedback("Cache já está limpo.")
                            }
                        }
                    )
                    HorizontalDivider(color = BorderSubtle)
                    SettingItemRow(
                        title = "Pasta de projetos",
                        value = "/BotiVideoEditor"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Seção Premium
            Text(
                text = "Assinatura",
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (uiState.isPremiumUser) GoldPremium.copy(alpha = 0.5f) else BorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPremium() }
                    .testTag("settings_premium_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = GoldPremium,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (uiState.isPremiumUser) "Plano VIP Pro Ativo" else "Upgrade para VIP Pro",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (uiState.isPremiumUser) "Acesso total sem limites" else "Sem marca d'água, 4K UHD e todos os efeitos",
                                color = GoldPremiumLight,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Seção Sobre
            Text(
                text = "Sobre",
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SettingItemRow(
                        title = "Avaliar o app",
                        value = "★★★★★",
                        onClick = { showRatingDialog = true }
                    )
                    HorizontalDivider(color = BorderSubtle)
                    SettingItemRow(
                        title = "Política de privacidade",
                        value = "100% Offline e Seguro",
                        onClick = { showPrivacyDialog = true }
                    )
                    HorizontalDivider(color = BorderSubtle)
                    SettingItemRow(
                        title = "Versão do Aplicativo",
                        value = "v2.5.0 Pro"
                    )
                }
            }
        }
    }

    if (showRatingDialog) {
        AlertDialog(
            onDismissRequest = { showRatingDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Avaliar o BOTI Video Editor", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Obrigado por usar o BOTI Video Editor! Avaliações nos ajudam a continuar trazendo novos recursos e efeitos profissionais.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRatingDialog = false
                        viewModel.setFeedback("Obrigado pela sua avaliação 5 estrelas! ★★★★★")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("Avaliar 5 Estrelas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRatingDialog = false }) {
                    Text("Mais tarde", color = TextSecondary)
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Privacidade & Segurança", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "O BOTI Video Editor processa todos os seus vídeos, fotos e áudios localmente no dispositivo. Nenhum conteúdo pessoal é transferido para servidores de terceiros sem seu consentimento explícito.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("Entendi")
                }
            }
        )
    }
}

@Composable
private fun SettingItemRow(
    title: String,
    value: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
