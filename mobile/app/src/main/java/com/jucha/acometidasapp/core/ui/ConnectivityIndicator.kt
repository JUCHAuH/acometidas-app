package com.jucha.acometidasapp.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jucha.acometidasapp.core.sync.ConnectivityObserver

@Composable
fun ConnectivityIndicator() {
    val context = LocalContext.current
    val connectivityObserver = remember { ConnectivityObserver(context) }
    val isOnline by connectivityObserver.isOnline.collectAsStateWithLifecycle(initialValue = null)

    if (isOnline != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Punto indicador
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isOnline!!) Color(0xFF22C55E) else Color(0xFFEF4444),
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            // Texto discreto
            Text(
                text = if (isOnline!!) "Activo" else "Sin conexión",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOnline!!) Color(0xFF22C55E) else Color(0xFFEF4444),
                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f
            )
        }
    }
}

