package si.nicofi.discgolflocator.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Simple JSON-based persistence for course data.
 * Each course is saved as a separate JSON file for safety.
 * If one file corrupts, others are unaffected.
 */
class CourseRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private fun getCoursesDir(): File {
        val dir = File(context.filesDir, "courses")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getCourseFile(courseId: String): File {
        return File(getCoursesDir(), "$courseId.json")
    }

    fun saveCourse(course: Course) {
        course.updatedAt = System.currentTimeMillis()
        val json = gson.toJson(course)
        getCourseFile(course.id).writeText(json)
    }

    fun loadCourse(courseId: String): Course? {
        val file = getCourseFile(courseId)
        if (!file.exists()) return null
        return try {
            val course = gson.fromJson(file.readText(), Course::class.java)
            course?.ensureHoles()
            course
        } catch (e: Exception) {
            null
        }
    }

    fun loadAllCourses(): List<Course> {
        val dir = getCoursesDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val course = gson.fromJson(file.readText(), Course::class.java)
                    course?.ensureHoles()
                    course
                } catch (e: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun deleteCourse(courseId: String) {
        getCourseFile(courseId).delete()
    }

    /**
     * Generate a full export JSON containing ALL raw data.
     * This is the archival format - every single GPS sample is included.
     */
    fun exportCourseJson(course: Course): String {
        val exportData = ExportData(
            exportVersion = 1,
            appVersion = "1.0",
            exportTimestamp = System.currentTimeMillis(),
            course = course
        )
        return gson.toJson(exportData)
    }

    /**
     * Export a simplified summary (just coordinates, no raw samples).
     */
    fun exportCourseSummary(course: Course): String {
        val holes = course.holes.take(course.holeCount).map { hole ->
            val teeStats = hole.teePad.mergedStats
                ?: hole.teePad.sessions.lastOrNull()?.stats
            val basketStats = hole.basket.mergedStats
                ?: hole.basket.sessions.lastOrNull()?.stats

            HoleSummary(
                number = hole.number,
                teeLat = teeStats?.latitude,
                teeLon = teeStats?.longitude,
                teeAlt = teeStats?.altitude,
                teeCep95 = teeStats?.cep95Meters,
                teeSessions = hole.teePad.sessions.size,
                teeTotalSamples = hole.teePad.sessions.sumOf { it.samples.size },
                basketLat = basketStats?.latitude,
                basketLon = basketStats?.longitude,
                basketAlt = basketStats?.altitude,
                basketCep95 = basketStats?.cep95Meters,
                basketSessions = hole.basket.sessions.size,
                basketTotalSamples = hole.basket.sessions.sumOf { it.samples.size },
                distanceMeters = hole.computeDistance(),
                elevationDiffMeters = if (teeStats?.altitude != null && basketStats?.altitude != null)
                    basketStats.altitude - teeStats.altitude else null
            )
        }

        val summary = CourseSummary(
            name = course.name,
            holeCount = course.holeCount,
            exportTimestamp = System.currentTimeMillis(),
            holes = holes
        )
        return gson.toJson(summary)
    }
}

data class ExportData(
    val exportVersion: Int,
    val appVersion: String,
    val exportTimestamp: Long,
    val course: Course
)

data class CourseSummary(
    val name: String,
    val holeCount: Int,
    val exportTimestamp: Long,
    val holes: List<HoleSummary>
)

data class HoleSummary(
    val number: Int,
    val teeLat: Double?,
    val teeLon: Double?,
    val teeAlt: Double?,
    val teeCep95: Double?,
    val teeSessions: Int,
    val teeTotalSamples: Int,
    val basketLat: Double?,
    val basketLon: Double?,
    val basketAlt: Double?,
    val basketCep95: Double?,
    val basketSessions: Int,
    val basketTotalSamples: Int,
    val distanceMeters: Double?,
    val elevationDiffMeters: Double?
)
