package com.example.rovermap_compose.rovermap

import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.rovermap_compose.copyAsset
import de.tubaf.rovermapgfx.*
import kotlinx.coroutines.awaitCancellation
import java.io.File
import java.io.IOException
import kotlin.random.Random

@OptIn(ExperimentalUnsignedTypes::class)
@Composable
fun RoverMap(
    modifier: Modifier = Modifier,
    mapFile: File? = null,
    roverMapState: RoverMapState = rememberRoverMapState(),
    contentDescription: String? = null,
    contentPadding: PaddingValues = NoPadding,
) {
    val context = LocalContext.current
    val useDarkTheme = isSystemInDarkTheme()
    val backgroundColor = MaterialTheme.colors.background.toArgb()

    var dialogData by remember { mutableStateOf<String?>(null) }
    var dbLayer by remember { mutableStateOf<Layer?>(null) }

    val mapView = remember {
        View(context).apply {
            val appDir = context.applicationInfo.dataDir
                ?: throw IOException("Failed to get application dir")

            val dbFileName = "freiberg.rovermap"
            val dbFile = mapFile ?: File(appDir, dbFileName)

            Log.d("RoverMap", "Using map file at ${dbFile.absoluteFile}")

            // copy the assets if necessary
            if (!dbFile.exists()) {
                copyAsset(context, dbFileName, dbFile)
            }

            val themeSelector = if(useDarkTheme) { "dark" } else { "light" }

            var styleBundleFileName = "map_styles_$themeSelector.bundle"
            var styleBundleFile = File(appDir, styleBundleFileName)

            if (!styleBundleFile.exists()) {
                copyAsset(context, styleBundleFileName, styleBundleFile)
            }

            // add map layer
            val mapLayerResult = addSQLiteLayer(
                dbFile = dbFile,
                styleBundleFile = styleBundleFile,
                labelPixelFactor = 2.0,
                tileCacheCapacity = 80u
            )

            when(mapLayerResult) {
                is LayerResult.Failure -> {
                    Log.d("RoverMap", "Adding map layer failed: ${mapLayerResult.errorMessage}")
                }
                is LayerResult.Success -> {
                    Log.d("RoverMap", "Map layer created.")
                    dbLayer = mapLayerResult.layer
                }
            }

            // add station layer
            styleBundleFileName = "poi.bundle"
            styleBundleFile = File(appDir, styleBundleFileName)

            if (!styleBundleFile.exists()) {
                copyAsset(context, styleBundleFileName, styleBundleFile)
            }

            var styleJsonFileName = "poi.json"
            var styleJsonFile = File(appDir, styleJsonFileName)

            if (!styleJsonFile.exists()) {
                copyAsset(context, styleJsonFileName, styleJsonFile)
            }

            val poiLayerResult = addCustomPOILayer(
                featureClassData = styleJsonFile.readBytes(),
                zoomLevels = ubyteArrayOf(16u, 13u, 10u),
                styleBundleFile = styleBundleFile,
                labelPixelFactor = 2.0,
                tileCacheCapacity = 10u,
                callback = { longitudeRange, latitudeRange, zoomLevel, poiList ->
                    val id = Random.nextInt(0, 100_000)

                    if(zoomLevel >= 16u) {
                        poiList.add(
                            CustomPOI(
                                featureClass = "poi",
                                coords = GeoCoords(
                                    lat = (latitudeRange.start + latitudeRange.endInclusive) / 2,
                                    lon = (longitudeRange.start + longitudeRange.endInclusive) / 2
                                ),
                                properties = mutableMapOf(
                                    // save charging box code for identification after interaction
                                    "code" to PropertyValue.String("$id"),
                                    "label" to PropertyValue.String("ID: $id"),
                                )
                            )
                        )
                    }
                }
            )

            when(poiLayerResult) {
                is LayerResult.Failure -> {
                    Log.d("RoverMap", "Adding POI layer failed: ${poiLayerResult.errorMessage}")
                }
                is LayerResult.Success -> {
                    roverMapState.setPOILayer(poiLayerResult.layer)
                }
            }

            // add location layer
            styleBundleFileName = "user_location.bundle"
            styleBundleFile = File(appDir, styleBundleFileName)

            if (!styleBundleFile.exists()) {
                copyAsset(context, styleBundleFileName, styleBundleFile)
            }

            styleJsonFileName = "user_location.json"
            styleJsonFile = File(appDir, styleJsonFileName)

            if (!styleJsonFile.exists()) {
                copyAsset(context, styleJsonFileName, styleJsonFile)
            }

            val locationLayerResult = addCustomPOILayer(
                featureClassData = styleJsonFile.readBytes(),
                zoomLevels = ubyteArrayOf(16u, 13u, 10u),
                styleBundleFile = styleBundleFile,
                labelPixelFactor = 2.0,
                tileCacheCapacity = 10u,
                callback = { longitudeRange, latitudeRange, zoomLevel, poiList ->
                    roverMapState.userLocation.let {
                        if (
                            longitudeRange.contains(it.lon) &&
                            latitudeRange.contains(it.lat)
                        ) {
                            poiList.add(
                                CustomPOI(
                                    featureClass = "user_location",
                                    coords = GeoCoords(
                                        lon = it.lon,
                                        lat = it.lat
                                    ),
                                    properties = mutableMapOf(
                                        "label" to PropertyValue.String("You are here")
                                    )
                                )
                            )

                        }
                    }
                }
            )

            when(locationLayerResult) {
                is LayerResult.Failure -> {
                    Log.d("RoverMap", "Adding location layer failed: ${locationLayerResult.errorMessage}")
                }
                is LayerResult.Success -> {
                    roverMapState.setLocationLayer(locationLayerResult.layer)
                }
            }

            setBackgroundColor(backgroundColor)
            setFadeTimeMillis(250u)
            tileBaseSize = roverMapState.tileBaseSize
            position = roverMapState.position
            scale = roverMapState.scale
            scaleRange = roverMapState.scaleRange
            latRange = roverMapState.latRange
            lonRange = roverMapState.lonRange
            rotation = roverMapState.rotation
            observerCallback = { position, scale, rotation ->
                if(!roverMapState.isAnimating) {
                    roverMapState.update(
                        newPosition = position,
                        newScale = scale,
                        newRotation = radToDeg(rotation),
                    )
                }
            }
            // TODO add onClick callback and select features from poiLayer
            // -> set dialogDate to "code" of the selected POI
            setMultisampleCount(roverMapState.multisampleCount)
        }
    }

    LaunchedEffect(mapFile) {
        dbLayer?.delete()

        val appDir = context.applicationInfo.dataDir
            ?: throw IOException("Failed to get application dir")

        val dbFileName = "freiberg.rovermap"
        val dbFile = mapFile ?: File(appDir, dbFileName)

        Log.d("RoverMap", "Using map file at ${dbFile.absoluteFile}")

        // copy the assets if necessary
        if (!dbFile.exists()) {
            copyAsset(context, dbFileName, dbFile)
        }

        val themeSelector = if(useDarkTheme) { "dark" } else { "light" }

        val styleBundleFileName = "map_styles_$themeSelector.bundle"
        val styleBundleFile = File(appDir, styleBundleFileName)

        if (!styleBundleFile.exists()) {
            copyAsset(context, styleBundleFileName, styleBundleFile)
        }

        // add map layer
        val mapLayerResult = mapView.addSQLiteLayer(
            dbFile = dbFile,
            styleBundleFile = styleBundleFile,
            labelPixelFactor = 2.0,
            tileCacheCapacity = 80u
        )

        when(mapLayerResult) {
            is LayerResult.Failure -> {
                Log.d("RoverMap", "Adding map layer failed: ${mapLayerResult.errorMessage}")
            }
            is LayerResult.Success -> {
                Log.d("RoverMap", "Map layer created.")
                dbLayer = mapLayerResult.layer
                mapView.setDrawOrder(arrayOf(mapLayerResult.layer, roverMapState.poiLayer, roverMapState.locationLayer).filterNotNull().toTypedArray())
            }
        }
    }

    val currentRoverMapState by rememberUpdatedState(roverMapState)
    val currentContentPadding by rememberUpdatedState(contentPadding)
    val parentComposition = rememberCompositionContext()

    LaunchedEffect(Unit) {
        disposingComposition {
            mapView.newComposition(parentComposition) {
                MapUpdater(
                    contentDescription = contentDescription,
                    roverMapState = currentRoverMapState,
                    contentPadding = currentContentPadding,
                )
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView })


        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.caption,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(8.dp)
        )

        if (dialogData != null) {
            AlertDialog(
                onDismissRequest = { dialogData = null },
                text = {
                    Text("You tapped on POI with ID $dialogData")
                },
                confirmButton = {
                    TextButton(onClick = { dialogData = null }) {
                        Text(text = "OK")
                    }
                }
            )
        }
    }
}

private suspend inline fun disposingComposition(factory: () -> Composition) {
    val composition = factory()
    try {
        awaitCancellation()
    } finally {
        composition.dispose()
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun View.newComposition(
    parent: CompositionContext,
    noinline content: @Composable () -> Unit
): Composition {
    return Composition(
        MapApplier(this), parent
    ).apply {
        setContent(content)
    }
}

fun radToDeg(value: Double) = (value / Math.PI * 180.0)
fun degToRad(value: Double) = (value * Math.PI / 180.0)