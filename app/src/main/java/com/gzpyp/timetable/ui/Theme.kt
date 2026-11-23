/*
 * 广职大课表 —— 广州职业技术大学教务系统课表客户端
 * Copyright (C) 2026 Campsisian
 *
 * 本程序是自由软件：你可以依据 GNU 通用公共许可证（GPLv3）的条款
 * 再分发和/或修改它。本程序按“原样”提供，不带任何担保。
 * 完整条款见项目根目录的 LICENSE 文件。
 */

package com.gzpyp.timetable.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 主题模式：0 跟随系统 / 1 浅色 / 2 深色。
 * 存在 AppSettings 里，改动即时生效，不需要重启。
 */
const val THEME_SYSTEM = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

/**
 * 当前是否是深色。课程色板之类的「非组件」颜色需要它，
 * 用 CompositionLocal 传，避免每个页面各自去读系统设置（用户可能手选了浅色）。
 */
val LocalAppDark = staticCompositionLocalOf { false }

/**
 * Material 3 完整配色：以绿色为种子推导出的整套 tonal palette。
 * 除 M3 必需的色彩角色外，还填了 surfaceContainer 系列 ——
 * 这是 M3 表达「层级」的主要手段（浅色下靠色调差，深色下靠亮度差），
 * 缺了它们卡片会和背景糊成一片。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2C6A4E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFAFF2CE),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4E6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D8),
    onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF3D6374),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FB),
    onTertiaryContainer = Color(0xFF001F2A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF5),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF6FBF5),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC0C9C0),
    inverseSurface = Color(0xFF2D322E),
    inverseOnSurface = Color(0xFFEEF2EC),
    inversePrimary = Color(0xFF93D6B4),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFD6DBD5),
    surfaceBright = Color(0xFFF6FBF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EF),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE4E9E3),
    surfaceContainerHighest = Color(0xFFDEE3DD)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93D6B4),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF0F5138),
    onPrimaryContainer = Color(0xFFAFF2CE),
    secondary = Color(0xFFB4CCBB),
    onSecondary = Color(0xFF20352A),
    secondaryContainer = Color(0xFF364B3F),
    onSecondaryContainer = Color(0xFFD0E8D8),
    tertiary = Color(0xFFA5CCDF),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5C),
    onTertiaryContainer = Color(0xFFC1E8FB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101410),
    onBackground = Color(0xFFDFE4DE),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFDFE4DE),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8B938B),
    outlineVariant = Color(0xFF414942),
    inverseSurface = Color(0xFFDFE4DE),
    inverseOnSurface = Color(0xFF2D322E),
    inversePrimary = Color(0xFF2C6A4E),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF101410),
    surfaceBright = Color(0xFF353A36),
    surfaceContainerLowest = Color(0xFF0B0F0C),
    surfaceContainerLow = Color(0xFF181D1A),
    surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF262B27),
    surfaceContainerHighest = Color(0xFF313632)
)

/** 中文字形下 M3 默认的字间距略松，统一收成 0 */
private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp
    )
)

/** M3 默认形状，large 略加圆润，卡片看起来更现代 */
private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

/**
 * @param themeMode 0 跟随系统 / 1 浅色 / 2 深色
 * @param dynamicColor Android 12+ 上跟随壁纸取色（Monet），关掉则用应用自带的绿色主题
 */
@Composable
fun AppTheme(
    themeMode: Int = THEME_SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> systemDark
    }

    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    CompositionLocalProvider(LocalAppDark provides dark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * 课程卡片配色：按课名取色，同一门课颜色稳定。
 * 浅色用低饱和底 + 深色字，深色反过来，保证文字对比度够看。
 */
private val CourseColorsLight = listOf(
    Color(0xFFD7EFDC) to Color(0xFF14532D),
    Color(0xFFD8E7FA) to Color(0xFF12386B),
    Color(0xFFFBE3D0) to Color(0xFF6B3410),
    Color(0xFFEBDFF7) to Color(0xFF412366),
    Color(0xFFF9DEE4) to Color(0xFF6B1B33),
    Color(0xFFDDF0F2) to Color(0xFF10464D),
    Color(0xFFF3EDD3) to Color(0xFF4F4310),
    Color(0xFFE6E6E6) to Color(0xFF2E2E2E)
)

private val CourseColorsDark = listOf(
    Color(0xFF1E3B2A) to Color(0xFFC5EBD4),
    Color(0xFF1B2F4A) to Color(0xFFC7DBF7),
    Color(0xFF432A17) to Color(0xFFF5D6BC),
    Color(0xFF35244A) to Color(0xFFDFCBF5),
    Color(0xFF44222C) to Color(0xFFF7CBD6),
    Color(0xFF17363B) to Color(0xFFC2E6EA),
    Color(0xFF3B3517) to Color(0xFFEDE3BD),
    Color(0xFF333333) to Color(0xFFE0E0E0)
)

fun courseColors(name: String, dark: Boolean): Pair<Color, Color> {
    val palette = if (dark) CourseColorsDark else CourseColorsLight
    val h = name.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
    return palette[h % palette.size]
}
