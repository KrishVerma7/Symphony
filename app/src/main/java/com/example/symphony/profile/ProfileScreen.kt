package com.example.symphony.profile

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.symphony.sign_in.UserData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit


/**
 * Manages all location related tasks for the app.
 */
//A callback for receiving notifications from the FusedLocationProviderClient.
lateinit var locationCallback: LocationCallback

//The main entry point for interacting with the Fused Location Provider
lateinit var locationProvider: FusedLocationProviderClient
const val REQUEST_LOCATION_PERMISSION = 1001 // You can use any unique integer value

@SuppressLint("MissingPermission")
@Composable
fun getUserLocation(activity: Activity): LatandLong {

    // The Fused Location Provider provides access to location APIs.
    locationProvider = LocationServices.getFusedLocationProviderClient(activity)

    var currentUserLocation by remember { mutableStateOf(LatandLong()) }

    DisposableEffect(key1 = locationProvider) {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {

                locationProvider.lastLocation
                    .addOnSuccessListener { location ->
                        location?.let {
                            val lat = location.latitude
                            val long = location.longitude
                            // Update data class with location data
                            currentUserLocation = LatandLong(latitude = lat, longitude = long)
                        }
                    }
                    .addOnFailureListener {
                        Log.e("Location_error", "${it.message}")
                    }

            }
        }

        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request for permission
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            // Permission has already been granted
            locationUpdate()
        }


        onDispose {
            stopLocationUpdate()
        }
    }

    return currentUserLocation

}

fun stopLocationUpdate() {
    try {
        //Removes all location updates for the given callback.
        val removeTask = locationProvider.removeLocationUpdates(locationCallback)
        removeTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("LOCATION_TAG", "Location Callback removed.")
            } else {
                Log.d("LOCATION_TAG", "Failed to remove Location Callback.")
            }
        }
    } catch (se: SecurityException) {
        Log.e("LOCATION_TAG", "Failed to remove Location Callback.. $se")
    }
}

@SuppressLint("MissingPermission")
fun locationUpdate() {
    CoroutineScope(Dispatchers.IO).launch {
        locationProvider.lastLocation.addOnSuccessListener { location ->
            location?.let {
                Log.d("LOCATION_TAG", "last known location: ${location.longitude}")
            }
        }.addOnFailureListener {
            Log.e("LOCATION_TAG", "Failed to fetch last location: ${it.message}")
        }
    }

    val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = TimeUnit.SECONDS.toMillis(10)
        fastestInterval = TimeUnit.SECONDS.toMillis(5)
        maxWaitTime = TimeUnit.MINUTES.toMillis(1)
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            locationProvider.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).await()
            Log.d("LOCATION_TAG", "Location updates started.")
        } catch (e: Exception) {
            Log.e("LOCATION_TAG", "Exception setting up location updates: ${e.message}")
            e.printStackTrace()
        }
    }
}

data class LatandLong(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

fun getReadableLocation(latitude: Double, longitude: Double, context: Context): String {
    var addressText = ""
    val geocoder = Geocoder(context, Locale.getDefault())

    try {

        val addresses = geocoder.getFromLocation(latitude, longitude, 1)

        if (addresses?.isNotEmpty() == true) {
            val address = addresses[0]
            addressText = "${address.getAddressLine(0)}, ${address.locality}"
            // Use the addressText in your app
            Log.d("geolocation", addressText)
        }

    } catch (e: IOException) {
        Log.d("geolocation", e.message.toString())

    }

    return addressText

}

fun saveUserLocation(context: Context, userLocation: LatandLong) {
    val sharedPreferences = context.getSharedPreferences("UserLocation", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putFloat("latitude", userLocation.latitude.toFloat())
    editor.putFloat("longitude", userLocation.longitude.toFloat())
    editor.apply()
}

fun uploadImageToFirebase(
    imageUri: Uri,
    userId: String,
    updateProfilePicture: (String) -> Unit,
    context: Context,
    onUploadSuccess: (Any?) -> Unit,
    onUploadFailure: () -> Unit
) {
    val storageRef = FirebaseStorage.getInstance().reference.child("profileImages/$userId.jpg")
    storageRef.putFile(imageUri).addOnSuccessListener { uploadTask ->
        storageRef.downloadUrl.addOnSuccessListener { uri ->
            val imageUrl = uri.toString()
            updateProfilePicture(imageUrl)
        }
    }.addOnFailureListener {
        Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
    }

}

suspend fun retrieveImageFromFirebase(imageUrl: Any?): String? {
    return withContext(Dispatchers.IO) {
        try {
            val storage = FirebaseStorage.getInstance()
            val imageRef = storage.getReferenceFromUrl(imageUrl.toString())
            val localFile = File.createTempFile("temp_image", "jpg")
            imageRef.getFile(localFile).await()
            localFile.readBytes()
            imageRef.downloadUrl.await().toString()

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@Composable
fun ProfileScreen(
    userData: UserData?,
    onSignOut: () -> Unit,
    updateProfilePicture: (String) -> Unit,
    paddingValues: PaddingValues,
    userLocation: LatandLong,
    context: Context
) {
    var imageUri by rememberSaveable {
        mutableStateOf(userData?.profilePictureUrl)
    }

    var imageByteArray by rememberSaveable {
        mutableStateOf<ByteArray?>(null)
    }
    LaunchedEffect(Unit) {
//        imageByteArray = retrieveImageFromFirebase(imageUri)
        imageUri = retrieveImageFromFirebase(imageUri)
        Log.d("Image", "retrieving image $imageByteArray")
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it.toString()
            uploadImageToFirebase(
                imageUri = uri,
                userId = userData?.userId ?: "",
                context = context,
                updateProfilePicture = { imageUrl ->
                    imageUri = imageUrl
                    updateProfilePicture(imageUrl)
                },
                onUploadSuccess = {
                    Toast.makeText(context, "Upload successful", Toast.LENGTH_SHORT).show()
                },
                onUploadFailure = {
                    Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (imageByteArray != null) {
            AsyncImage(

                model = imageByteArray ?: "",
                contentDescription = "Profile picture",
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            if (userData?.profilePictureUrl != null) {
                AsyncImage(

                    model = imageUri ?: "",
                    contentDescription = "Profile picture",
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            if (userData?.profilePictureUrl != null) {
//        AsyncImage(
//
//            model = imageUri ?: "",
//            contentDescription = "Profile picture",
//            modifier = Modifier
//                .size(150.dp)
//                .clip(CircleShape),
//            contentScale = ContentScale.Crop
//        )
//
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (userData?.username != null) {
            Text(
                text = userData.username,
                textAlign = TextAlign.Center,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(onClick = {
            imagePickerLauncher.launch("image/*")
        }) {
            Text(text = "Change Profile Picture")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onSignOut) {
            Text(text = "Sign Out")

        }
        val readableLocation =
            getReadableLocation(userLocation.latitude, userLocation.longitude, context)
        LaunchedEffect(userLocation) {
            saveUserLocation(context, userLocation)
        }
        Text(
            text = "Your Location: $readableLocation",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
        )
    }
}


//@Preview
//@Composable
//fun ProfileScreenPreview() {
//    val userData = UserData(username = "Your Name", profilePictureUrl = null, userId = "12345")
//    val paddingValues = PaddingValues(16.dp) // Sample padding values
//    val userLocation = LatandLong(latitude = 0.0, longitude = 0.0)
//
//    ProfileScreen(
//        userData = userData,
//        onSignOut = {},
//        updateProfilePicture = {},
//        paddingValues = paddingValues,
//        userLocation = userLocation,
//        context = Context
//    )
//}
