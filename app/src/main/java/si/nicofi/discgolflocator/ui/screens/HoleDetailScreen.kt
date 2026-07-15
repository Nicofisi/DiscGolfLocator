package si.nicofi.discgolflocator.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.nicofi.discgolflocator.data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoleDetailScreen(
    course: Course,
    holeNumber: Int,
    currentLat: Double,
    currentLon: Double,
    hasGpsFix: Boolean,
    gpsAccuracy: Float,
    onBack: () -> Unit,
    onMeasure: (PointType) -> Unit,
    onDeleteSession: (PointType, String) -> Unit
) {
    val hole = course.holes.getOrNull(holeNumber - 1) ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${course.name} - Hole #$holeNumber", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Distance summary
            hole.computeDistance()?.let { dist ->
                item {
                    // Compute elevation difference if both points have altitude
                    val teeAlt = hole.teePad.mergedStats?.altitude
                        ?: hole.teePad.sessions.lastOrNull()?.stats?.altitude
                    val basketAlt = hole.basket.mergedStats?.altitude
                        ?: hole.basket.sessions.lastOrNull()?.stats?.altitude
                    val elevDiff = if (teeAlt != null && basketAlt != null) basketAlt - teeAlt else null

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Straighten,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tee to Basket: ${String.format("%.1f", dist)}m (${(dist * 3.28084).toInt()}ft)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (elevDiff != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                val arrow = if (elevDiff >= 0) "+" else ""
                                Text(
                                    text = "Elevation: ${arrow}${String.format("%.1f", elevDiff)}m (${if (elevDiff >= 0) "uphill" else "downhill"})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Tee Pad section
            item {
                val teeStats = hole.teePad.mergedStats
                    ?: hole.teePad.sessions.lastOrNull()?.stats
                val distToTee = if (hasGpsFix && teeStats != null)
                    GeoUtils.distanceMeters(currentLat, currentLon, teeStats.latitude, teeStats.longitude)
                else null

                PointSection(
                    title = "Tee Pad",
                    icon = Icons.Default.Adjust,
                    point = hole.teePad,
                    liveDistanceMeters = distToTee,
                    gpsAccuracy = if (hasGpsFix) gpsAccuracy else null,
                    onMeasure = { onMeasure(PointType.TEE) },
                    onDeleteSession = { sessionId -> onDeleteSession(PointType.TEE, sessionId) }
                )
            }

            // Basket section
            item {
                val basketStats = hole.basket.mergedStats
                    ?: hole.basket.sessions.lastOrNull()?.stats
                val distToBasket = if (hasGpsFix && basketStats != null)
                    GeoUtils.distanceMeters(currentLat, currentLon, basketStats.latitude, basketStats.longitude)
                else null

                PointSection(
                    title = "Basket",
                    icon = Icons.Default.Flag,
                    point = hole.basket,
                    liveDistanceMeters = distToBasket,
                    gpsAccuracy = if (hasGpsFix) gpsAccuracy else null,
                    onMeasure = { onMeasure(PointType.BASKET) },
                    onDeleteSession = { sessionId -> onDeleteSession(PointType.BASKET, sessionId) }
                )
            }
        }
    }
}

@Composable
private fun PointSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    point: MeasurementPoint,
    liveDistanceMeters: Double?,
    gpsAccuracy: Float?,
    onMeasure: () -> Unit,
    onDeleteSession: (String) -> Unit
) {
    var sessionToDelete by remember { mutableStateOf<MeasurementSession?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(icon, contentDescription = null)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(onClick = onMeasure) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (point.sessions.isEmpty()) "Measure" else "Add Session")
                }
            }

            // Live distance to this point
            if (liveDistanceMeters != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LiveDistanceChip(distanceMeters = liveDistanceMeters, gpsAccuracy = gpsAccuracy)
            }

            // Merged stats (if multiple sessions)
            point.mergedStats?.let { stats ->
                Spacer(modifier = Modifier.height(12.dp))
                MergedStatsCard(stats, point.sessions.size)
            }

            // Session list
            if (point.sessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Sessions (${point.sessions.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))

                point.sessions.forEachIndexed { index, session ->
                    SessionItem(
                        session = session,
                        index = index + 1,
                        onDelete = { sessionToDelete = session }
                    )
                    if (index < point.sessions.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }

    // Delete session confirmation
    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Session") },
            text = {
                Text("Delete this measurement session (${session.samples.size} samples)? The merged position will be recalculated without it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(session.id)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MergedStatsCard(stats: PositionStats, sessionCount: Int) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (sessionCount > 1) {
                Text(
                    "Merged from $sessionCount sessions",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                "Final Position",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Coordinates
            Text(
                text = String.format("%.7f, %.7f", stats.latitude, stats.longitude),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = String.format("Alt: %.1fm", stats.altitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Error metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMetric("CEP95", String.format("%.2fm", stats.cep95Meters))
                StatMetric("CEP68", String.format("%.2fm", stats.cep68Meters))
                StatMetric("StdDev", String.format("%.2fm", stats.stdDevMeters))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMetric("Samples", "${stats.usedSampleCount}/${stats.sampleCount}")
                StatMetric("Avg Acc", String.format("%.1fm", stats.meanAccuracy))
                StatMetric("Avg Sat", String.format("%.0f", stats.meanSatellites))
            }
        }
    }
}

@Composable
private fun StatMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SessionItem(
    session: MeasurementSession,
    index: Int,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
    val stats = session.stats

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Session $index - ${dateFormat.format(Date(session.startTime))}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            if (stats != null) {
                Text(
                    text = "${stats.usedSampleCount} samples, ${stats.durationSeconds}s, CEP95: ${String.format("%.2f", stats.cep95Meters)}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            if (session.note.isNotBlank()) {
                Text(
                    text = session.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete session",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Live distance chip showing how far you currently are from a measured point.
 * Color-coded: green (<3m), amber (<10m), red (>10m).
 */
@Composable
private fun LiveDistanceChip(
    distanceMeters: Double,
    gpsAccuracy: Float?
) {
    val chipColor = when {
        distanceMeters < 3.0 -> Color(0xFF1B5E20)   // green - you're on it
        distanceMeters < 10.0 -> Color(0xFFF57F17)   // amber - close
        else -> Color(0xFFB71C1C)                     // red - far
    }

    Surface(
        color = chipColor.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.NearMe,
                contentDescription = null,
                tint = chipColor,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "You are ${String.format("%.1f", distanceMeters)}m away",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = chipColor
                )
                if (gpsAccuracy != null) {
                    Text(
                        text = "GPS accuracy: ${String.format("%.1f", gpsAccuracy)}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            // Big distance number on the right
            Text(
                text = String.format("%.1f", distanceMeters),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = chipColor
            )
            Text(
                text = "m",
                style = MaterialTheme.typography.bodySmall,
                color = chipColor.copy(alpha = 0.7f)
            )
        }
    }
}
