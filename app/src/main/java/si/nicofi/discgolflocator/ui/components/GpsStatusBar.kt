package si.nicofi.discgolflocator.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Always-visible GPS status bar at the top of the app.
 * Shows accuracy, satellite count, dual-frequency status, and warm-up indicator.
 * Tapping opens the satellite detail dialog.
 */
@Composable
fun GpsStatusBar(
    accuracy: Float,
    satellitesUsed: Int,
    satellitesInView: Int,
    hasDualFrequency: Boolean,
    gpsFixAgeSeconds: Long?,
    isMeasuring: Boolean,
    onSatelliteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            accuracy <= 0f || satellitesUsed == 0 -> Color(0xFFB71C1C) // dark red - no fix
            accuracy <= 3f -> Color(0xFF1B5E20)  // dark green - excellent
            accuracy <= 8f -> Color(0xFFF57F17)  // amber - good
            else -> Color(0xFFB71C1C)            // red - poor
        },
        label = "statusColor"
    )

    val gpsIcon = when {
        satellitesUsed == 0 -> Icons.Default.GpsOff
        accuracy <= 3f -> Icons.Default.GpsFixed
        else -> Icons.Default.GpsNotFixed
    }

    val isWarmingUp = gpsFixAgeSeconds != null && gpsFixAgeSeconds < 120

    Surface(
        color = statusColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: GPS icon + accuracy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = gpsIcon,
                    contentDescription = "GPS status",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (accuracy > 0f) String.format("%.1fm", accuracy) else "No fix",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Center: Satellites (clickable)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable(onClick = onSatelliteClick)
            ) {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = "Satellites",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$satellitesUsed/$satellitesInView",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }

            // Right: Dual-freq indicator + warm-up
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hasDualFrequency) {
                    Surface(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "L5",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                if (isWarmingUp) {
                    val remaining = 120 - (gpsFixAgeSeconds ?: 0)
                    val minutes = remaining / 60
                    val seconds = remaining % 60
                    Surface(
                        color = Color(0xFFFF8F00).copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = String.format("WARM %d:%02d", minutes, seconds),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                if (isMeasuring) {
                    Surface(
                        color = Color(0xFFD32F2F),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "REC",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
