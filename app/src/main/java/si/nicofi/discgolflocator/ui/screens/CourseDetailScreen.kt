package si.nicofi.discgolflocator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.nicofi.discgolflocator.data.Course
import si.nicofi.discgolflocator.data.Hole
import si.nicofi.discgolflocator.data.MeasurementPoint

/**
 * Get the effective CEP95 for a measurement point.
 * Uses mergedStats if available (multi-session), otherwise falls back to
 * the last session's stats. Returns null if no measurements exist.
 */
private fun MeasurementPoint.effectiveCep95(): Double? {
    return mergedStats?.cep95Meters
        ?: sessions.lastOrNull()?.stats?.cep95Meters
}

/**
 * Color-code a CEP95 value:
 * - Green: < 1.0m (excellent)
 * - Amber: 1.0–3.0m (acceptable)
 * - Red: > 3.0m (poor)
 */
private fun cep95Color(cep95: Double): Color = when {
    cep95 < 1.0 -> Color(0xFF1B5E20)
    cep95 < 3.0 -> Color(0xFFF57F17)
    else -> Color(0xFFB71C1C)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    course: Course,
    onBack: () -> Unit,
    onHoleClick: (Int) -> Unit,
    onExport: () -> Unit
) {
    val holes = course.holes.take(course.holeCount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(course.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${course.holeCount} holes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Summary stats
            CourseSummaryBar(holes)

            // Hole grid — 2 columns for more space per tile
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(holes, key = { it.number }) { hole ->
                    HoleGridItem(
                        hole = hole,
                        onClick = { onHoleClick(hole.number) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseSummaryBar(holes: List<Hole>) {
    val totalTees = holes.count { it.teePad.sessions.isNotEmpty() }
    val totalBaskets = holes.count { it.basket.sessions.isNotEmpty() }
    val fullyMeasured = holes.count {
        it.teePad.sessions.isNotEmpty() && it.basket.sessions.isNotEmpty()
    }

    // Quality counts based on worst CEP95 per hole
    val measuredHoles = holes.filter {
        it.teePad.sessions.isNotEmpty() || it.basket.sessions.isNotEmpty()
    }
    val excellent = measuredHoles.count { hole ->
        val worst = listOfNotNull(hole.teePad.effectiveCep95(), hole.basket.effectiveCep95()).maxOrNull()
        worst != null && worst < 1.0
    }
    val poor = measuredHoles.count { hole ->
        val worst = listOfNotNull(hole.teePad.effectiveCep95(), hole.basket.effectiveCep95()).maxOrNull()
        worst != null && worst >= 3.0
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryChip("Tees", "$totalTees/${holes.size}")
            SummaryChip("Baskets", "$totalBaskets/${holes.size}")
            SummaryChip("Complete", "$fullyMeasured/${holes.size}")
            if (measuredHoles.isNotEmpty()) {
                SummaryChip("Quality", "$excellent/${measuredHoles.size}",
                    color = if (poor > 0) Color(0xFFB71C1C)
                            else if (excellent == measuredHoles.size) Color(0xFF1B5E20)
                            else Color(0xFFF57F17)
                )
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String, color: Color? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color ?: Color.Unspecified
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun HoleGridItem(
    hole: Hole,
    onClick: () -> Unit
) {
    val hasTee = hole.teePad.sessions.isNotEmpty()
    val hasBasket = hole.basket.sessions.isNotEmpty()

    val teeSessions = hole.teePad.sessions.size
    val basketSessions = hole.basket.sessions.size
    val teeCep95 = hole.teePad.effectiveCep95()
    val basketCep95 = hole.basket.effectiveCep95()

    // Completeness level: based on min sessions across both points
    // 0 = nothing, 1 = partial, 2 = both have 1, 3 = both have 2+
    val completeness = when {
        teeSessions >= 2 && basketSessions >= 2 -> 3
        hasTee && hasBasket -> 2
        hasTee || hasBasket -> 1
        else -> 0
    }

    // Navy intensity background — theme-aware via primary color with scaled alpha
    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = when (completeness) {
        0 -> MaterialTheme.colorScheme.surfaceVariant
        1 -> primaryColor.copy(alpha = 0.08f)
        2 -> primaryColor.copy(alpha = 0.16f)
        else -> primaryColor.copy(alpha = 0.25f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Row 1: hole number (left) + distance (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${hole.number}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                hole.computeDistance()?.let { dist ->
                    Text(
                        text = "${String.format("%.1f", dist)}m",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Tee info (left) | Basket info (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Tee
                PointBadge(
                    label = "T",
                    sessions = teeSessions,
                    measured = hasTee,
                    cep95 = teeCep95
                )
                // Basket
                PointBadge(
                    label = "B",
                    sessions = basketSessions,
                    measured = hasBasket,
                    cep95 = basketCep95
                )
            }
        }
    }
}

/**
 * Compact badge showing point status: session count badge + CEP95 value.
 * Badge uses navy intensity scale for session count.
 * CEP95 text uses green/amber/red quality scale.
 */
@Composable
private fun PointBadge(
    label: String,
    sessions: Int,
    measured: Boolean,
    cep95: Double?
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Session count badge — navy intensity scale
        val badgeColor = when {
            !measured -> MaterialTheme.colorScheme.outlineVariant  // grey/neutral
            sessions >= 3 -> primaryColor                          // full navy
            sessions >= 2 -> primaryColor.copy(alpha = 0.75f)     // strong navy
            else -> primaryColor.copy(alpha = 0.45f)              // light navy
        }
        Surface(
            color = badgeColor,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(width = 28.dp, height = 20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (measured) "$label$sessions" else label,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // CEP95 value — green/amber/red quality scale
        if (cep95 != null) {
            Text(
                text = String.format("%.2fm", cep95),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = cep95Color(cep95)
            )
        } else if (measured) {
            Text(
                text = "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}
