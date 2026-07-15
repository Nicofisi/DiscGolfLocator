package si.nicofi.discgolflocator.ui.components

import android.location.GnssStatus
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import si.nicofi.discgolflocator.gps.SatelliteInfo
import kotlin.math.cos
import kotlin.math.sin

// Constellation colors
private val GPS_COLOR = Color(0xFF4CAF50)      // Green
private val GLONASS_COLOR = Color(0xFFF44336)   // Red
private val GALILEO_COLOR = Color(0xFF2196F3)   // Blue
private val BEIDOU_COLOR = Color(0xFFFF9800)    // Orange
private val QZSS_COLOR = Color(0xFF9C27B0)      // Purple
private val SBAS_COLOR = Color(0xFF795548)       // Brown
private val IRNSS_COLOR = Color(0xFF00BCD4)      // Cyan
private val UNKNOWN_COLOR = Color(0xFF9E9E9E)    // Grey

private fun constellationColor(type: Int): Color = when (type) {
    GnssStatus.CONSTELLATION_GPS -> GPS_COLOR
    GnssStatus.CONSTELLATION_GLONASS -> GLONASS_COLOR
    GnssStatus.CONSTELLATION_GALILEO -> GALILEO_COLOR
    GnssStatus.CONSTELLATION_BEIDOU -> BEIDOU_COLOR
    GnssStatus.CONSTELLATION_QZSS -> QZSS_COLOR
    GnssStatus.CONSTELLATION_SBAS -> SBAS_COLOR
    GnssStatus.CONSTELLATION_IRNSS -> IRNSS_COLOR
    else -> UNKNOWN_COLOR
}

@Composable
fun SatelliteDialog(
    satellites: List<SatelliteInfo>,
    onDismiss: () -> Unit
) {
    val usedCount = satellites.count { it.usedInFix }
    val totalCount = satellites.size
    val l5Count = satellites.count { it.isL5 }

    // Group by constellation for the summary
    val byConstellation = satellites.groupBy { it.constellationType }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Title
                Text(
                    text = "Satellites",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$usedCount used in fix / $totalCount in view" +
                            if (l5Count > 0) " / $l5Count L5" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Sky map
                    SkyMap(
                        satellites = satellites,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Legend
                    ConstellationLegend(byConstellation)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Signal strength bars
                    Text(
                        text = "Signal Strength (C/N0)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SignalBars(satellites)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Satellite table
                    Text(
                        text = "All Satellites",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SatelliteTable(satellites)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * Polar sky map — center is zenith (90 deg up), edge is horizon (0 deg).
 * Satellites plotted by azimuth (angle from north) and elevation (distance from center).
 */
@Composable
private fun SkyMap(
    satellites: List<SatelliteInfo>,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy) * 0.9f

        // Background circle
        drawCircle(
            color = surfaceColor,
            radius = radius,
            center = Offset(cx, cy)
        )

        // Elevation rings (30, 60 degrees)
        for (elev in listOf(30f, 60f)) {
            val r = radius * (1f - elev / 90f)
            drawCircle(
                color = outlineColor.copy(alpha = 0.3f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1f)
            )
        }

        // Horizon circle
        drawCircle(
            color = outlineColor.copy(alpha = 0.6f),
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )

        // Cross hairs (N-S, E-W)
        drawLine(
            color = outlineColor.copy(alpha = 0.3f),
            start = Offset(cx, cy - radius),
            end = Offset(cx, cy + radius),
            strokeWidth = 1f
        )
        drawLine(
            color = outlineColor.copy(alpha = 0.3f),
            start = Offset(cx - radius, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = 1f
        )

        // Cardinal direction labels
        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor(
                if (onSurfaceColor.red > 0.5f) "#DDDDDD" else "#333333"
            )
            textSize = 13.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText("N", cx, cy - radius - 6.dp.toPx(), labelPaint)
            drawText("S", cx, cy + radius + 16.dp.toPx(), labelPaint)
            drawText("E", cx + radius + 12.dp.toPx(), cy + 5.dp.toPx(), labelPaint)
            drawText("W", cx - radius - 12.dp.toPx(), cy + 5.dp.toPx(), labelPaint)
        }

        // Elevation ring labels
        val smallLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor(
                if (onSurfaceColor.red > 0.5f) "#999999" else "#888888"
            )
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.LEFT
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.apply {
            val r30 = radius * (1f - 30f / 90f)
            val r60 = radius * (1f - 60f / 90f)
            drawText("30", cx + 3.dp.toPx(), cy - r30 + 12.dp.toPx(), smallLabelPaint)
            drawText("60", cx + 3.dp.toPx(), cy - r60 + 12.dp.toPx(), smallLabelPaint)
        }

        // Plot satellites
        val satLabelPaint = android.graphics.Paint().apply {
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        for (sat in satellites) {
            // Convert elevation/azimuth to x,y
            // elevation: 90 = center, 0 = edge
            val r = radius * (1f - sat.elevationDeg / 90f)
            // azimuth: 0 = north (up), clockwise
            val azRad = Math.toRadians(sat.azimuthDeg.toDouble())
            val x = cx + r * sin(azRad).toFloat()
            val y = cy - r * cos(azRad).toFloat()

            val color = constellationColor(sat.constellationType)
            val dotRadius = if (sat.usedInFix) 8.dp.toPx() else 5.dp.toPx()
            val alpha = if (sat.usedInFix) 1f else 0.4f

            // Draw satellite dot
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = dotRadius,
                center = Offset(x, y)
            )

            // Outline for used-in-fix
            if (sat.usedInFix) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f),
                    radius = dotRadius,
                    center = Offset(x, y),
                    style = Stroke(width = 1.5f)
                )
            }

            // L5 indicator — small inner ring
            if (sat.isL5) {
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }

            // SVID label
            satLabelPaint.color = if (sat.usedInFix)
                android.graphics.Color.WHITE
            else
                android.graphics.Color.argb(100, 200, 200, 200)

            drawContext.canvas.nativeCanvas.drawText(
                sat.svid.toString(),
                x,
                y - dotRadius - 2.dp.toPx(),
                satLabelPaint
            )
        }
    }
}

@Composable
private fun ConstellationLegend(byConstellation: Map<Int, List<SatelliteInfo>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Fixed order for consistency
        val order = listOf(
            GnssStatus.CONSTELLATION_GPS to "GPS",
            GnssStatus.CONSTELLATION_GLONASS to "GLONASS",
            GnssStatus.CONSTELLATION_GALILEO to "Galileo",
            GnssStatus.CONSTELLATION_BEIDOU to "BeiDou",
            GnssStatus.CONSTELLATION_QZSS to "QZSS",
            GnssStatus.CONSTELLATION_SBAS to "SBAS",
            GnssStatus.CONSTELLATION_IRNSS to "IRNSS",
        )

        for ((type, name) in order) {
            val sats = byConstellation[type] ?: continue
            val used = sats.count { it.usedInFix }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(constellationColor(type), CircleShape)
                )
                Text(
                    text = "$name $used/${sats.size}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Bar chart of signal strengths, sorted by CN0 descending, colored by constellation.
 */
@Composable
private fun SignalBars(satellites: List<SatelliteInfo>) {
    val sorted = satellites.sortedByDescending { it.cn0DbHz }
    val maxCn0 = 50f // typical max for good signal

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (sat in sorted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Label
                Text(
                    text = "${sat.constellationName.take(3)}${sat.svid}",
                    fontSize = 10.sp,
                    modifier = Modifier.width(48.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(6.dp))

                // Bar
                val fraction = (sat.cn0DbHz / maxCn0).coerceIn(0f, 1f)
                val barColor = constellationColor(sat.constellationType)
                    .let { if (sat.usedInFix) it else it.copy(alpha = 0.35f) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.extraSmall
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(barColor, MaterialTheme.shapes.extraSmall)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = String.format("%.0f", sat.cn0DbHz),
                    fontSize = 10.sp,
                    modifier = Modifier.width(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * Simple table listing all satellites with details.
 */
@Composable
private fun SatelliteTable(satellites: List<SatelliteInfo>) {
    val sorted = satellites.sortedWith(
        compareBy<SatelliteInfo> { it.constellationType }
            .thenByDescending { it.usedInFix }
            .thenByDescending { it.cn0DbHz }
    )

    // Header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text("Sys", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
        Text("ID", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
        Text("El", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
        Text("Az", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
        Text("C/N0", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
        Text("Freq", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
        Text("Fix", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
    }

    for (sat in sorted) {
        val alpha = if (sat.usedInFix) 1f else 0.5f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                sat.constellationName.take(3),
                fontSize = 10.sp,
                modifier = Modifier.width(48.dp),
                color = constellationColor(sat.constellationType).copy(alpha = alpha)
            )
            Text(
                sat.svid.toString(),
                fontSize = 10.sp,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                String.format("%.0f", sat.elevationDeg),
                fontSize = 10.sp,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                String.format("%.0f", sat.azimuthDeg),
                fontSize = 10.sp,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                String.format("%.0f", sat.cn0DbHz),
                fontSize = 10.sp,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                sat.frequencyBand ?: "-",
                fontSize = 10.sp,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                if (sat.usedInFix) "Y" else "",
                fontSize = 10.sp,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center,
                fontWeight = if (sat.usedInFix) FontWeight.Bold else FontWeight.Normal,
                color = if (sat.usedInFix) GPS_COLOR else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}
