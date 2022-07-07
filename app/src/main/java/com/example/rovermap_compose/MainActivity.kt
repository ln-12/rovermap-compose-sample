package com.example.rovermap_compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // turn off so that we can draw the map under the status and navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // provide current user location
        val locationRepository = LocationRepository(applicationContext)

        // provide document selection dialog
        val documentIntentResolver = DocumentIntentResolver(activityResultRegistry)

        // attach the document resolver to the activity lifecycle
        lifecycle.addObserver(documentIntentResolver)

        setContent {
            // set status and navigation bars transparent
            val useDarkTheme = isSystemInDarkTheme()
            val systemUiController = rememberSystemUiController()

            SideEffect {
                systemUiController.setStatusBarColor(
                    color = Color.Transparent,
                    darkIcons = !useDarkTheme
                )

                systemUiController.setNavigationBarColor(
                    color = Color.Transparent,
                    darkIcons = !useDarkTheme
                )
            }

            RoverMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MapScreen(
                        locationRepository = locationRepository,
                        documentIntentResolver = documentIntentResolver,
                    )
                }
            }
        }
    }
}


@Composable
fun RoverMapTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable() () -> Unit) {
    val colors = if (darkTheme) {
        darkColors()
    } else {
        lightColors()
    }

    MaterialTheme(
        colors = colors,
        content = content
    )
}