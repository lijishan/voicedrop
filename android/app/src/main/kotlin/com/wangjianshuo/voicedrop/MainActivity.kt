package com.wangjianshuo.voicedrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VoiceDropApp
        setContent {
            CompositionLocalProvider(
                LocalAuthStore provides app.auth,
                LocalAPI provides app.api,
                LocalHttpClient provides app.httpClient,
                LocalLibraryStore provides app.library,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    AppRoot(navController = navController)
                }
            }
        }
    }
}
