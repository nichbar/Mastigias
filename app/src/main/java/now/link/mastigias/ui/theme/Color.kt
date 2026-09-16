package now.link.mastigias.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Teal / Deep Sea primary palette
val PrimaryLight = Color(0xFF006874)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFF97F0FF)
val OnPrimaryContainerLight = Color(0xFF001F24)

val SecondaryLight = Color(0xFF4A6267)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFCDE7EC)
val OnSecondaryContainerLight = Color(0xFF051F23)

val TertiaryLight = Color(0xFF515B92)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFDEE0FF)
val OnTertiaryContainerLight = Color(0xFF0C174B)

val BackgroundLight = Color(0xFFFBFCFD)
val OnBackgroundLight = Color(0xFF191C1D)
val SurfaceLight = Color(0xFFFBFCFD)
val OnSurfaceLight = Color(0xFF191C1D)
val SurfaceVariantLight = Color(0xFFDBE4E6)
val OnSurfaceVariantLight = Color(0xFF3F484A)
val OutlineLight = Color(0xFF6F797B)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Dark palette
val PrimaryDark = Color(0xFF4DD0E1)
val OnPrimaryDark = Color(0xFF00363D)
val PrimaryContainerDark = Color(0xFF004F58)
val OnPrimaryContainerDark = Color(0xFF83F3FF)

val SecondaryDark = Color(0xFFB1CBD0)
val OnSecondaryDark = Color(0xFF1C3438)
val SecondaryContainerDark = Color(0xFF334B4F)
val OnSecondaryContainerDark = Color(0xFFCDE7EC)

val TertiaryDark = Color(0xFFBAC3FF)
val OnTertiaryDark = Color(0xFF212C60)
val TertiaryContainerDark = Color(0xFF384378)
val OnTertiaryContainerDark = Color(0xFFDEE0FF)

val BackgroundDark = Color(0xFF191C1D)
val OnBackgroundDark = Color(0xFFE1E3E3)
val SurfaceDark = Color(0xFF191C1D)
val OnSurfaceDark = Color(0xFFE1E3E3)
val SurfaceVariantDark = Color(0xFF3F484A)
val OnSurfaceVariantDark = Color(0xFFBFC8CA)
val OutlineDark = Color(0xFF899294)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)
