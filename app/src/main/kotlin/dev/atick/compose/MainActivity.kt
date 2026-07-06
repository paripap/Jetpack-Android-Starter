package dev.atick.compose

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AmoledTrackerApp()
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun AmoledTrackerApp() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // UI State Colors (Pitch Black AMOLED)
    val pitchBlack = Color(0xFF000000)
    val pureWhite = Color(0xFFFFFFFF)
    val accentGreen = Color(0xFF00E676)
    val mutedGray = Color(0xFF424242)

    // Sensor States
    var hasLocationPermission by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    
    // Tracking States
    var isTracking by remember { mutableStateOf(false) }
    var trackingStartTime by remember { mutableStateOf(0L) }
    var lastTrackedLocation by remember { mutableStateOf<Location?>(null) }
    
    // Stats
    var totalDistanceMeters by remember { mutableFloatStateOf(0f) }
    var topSpeed by remember { mutableFloatStateOf(0f) }
    var startElevation by remember { mutableDoubleStateOf(0.0) }
    var hasFinishedTracking by remember { mutableStateOf(false) }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Initialize tracking logic
    LaunchedEffect(hasLocationPermission) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true
            
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateDistanceMeters(1f) // Update every 1 meter
                .build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    for (location in result.locations) {
                        currentLocation = location
                        
                        if (isTracking) {
                            // Calculate Distance
                            lastTrackedLocation?.let { lastLoc ->
                                totalDistanceMeters += lastLoc.distanceTo(location)
                            }
                            lastTrackedLocation = location
                            
                            // Calculate Top Speed
                            if (location.speed > topSpeed) {
                                topSpeed = location.speed
                            }
                        }
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Calculations for UI
    val currentSpeedKmH = (currentLocation?.speed ?: 0f) * 3.6f
    val currentElevation = currentLocation?.altitude ?: 0.0
    val topSpeedKmH = topSpeed * 3.6f
    val averageSpeedKmH = if (isTracking && trackingStartTime > 0) {
        val elapsedHours = (System.currentTimeMillis() - trackingStartTime) / 3600000.0
        if (elapsedHours > 0) (totalDistanceMeters / 1000.0) / elapsedHours else 0.0
    } else {
        0.0
    }

    // AMOLED UI Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pitchBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Live Sensor Data
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ELEVATION", color = mutedGray, fontSize = 14.sp, letterSpacing = 2.sp)
            Text("${currentElevation.roundToInt()} m", color = pureWhite, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text("VELOCITY", color = mutedGray, fontSize = 14.sp, letterSpacing = 2.sp)
            Text(String.format("%.1f", currentSpeedKmH), color = pureWhite, fontSize = 72.sp, fontWeight = FontWeight.Black)
            Text("km/h", color = accentGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // Stats Card (Shows when stopped)
        if (hasFinishedTracking && !isTracking) {
            Surface(
                color = Color(0xFF121212), // Deep gray to separate from pitch black
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("TRIP SUMMARY", color = accentGreen, fontSize = 12.sp, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    StatRow("Distance", "${String.format("%.2f", totalDistanceMeters / 1000f)} km")
                    StatRow("Avg Velocity", "${String.format("%.1f", averageSpeedKmH)} km/h")
                    StatRow("Top Velocity", "${String.format("%.1f", topSpeedKmH)} km/h")
                    StatRow("Elevation Shift", "${(currentElevation - startElevation).roundToInt()} m")
                }
            }
        }

        // Main Action Button
        Button(
            onClick = {
                if (isTracking) {
                    // Stop Tracking
                    isTracking = false
                    hasFinishedTracking = true
                } else {
                    // Start Tracking
                    totalDistanceMeters = 0f
                    topSpeed = 0f
                    startElevation = currentElevation
                    trackingStartTime = System.currentTimeMillis()
                    lastTrackedLocation = currentLocation
                    hasFinishedTracking = false
                    isTracking = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) Color(0xFFD50000) else pureWhite
            ),
            shape = RoundedCornerShape(32.dp)
        ) {
            Text(
                if (isTracking) "STOP MEASURING",
                color = if (isTracking) pureWhite else pitchBlack,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.LightGray, fontSize = 16.sp)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
