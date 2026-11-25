package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.ApiKeyVault
import com.example.core.DeviceActionBridge
import com.example.core.attemptAutoSignIn
import com.example.core.configureAppCheck
import com.example.data.ZenvixAiService
import com.example.data.ZenvixFirestoreRepository
import com.example.data.ZenvixLiveVoiceManager
import com.example.model.isVerifiedOwnerAccount
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ZenvixWorkspace
import com.example.ui.theme.ZenvixTheme
import com.example.viewmodel.WorkspaceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase ops.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ZenvixTheme {
                ZenvixAppContent()
            }
        }
    }
}

@Composable
fun ZenvixAppContent() {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var checkingAutoSignIn by remember { mutableStateOf(Firebase.auth.currentUser == null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                checkingAutoSignIn = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    checkingAutoSignIn = false
                },
                onUnauthenticated = {
                    checkingAutoSignIn = false
                },
                scope = this
            )
        }
    }

    val user = currentUser
    val authPhase = when {
        user == null && checkingAutoSignIn -> 0
        user == null -> 1
        else -> 2
    }

    Crossfade(
        targetState = authPhase,
        animationSpec = tween(320),
        label = "auth_workspace_crossfade"
    ) { phase ->
        when (phase) {
            0 -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            1 -> {
                AuthScreen(
                    onAuthSuccess = { currentUser = Firebase.auth.currentUser }
                )
            }
            else -> {
                val activeUser = user ?: return@Crossfade
                val uid = activeUser.uid
                key(uid) {
                    val workspaceViewModel: WorkspaceViewModel = viewModel(
                        key = uid,
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) {
                                    "APPLICATION_KEY missing from CreationExtras"
                                }
                                val databaseId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(databaseId)
                                val ownerVerified = isVerifiedOwnerAccount(
                                    email = activeUser.email,
                                    isEmailVerified = activeUser.isEmailVerified
                                )
                                WorkspaceViewModel(
                                    repository = ZenvixFirestoreRepository(db),
                                    aiService = ZenvixAiService(),
                                    apiKeyVault = ApiKeyVault(app),
                                    currentUserId = uid,
                                    currentUserName = activeUser.displayName?.takeIf { it.isNotBlank() } ?: "Zenvix User",
                                    currentUserEmail = activeUser.email.orEmpty(),
                                    currentUserPhotoUrl = activeUser.photoUrl?.toString().orEmpty(),
                                    isOwnerAdmin = ownerVerified,
                                    liveVoiceManager = ZenvixLiveVoiceManager(app),
                                    deviceActionBridge = DeviceActionBridge(app)
                                )
                            }
                        }
                    )
                    ZenvixWorkspace(
                        user = activeUser,
                        viewModel = workspaceViewModel,
                        onSignedOut = { currentUser = null }
                    )
                }
            }
        }
    }
}
