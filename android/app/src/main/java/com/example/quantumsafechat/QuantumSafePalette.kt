package com.example.quantumsafechat

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class QuantumSafePalette(
    val background: Color,
    val surface: Color,
    val navy: Color,
    val deepBlue: Color,
    val cyberPeaceBlue: Color,
    val softBlueAccent: Color,
    val softBlue: Color,
    val securityBackground: Color,
    val outgoingBubble: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val success: Color,
    val danger: Color,
    val action: Color,
    val onAction: Color
)

val LightQuantumSafePalette = QuantumSafePalette(
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
    navy = Color(0xFF003C69),
    deepBlue = Color(0xFF124F7D),
    cyberPeaceBlue = Color(0xFF1B76BC),
    softBlueAccent = Color(0xFFE3EEF7),
    softBlue = Color(0xFFDCE5F2),
    securityBackground = Color(0xFFEAF3F9),
    outgoingBubble = Color(0xFFDCEAF5),
    textPrimary = Color(0xFF101828),
    textSecondary = Color(0xFF59677D),
    divider = Color(0xFFE5EAF0),
    success = Color(0xFF16824A),
    danger = Color(0xFFC73A3A),
    action = Color(0xFF1B76BC),
    onAction = Color(0xFFFFFFFF)
)

// Dark surfaces follow the CyberPeace mark (deep teal-navy), not WhatsApp green
// and not the unused purple Material defaults.
val DarkQuantumSafePalette = QuantumSafePalette(
    background = Color(0xFF07161F),
    surface = Color(0xFF102430),
    navy = Color(0xFFD6E8F4),
    deepBlue = Color(0xFF4AA3E0),
    cyberPeaceBlue = Color(0xFF4DB0E8),
    softBlueAccent = Color(0xFF163445),
    softBlue = Color(0xFF1A3A4C),
    securityBackground = Color(0xFF123348),
    outgoingBubble = Color(0xFF1A4F73),
    textPrimary = Color(0xFFE8F0F6),
    textSecondary = Color(0xFF8AA0B0),
    divider = Color(0xFF1E3A4A),
    success = Color(0xFF3DCF7A),
    danger = Color(0xFFE05C5C),
    action = Color(0xFF1B76BC),
    onAction = Color(0xFFFFFFFF)
)

val LocalQuantumSafePalette = staticCompositionLocalOf { LightQuantumSafePalette }
