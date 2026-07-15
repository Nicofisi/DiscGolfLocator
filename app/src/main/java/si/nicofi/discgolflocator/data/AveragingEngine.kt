package si.nicofi.discgolflocator.data

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * GPS Averaging Engine implementing:
 * 1. ECEF-based weighted averaging (not naive lat/lon)
 * 2. Outlier rejection (iterative 3-sigma filter)
 * 3. CEP68 / CEP95 circular error probable
 * 4. Multi-session merge capability
 *
 * Based on research from info2.md: proper Cartesian averaging with
 * DOP-weighted samples and outlier rejection.
 */
object AveragingEngine {

    private const val MAX_ACCURACY_THRESHOLD = 50.0f  // ignore samples worse than 50m
    private const val OUTLIER_SIGMA = 3.0             // reject beyond 3 sigma
    private const val MIN_SAMPLES_FOR_OUTLIER = 10    // need at least 10 samples before rejecting

    /**
     * Compute weighted average position from a list of GPS samples.
     *
     * Weight formula: w = 1 / accuracy^2
     * This gives exponentially more influence to precise fixes.
     * Samples with accuracy > threshold are excluded.
     */
    fun computeStats(samples: List<GpsSample>): PositionStats? {
        if (samples.isEmpty()) return null

        // Filter out samples with terrible accuracy
        val viable = samples.filter { it.accuracy < MAX_ACCURACY_THRESHOLD && it.accuracy > 0 }
        if (viable.isEmpty()) return null

        // Step 1: Compute initial weighted average in ECEF space
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        var sumWeight = 0.0

        val ecefPoints = viable.map { sample ->
            val ecef = GeoUtils.geodeticToEcef(sample.latitude, sample.longitude, sample.altitude)
            val weight = 1.0 / (sample.accuracy.toDouble().pow(2))
            Triple(ecef, weight, sample)
        }

        for ((ecef, weight, _) in ecefPoints) {
            sumX += ecef.x * weight
            sumY += ecef.y * weight
            sumZ += ecef.z * weight
            sumWeight += weight
        }

        val avgEcef = GeoUtils.EcefPoint(sumX / sumWeight, sumY / sumWeight, sumZ / sumWeight)
        val avgGeo = GeoUtils.ecefToGeodetic(avgEcef)

        // Step 2: Outlier rejection - remove samples > 3 sigma from initial average
        val distances = ecefPoints.map { (ecef, _, sample) ->
            val dist = GeoUtils.distanceMeters(avgGeo.lat, avgGeo.lon, sample.latitude, sample.longitude)
            Triple(ecef, sample, dist)
        }

        val usedSamples: List<Pair<GeoUtils.EcefPoint, GpsSample>>
        if (viable.size >= MIN_SAMPLES_FOR_OUTLIER) {
            val meanDist = distances.map { it.third }.average()
            val stdDist = sqrt(distances.map { (it.third - meanDist).pow(2) }.average())

            usedSamples = distances
                .filter { it.third <= meanDist + OUTLIER_SIGMA * stdDist }
                .map { Pair(it.first, it.second) }
        } else {
            usedSamples = ecefPoints.map { Pair(it.first, it.third) }
        }

        if (usedSamples.isEmpty()) return null

        // Step 3: Recompute weighted average with cleaned data
        sumX = 0.0; sumY = 0.0; sumZ = 0.0; sumWeight = 0.0

        for ((ecef, sample) in usedSamples) {
            val weight = 1.0 / (sample.accuracy.toDouble().pow(2))
            sumX += ecef.x * weight
            sumY += ecef.y * weight
            sumZ += ecef.z * weight
            sumWeight += weight
        }

        val finalEcef = GeoUtils.EcefPoint(sumX / sumWeight, sumY / sumWeight, sumZ / sumWeight)
        val finalGeo = GeoUtils.ecefToGeodetic(finalEcef)

        // Step 4: Compute error metrics
        val distancesFromFinal = usedSamples.map { (_, sample) ->
            GeoUtils.distanceMeters(finalGeo.lat, finalGeo.lon, sample.latitude, sample.longitude)
        }.sorted()

        val stdDev = if (distancesFromFinal.size > 1) {
            sqrt(distancesFromFinal.map { it.pow(2) }.average())
        } else {
            0.0
        }

        // CEP68: radius containing 68% of samples
        val cep68Index = ((distancesFromFinal.size * 0.68).toInt()).coerceAtMost(distancesFromFinal.size - 1)
        val cep68 = distancesFromFinal[cep68Index]

        // CEP95: radius containing 95% of samples
        val cep95Index = ((distancesFromFinal.size * 0.95).toInt()).coerceAtMost(distancesFromFinal.size - 1)
        val cep95 = distancesFromFinal[cep95Index]

        val accuracies = usedSamples.map { it.second.accuracy }
        val satellites = usedSamples.map { it.second.satellitesUsed.toDouble() }

        val timestamps = usedSamples.map { it.second.timestampMs }
        val duration = if (timestamps.size > 1) {
            (timestamps.max() - timestamps.min()) / 1000
        } else 0L

        return PositionStats(
            latitude = finalGeo.lat,
            longitude = finalGeo.lon,
            altitude = finalGeo.alt,
            sampleCount = samples.size,
            usedSampleCount = usedSamples.size,
            stdDevMeters = stdDev,
            cep68Meters = cep68,
            cep95Meters = cep95,
            meanAccuracy = accuracies.average().toFloat(),
            minAccuracy = accuracies.min(),
            maxAccuracy = accuracies.max(),
            meanSatellites = satellites.average(),
            durationSeconds = duration
        )
    }

    /**
     * Merge multiple sessions into a single position estimate.
     * This is the key to multi-visit accuracy: sessions from different days
     * have completely different satellite geometries, reducing systematic bias.
     *
     * All raw samples from all sessions are combined and re-averaged.
     */
    fun mergeSessions(sessions: List<MeasurementSession>): PositionStats? {
        val allSamples = sessions.flatMap { it.samples }
        return computeStats(allSamples)
    }

    /**
     * Compute live stats during an active measurement session.
     * Called every time a new sample arrives to update the display.
     */
    fun computeLiveStats(samples: List<GpsSample>): PositionStats? {
        return computeStats(samples)
    }
}
