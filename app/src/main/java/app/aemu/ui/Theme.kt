package app.aemu.ui

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AemuTokens {
    // Palette tokens (Dark Layered reference)
    val BgBottom = Color(0xFF121212)
    val BgTop = Color(0xFF2E2E2E)
    val SurfaceCard = Color(0xFF202020)
    val SurfaceCardTranslucent = Color(0x12FFFFFF)
    val SurfaceSheet = Color(0xFF242424)
    val SurfaceInteractive = Color(0xFF2C2C2C)
    val SurfaceIconBox = Color(0xFF2A2A2A)
    val ScrimDark = Color(0xA6000000)
    val AccentBlue = Color(0xFF0A78D2)
    val AccentBlueSoft = Color(0x260A78D2)
    val TextPrimary = Color(0xFFF2F2F2)
    val TextSecondary = Color(0xFF9A9A9A)
    val BorderSubtle = Color(0x1AFFFFFF)
    val BorderInteractive = Color(0x24FFFFFF)
    val DividerSubtle = Color(0x14FFFFFF)
    val GlowTopColor = Color(0x18FFFFFF)
    val TerminalBg = Color(0xF2121212)
    val TerminalCmd = Color(0xFF6AB7FF)

    // Shape & radius tokens
    val RadiusCard = 24.dp
    val RadiusSheet = 28.dp
    val RadiusButton = 18.dp
    val RadiusCover = 16.dp
    val RadiusSmall = 12.dp
    val ShapeCard = RoundedCornerShape(RadiusCard)
    val ShapeSheet = RoundedCornerShape(topStart = RadiusSheet, topEnd = RadiusSheet)
    val ShapeDialog = RoundedCornerShape(RadiusSheet)
    val ShapeButton = RoundedCornerShape(RadiusButton)
    val ShapeCover = RoundedCornerShape(RadiusCover)
    val ShapeSmall = RoundedCornerShape(RadiusSmall)
    val ShapePill = CircleShape

    // Border tokens
    val BorderWidth = 1.dp
    val CardBorder = BorderStroke(BorderWidth, BorderSubtle)
    val ButtonSecondaryBorder = BorderStroke(BorderWidth, BorderInteractive)

    // Spacing & dimension tokens
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 20.dp
    val SpaceXxl = 24.dp
    val SpaceBottomFab = 112.dp

    val RowHeight = 56.dp
    val RowHeightDesc = 72.dp
    val TouchMin = 48.dp
    val IconSm = 18.dp
    val IconMd = 22.dp
    val IconLg = 24.dp
    val CoverSize = 52.dp
    val LogoSize = 56.dp
    val ProgressHeight = 6.dp
    val SheetMaxListHeight = 200.dp
    val DialogMaxWidth = 360.dp
    val NavBarCompactSize = 36.dp
    val NavBarNormalSize = 48.dp
    val LedDotSize = 12.dp
    val LedStripWidth = 148.dp
    val LedStripHeight = 5.dp

    // Motion & animation tokens
    const val DurationFast = 135
    const val DurationStandard = 220
    const val DurationLong = 320
    const val PressScale = 0.97f
    val EasingStandard = FastOutSlowInEasing
    fun <T> tweenFast() = tween<T>(durationMillis = DurationFast, easing = EasingStandard)
    fun <T> tweenStandard() = tween<T>(durationMillis = DurationStandard, easing = EasingStandard)
    fun <T> springStandard() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // Brushes
    val ScreenGradient = Brush.verticalGradient(listOf(BgTop, BgBottom))
    val SheetTopGlow = Brush.radialGradient(
        colors = listOf(GlowTopColor, Color.Transparent),
        center = Offset(500f, 0f),
        radius = 640f
    )
}

private val Light = lightColorScheme(
    primary = AemuTokens.AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FA),
    onPrimaryContainer = Color(0xFF06294D),
    secondary = Color(0xFF4F5E6D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE4EC),
    onSecondaryContainer = Color(0xFF17212B),
    tertiary = Color(0xFF4F5E6D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6ECF2),
    onTertiaryContainer = Color(0xFF17212B),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF16181A),
    onSurfaceVariant = Color(0xFF5C6268),
    surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE4E7EC),
    surfaceContainerHighest = Color(0xFFDCE0E6),
    surfaceContainerLow = Color(0xFFF1F3F6),
    error = Color(0xFF4F5E6D),
)

private val Dark = darkColorScheme(
    primary = AemuTokens.AccentBlue,
    onPrimary = AemuTokens.TextPrimary,
    primaryContainer = AemuTokens.SurfaceInteractive,
    onPrimaryContainer = AemuTokens.TextPrimary,
    secondary = AemuTokens.TextSecondary,
    onSecondary = AemuTokens.BgBottom,
    secondaryContainer = AemuTokens.SurfaceInteractive,
    onSecondaryContainer = AemuTokens.TextPrimary,
    tertiary = AemuTokens.TextSecondary,
    onTertiary = AemuTokens.BgBottom,
    tertiaryContainer = AemuTokens.SurfaceCard,
    onTertiaryContainer = AemuTokens.TextPrimary,
    background = AemuTokens.BgBottom,
    surface = AemuTokens.BgBottom,
    onSurface = AemuTokens.TextPrimary,
    onSurfaceVariant = AemuTokens.TextSecondary,
    surfaceContainer = AemuTokens.SurfaceCard,
    surfaceContainerHigh = AemuTokens.SurfaceSheet,
    surfaceContainerHighest = AemuTokens.SurfaceInteractive,
    surfaceContainerLow = AemuTokens.BgBottom,
    outline = AemuTokens.BorderInteractive,
    outlineVariant = AemuTokens.DividerSubtle,
    error = AemuTokens.TextSecondary,
    errorContainer = AemuTokens.SurfaceCard,
    onErrorContainer = AemuTokens.TextPrimary,
)

private val AppShapes = Shapes(
    extraSmall = AemuTokens.ShapeSmall,
    small = AemuTokens.ShapeSmall,
    medium = AemuTokens.ShapeButton,
    large = AemuTokens.ShapeCard,
    extraLarge = AemuTokens.ShapeDialog,
)

private val BaseTypography = Typography()
private val AppType = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    headlineLarge = BaseTypography.headlineLarge.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
    headlineSmall = BaseTypography.headlineSmall.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleLarge = BaseTypography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = BaseTypography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = BaseTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = BaseTypography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = BaseTypography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun AemuTheme(forceDark: Boolean? = null, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val rev = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(ctx) {
        val sp = app.aemu.AppPrefs.prefs(ctx)
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> rev.intValue++ }
        sp.registerOnSharedPreferenceChangeListener(l)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(l) }
    }
    rev.intValue
    val dark = forceDark ?: when (app.aemu.AppPrefs.theme(ctx)) {
        app.aemu.AppPrefs.THEME_LIGHT -> false
        app.aemu.AppPrefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && app.aemu.AppPrefs.dynamicColor(ctx) -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = AppType,
        content = content,
    )
}

val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
