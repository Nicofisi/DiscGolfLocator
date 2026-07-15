package si.nicofi.discgolflocator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.nicofi.discgolflocator.data.GpsSample
import si.nicofi.discgolflocator.data.GeoUtils
import si.nicofi.discgolflocator.data.PointType
import si.nicofi.discgolflocator.data.PositionStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(
    courseName: String,
    holeNumber: Int,
    pointType: PointType,
    isMeasuring: Boolean,
    samples: List<GpsSample>,
    liveStats: PositionStats?,
    elapsedSeconds: Long,
    gpsAccuracy: Float,
    onStart: () -> Unit,
    onStop: (note: String) -> Unit,
    onCancel: () -> Unit
) {
    var note by remember { mutableStateOf("") }
    var showStopConfirm by remember { mutableStateOf(false) }

    val pointLabel = when (pointType) {
        PointType.TEE -> "Tee Pad"
        PointType.BASKET -> "Basket"
    }

    // Keep screen on during measurement
    val view = LocalView.current
    DisposableEffect(isMeasuring) {
        if (isMeasuring) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hole #$holeNumber - $pointLabel", fontWeight = FontWeight.Bold)
                        Text(
                            courseName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    if (!isMeasuring) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isMeasuring) {
                // === ACTIVE MEASUREMENT DISPLAY ===

                // Timer
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatTime(elapsedSeconds),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (elapsedSeconds < 60) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                if (elapsedSeconds < 60) {
                    Text(
                        text = "Minimum 1 minute recommended",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sample count
                Text(
                    text = "${samples.size} samples",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scatter plot
                if (samples.size > 1 && liveStats != null) {
                    ScatterPlot(
                        samples = samples,
                        center = liveStats,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live stats grid
                liveStats?.let { stats ->
                    LiveStatsGrid(stats, gpsAccuracy)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Note field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Phone on basket, windy") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Stop button
                Button(
                    onClick = { showStopConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Measurement", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))

            } else {
                // === PRE-MEASUREMENT STATE ===

                Spacer(modifier = Modifier.height(32.dp))

                Icon(
                    imageVector = when (pointType) {
                        PointType.TEE -> Icons.Default.Adjust
                        PointType.BASKET -> Icons.Default.Flag
                    },
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Measure $pointLabel",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Instructions
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InstructionRow("1.", "Place phone flat on the ${pointLabel.lowercase()}")
                        InstructionRow("2.", "Step back 1-2m to avoid body blocking signal")
                        InstructionRow("3.", "Wait minimum 1 minute (longer = better)")
                        InstructionRow("4.", "For best results, repeat on a different day")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Current GPS accuracy
                Text(
                    text = "Current GPS accuracy: ${String.format("%.1f", gpsAccuracy)}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (gpsAccuracy <= 5f) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Note field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. Phone on basket, cloudy") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Start button
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Measurement", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Stop confirmation
    if (showStopConfirm) {
        val warningText = if (elapsedSeconds < 60) {
            "Only ${formatTime(elapsedSeconds)} elapsed. 1+ minute is recommended for good accuracy. Stop anyway?"
        } else {
            "Save measurement with ${samples.size} samples?"
        }

        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("Stop Measurement") },
            text = { Text(warningText) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    onStop(note)
                }) {
                    Text("Save & Stop")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text("Continue Measuring")
                }
            }
        )
    }
}

@Composable
private fun InstructionRow(number: String, text: String) {
    Row {
        Text(
            text = number,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp)
        )
        Text(text = text)
    }
}

@Composable
private fun LiveStatsGrid(stats: PositionStats, currentAccuracy: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Coordinates
            Text(
                text = String.format("%.7f, %.7f", stats.latitude, stats.longitude),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Error metrics row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LiveMetric(
                    label = "CEP95",
                    value = String.format("%.2fm", stats.cep95Meters),
                    color = when {
                        stats.cep95Meters < 1.0 -> Color(0xFF1B5E20)
                        stats.cep95Meters < 3.0 -> Color(0xFFF57F17)
                        else -> Color(0xFFB71C1C)
                    }
                )
                LiveMetric(
                    label = "CEP68",
                    value = String.format("%.2fm", stats.cep68Meters),
                    color = when {
                        stats.cep68Meters < 0.5 -> Color(0xFF1B5E20)
                        stats.cep68Meters < 2.0 -> Color(0xFFF57F17)
                        else -> Color(0xFFB71C1C)
                    }
                )
                LiveMetric(
                    label = "StdDev",
                    value = String.format("%.2fm", stats.stdDevMeters),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Error metrics row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LiveMetric(
                    label = "Used",
                    value = "${stats.usedSampleCount}/${stats.sampleCount}",
                    color = MaterialTheme.colorScheme.onSurface
                )
                LiveMetric(
                    label = "Avg Acc",
                    value = String.format("%.1fm", stats.meanAccuracy),
                    color = MaterialTheme.colorScheme.onSurface
                )
                LiveMetric(
                    label = "GPS Now",
                    value = String.format("%.1fm", currentAccuracy),
                    color = when {
                        currentAccuracy <= 3f -> Color(0xFF1B5E20)
                        currentAccuracy <= 8f -> Color(0xFFF57F17)
                        else -> Color(0xFFB71C1C)
                    }
                )
            }
        }
    }
}

@Composable
private fun LiveMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun ScatterPlot(
    samples: List<GpsSample>,
    center: PositionStats,
    modifier: Modifier = Modifier
) {
    val recentColor = Color(0xFF1565C0)
    val oldColor = Color(0xFF90CAF9)
    val centerColor = Color(0xFFD32F2F)
    val cep95Color = Color(0xFF4CAF50)
    val cep68Color = Color(0xFFFFC107)

    val textMeasurer = rememberTextMeasurer()
    val cep95Label = String.format("CEP95: %.2fm", center.cep95Meters)
    val cep68Label = String.format("CEP68: %.2fm", center.cep68Meters)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Compute scale: fit all points with padding
        val mPerDegreeLat = GeoUtils.metersPerDegreeLat()
        val mPerDegreeLon = GeoUtils.metersPerDegreeLon(center.latitude)

        val maxDist = if (samples.size > 2) {
            samples.maxOf { s ->
                val dx = (s.longitude - center.longitude) * mPerDegreeLon
                val dy = (s.latitude - center.latitude) * mPerDegreeLat
                maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
            }.coerceAtLeast(1.0)
        } else 5.0

        val scale = (minOf(w, h) / 2f * 0.8f) / maxDist.toFloat()

        // Draw CEP circles
        val cep95Radius = center.cep95Meters.toFloat() * scale
        drawCircle(
            color = cep95Color.copy(alpha = 0.15f),
            radius = cep95Radius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = cep95Color.copy(alpha = 0.4f),
            radius = cep95Radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )

        // CEP95 label - positioned at top of circle
        val cep95Style = TextStyle(color = cep95Color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        val cep95TextLayout = textMeasurer.measure(cep95Label, cep95Style)
        drawText(
            textLayoutResult = cep95TextLayout,
            topLeft = Offset(
                cx - cep95TextLayout.size.width / 2f,
                cy - cep95Radius - cep95TextLayout.size.height - 2f
            )
        )

        val cep68Radius = center.cep68Meters.toFloat() * scale
        drawCircle(
            color = cep68Color.copy(alpha = 0.15f),
            radius = cep68Radius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = cep68Color.copy(alpha = 0.4f),
            radius = cep68Radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )

        // CEP68 label - positioned at bottom of circle
        val cep68Style = TextStyle(color = cep68Color.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        val cep68TextLayout = textMeasurer.measure(cep68Label, cep68Style)
        drawText(
            textLayoutResult = cep68TextLayout,
            topLeft = Offset(
                cx - cep68TextLayout.size.width / 2f,
                cy + cep68Radius + 2f
            )
        )

        // Draw sample points
        val totalSamples = samples.size
        samples.forEachIndexed { index, sample ->
            val dx = ((sample.longitude - center.longitude) * mPerDegreeLon).toFloat()
            val dy = ((sample.latitude - center.latitude) * mPerDegreeLat).toFloat()
            val px = cx + dx * scale
            val py = cy - dy * scale  // Y is inverted (north = up)

            val age = index.toFloat() / totalSamples.coerceAtLeast(1)
            val color = Color(
                red = oldColor.red + (recentColor.red - oldColor.red) * age,
                green = oldColor.green + (recentColor.green - oldColor.green) * age,
                blue = oldColor.blue + (recentColor.blue - oldColor.blue) * age,
                alpha = 0.3f + 0.5f * age
            )
            drawCircle(color = color, radius = 6f, center = Offset(px, py))
        }

        // Draw center cross
        val crossSize = 8f
        drawLine(centerColor, Offset(cx - crossSize, cy), Offset(cx + crossSize, cy), strokeWidth = 2f)
        drawLine(centerColor, Offset(cx, cy - crossSize), Offset(cx, cy + crossSize), strokeWidth = 2f)
        drawCircle(centerColor, radius = 4f, center = Offset(cx, cy))
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}
