package si.nicofi.discgolflocator.data

import kotlin.math.*

/**
 * Geographic utility functions implementing proper ECEF-based averaging
 * as described in the research document (info2.md).
 *
 * Key principle: Never average lat/lon directly. Convert to Cartesian ECEF,
 * average in 3D space, convert back. This avoids distortion at high latitudes.
 */
object GeoUtils {

    // WGS84 ellipsoid parameters
    private const val SEMI_MAJOR_A = 6378137.0          // equatorial radius in meters
    private const val FLATTENING = 1.0 / 298.257223563
    private val SEMI_MINOR_B = SEMI_MAJOR_A * (1.0 - FLATTENING)
    private val E_SQ = 1.0 - (SEMI_MINOR_B * SEMI_MINOR_B) / (SEMI_MAJOR_A * SEMI_MAJOR_A)

    data class EcefPoint(val x: Double, val y: Double, val z: Double)
    data class GeoPoint(val lat: Double, val lon: Double, val alt: Double)

    /**
     * Convert geodetic (lat, lon, alt) to ECEF Cartesian coordinates.
     * Uses full WGS84 ellipsoid model (not simplified sphere).
     */
    fun geodeticToEcef(latDeg: Double, lonDeg: Double, altMeters: Double): EcefPoint {
        val latRad = Math.toRadians(latDeg)
        val lonRad = Math.toRadians(lonDeg)
        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val sinLon = sin(lonRad)
        val cosLon = cos(lonRad)

        // Radius of curvature in the prime vertical
        val n = SEMI_MAJOR_A / sqrt(1.0 - E_SQ * sinLat * sinLat)

        val x = (n + altMeters) * cosLat * cosLon
        val y = (n + altMeters) * cosLat * sinLon
        val z = (n * (1.0 - E_SQ) + altMeters) * sinLat

        return EcefPoint(x, y, z)
    }

    /**
     * Convert ECEF back to geodetic coordinates.
     * Uses Bowring's iterative method for accuracy.
     */
    fun ecefToGeodetic(ecef: EcefPoint): GeoPoint {
        val x = ecef.x
        val y = ecef.y
        val z = ecef.z

        val lonRad = atan2(y, x)
        val p = sqrt(x * x + y * y)

        // Initial estimate using Bowring's method
        var latRad = atan2(z, p * (1.0 - E_SQ))
        for (i in 0 until 10) {
            val sinLat = sin(latRad)
            val n = SEMI_MAJOR_A / sqrt(1.0 - E_SQ * sinLat * sinLat)
            latRad = atan2(z + E_SQ * n * sinLat, p)
        }

        val sinLat = sin(latRad)
        val n = SEMI_MAJOR_A / sqrt(1.0 - E_SQ * sinLat * sinLat)
        val alt = p / cos(latRad) - n

        return GeoPoint(
            lat = Math.toDegrees(latRad),
            lon = Math.toDegrees(lonRad),
            alt = alt
        )
    }

    /**
     * Haversine distance between two points in meters.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return r * 2.0 * asin(sqrt(a))
    }

    /**
     * Convert horizontal distance in meters to approximate degrees at given latitude.
     * Used for computing std dev in meters from coordinate differences.
     */
    fun metersPerDegreeLat(): Double = 111_132.92

    fun metersPerDegreeLon(latDeg: Double): Double {
        return 111_132.92 * cos(Math.toRadians(latDeg))
    }
}
