package com.example.rovermap_compose

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rovermap_compose.rovermap.RoverMap
import com.example.rovermap_compose.rovermap.RoverMapState
import com.example.rovermap_compose.rovermap.rememberRoverMapState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import de.tubaf.rovermapgfx.GeoCoords
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import kotlin.math.log
import kotlin.math.pow
import kotlin.math.roundToInt

private val initialLocation = GeoCoords(lat = 50.9257, lon = 13.3309)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    locationRepository: LocationRepository,
    documentIntentResolver: DocumentIntentResolver,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isVulkanAvailable = remember { context.packageManager.hasSystemFeature(
        PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0) }

    var showDebugMenu by remember { mutableStateOf(false) }
    var mapFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableStateOf(0.0) }

    val roverMapState = rememberRoverMapState {
        update(
            newPosition = initialLocation,
            newScale = 20.0,
            newRotation = 0.0,
            newLatRange = 50.85..50.97,
            newLonRange = 13.24..13.45,
            newScaleRange = 13.0..23.0,
            newTileBaseSize = 115.0,
            newMultisampleCount = 4U,
        )

        updateUserLocation(
            location = initialLocation
        )
    }


    val locationPermissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    LaunchedEffect(true) {
        locationPermissionsState.launchMultiplePermissionRequest()
    }

    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if(locationPermissionsState.allPermissionsGranted) {
            locationRepository.awaitLastLocation()?.let {
                // update the location marker on the map
                roverMapState.updateUserLocation(GeoCoords(lat = it.latitude, lon = it.longitude))
            }

            locationRepository.locationFlow().collect {
                roverMapState.updateUserLocation(GeoCoords(lat = it.latitude, lon = it.longitude))
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        // animate map position to current user location
                        roverMapState.updateAnimated(
                            newPosition = GeoCoords(
                                lat = roverMapState.userLocation.lat,
                                lon = roverMapState.userLocation.lon
                            ),
                            newScale = 20.0,
                        )
                    }
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Image(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Go to current location"
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
    ) {
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier.padding(it)
        ) {
            if(isVulkanAvailable) {
                RoverMap(
                    modifier = Modifier.fillMaxSize(),
                    mapFile = mapFile,
                    roverMapState = roverMapState
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Your device does not support vulkan")
                }
            }

            AnimatedVisibility(
                visible = showDebugMenu,
                enter = fadeIn() + slideInHorizontally(),
                exit = fadeOut() + slideOutHorizontally()
            ) {
                DebuggingMenu(
                    roverMapState = roverMapState,
                    documentIntentResolver = documentIntentResolver,
                    isLoading = { value -> isLoading = value },
                    onProgressUpdate = { value -> loadingProgress = value },
                    mapFile = mapFile,
                    setMapFile = { value -> mapFile = value }
                )
            }

            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxSize(),
                contentAlignment = Alignment.TopEnd
            ) {
                Column {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                roverMapState.updateAnimated(
                                    newRotation = 0.0
                                )
                            }
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .rotate(-(roverMapState.rotation.toFloat() + 45f))
                    ) {
                        Image(
                            imageVector = Icons.Outlined.Explore,
                            contentDescription = "Reset map rotation",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            showDebugMenu = !showDebugMenu
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                    ) {
                        Image(
                            imageVector = if(showDebugMenu) { Icons.Outlined.VisibilityOff } else { Icons.Outlined.Visibility },
                            contentDescription = "Toggle debug menu",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            if(isLoading) {
                LoadingSpinner(
                    progress = loadingProgress,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
fun DebuggingMenu(
    roverMapState: RoverMapState,
    documentIntentResolver: DocumentIntentResolver,
    isLoading: (loading: Boolean) -> Unit,
    onProgressUpdate: (progress: Double) -> Unit,
    modifier: Modifier = Modifier,
    mapFile: File? = null,
    setMapFile: (newFile: File?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var multiSampleCount by remember { mutableStateOf(log(roverMapState.multisampleCount.toDouble(), 2.0).toFloat()) }

    Card(
        modifier = modifier
            .statusBarsPadding()
            .padding(8.dp)
            .width(250.dp),
        backgroundColor = MaterialTheme.colors.surface.copy(0.9f),
        elevation = 0.dp
    ) {
        LazyColumn {
            item {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Position:\n" +
                                "  Latitude: ${roverMapState.position.lat.format(4)}\n" +
                                "  Longitude: ${roverMapState.position.lon.format(4)}"
                    )
                    Text(text = "Scale: ${roverMapState.scale.format(4)}")
                    Text(text = "Rotation: ${roverMapState.rotation.format(4)}")
                    Text(
                        text = "User location:\n" +
                                "  Latitude: ${roverMapState.userLocation.lat.format(4)}\n" +
                                "  Longitude: ${roverMapState.userLocation.lon.format(4)}"
                    )
                    Text(text = "Map file: ${mapFile ?: "default"}")

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Show user location")
                        Switch(
                            checked = roverMapState.showUserLocation,
                            onCheckedChange = { roverMapState.showUserLocation(it) })
                    }

                    Text("Tile base size")
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = roverMapState.tileBaseSize.toFloat(),
                            onValueChange = {
                                roverMapState.update(
                                    newTileBaseSize = it.roundToInt().toDouble()
                                )
                            },
                            valueRange = 1f..500f,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = roverMapState.tileBaseSize.format(0),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(35.dp),
                        )
                    }

                    Text("Multisample count")
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = multiSampleCount,
                            onValueChange = {
                                multiSampleCount = it
                                roverMapState.update(
                                    newMultisampleCount = 2.0.pow(it.roundToInt().toDouble()).toUInt()
                                )
                            },
                            valueRange = 0f..3f,
                            steps = 2,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent,
                                disabledActiveTickColor = Color.Transparent,
                                disabledInactiveTickColor = Color.Transparent
                            )
                        )

                        Text(
                            text = roverMapState.multisampleCount.toString(),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(35.dp),
                        )
                    }

                    Text("Scale factor")
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = roverMapState.scaleFactor,
                            onValueChange = {
                                roverMapState.update(
                                    newScaleFactor = it
                                )
                            },
                            valueRange = 0.1f..2f,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = roverMapState.scaleFactor.format(2),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(35.dp),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(onClick = {
                            // here we want the user to select a new .rovermap file
                            // of course, you could also just download and use a map file
                            scope.launch {
                                val newMapFile = getFilePath(
                                    context = context,
                                    documentIntentResolver = documentIntentResolver,
                                    onProgressUpdate = onProgressUpdate,
                                    isLoading = isLoading
                                )

                                setMapFile(newMapFile)

                                roverMapState.update(
                                    newLatRange = 47.45..55.05,
                                    newLonRange = 5.87..15.04,
                                    newScaleRange = 9.0..20.0
                                )
                            }
                        }) {
                            Text("Load custom map")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingSpinner(
    progress: Double,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Gray.copy(alpha = 0.75f),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    text = "${progress.format(2)} %",
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(Locale.US ,this)
fun Float.format(digits: Int) = "%.${digits}f".format(Locale.US ,this)
