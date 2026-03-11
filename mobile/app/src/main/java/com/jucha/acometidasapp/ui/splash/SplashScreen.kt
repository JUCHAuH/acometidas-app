package com.jucha.acometidasapp.ui.splash

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jucha.acometidasapp.core.navigation.Routes
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {

    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
        delay(2400)
        navController.navigate(Routes.LOGIN) {
            popUpTo(Routes.SPLASH) { inclusive = true }
        }
    }

    // Icono: escala con rebote suave + fade
    val iconScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.15f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "iconScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue      = if (started) 1f else 0f,
        animationSpec    = tween(durationMillis = 450),
        label            = "iconAlpha"
    )

    // Título: sube desde abajo + fade (empieza a los 350 ms)
    val titleOffset by animateFloatAsState(
        targetValue   = if (started) 0f else 70f,
        animationSpec = tween(durationMillis = 600, delayMillis = 350),
        label         = "titleOffset"
    )
    val titleAlpha by animateFloatAsState(
        targetValue   = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 350),
        label         = "titleAlpha"
    )

    // Subtítulo: fade más tarde (a los 750 ms)
    val subAlpha by animateFloatAsState(
        targetValue   = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 750),
        label         = "subAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A1628), Color(0xFF1565A0))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icono con rebote
            Icon(
                imageVector     = Icons.Filled.WaterDrop,
                contentDescription = null,
                tint            = Color(0xFF4FACCE),
                modifier        = Modifier
                    .size(100.dp)
                    .graphicsLayer(
                        scaleX = iconScale,
                        scaleY = iconScale,
                        alpha  = iconAlpha
                    )
            )

            Spacer(Modifier.height(28.dp))

            // Nombre de la app — sube desde abajo
            Text(
                text     = "AquaDocs",
                style    = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color    = Color.White,
                modifier = Modifier.graphicsLayer(
                    translationY = titleOffset,
                    alpha        = titleAlpha
                )
            )

            Spacer(Modifier.height(8.dp))

            // Tagline — fade simple
            Text(
                text     = "Gestión de acometidas",
                style    = MaterialTheme.typography.bodyLarge,
                color    = Color(0xFF4FACCE),
                modifier = Modifier.graphicsLayer(alpha = subAlpha)
            )
        }
    }
}