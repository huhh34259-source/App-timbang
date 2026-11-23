package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Editorial Ledger palette — deep forest emerald, crisp ivory paper, slate teal & amber accents
val EmeraldPrimary = Color(0xFF0F5238)
val EmeraldOnPrimary = Color(0xFFFFFFFF)
val EmeraldPrimaryContainer = Color(0xFFC8F2DC)
val EmeraldOnPrimaryContainer = Color(0xFF052114)

val SlateTealSecondary = Color(0xFF1E6B7B)
val SlateTealOnSecondary = Color(0xFFFFFFFF)
val SlateTealContainer = Color(0xFFCDEEF5)
val SlateTealOnSecondaryContainer = Color(0xFF06252C)

val AmberTertiary = Color(0xFFB86314)
val AmberOnTertiary = Color(0xFFFFFFFF)
val AmberContainer = Color(0xFFFFE1C4)
val AmberOnTertiaryContainer = Color(0xFF331800)

val PaperBackground = Color(0xFFF5F8F6)
val PaperOnBackground = Color(0xFF141C18)
val SheetSurface = Color(0xFFFFFFFF)
val SheetOnSurface = Color(0xFF141C18)
val SheetSurfaceVariant = Color(0xFFE7EEEA)
val SheetOnSurfaceVariant = Color(0xFF425049)
val GridBorderLight = Color(0xFFD5E0DA)
val HeaderBgLight = Color(0xFFEBF2EE)

val DarkEmeraldPrimary = Color(0xFF6FE0AE)
val DarkEmeraldOnPrimary = Color(0xFF003823)
val DarkEmeraldContainer = Color(0xFF0B462F)
val DarkEmeraldOnContainer = Color(0xFFC8F2DC)

val DarkSlateSecondary = Color(0xFF8BD0DF)
val DarkSlateOnSecondary = Color(0xFF003640)
val DarkSlateContainer = Color(0xFF114B57)
val DarkSlateOnContainer = Color(0xFFCDEEF5)

val DarkAmberTertiary = Color(0xFFF6B77C)
val DarkAmberOnTertiary = Color(0xFF482400)
val DarkAmberContainer = Color(0xFF673600)
val DarkAmberOnContainer = Color(0xFFFFE1C4)

val DarkBackground = Color(0xFF0E1411)
val DarkOnBackground = Color(0xFFE1E8E4)
val DarkSurface = Color(0xFF141C18)
val DarkOnSurface = Color(0xFFE1E8E4)
val DarkSurfaceVariant = Color(0xFF222E28)
val DarkOnSurfaceVariant = Color(0xFFB4C4BC)
val GridBorderDark = Color(0xFF2D3B34)
val HeaderBgDark = Color(0xFF1B2621)

val ErrorRed = Color(0xFFBA1A1A)
val ErrorContainerLight = Color(0xFFFFDAD6)
val ErrorRedDark = Color(0xFFFFB4AB)
val ErrorContainerDark = Color(0xFF93000A)

data class SwatchColor(val name: String, val hex: String?, val previewColor: Color)

val CellColorPalette = listOf(
    SwatchColor("Tanpa Warna", null, Color.Transparent),
    SwatchColor("Hijau Mint", "#FFD1FAE5", Color(0xFFD1FAE5)),
    SwatchColor("Hijau Zamrud", "#FF6EE7B7", Color(0xFF6EE7B7)),
    SwatchColor("Kuning Stabilo", "#FFFEF08A", Color(0xFFFEF08A)),
    SwatchColor("Oranye Hangat", "#FFFED7AA", Color(0xFFFED7AA)),
    SwatchColor("Merah Muda", "#FFFECDD3", Color(0xFFFECDD3)),
    SwatchColor("Koral", "#FFFCA5A5", Color(0xFFFCA5A5)),
    SwatchColor("Biru Langit", "#FFBAE6FD", Color(0xFFBAE6FD)),
    SwatchColor("Biru Kobalt", "#FFBFDBFE", Color(0xFFBFDBFE)),
    SwatchColor("Ungu Lavender", "#FFDDD6FE", Color(0xFFDDD6FE)),
    SwatchColor("Abu Lembut", "#FFE5E7EB", Color(0xFFE5E7EB)),
    SwatchColor("Abu Gelap", "#FFCBD5E1", Color(0xFFCBD5E1)),
    SwatchColor("Hijau Header", "#FF0F5238", Color(0xFF0F5238)),
    SwatchColor("Biru Navy", "#FF1E3A8A", Color(0xFF1E3A8A)),
    SwatchColor("Cokelat Emas", "#FFB45309", Color(0xFFB45309)),
    SwatchColor("Merah Marun", "#FF991B1B", Color(0xFF991B1B))
)

fun parseHexColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.trim().removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16).toInt())
            8 -> Color(cleaned.toLong(16).toInt())
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

fun contrastingTextColor(bg: Color): Color {
    val luminance = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue)
    return if (luminance < 0.52f) Color.White else Color(0xFF111815)
}
