package si.nicofi.discgolflocator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import si.nicofi.discgolflocator.data.PointType
import si.nicofi.discgolflocator.ui.components.GpsStatusBar
import si.nicofi.discgolflocator.ui.components.SatelliteDialog
import si.nicofi.discgolflocator.ui.screens.*
import si.nicofi.discgolflocator.ui.theme.DiscGolfLocatorTheme
import si.nicofi.discgolflocator.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_FINISH_APP = "si.nicofi.discgolflocator.FINISH_APP"
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineGranted) {
            startGpsService()
        }
    }

    private var viewModelRef: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check if launched with finish action (from notification Stop GPS)
        if (intent?.action == ACTION_FINISH_APP) {
            finishAndRemoveTask()
            return
        }
        enableEdgeToEdge()
        setContent {
            DiscGolfLocatorTheme {
                val vm: MainViewModel = viewModel()
                viewModelRef = vm

                LaunchedEffect(Unit) {
                    vm.loadCourses()
                    requestPermissionsAndStart(vm)
                }

                AppContent(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_FINISH_APP) {
            finishAndRemoveTask()
        }
    }

    private fun requestPermissionsAndStart(vm: MainViewModel) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startGpsService()
        } else {
            locationPermissionRequest.launch(permissions.toTypedArray())
        }
    }

    private fun startGpsService() {
        viewModelRef?.bindGpsService()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            // Activity is truly finishing — ensure GPS is cleaned up.
            // ViewModel.onCleared() will also fire, but do explicit cleanup
            // in case the ordering is unreliable.
            val vm = viewModelRef
            if (vm != null && !vm.isMeasuring) {
                vm.unbindGpsService()
                vm.stopGpsService()
            }
        }
        super.onDestroy()
    }
}

@Composable
fun AppContent(vm: MainViewModel) {
    val navController = rememberNavController()

    // Track current measurement context
    var measureCourseId by remember { mutableStateOf("") }
    var measureHoleNumber by remember { mutableStateOf(1) }
    var measurePointType by remember { mutableStateOf(PointType.TEE) }
    var showSatelliteDialog by remember { mutableStateOf(false) }

    // Satellite dialog
    if (showSatelliteDialog) {
        SatelliteDialog(
            satellites = vm.gpsSatellites,
            onDismiss = { showSatelliteDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Always visible GPS status bar
            GpsStatusBar(
                accuracy = vm.gpsAccuracy,
                satellitesUsed = vm.gpsSatellitesUsed,
                satellitesInView = vm.gpsSatellitesInView,
                hasDualFrequency = vm.gpsHasDualFrequency,
                gpsFixAgeSeconds = vm.gpsFixAgeSeconds,
                isMeasuring = vm.isMeasuring,
                onSatelliteClick = { showSatelliteDialog = true }
            )

            // Navigation
            NavHost(
                navController = navController,
                startDestination = "courses",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("courses") {
                    vm.loadCourses()
                    CourseListScreen(
                        courses = vm.courses,
                        onCourseClick = { course ->
                            vm.loadCourse(course.id)
                            navController.navigate("course/${course.id}")
                        },
                        onCreateCourse = { name, count ->
                            val course = vm.createCourse(name, count)
                            vm.loadCourse(course.id)
                            navController.navigate("course/${course.id}")
                        },
                        onDeleteCourse = { courseId ->
                            vm.deleteCourse(courseId)
                        }
                    )
                }

                composable(
                    "course/{courseId}",
                    arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable

                    LaunchedEffect(courseId) {
                        vm.loadCourse(courseId)
                    }

                    val course = vm.currentCourse
                    if (course != null) {
                        CourseDetailScreen(
                            course = course,
                            onBack = { navController.popBackStack() },
                            onHoleClick = { holeNumber ->
                                navController.navigate("course/$courseId/hole/$holeNumber")
                            },
                            onExport = {
                                navController.navigate("course/$courseId/export")
                            }
                        )
                    }
                }

                composable(
                    "course/{courseId}/hole/{holeNumber}",
                    arguments = listOf(
                        navArgument("courseId") { type = NavType.StringType },
                        navArgument("holeNumber") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable
                    val holeNumber = backStackEntry.arguments?.getInt("holeNumber") ?: return@composable

                    LaunchedEffect(courseId) {
                        vm.loadCourse(courseId)
                    }

                    val course = vm.currentCourse
                    if (course != null) {
                        HoleDetailScreen(
                            course = course,
                            holeNumber = holeNumber,
                            currentLat = vm.currentLatitude,
                            currentLon = vm.currentLongitude,
                            hasGpsFix = vm.hasGpsFix,
                            gpsAccuracy = vm.gpsAccuracy,
                            onBack = { navController.popBackStack() },
                            onMeasure = { pointType ->
                                measureCourseId = courseId
                                measureHoleNumber = holeNumber
                                measurePointType = pointType
                                navController.navigate("measure")
                            },
                            onDeleteSession = { pointType, sessionId ->
                                vm.deleteSession(courseId, holeNumber, pointType, sessionId)
                            }
                        )
                    }
                }

                composable("measure") {
                    val course = vm.currentCourse

                    MeasurementScreen(
                        courseName = course?.name ?: "",
                        holeNumber = measureHoleNumber,
                        pointType = measurePointType,
                        isMeasuring = vm.isMeasuring,
                        samples = vm.measurementSamples,
                        liveStats = vm.liveStats,
                        elapsedSeconds = vm.measurementElapsedSeconds,
                        gpsAccuracy = vm.gpsAccuracy,
                        onStart = {
                            vm.startMeasurement()
                        },
                        onStop = { note ->
                            vm.stopMeasurement(
                                courseId = measureCourseId,
                                holeNumber = measureHoleNumber,
                                pointType = measurePointType,
                                note = note
                            )
                            navController.popBackStack()
                        },
                        onCancel = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    "course/{courseId}/export",
                    arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable

                    LaunchedEffect(courseId) {
                        vm.loadCourse(courseId)
                    }

                    val course = vm.currentCourse
                    if (course != null) {
                        ExportScreen(
                            course = course,
                            onBack = { navController.popBackStack() },
                            onExportFull = { vm.exportCourseFullJson(courseId) },
                            onExportSummary = { vm.exportCourseSummary(courseId) }
                        )
                    }
                }
            }
        }
    }
}
