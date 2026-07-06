package dev.atick.compose

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- THE CRASH CATCHER ---
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val lastCrash = sharedPrefs.getString("LAST_CRASH", null)
        
        if (lastCrash != null) {
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("APP CRASHED:", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(lastCrash, color = Color.White, fontSize = 12.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { 
                            sharedPrefs.edit().remove("LAST_CRASH").apply()
                            finish() 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CLEAR CRASH & RESTART")
                    }
                }
            }
            return
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            sharedPrefs.edit().putString("LAST_CRASH", exception.stackTraceToString()).commit()
            defaultHandler?.uncaughtException(thread, exception) ?: exitProcess(1)
        }

        // -------------------------

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
    
    val pitchBlack = Color(0xFF000000)
    val pureWhite = Color(0xFFFFFFFF)
    val accentGreen = Color(0xFF00E676)
    val mutedGray = Color(0xFF424242)

    var hasLocationPermission by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    
    var isTracking by remember { mutableStateOf(false) }
    var trackingStartTime by remember { mutableStateOf(0L) }
    var lastTrackedLocation by remember { mutableStateOf<Location?>(null) }
    
    var totalDistanceMeters by remember { mutableFloatStateOf(0f) }
    var topSpeed by remember { mutableFloatStateOf(0f) }
    var startElevation by remember { mutableDoubleStateOf(0.0) }
    var endElevation by remember { mutableDoubleStateOf(0.0) }
    var hasFinishedTracking by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    DisposableEffect(hasLocationPermission) {
        var callback: LocationCallback? = null
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasLocationPermission = true
            
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateDistanceMeters(1f) 
                .build()

            callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    for (location in result.locations) {
                        currentLocation = location
                        
                        if (isTracking) {
                            lastTrackedLocation?.let { lastLoc ->
                                totalDistanceMeters += lastLoc.distanceTo(location)
                            }
                            lastTrackedLocation = location
                            
                            if (location.speed > topSpeed) {
                                topSpeed = location.speed
                            }
                        }
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        
        onDispose {
            callback?.let { fusedLocationClient.removeLocationUpdates(it) }
        }
    }

    val currentSpeedKmH = (currentLocation?.speed ?: 0f) * 3.6f
    val currentElevation = currentLocation?.altitude ?: 0.0
    val topSpeedKmH = topSpeed * 3.6f
    val averageSpeedKmH = if (isTracking && trackingStartTime > 0) {
        val elapsedHours = (System.currentTimeMillis() - trackingStartTime) / 3600000.0
        if (elapsedHours > 0) (totalDistanceMeters / 1000.0) / elapsedHours else 0.0
    } else {
        0.0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pitchBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ELEVATION", color = mutedGray, fontSize = 14.sp, letterSpacing = 2.sp)
            Text("${currentElevation.roundToInt()} m", color = pureWhite, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text("VELOCITY", color = mutedGray, fontSize = 14.sp, letterSpacing = 2.sp)
            Text(String.format("%.1f", currentSpeedKmH), color = pureWhite, fontSize = 72.sp, fontWeight = FontWeight.Black)
            Text("km/h", color = accentGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (hasFinishedTracking && !isTracking) {
            Surface(
                color = Color(0xFF121212),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("TRIP SUMMARY", color = accentGreen, fontSize = 12.sp, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    StatRow("Distance", "${String.format("%.2f", totalDistanceMeters / 1000f)} km")
                    StatRow("Avg Velocity", "${String.format("%.1f", averageSpeedKmH)} km/h")
                    StatRow("Top Velocity", "${String.format("%.1f", topSpeedKmH)} km/h")
                    StatRow("Elevation Shift", "${(endElevation - startElevation).roundToInt()} m")
                }
            }
        }

        Button(
            onClick = {
                if (isTracking) {
                    isTracking = false
                    endElevation = currentElevation
                    hasFinishedTracking = true
                } else {
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
                if (isTracking) "STOP MEASURING" else "START MEASURING",
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
