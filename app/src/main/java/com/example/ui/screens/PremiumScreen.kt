package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GradientPremium
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.theme.*
import com.example.viewmodel.EditorViewModel

@Composable
fun PremiumScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedPlan by remember { mutableStateOf("annual") } // "annual" or "monthly"
    var showSuccessDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp)
        ) {
            // Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF5B21B6),
                                Color(0xFF2E1065),
                                BackgroundDark
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(GoldPremium.copy(alpha = 0.2f))
                            .border(2.dp, GoldPremium, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Diamond,
                            contentDescription = null,
                            tint = GoldPremium,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Desbloqueie o Boti PRO",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Edição profissional sem limitações",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Features List
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        PremiumFeatureItem(
                            icon = Icons.Default.HighQuality,
                            title = "Exportação 4K a 60 FPS",
                            description = "Qualidade máxima sem perda de fidelidade"
                        )
                        Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                        PremiumFeatureItem(
                            icon = Icons.Default.CheckCircle,
                            title = "Sem Marca d'Água",
                            description = "Seus vídeos 100% limpos e profissionais"
                        )
                        Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                        PremiumFeatureItem(
                            icon = Icons.Default.Subtitles,
                            title = "Legendas Automáticas com IA",
                            description = "Geração ilimitada em mais de 7 idiomas"
                        )
                        Divider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))
                        PremiumFeatureItem(
                            icon = Icons.Default.AutoAwesome,
                            title = "+100 Efeitos Visuais & Transições",
                            description = "Acesso irrestrito a todos os efeitos VIP"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Plan options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Annual
                    val isAnnual = selectedPlan == "annual"
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedPlan = "annual" }
                            .border(
                                width = if (isAnnual) 2.dp else 1.dp,
                                color = if (isAnnual) PrimaryPurpleVariant else BorderStrong,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .testTag("plan_annual"),
                        color = if (isAnnual) PrimaryPurpleSoft else SurfaceElevated
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PrimaryPurple
                            ) {
                                Text(
                                    text = "-50% OFF",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Anual",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "R$ 89,90",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isAnnual) PrimaryPurpleVariant else TextPrimary
                            )
                            Text(
                                text = "equiv. R$ 7,49/mês",
                                fontSize = 11.sp,
                                color = TextTertiary
                            )
                        }
                    }

                    // Monthly
                    val isMonthly = selectedPlan == "monthly"
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedPlan = "monthly" }
                            .border(
                                width = if (isMonthly) 2.dp else 1.dp,
                                color = if (isMonthly) PrimaryPurpleVariant else BorderStrong,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .testTag("plan_monthly"),
                        color = if (isMonthly) PrimaryPurpleSoft else SurfaceElevated
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Flexível",
                                color = TextTertiary,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Mensal",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "R$ 14,90",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isMonthly) PrimaryPurpleVariant else TextPrimary
                            )
                            Text(
                                text = "por mês",
                                fontSize = 11.sp,
                                color = TextTertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // CTA Button
                if (uiState.isPremiumUser) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SuccessGreen.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Você já é assinante Boti PRO!",
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    PrimaryGradientButton(
                        text = if (selectedPlan == "annual") "Assinar Anual (R$ 89,90)" else "Assinar Mensal (R$ 14,90)",
                        onClick = {
                            viewModel.subscribePremium()
                            showSuccessDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Diamond,
                        testTag = "subscribe_premium_button"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Restaurar Compras • Cancelamento fácil a qualquer momento",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            containerColor = SurfaceDark,
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(44.dp)
                )
            },
            title = {
                Text("Assinatura Ativada!", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Todos os recursos PRO estão liberados: exportação em 4K 60FPS, sem marca d'água e legendas IA ilimitadas.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) {
                    Text("Excelente!")
                }
            }
        )
    }
}

@Composable
private fun PremiumFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaryPurple.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryPurpleVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = TextTertiary,
                fontSize = 12.sp
            )
        }
    }
}
