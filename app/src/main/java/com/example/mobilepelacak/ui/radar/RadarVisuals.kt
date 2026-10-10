package com.example.mobilepelacak.ui.radar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobilepelacak.domain.model.RssiSample
import com.example.mobilepelacak.domain.model.SignalZone
import com.example.mobilepelacak.ui.components.color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

private const val MIN_DISTANCE_M = 0.3
private const val MAX_DISTANCE_M = 40.0

/** Batas zona (meter) yang digambar sebagai cincin radar, sesuai tabel spesifikasi. */
private val ZONE_RINGS_M = listOf(1.0, 3.0, 10.0, 20.0)

/** Memetakan jarak ke posisi radial (0..1) dengan skala logaritmik agar zona dekat tetap terbaca. */
internal fun radiusFraction(meters: Double): Float {
    val clamped = meters.coerceIn(MIN_DISTANCE_M, MAX_DISTANCE_M)
    return ((log10(clamped) - log10(MIN_DISTANCE_M)) / (log10(MAX_DISTANCE_M) - log10(MIN_DISTANCE_M))).toFloat()
}

/**
 * Radar kedekatan. Titik target ditempatkan berdasarkan estimasi jarak (bukan arah —
 * RSSI tidak memberi informasi arah), dengan warna & animasi pulse sesuai zona sinyal.
 */
@Composable
fun ProximityRadar(
    zone: SignalZone,
    distanceMeters: Double?,
    address: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2_400, easing = LinearEasing)),
        label = "sweep",
    )
    // Pulse lebih cepat saat lebih dekat → efek "hot & cold".
    val pulseDuration = when (zone) {
        SignalZone.VERY_STRONG -> 500
        SignalZone.STRONG -> 750
        SignalZone.GOOD -> 1_000
        SignalZone.WEAK -> 1_300
        else -> 1_600
    }
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(pulseDuration), RepeatMode.Restart),
        label = "pulse",
    )
    val fraction by animateFloatAsState(
        targetValue = distanceMeters?.let { radiusFraction(it) } ?: 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "fraction",
    )
    val zoneColor by animateColorAsState(zone.color(), label = "zoneColor")

    val ringColor = MaterialTheme.colorScheme.outlineVariant
    val sweepColor = MaterialTheme.colorScheme.primary
    val centerColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    // Sudut tetap per perangkat (dari hash address) agar posisi titik konsisten.
    val angleDegrees = (abs(address.hashCode()) % 360).toDouble()

    Canvas(modifier.aspectRatio(1f)) {
        val maxRadius = size.minDimension / 2f
        val center = this.center

        // Latar zona saat ini (cincin berwarna samar)
        if (distanceMeters != null) {
            drawCircle(color = zoneColor.copy(alpha = 0.08f), radius = maxRadius)
        }

        // Cincin batas zona + label jarak
        val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
        ZONE_RINGS_M.forEach { meters ->
            val r = maxRadius * radiusFraction(meters)
            drawCircle(
                color = ringColor,
                radius = r,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                ),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "${meters.toInt()} m",
                topLeft = Offset(center.x + 4.dp.toPx(), center.y - r + 2.dp.toPx()),
                style = labelStyle,
            )
        }
        drawCircle(color = ringColor, radius = maxRadius, style = Stroke(width = 1.5.dp.toPx()))

        // Sapuan radar
        rotate(degrees = sweepAngle, pivot = center) {
            drawArc(
                color = sweepColor.copy(alpha = 0.15f),
                startAngle = -35f,
                sweepAngle = 35f,
                useCenter = true,
                topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                size = Size(maxRadius * 2, maxRadius * 2),
            )
        }

        // Posisi pengguna
        drawCircle(color = centerColor, radius = 7.dp.toPx(), center = center)
        drawCircle(color = Color.White, radius = 3.dp.toPx(), center = center)

        // Target
        if (distanceMeters != null) {
            val rad = Math.toRadians(angleDegrees)
            val r = maxRadius * fraction.coerceIn(0.08f, 0.97f)
            val target = Offset(
                x = center.x + (cos(rad) * r).toFloat(),
                y = center.y + (sin(rad) * r).toFloat(),
            )
            drawLine(
                color = zoneColor.copy(alpha = 0.5f),
                start = center,
                end = target,
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            drawCircle(
                color = zoneColor.copy(alpha = (1f - pulse) * 0.45f),
                radius = 10.dp.toPx() + pulse * 26.dp.toPx(),
                center = target,
            )
            drawCircle(color = zoneColor, radius = 10.dp.toPx(), center = target)
            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = target)
        }
    }
}

/**
 * Grafik RSSI (raw = titik, terfilter = garis) beserta garis batas zona.
 */
@Composable
fun RssiChart(
    samples: List<RssiSample>,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val rawColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val lineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val minDbm = -100f
    val maxDbm = -20f

    Canvas(modifier) {
        val leftPad = 34.dp.toPx()
        val chartWidth = size.width - leftPad
        fun y(dbm: Float): Float = size.height * (1f - (dbm.coerceIn(minDbm, maxDbm) - minDbm) / (maxDbm - minDbm))

        // Garis batas zona
        listOf(-30, -50, -70, -80, -90).forEach { threshold ->
            val yy = y(threshold.toFloat())
            drawLine(
                color = SignalZone.fromRssi(threshold).color().copy(alpha = 0.5f),
                start = Offset(leftPad, yy),
                end = Offset(size.width, yy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "$threshold",
                topLeft = Offset(0f, yy - 7.dp.toPx()),
                style = TextStyle(fontSize = 9.sp, color = labelColor),
            )
        }
        drawLine(gridColor, Offset(leftPad, 0f), Offset(leftPad, size.height), strokeWidth = 1.dp.toPx())

        if (samples.size < 2) return@Canvas
        val first = samples.first().timestamp
        val span = (samples.last().timestamp - first).coerceAtLeast(1L).toFloat()
        fun x(ts: Long): Float = leftPad + chartWidth * ((ts - first) / span)

        samples.forEach { s ->
            drawCircle(rawColor, radius = 2.dp.toPx(), center = Offset(x(s.timestamp), y(s.rssi.toFloat())))
        }
        samples.zipWithNext().forEach { (a, b) ->
            drawLine(
                color = lineColor,
                start = Offset(x(a.timestamp), y(a.smoothedRssi.toFloat())),
                end = Offset(x(b.timestamp), y(b.smoothedRssi.toFloat())),
                strokeWidth = 2.5.dp.toPx(),
            )
        }
    }
}
