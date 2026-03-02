package com.jucha.acometidasapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
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
    tertiaryContainer      = AzulAguaContainer,
    onTertiaryContainer    = SobreAzulOscuro,

    background             = FondoClaro,
    onBackground           = Color(0xFF0D1C28),

    surface                = SuperficieClaro,
    onSurface              = Color(0xFF0D1C28),
    surfaceVariant         = VarianteSup,
    onSurfaceVariant       = SobreVariante,

    outline                = AzulAguaClaro,
    outlineVariant         = VarianteSup,
)

private val DarkColorScheme = darkColorScheme(
    primary                = AzulAguaDark,
    onPrimary              = Color(0xFF003550),
    primaryContainer       = AzulAgua,
    onPrimaryContainer     = AzulAguaContainer,

    secondary              = TealAguaDark,
    onSecondary            = Color(0xFF002B2B),
    secondaryContainer     = TealAgua,
    onSecondaryContainer   = TealContainer,

    tertiary               = AzulAguaClaro,
    onTertiary             = Color(0xFF003550),

    background             = FondoOscuro,
    onBackground           = Color(0xFFDEECF5),

    surface                = SuperficieOscura,
    onSurface              = Color(0xFFDEECF5),
    surfaceVariant         = Color(0xFF1D3244),
    onSurfaceVariant       = Color(0xFFB0C8D8),

    outline                = AzulAguaClaro,
)

@Composable
fun AcometidasAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}