package com.jucha.acometidasapp.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary                = AzulAgua,
    onPrimary              = Color.White,
    primaryContainer       = AzulAguaContainer,
    onPrimaryContainer     = SobreAzulOscuro,

    secondary              = TealAgua,
    onSecondary            = Color.White,
    secondaryContainer     = TealContainer,
    onSecondaryContainer   = SobreTealOscuro,

    tertiary               = AzulAguaClaro,
    onTertiary             = Color.White,

    background             = FondoClaro,
    onBackground           = Color(0xFF0D1C28),

    surface                = SuperficieClaro,
    onSurface              = Color(0xFF0D1C28),
    surfaceVariant         = VarianteSup,
    onSurfaceVariant       = SobreVariante,

    outline                = AzulAguaClaro,
    outlineVariant         = VarianteSup,
)

private val DarkColors = darkColorScheme(
    primary                = AzulAguaDark,
    onPrimary              = Color(0xFF003550),
    primaryContainer       = AzulAgua,
    onPrimaryContainer     = AzulAguaContainer,

    secondary              = TealAguaDark,
    onSecondary            = Color(0xFF002B2B),
    secondaryContainer     = TealAgua,
    onSecondaryContainer   = TealContainer,

    background             = FondoOscuro,
    onBackground           = Color(0xFFDEECF5),

    surface                = SuperficieOscura,
    onSurface              = Color(0xFFDEECF5),
    surfaceVariant         = Color(0xFF1D3244),
    onSurfaceVariant       = Color(0xFFB0C8D8),

    outline                = AzulAguaClaro,
)

@Composable
fun AcometidasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}