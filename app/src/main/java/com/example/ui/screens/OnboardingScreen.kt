package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PrimaryGradientButton
import com.example.ui.theme.*

data class OnboardingStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val steps = listOf(
        OnboardingStep(
            title = "Edição Fácil e Poderosa",
            description = "Corte, divida e combine vídeos com precisão cirúrgica diretamente no seu celular.",
            icon = Icons.Default.Movie,
            gradientColors = listOf(Color(0xFF7C3AED), Color(0xFF5B21B6))
        ),
        OnboardingStep(
            title = "Efeitos e Transições Incríveis",
            description = "Transforme seus vídeos com efeitos visuais VFX, glitches, desfoques e filtros de cor vibrantes.",
            icon = Icons.Default.AutoAwesome,
            gradientColors = listOf(Color(0xFF8A2BE2), Color(0xFF6D28D9))
        ),
        OnboardingStep(
            title = "Legendas com IA & 4K 60FPS",
            description = "Gere legendas automáticas perfeitamente sincronizadas e exporte na melhor resolução do mercado.",
            icon = Icons.Default.Subtitles,
            gradientColors = listOf(Color(0xFF6D28D9), Color(0xFF4C1D95))
        )
    )

    var currentStep by remember { mutableStateOf(0) }
    val step = steps[currentStep]

    Scaffold(
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Skip Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinishOnboarding) {
                    Text("Pular", color = TextTertiary, fontSize = 14.sp)
                }
            }

            // Center Illustration Box
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Brush.linearGradient(step.gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = step.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = step.description,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Dots indicator
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (index == currentStep) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (index == currentStep) PrimaryPurpleVariant else BorderStrong)
                        )
                    }
                }
            }

            // Bottom Buttons
            PrimaryGradientButton(
                text = if (currentStep == steps.size - 1) "Começar a Criar" else "Próximo",
                onClick = {
                    if (currentStep < steps.size - 1) {
                        currentStep++
                    } else {
                        onFinishOnboarding()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "onboarding_next_button"
            )
        }
    }
}
