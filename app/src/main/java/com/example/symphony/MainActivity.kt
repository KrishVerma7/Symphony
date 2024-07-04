package com.example.symphony

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberImagePainter
import com.example.symphony.profile.ProfileScreen
import com.example.symphony.profile.getUserLocation
//import coil.compose.rememberImagePainter
import com.example.symphony.sign_in.GoogleAuthUIClient
import com.example.symphony.sign_in.SignInScreen
import com.example.symphony.sign_in.SignInViewModel
import com.example.symphony.ui.theme.SymphonyTheme
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()
    private val googleAuthUIClient by lazy {
        GoogleAuthUIClient(
            context = applicationContext,
            oneTapClient = Identity.getSignInClient(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SymphonyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    Scaffold(
                        topBar = {
                            TopNavigationBar(navController) {
                                lifecycleScope.launch {
                                    googleAuthUIClient.signOut()
                                    Toast.makeText(applicationContext, "Signed Out", Toast.LENGTH_LONG).show()
                                    navController.popBackStack("sign_in", inclusive = true)
                                }
                            }
                        }
                    ) { paddingValues ->
                        Column(modifier = Modifier.padding(paddingValues)) {
                            NavHost(
                                navController = navController,
                                startDestination = "sign_in",
                                modifier = Modifier.padding(top = if (navController.currentDestination?.route == "music_display") 56.dp else 0.dp)
                            ) {
                                composable("sign_in") {
                                    SignInDestination(navController, googleAuthUIClient)
                                }
                                composable("profile") {
                                    ProfileDestination(navController, googleAuthUIClient)
                                }
                                composable("music_display") {
                                    val musicViewModel: MusicViewModel = viewModel()
                                    MusicDisplayScreen(musicViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun MusicDisplayScreen(musicViewModel: MusicViewModel) {
    val music by musicViewModel.music.observeAsState()

    val isLoading = remember { mutableStateOf(true) }

    LaunchedEffect(true) {
        musicViewModel.fetchMusic()
        isLoading.value = false
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        music?.data?.forEach { data ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        data.album.let { album ->
                            Image(
                                painter = rememberImagePainter(album.cover),
                                contentDescription = data.title,
                                modifier = Modifier.size(80.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = data.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 2.dp)
                            )
                            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                                Button(
                                    onClick = { musicViewModel.initAndPlayMusic(data.preview) },
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .fillMaxWidth()
                                        .weight(1F)
                                ) {
                                    Text("Play")
                                }
                                Button(
                                    onClick = { musicViewModel.pauseMusic() },
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .fillMaxWidth()
                                        .weight(1F)
                                ) {
                                    Text("Pause")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SignInDestination(navController: NavController, googleAuthUIClient: GoogleAuthUIClient) {
    val viewModel = viewModel<SignInViewModel>()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(key1 = state.isSignInSuccessful) {
        if (state.isSignInSuccessful) {
            Log.d("SignInDestination", "SignInDestination")
            Toast.makeText(context, "Sign In Successfully", Toast.LENGTH_LONG).show()
            navController.navigate("music_display") {
                popUpTo("sign_in") { inclusive = true }
            }
            viewModel.resetState()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { result ->
            if (result.resultCode == RESULT_OK) {
                Log.d("GoogleAuthSignInDestination", "SignInDestinationGoogleAuth")
                coroutineScope.launch {
                    val signInResult = googleAuthUIClient.signInWithIntent(result.data ?: return@launch)
                    viewModel.onSignInResult(signInResult)
                }
            }
        }
    )

    SignInScreen(state = state, onSignInClick = {
        coroutineScope.launch {
            val signInIntentSender = googleAuthUIClient.signIn()
            launcher.launch(IntentSenderRequest.Builder(signInIntentSender ?: return@launch).build())
        }
    })
}

@Composable
fun ProfileDestination(navController: NavController, googleAuthUIClient: GoogleAuthUIClient) {
    val context = LocalContext.current
    val activity = context as Activity
    val userLocation = getUserLocation(activity)
    val userData = remember { mutableStateOf(googleAuthUIClient.getSignedInUser()) }
    val coroutineScope = rememberCoroutineScope()

    ProfileScreen(
        userData = googleAuthUIClient.getSignedInUser(),
        onSignOut = {
            coroutineScope.launch {
                googleAuthUIClient.signOut()
                Toast.makeText(context, "Signed Out", Toast.LENGTH_LONG).show()
                navController.navigate("sign_in") {
                    popUpTo("profile") { inclusive = true }
                }
            }
        },
        updateProfilePicture = { imageUrl ->
            val updatedUserData = userData.value?.copy(profilePictureUrl = imageUrl)
            userData.value = updatedUserData
        },
        paddingValues = PaddingValues(),
        userLocation = userLocation,
        context = activity
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavigationBar(navController: NavController, onSignOutClick: () -> Unit) {
    TopAppBar(
        title = { Text(text = "Music Player") },
        navigationIcon = {
            IconButton(onClick = { navController.navigate("profile") }) {
                Icon(Icons.Filled.AccountCircle, contentDescription = "Menu")
            }
        }
    )
}

@Preview
@Composable
fun MainActivityPreview() {
    SymphonyTheme {
        val navController = rememberNavController()
        Scaffold(
            topBar = { TopNavigationBar(navController, onSignOutClick = {}) }
        ) { paddingValues ->
            Surface {
                Modifier.padding(paddingValues)
            }
            MainActivity()
        }
    }
}
