package si.nicofi.discgolflocator.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import si.nicofi.discgolflocator.data.*
import si.nicofi.discgolflocator.gps.GpsService
import si.nicofi.discgolflocator.gps.SatelliteInfo

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)
    private var gpsService: GpsService? = null
    var isServiceBound by mutableStateOf(false)
        private set

    // GPS status - updated from service
    var gpsAccuracy by mutableStateOf(0f)
        private set
    var gpsSatellitesUsed by mutableStateOf(0)
        private set
    var gpsSatellitesInView by mutableStateOf(0)
        private set
    var gpsHasDualFrequency by mutableStateOf(false)
        private set
    var gpsFixAgeSeconds by mutableStateOf<Long?>(null)
        private set
    var gpsSatellites by mutableStateOf<List<SatelliteInfo>>(emptyList())
        private set

    // Current live position
    var currentLatitude by mutableStateOf(0.0)
        private set
    var currentLongitude by mutableStateOf(0.0)
        private set
    var hasGpsFix by mutableStateOf(false)
        private set

    // Course list
    var courses by mutableStateOf<List<Course>>(emptyList())
        private set

    // Current course being viewed/edited
    var currentCourse by mutableStateOf<Course?>(null)
        private set

    // Measurement state
    var isMeasuring by mutableStateOf(false)
        private set
    var measurementSamples by mutableStateOf<List<GpsSample>>(emptyList())
        private set
    var liveStats by mutableStateOf<PositionStats?>(null)
        private set
    var measurementElapsedSeconds by mutableStateOf(0L)
        private set

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as GpsService.LocalBinder
            gpsService = binder.getService().also { svc ->
                isServiceBound = true
                svc.onGpsStatusUpdate = {
                    gpsAccuracy = svc.currentAccuracy
                    gpsSatellitesUsed = svc.satellitesUsed
                    gpsSatellitesInView = svc.satellitesInView
                    gpsHasDualFrequency = svc.hasDualFrequency
                    gpsSatellites = svc.satellites
                    svc.gpsFixTime?.let { fixTime ->
                        gpsFixAgeSeconds = (System.currentTimeMillis() - fixTime) / 1000
                    }
                }
                svc.onLocationUpdate = { location ->
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                    hasGpsFix = true
                }
                svc.onSampleCollected = { sample ->
                    measurementSamples = svc.currentSamples.toList()
                    liveStats = AveragingEngine.computeLiveStats(svc.currentSamples)
                    measurementElapsedSeconds = (System.currentTimeMillis() - svc.measurementStartTime) / 1000
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            gpsService = null
            isServiceBound = false
        }
    }

    fun bindGpsService() {
        val context = getApplication<Application>()
        val intent = Intent(context, GpsService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindGpsService() {
        val context = getApplication<Application>()
        if (isServiceBound) {
            // Null out callbacks so service doesn't invoke stale references
            gpsService?.let { svc ->
                svc.onGpsStatusUpdate = null
                svc.onLocationUpdate = null
                svc.onSampleCollected = null
            }
            context.unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    fun stopGpsService() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, GpsService::class.java))
    }

    override fun onCleared() {
        val context = getApplication<Application>()
        // Clean up service binding
        if (isServiceBound) {
            gpsService?.let { svc ->
                svc.onGpsStatusUpdate = null
                svc.onLocationUpdate = null
                svc.onSampleCollected = null
            }
            try { context.unbindService(serviceConnection) } catch (_: Exception) {}
            isServiceBound = false
        }
        // Stop service if not measuring
        if (!isMeasuring) {
            context.stopService(Intent(context, GpsService::class.java))
        }
        super.onCleared()
    }

    // === Course operations ===

    fun loadCourses() {
        courses = repository.loadAllCourses()
    }

    fun createCourse(name: String, holeCount: Int): Course {
        val course = Course(name = name, holeCount = holeCount)
        repository.saveCourse(course)
        loadCourses()
        return course
    }

    fun loadCourse(courseId: String) {
        currentCourse = repository.loadCourse(courseId)
    }

    fun deleteCourse(courseId: String) {
        repository.deleteCourse(courseId)
        loadCourses()
    }

    fun updateCourseHoleCount(newCount: Int) {
        currentCourse?.let { course ->
            course.updateHoleCount(newCount)
            repository.saveCourse(course)
            currentCourse = repository.loadCourse(course.id)
        }
    }

    // === Measurement operations ===

    fun startMeasurement() {
        gpsService?.let { svc ->
            svc.startMeasurement()
            isMeasuring = true
            measurementSamples = emptyList()
            liveStats = null
            measurementElapsedSeconds = 0
        }
    }

    fun stopMeasurement(courseId: String, holeNumber: Int, pointType: PointType, note: String): MeasurementSession? {
        val svc = gpsService ?: return null
        val samples = svc.stopMeasurement()
        isMeasuring = false

        if (samples.isEmpty()) return null

        val session = MeasurementSession(
            startTime = svc.measurementStartTime,
            endTime = System.currentTimeMillis(),
            samples = samples.toMutableList(),
            note = note,
            stats = AveragingEngine.computeStats(samples)
        )

        // Save to course
        val course = repository.loadCourse(courseId) ?: return null
        val hole = course.holes.getOrNull(holeNumber - 1) ?: return null
        val point = when (pointType) {
            PointType.TEE -> hole.teePad
            PointType.BASKET -> hole.basket
        }

        point.sessions.add(session)
        point.mergedStats = AveragingEngine.mergeSessions(point.sessions)
        repository.saveCourse(course)
        currentCourse = repository.loadCourse(courseId)

        return session
    }

    fun deleteSession(courseId: String, holeNumber: Int, pointType: PointType, sessionId: String) {
        val course = repository.loadCourse(courseId) ?: return
        val hole = course.holes.getOrNull(holeNumber - 1) ?: return
        val point = when (pointType) {
            PointType.TEE -> hole.teePad
            PointType.BASKET -> hole.basket
        }

        point.sessions.removeAll { it.id == sessionId }
        point.mergedStats = if (point.sessions.isNotEmpty()) {
            AveragingEngine.mergeSessions(point.sessions)
        } else null

        repository.saveCourse(course)
        currentCourse = repository.loadCourse(courseId)
    }

    // === Export ===

    fun exportCourseFullJson(courseId: String): String? {
        val course = repository.loadCourse(courseId) ?: return null
        return repository.exportCourseJson(course)
    }

    fun exportCourseSummary(courseId: String): String? {
        val course = repository.loadCourse(courseId) ?: return null
        return repository.exportCourseSummary(course)
    }
}
