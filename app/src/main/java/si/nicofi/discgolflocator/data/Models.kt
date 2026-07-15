package si.nicofi.discgolflocator.data

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * A single raw GPS sample collected during a measurement session.
 * We store EVERYTHING here so the export file allows full re-processing.
 */
data class GpsSample(
    @SerializedName("ts") val timestampMs: Long,
    @SerializedName("lat") val latitude: Double,
    @SerializedName("lon") val longitude: Double,
    @SerializedName("alt") val altitude: Double,
    @SerializedName("acc") val accuracy: Float,          // horizontal accuracy in meters
    @SerializedName("vAcc") val verticalAccuracy: Float?, // vertical accuracy if available
    @SerializedName("sat") val satellitesUsed: Int,
    @SerializedName("satV") val satellitesInView: Int,
    @SerializedName("spd") val speed: Float,              // should be ~0 for stationary
    @SerializedName("brg") val bearing: Float,
    @SerializedName("prov") val provider: String,         // "gps", "fused", "network"
    @SerializedName("hasL5") val hasDualFrequency: Boolean, // L5/E5a detected
    @SerializedName("used") val usedInAverage: Boolean = true, // false if outlier-rejected
    @SerializedName("w") val weight: Double = 1.0         // computed weight for averaging
)

/**
 * Statistics computed for a set of GPS samples.
 */
data class PositionStats(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val sampleCount: Int,
    val usedSampleCount: Int,          // after outlier rejection
    val stdDevMeters: Double,          // standard deviation in meters
    val cep68Meters: Double,           // 68% of samples within this radius
    val cep95Meters: Double,           // 95% of samples within this radius
    val meanAccuracy: Float,           // mean reported accuracy
    val minAccuracy: Float,
    val maxAccuracy: Float,
    val meanSatellites: Double,
    val durationSeconds: Long
)

/**
 * A single measurement session - one visit to a point, phone placed down for N minutes.
 */
data class MeasurementSession(
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val samples: MutableList<GpsSample> = mutableListOf(),
    var note: String = "",
    var stats: PositionStats? = null
)

enum class PointType {
    @SerializedName("tee") TEE,
    @SerializedName("basket") BASKET
}

/**
 * A measurement point (tee pad or basket) for a hole.
 * Contains multiple sessions that can be merged for final position.
 */
data class MeasurementPoint(
    val type: PointType,
    val sessions: MutableList<MeasurementSession> = mutableListOf(),
    var mergedStats: PositionStats? = null  // computed from all sessions combined
)

/**
 * A single hole on a disc golf course.
 */
data class Hole(
    val number: Int,
    val teePad: MeasurementPoint = MeasurementPoint(type = PointType.TEE),
    val basket: MeasurementPoint = MeasurementPoint(type = PointType.BASKET)
) {
    /** Distance between tee and basket in meters, null if either not measured */
    fun computeDistance(): Double? {
        val tee = teePad.mergedStats ?: teePad.sessions.lastOrNull()?.stats ?: return null
        val basket = this.basket.mergedStats ?: this.basket.sessions.lastOrNull()?.stats ?: return null
        return GeoUtils.distanceMeters(tee.latitude, tee.longitude, basket.latitude, basket.longitude)
    }
}

/**
 * A disc golf course (field/pole).
 */
data class Course(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var holeCount: Int,
    val holes: MutableList<Hole> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    init {
        // Ensure holes list matches holeCount
        while (holes.size < holeCount) {
            holes.add(Hole(number = holes.size + 1))
        }
    }

    fun updateHoleCount(newCount: Int) {
        holeCount = newCount
        ensureHoles()
    }

    /**
     * Ensure holes list has at least holeCount entries.
     * Called after Gson deserialization (which bypasses init block)
     * and after updating hole count.
     */
    fun ensureHoles() {
        while (holes.size < holeCount) {
            holes.add(Hole(number = holes.size + 1))
        }
    }
}
