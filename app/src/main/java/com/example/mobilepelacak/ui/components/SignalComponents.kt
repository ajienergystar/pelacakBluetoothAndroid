package com.example.mobilepelacak.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mobilepelacak.domain.model.SignalZone

/** Warna indikator per zona sinyal: hijau (dekat) → merah (jauh) → abu-abu (hilang). */
fun SignalZone.color(): Color = when (this) {
    SignalZone.VERY_STRONG -> Color(0xFF00C853)
    SignalZone.STRONG -> Color(0xFF64DD17)
    SignalZone.GOOD -> Color(0xFFFFB300)
    SignalZone.WEAK -> Color(0xFFFF6D00)
    SignalZone.VERY_WEAK -> Color(0xFFDD2C00)
    SignalZone.LOST -> Color(0xFF9E9E9E)
}

/** Ikon bar sinyal (5 bar) yang terisi sesuai zona. */
@Composable
fun SignalBars(zone: SignalZone, modifier: Modifier = Modifier.size(28.dp)) {
    val activeColor = zone.color()
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val bars = 5
        val gap = size.width * 0.06f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val heightFraction = (i + 1f) / bars
            val barHeight = size.height * heightFraction
            drawRoundRect(
                color = if (i < zone.bars) activeColor else inactiveColor,
                topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 3, barWidth / 3),
            )
        }
    }
}

/** Label kategori zona dengan titik warna. */
@Composable
fun ZoneChip(zone: SignalZone, modifier: Modifier = Modifier) {
    val color = zone.color()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape),
                color = color,
            ) {}
            Spacer(Modifier.width(6.dp))
            Text(
                text = zone.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
