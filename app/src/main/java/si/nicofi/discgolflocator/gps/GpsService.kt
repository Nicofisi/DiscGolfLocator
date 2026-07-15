package si.nicofi.discgolflocator.gps

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import si.nicofi.discgolflocator.MainActivity
import si.nicofi.discgolflocator.R
import si.nicofi.discgolflocator.data.GpsSample

/**
 * Info about a single satellite visible to the receiver.
 */
data class SatelliteInfo(
    val svid: Int,                  // Satellite vehicle ID
    val constellationType: Int,     // GnssStatus.CONSTELLATION_*
    val elevationDeg: Float,        // 0-90 degrees above horizon
    val azimuthDeg: Float,          // 0-360 degrees from north
    val cn0DbHz: Float,             // Signal-to-noise ratio (carrier-to-noise density)
    val usedInFix: Boolean,
    val hasCarrierFrequency: Boolean,
    val carrierFrequencyHz: Float?, // null if not available
) {
    val constellationName: String get() = when (constellationType) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
        else -> "Unknown"
    }

    val frequencyBand: String? get() {
        val freq = carrierFrequencyHz ?: return null
        return when {
            freq > 1570e6 && freq < 1580e6 -> "L1"
            freq > 1170e6 && freq < 1180e6 -> "L5"
            freq > 1220e6 && freq < 1230e6 -> "L2"
            freq > 1270e6 && freq < 1280e6 -> "L3"
            freq > 1600e6 && freq < 1610e6 -> "G1"   // GLONASS
            freq > 1240e6 && freq < 1250e6 -> "G2"   // GLONASS
            else -> String.format("%.0f", freq / 1e6)
        }
    }

    val isL5: Boolean get() {
        val freq = carrierFrequencyHz ?: return false
        return freq > 1170e6 && freq < 1180e6
    }
}

/**
 * Foreground service for GPS data collection.
 * Runs as a foreground service to prevent Android from killing it.
 * Uses WakeLock to keep CPU running even with screen off.
 */
class GpsService : Service() {

    companion object {
        const val CHANNEL_ID = "gps_measurement_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "si.nicofi.discgolflocator.STOP_GPS"
    }

    /** True after the user tapped "Stop GPS" — GPS updates are stopped but service
     *  may linger until the Activity unbinds. */
    var isStopping: Boolean = false
        private set

    private lateinit var locationManager: LocationManager
    private var locationListener: LocationListener? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // GPS status data
    var currentAccuracy: Float = 0f
        private set
    var satellitesUsed: Int = 0
        private set
    var satellitesInView: Int = 0
        private set
    var hasDualFrequency: Boolean = false
        private set
    var lastLocation: Location? = null
        private set
    var gpsFixTime: Long? = null
        private set
    var satellites: List<SatelliteInfo> = emptyList()
        private set

    // Measurement state
    var isMeasuring: Boolean = false
        private set
    var currentSamples: MutableList<GpsSample> = mutableListOf()
        private set
    var measurementStartTime: Long = 0
        private set

    // Callbacks
    var onLocationUpdate: ((Location) -> Unit)? = null
    var onGpsStatusUpdate: (() -> Unit)? = null
    var onSampleCollected: ((GpsSample) -> Unit)? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): GpsService = this@GpsService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        setupLocationListener()
        setupGnssStatusCallback()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Immediately stop GPS even if binding keeps the service alive.
            isStopping = true
            stopLocationUpdates()
            stopGnssStatusMonitoring()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            // Tell the Activity to finish itself (which will unbind + stopService in onDestroy)
            val finishIntent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_FINISH_APP
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(finishIntent)
            // stopSelf() will complete once the Activity unbinds in its onDestroy
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, createNotification("GPS active"))
        acquireWakeLock()
        startLocationUpdates()
        startGnssStatusMonitoring()
        return START_STICKY
    }

    override fun onDestroy() {
        if (!isStopping) {
            // Normal destroy path (e.g. Activity called stopService directly)
            stopLocationUpdates()
            stopGnssStatusMonitoring()
            releaseWakeLock()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPS Measurement",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active GPS measurement for disc golf course mapping"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        // Tap notification -> open app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Disc Golf Locator")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)

        // Only show Stop button when NOT measuring (safety: don't kill GPS mid-recording)
        if (!isMeasuring) {
            val stopIntent = Intent(this, GpsService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop GPS",
                stopPendingIntent
            )
        }

        return builder.build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DiscGolfLocator::GpsMeasurement"
        ).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun setupLocationListener() {
        locationListener = LocationListener { location ->
            lastLocation = location
            currentAccuracy = location.accuracy

            if (gpsFixTime == null) {
                gpsFixTime = System.currentTimeMillis()
            }

            onLocationUpdate?.invoke(location)
            onGpsStatusUpdate?.invoke()

            if (isMeasuring) {
                collectSample(location)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupGnssStatusCallback() {
        gnssStatusCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                var total = status.satelliteCount
                var dualFreq = false
                val satList = mutableListOf<SatelliteInfo>()

                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                    val hasFreq = status.hasCarrierFrequencyHz(i)
                    val freq = if (hasFreq) status.getCarrierFrequencyHz(i) else null
                    // Check for L5/E5a (carrier frequency != L1)
                    if (hasFreq && freq != null && freq > 1170e6 && freq < 1180e6) {
                        dualFreq = true
                    }

                    satList.add(SatelliteInfo(
                        svid = status.getSvid(i),
                        constellationType = status.getConstellationType(i),
                        elevationDeg = status.getElevationDegrees(i),
                        azimuthDeg = status.getAzimuthDegrees(i),
                        cn0DbHz = status.getCn0DbHz(i),
                        usedInFix = status.usedInFix(i),
                        hasCarrierFrequency = hasFreq,
                        carrierFrequencyHz = freq
                    ))
                }

                satellitesUsed = used
                satellitesInView = total
                hasDualFrequency = dualFreq
                satellites = satList
                onGpsStatusUpdate?.invoke()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        locationListener?.let { listener ->
            // Raw GPS_PROVIDER: no sensor fusion, no caching, real accuracy values
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,  // 1 Hz updates (minimum interval)
                0f,     // 0m minimum distance — we want every update
                listener,
                mainLooper
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGnssStatusMonitoring() {
        gnssStatusCallback?.let {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                locationManager.registerGnssStatusCallback(it, null)
            }
        }
    }

    private fun stopLocationUpdates() {
        locationListener?.let { locationManager.removeUpdates(it) }
    }

    private fun stopGnssStatusMonitoring() {
        gnssStatusCallback?.let {
            locationManager.unregisterGnssStatusCallback(it)
        }
    }

    private fun collectSample(location: Location) {
        val sample = GpsSample(
            timestampMs = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else 0.0,
            accuracy = location.accuracy,
            verticalAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy())
                location.verticalAccuracyMeters else null,
            satellitesUsed = satellitesUsed,
            satellitesInView = satellitesInView,
            speed = location.speed,
            bearing = location.bearing,
            provider = location.provider ?: "unknown",
            hasDualFrequency = hasDualFrequency
        )

        currentSamples.add(sample)
        onSampleCollected?.invoke(sample)

        // Update notification with progress
        val elapsed = (System.currentTimeMillis() - measurementStartTime) / 1000
        updateNotification("Measuring: ${currentSamples.size} samples, ${elapsed}s, acc: ${String.format("%.1f", location.accuracy)}m")
    }

    fun startMeasurement() {
        currentSamples.clear()
        measurementStartTime = System.currentTimeMillis()
        isMeasuring = true
        updateNotification("Measurement started...")
    }

    fun stopMeasurement(): List<GpsSample> {
        isMeasuring = false
        val result = currentSamples.toList()
        updateNotification("GPS active - ${satellitesUsed} satellites")
        return result
    }
}
