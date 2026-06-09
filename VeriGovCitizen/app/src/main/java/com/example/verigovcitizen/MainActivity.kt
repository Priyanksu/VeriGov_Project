@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.verigovcitizen

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.verigovcitizen.ui.theme.VeriGovCitizenTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import android.location.Location
import com.google.android.gms.location.LocationServices
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.content.ContextCompat
import androidx.camera.core.ImageCapture
import androidx.compose.ui.platform.LocalContext
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import coil.compose.AsyncImage // This displays the image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import okhttp3.MediaType.Companion.toMediaType
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

// 1. Define the Routes for our 3 Buttons
sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Report : Screen("report", "Report", Icons.Default.AddCircle)
    object History : Screen("history", "History", Icons.Default.List)
    object Nearby : Screen("nearby", "Nearby", Icons.Default.LocationOn)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VeriGovCitizenTheme {
                val navController = rememberNavController()

                // 1. Permission Logic stays at the top of the setContent
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
                    val locationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                    // Optional: Handle what happens if denied
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.CAMERA,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }

                val context = LocalContext.current
                val sharedPrefs = remember { context.getSharedPreferences("VeriGovPrefs", android.content.Context.MODE_PRIVATE) }
                val userToken = remember { mutableStateOf(sharedPrefs.getString("jwt_token", null)) }

                if (userToken.value == null) {
                    // Show AuthScreen if no valid JWT session exists
                    AuthScreen(
                        onAuthSuccess = { token ->
                            sharedPrefs.edit().putString("jwt_token", token).apply()
                            userToken.value = token
                        }
                    )
                } else {
                    Scaffold(
                        bottomBar = { VeriGovBottomBar(navController) }
                    ) { innerPadding ->
                        val capturedFiles = remember { mutableStateListOf<File>() }
                        val description = remember { mutableStateOf("") }
                        val lastLocation = remember { mutableStateOf<Location?>(null) }

                        NavHost(
                            navController = navController,
                            startDestination = Screen.Report.route,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(Screen.Report.route) {
                                ReportScreen(
                                    capturedFiles = capturedFiles,
                                    description = description,
                                    lastLocation = lastLocation,
                                    token = userToken.value ?: "" // Pass token downstream
                                )
                            }
                            composable(Screen.History.route) {
                                HistoryScreen(token = userToken.value ?: "") // Pass token downstream
                            }
                            composable(Screen.Nearby.route) { NearbyScreen() }
                        }
                    }
                }
            }
        }
    }
}
// fetching location coordinates
private fun fetchLocation(context: android.content.Context, onLocationReceived: (Location?) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    try {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            onLocationReceived(location)
        }
    } catch (e: SecurityException) {
        onLocationReceived(null)
    }
}
@Composable
fun VeriGovBottomBar(navController: NavHostController) {
    val items = listOf(Screen.Report, Screen.History, Screen.Nearby)
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        // Avoid multiple copies of the same destination when re-selecting
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

// 3. Simple Placeholder Screens (We will build these tomorrow!)
@SuppressLint("DefaultLocale")
@Composable
fun ReportScreen(
    capturedFiles: androidx.compose.runtime.snapshots.SnapshotStateList<File>,
    description: androidx.compose.runtime.MutableState<String>,
    lastLocation: androidx.compose.runtime.MutableState<Location?>,
    token: String // Add parameter token variable mapping
) {
    // Keep ONLY these UI-specific states internally
    val isTakingPhoto = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val selectedImage = remember { mutableStateOf<File?>(null) }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        }
    }
    LaunchedEffect(Unit) {
        // This connects the camera to the screen's lifecycle so it actually turns ON
        cameraController.bindToLifecycle(context as androidx.lifecycle.LifecycleOwner)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Report an Issue",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
// --- IMPROVED: SCROLLABLE GALLERY WITH DELETE ---
        if (capturedFiles.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Evidence (${capturedFiles.size}/1", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(capturedFiles) { file ->
                        Box(modifier = Modifier.size(120.dp)) {
                            // --- THIS IS THE GALLERY CARD ---

                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        selectedImage.value = file
                                    },
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Evidence",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(bottomEnd = 8.dp),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = lastLocation.value?.let {
                                        "${String.format(java.util.Locale.ENGLISH, "%.4f", it.latitude)}, " +
                                                String.format(java.util.Locale.ENGLISH, "%.4f", it.longitude)
                                    } ?: "Locating...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                            // DELETE BUTTON (Top Right)
                            IconButton(
                                onClick = { capturedFiles.remove(file) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(30.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- MAXIMIZE VIEW (Bottom Sheet Style) ---
        selectedImage.value?.let { file ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { selectedImage.value = null }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = file,
                            contentDescription = "Maximized Evidence",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )

                        // 2. THE WATERMARK (Inside the Dialog now!)
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(bottomEnd = 12.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = lastLocation.value?.let {
                                    "${String.format(java.util.Locale.ENGLISH, "%.4f", it.latitude)}, " +
                                            String.format(java.util.Locale.ENGLISH, "%.4f", it.longitude)
                                } ?: "Locating...",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(4.dp)
                            )
                        }

                        // CLOSE BUTTON
                        Button(
                            onClick = { selectedImage.value = null },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }

        // 1. Camera Card - CLOSE THE BRACES IMMEDIATELY AFTER THE ANDROIDVIEW
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        controller = cameraController
                    }
                }
            )
        } // <--- BRACE CLOSED HERE. This keeps the camera in its own box.

        // 2. Action Buttons - Now outside the Card, so they are visible!
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ){
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp), // ADD THIS LINE
//                enabled = !isTakingPhoto.value, // Prevents multiple clicks
                enabled = !isTakingPhoto.value && capturedFiles.size < 1,
                onClick = {
                    isTakingPhoto.value = true // Start capture process
                    fetchLocation(context) { location ->
                        lastLocation.value = location // This "Locks" the location for the UI
                        val name = "VeriGov_${System.currentTimeMillis()}.jpg"
                        val file = File(context.cacheDir, name)
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                        cameraController.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                // Inside onImageSaved callback:
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    isTakingPhoto.value = false
                                    capturedFiles.add(file) // <--- ADD THIS LINE to update the gallery
                                    println("Success: Photo saved at ${file.absolutePath}")
                                }

                                override fun onError(exc: ImageCaptureException) {
                                    isTakingPhoto.value = false // Reset state
                                    println("Error: ${exc.message}")
                                }
                            }
                        )
                    }
                }
            ){
                Icon(Icons.Default.AddCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                val label = if (capturedFiles.size >= 1) "Limit Reached" else "Take Photo (${capturedFiles.size}/1)"
                Text(label)
            }
// 2. VOICE NOTE BUTTON (Full Width)
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = { /* TODO: Voice Note */ }
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Record Voice Note")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Description Field
        OutlinedTextField(
            value = description.value,
            onValueChange = { description.value = it },
            label = { Text("Describe the problem (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Submit Button

        Button(
            onClick = {
                submitReport(
                    context = context,
                    files = capturedFiles,
                    description = description, // Exact Change: Pass the mutable state wrapper itself instead of .value
                    location = lastLocation,
                    token = token,// Exact Change: Pass the mutable state wrapper itself instead of .value
                    onSuccessReset = {
                        // EXACT TRIGGER: Clears out all dynamic parameters reactively
                        capturedFiles.clear()
                        description.value = ""
                        lastLocation.value = null
                    }
                )
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            enabled = capturedFiles.isNotEmpty()
        ) {
            Text("SUBMIT REPORT", style = MaterialTheme.typography.titleMedium)
        }
    }


}


interface VeriGovApiService {
    @retrofit2.http.Multipart
    @retrofit2.http.POST("submit_grievance")
    fun uploadGrievance(
        @retrofit2.http.Header("Authorization") authHeader: String, // Dynamic Authorization injection
        @retrofit2.http.Part image: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("description") description: okhttp3.RequestBody,
        @retrofit2.http.Part("location_gps") locationGps: okhttp3.RequestBody
    ): retrofit2.Call<okhttp3.ResponseBody>

    @retrofit2.http.GET("api/history")
    fun getGrievanceHistory(
        @retrofit2.http.Header("Authorization") authHeader: String // Isolates request history context safely
    ): retrofit2.Call<List<GrievanceItem>>

    @retrofit2.http.POST("api/auth/register")
    fun registerUser(@retrofit2.http.Body body: java.util.HashMap<String, String>): retrofit2.Call<okhttp3.ResponseBody>

    @retrofit2.http.POST("api/auth/login")
    fun loginUser(@retrofit2.http.Body body: java.util.HashMap<String, String>): retrofit2.Call<okhttp3.ResponseBody>

    data class GrievanceItem(
        val id: Int,
        val description: String,
        val category: String,
        val priority_score: Int,
        val confidence_level: String,
        val location_gps: String,
        val sha256_hash: String,
        val status: String,
        val created_at: String
    )
}


object RetrofitClient {
    // Emulator: http://10.0.2.2:5000/ | Physical Phone: http://<your-computer-ip>:5000/
    private const val BASE_URL = "http://192.168.1.12:5000/"
    val instance: VeriGovApiService by lazy {
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
        retrofit.create(VeriGovApiService::class.java)
    }
}

fun submitReport(
    context: android.content.Context,
    files: androidx.compose.runtime.snapshots.SnapshotStateList<File>, // Changed to handle reactive list state directly
    description: androidx.compose.runtime.MutableState<String>,      // Changed to pass-by-reference text state wrapper
    location: androidx.compose.runtime.MutableState<Location?>,
    token: String,
    onSuccessReset: () -> Unit                                        // Added success execution handler callback
) {
    if (files.isEmpty()) {
        Toast.makeText(context, "Please add at least one photo", Toast.LENGTH_SHORT).show()
        return
    }

    val primaryFile = files[0]
    if (!primaryFile.exists()) {
        Toast.makeText(context, "File reading error. Recapture image.", Toast.LENGTH_SHORT).show()
        return
    }

    // Exact Change: Unpack values from the state wrapper references safely
    val lat = location.value?.latitude ?: 0.0
    val lon = location.value?.longitude ?: 0.0
    val gpsString = "$lat, $lon"

    Toast.makeText(context, "📡 Streaming to VeriGov Dual AI...", Toast.LENGTH_SHORT).show()

    val requestFile = okhttp3.RequestBody.create("image/jpeg".toMediaType(), primaryFile)
    val imagePart = okhttp3.MultipartBody.Part.createFormData("image", primaryFile.name, requestFile)

    // Exact Change: Extract values dynamically via .value from state targets
    val descPart = okhttp3.RequestBody.create("text/plain".toMediaType(), description.value)
    val gpsPart = okhttp3.RequestBody.create("text/plain".toMediaType(), gpsString)

    RetrofitClient.instance.uploadGrievance("Bearer $token",imagePart, descPart, gpsPart)
        .enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
            override fun onResponse(
                call: retrofit2.Call<okhttp3.ResponseBody>,
                response: retrofit2.Response<okhttp3.ResponseBody>
            ) {
                if (response.isSuccessful) {
                    val rawJson = response.body()?.string() ?: ""
                    println(">>> VERIGOV LOG NETWORK SUCCESS: $rawJson")

                    // --- EXACT CHANGE: TRIGGER RESET ---
                    onSuccessReset() // Fires layout state flush seamlessly on UI Thread

                    Toast.makeText(context, "✅ Ledger Signed & Verified by AI!", Toast.LENGTH_LONG).show()
                } else {
                    val errorPayload = response.errorBody()?.string() ?: "Validation Fault"
                    println(">>> VERIGOV LOG SERVER REJECTION: $errorPayload")

                    if (errorPayload.contains("Rejected")) {
                        Toast.makeText(context, "❌ AI Gatekeeper: Infrastructure is Clean!", Toast.LENGTH_LONG).show()
                    } else if (errorPayload.contains("Warning") || errorPayload.contains("Mismatch")) {
                        Toast.makeText(context, "⚠️ Text Mismatch: Description conflicts with image context!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "⚠️ Rejected: Structural validation failed.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<okhttp3.ResponseBody>, t: Throwable) {
                println(">>> VERIGOV LOG CONNECTION FAILED: ${t.message}")
                Toast.makeText(context, "🚨 Connection Error: Server unreachable.", Toast.LENGTH_SHORT).show()
            }
        })
}
@Composable
fun HistoryScreen(token: String) { // EXACT FIX: Accepts the token parameter mapping
    val context = LocalContext.current
    val grievances = remember { mutableStateListOf<VeriGovApiService.GrievanceItem>() }
    val isLoading = remember { mutableStateOf(true) }

    // This automatically triggers the dynamic fetch when the user taps into the History tab
    LaunchedEffect(Unit) {
        RetrofitClient.instance.getGrievanceHistory("Bearer $token")
            .enqueue(object : retrofit2.Callback<List<VeriGovApiService.GrievanceItem>> {
                override fun onResponse(
                    call: retrofit2.Call<List<VeriGovApiService.GrievanceItem>>,
                    response: retrofit2.Response<List<VeriGovApiService.GrievanceItem>>
                ) {
                    isLoading.value = false
                    if (response.isSuccessful) {
                        response.body()?.let {
                            grievances.clear()
                            grievances.addAll(it) // Seamlessly forces UI refresh layout state
                        }
                    } else {
                        Toast.makeText(context, "Failed to load dynamic audit logs", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: retrofit2.Call<List<VeriGovApiService.GrievanceItem>>, t: Throwable) {
                    isLoading.value = false
                    Toast.makeText(context, "Server unreachable. History offline.", Toast.LENGTH_SHORT).show()
                }
            })
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isLoading.value) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (grievances.isEmpty()) {
            Text(
                text = "No recorded grievances found in your account ledger.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "My Grievance Audit Logs",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(grievances) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.category.uppercase().replace("_", " "),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    val badgeColor = when (item.status) {
                                        "Pending Review" -> Color(0xFFE65100)
                                        "Pending" -> Color(0xFF0D47A1)
                                        else -> Color(0xFF1B5E20)
                                    }
                                    Surface(color = badgeColor, shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = item.status,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = item.description, style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Priority Score: ${item.priority_score} | SHA-256: ${item.sha256_hash.take(8)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun NearbyScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Nearby: Community problems and Upvotes.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(onAuthSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val isLogin = remember { mutableStateOf(true) }

    val name = remember { mutableStateOf("") }
    val email = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(false) }

    // EXACT CHANGE: State tracker for password visibility toggle
    val passwordVisible = remember { mutableStateOf(false) }

    val darkBackground = Color(0xFF121212)
    val techGreen = Color(0xFF00E676)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "VeriGov",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = techGreen
            )

            Text(
                text = if (isLogin.value) "Citizen Portal Secure Login" else "Register Immutable Citizen ID",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!isLogin.value) {
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("Full Name", color = Color.White.copy(alpha = 0.6f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = techGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = techGreen,
                        cursorColor = techGreen,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            OutlinedTextField(
                value = email.value,
                onValueChange = { email.value = it },
                label = { Text("Email Address", color = Color.White.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = techGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = techGreen,
                    cursorColor = techGreen,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // --- EXACT CHANGE: PASSWORD FIELD WITH INTERACTIVE EYE TOGGLE BUTTON ---
            OutlinedTextField(
                value = password.value,
                onValueChange = { password.value = it },
                label = { Text("Secure Password", color = Color.White.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible.value) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible.value) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    val description = if (passwordVisible.value) "Hide password" else "Show password"

                    IconButton(onClick = { passwordVisible.value = !passwordVisible.value }) {
                        Icon(imageVector = image, contentDescription = description, tint = techGreen)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = techGreen,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = techGreen,
                    cursorColor = techGreen,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (email.value.isBlank() || password.value.isBlank()) {
                        Toast.makeText(context, "Fields cannot be blank", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading.value = true
                    val payload = java.util.HashMap<String, String>().apply {
                        put("email", email.value)
                        put("password", password.value)
                        if (!isLogin.value) put("name", name.value)
                    }

                    val call = if (isLogin.value) {
                        RetrofitClient.instance.loginUser(payload)
                    } else {
                        RetrofitClient.instance.registerUser(payload)
                    }

                    call.enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
                        override fun onResponse(
                            call: retrofit2.Call<okhttp3.ResponseBody>,
                            response: retrofit2.Response<okhttp3.ResponseBody>
                        ) {
                            isLoading.value = false
                            val rawBody = response.body()?.string() ?: ""
                            if (response.isSuccessful) {
                                if (isLogin.value) {
                                    val token = org.json.JSONObject(rawBody).getString("token")
                                    onAuthSuccess(token)
                                    Toast.makeText(context, "Logged in securely!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Registration complete! Please login.", Toast.LENGTH_LONG).show()
                                    isLogin.value = true
                                    password.value = "" // Flush state on view swap
                                }
                            } else {
                                val err = response.errorBody()?.string() ?: "Auth Failure"
                                val msg = try { org.json.JSONObject(err).getString("error") } catch(e: Exception) { "Invalid Credentials" }
                                Toast.makeText(context, "❌ $msg", Toast.LENGTH_LONG).show()
                            }
                        }

                        override fun onFailure(call: retrofit2.Call<okhttp3.ResponseBody>, t: Throwable) {
                            isLoading.value = false
                            Toast.makeText(context, "Auth Service Offline", Toast.LENGTH_SHORT).show()
                        }
                    })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = techGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading.value
            ) {
                if (isLoading.value) CircularProgressIndicator(color = Color.Black)
                else Text(if (isLogin.value) "LOGIN" else "SIGN UP", style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            }

            Text(
                text = if (isLogin.value) "Create a new Citizen Account" else "Already have an account? Sign In",
                color = techGreen,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clickable { isLogin.value = !isLogin.value }
                    .padding(8.dp)
            )
        }
    }
}