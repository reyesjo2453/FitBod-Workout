package com.johnreyes.fitbod

import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.johnreyes.fitbod.ui.FitbodRoot
import com.johnreyes.fitbod.ui.FitbodTheme
import com.johnreyes.fitbod.ui.rememberPosture

class FitbodApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
        // Pull changes (e.g. workouts Merlin logged) whenever the app comes to the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = repo.syncNow()
        })
    }
}

class MainActivity : ComponentActivity() {
    private val repo get() = (application as FitbodApp).repo

    private val signInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveClient.SCOPE))
            .build()
    }

    private val signIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            repo.onSignedIn(account.email)
        } catch (e: ApiException) {
            val hint = if (e.statusCode == 10) " (this build's signing key isn't registered in your Cloud project)" else ""
            Toast.makeText(this, "Google sign-in failed: ${e.statusCode}$hint", Toast.LENGTH_LONG).show()
        }
    }

    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        repo.consentIntent.value = null
        repo.syncNow()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val sizeClass = calculateWindowSizeClass(this)
            val posture = rememberPosture(this)
            val consentIntent by repo.consentIntent.collectAsState()
            LaunchedEffect(consentIntent) { consentIntent?.let { consent.launch(it) } }
            FitbodTheme {
                FitbodRoot(
                    repo = repo,
                    widthClass = sizeClass.widthSizeClass,
                    posture = posture,
                    onSignIn = { signIn.launch(GoogleSignIn.getClient(this, signInOptions).signInIntent) },
                    onSignOut = {
                        GoogleSignIn.getClient(this, signInOptions).signOut()
                        repo.signedOut()
                    },
                )
            }
        }
    }
}
