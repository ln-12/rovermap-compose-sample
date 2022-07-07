package com.example.rovermap_compose.rovermap

import android.os.Parcelable
import android.util.Log
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.tubaf.rovermapgfx.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

// state handling is heavily inspired by
// https://github.com/googlemaps/android-maps-compose/blob/4c0786690d610b807fa6dc09b8a9857460d0e581/maps-compose/src/main/java/com/google/maps/android/compose/CameraPositionState.kt
@Composable
inline fun rememberRoverMapState(
    key: String? = null,
    crossinline init: RoverMapState.() -> Unit = {}
): RoverMapState = rememberSaveable(key = key, saver = RoverMapState.Saver) {
        RoverMapState().apply(init)
    }

class RoverMapState(
    initialPosition: GeoCoords = GeoCoords(lon = 0.0, lat = 0.0),
    initialLatRange: ClosedRange<Double> = -90.0..90.0,
    initialLonRange: ClosedRange<Double> = -180.0..180.0,
    initialScale: FracZoomLevel = 0.0,
    initialScaleRange: ClosedRange<Double> = 0.0..23.0,
    initialRotation: Double = 0.0,
    initialUserLocation: GeoCoords = GeoCoords(lon = 0.0, lat = 0.0),
    initialShowUserLocation: Boolean = true,
    initialTileBaseSize: FracPoint = 115.0,
    initialMultisampleCount: UInt = 4U,
    initialScaleFactor: Float = 1f,
) {
    var position by mutableStateOf(initialPosition)
        private set

    var latRange by mutableStateOf(initialLatRange)
        private set

    var lonRange by mutableStateOf(initialLonRange)
        private set

    var scale by mutableStateOf(initialScale)
        private set

    var scaleRange by mutableStateOf(initialScaleRange)
        private set

    var rotation by mutableStateOf(initialRotation)
        private set

    var showUserLocation by mutableStateOf(initialShowUserLocation)
        private set

    var userLocation by mutableStateOf(initialUserLocation)
        private set

    var tileBaseSize by mutableStateOf(initialTileBaseSize)
        private set

    var multisampleCount by mutableStateOf(initialMultisampleCount)
        private set

    var scaleFactor by mutableStateOf(initialScaleFactor)
        private set

    var isAnimating by mutableStateOf(false)
        private set

    private var map by mutableStateOf<View?>(null)
     var locationLayer by mutableStateOf<Layer?>(null)
     var poiLayer by mutableStateOf<Layer?>(null)

    private val animationDuration = 500

    fun showUserLocation(isVisible: Boolean) {
        locationLayer?.setVisibility(isVisible)

        showUserLocation = isVisible
    }

    fun updateUserLocation(location: GeoCoords) {
        val oldLocation = userLocation
        userLocation = location

        locationLayer?.updateAtGeoPosition(oldLocation)
        locationLayer?.updateAtGeoPosition(location)
    }

    // Used to perform side effects thread-safely.
    // Guards all mutable properties that are not `by mutableStateOf`.
    private val lock = Any()

    // The current map is set and cleared by side effect.
    // There can be only one associated at a time.
    internal fun setMap(map: View?) {
        synchronized(lock) {
            if (this.map == null && map == null) return
            if (this.map != null && map != null) {
                error("RoverMapState may only be associated with one map at a time")
            }
            this.map = map
        }
    }

    internal fun setLocationLayer(layer: Layer?) {
        synchronized(lock) {
            if (this.locationLayer == null && layer == null) return
            if (this.locationLayer != null && layer != null) {
                Log.d("RoverMapState", "RoverMapState may only be associated with one location layer at a time, deleting old layer")
                this.locationLayer?.delete()
            }
            if (this.locationLayer != null && layer == null) {
                Log.d("RoverMapState", "Deleting location layer")
                this.locationLayer?.delete()
            }
            this.locationLayer = layer
        }
    }

    internal fun setPOILayer(layer: Layer?) {
        synchronized(lock) {
            if (this.poiLayer == null && layer == null) return
            if (this.poiLayer != null && layer != null) {
                Log.d("RoverMapState", "RoverMapState may only be associated with one poi layer at a time, deleting old layer")
                this.poiLayer?.delete()
            }
            if (this.poiLayer != null && layer == null) {
                Log.d("RoverMapState", "Deleting poi layer")
                this.poiLayer?.delete()
            }
            this.poiLayer = layer
        }
    }

    fun update(
        newPosition: GeoCoords = position,
        newScale: Double = scale,
        newRotation: Double = rotation,
        newLatRange: ClosedRange<Double> = latRange,
        newLonRange: ClosedRange<Double> = lonRange,
        newScaleRange: ClosedRange<FracZoomLevel> = scaleRange,
        newTileBaseSize: FracPoint = tileBaseSize,
        newMultisampleCount: UInt = multisampleCount,
        newScaleFactor: Float = scaleFactor,
    ) {
        map?.apply {
            position = newPosition
            scale = newScale
            rotation = degToRad(newRotation)
            latRange = newLatRange
            lonRange = newLonRange
            scaleRange = newScaleRange
            tileBaseSize = newTileBaseSize
            scaleFactor = newScaleFactor.toDouble()
            val result = setMultisampleCount(newMultisampleCount)
            if(result != MultisampleError.SUCCESS) {
                Log.d("MultisampleCount", "Result ${result.name} for value $newMultisampleCount")
            }
        }

        this.apply {
            position = newPosition
            scale = newScale
            rotation = newRotation
            latRange = newLatRange
            lonRange = newLonRange
            scaleRange = newScaleRange
            tileBaseSize = newTileBaseSize
            multisampleCount = newMultisampleCount
            scaleFactor = newScaleFactor
        }
    }

    suspend fun updateAnimated(
        newPosition: GeoCoords = position,
        newScale: Double = scale,
        newRotation: Double = rotation,
        newLatRange: ClosedRange<Double> = latRange,
        newLonRange: ClosedRange<Double> = lonRange,
        newScaleRange: ClosedRange<FracZoomLevel> = scaleRange,
        newTileBaseSize: FracPoint = tileBaseSize,
        newMultisampleCount: UInt = multisampleCount,
        newScaleFactor: Float = scaleFactor,
    ) = coroutineScope {
        isAnimating = true

        map?.apply {
            latRange = newLatRange
            lonRange = newLonRange
            scaleRange = newScaleRange
            tileBaseSize = newTileBaseSize
            scaleFactor = newScaleFactor.toDouble()
            val result = setMultisampleCount(newMultisampleCount)
            if(result != MultisampleError.SUCCESS) {
                Log.d("MultisampleCount", "Result ${result.name} for value $newMultisampleCount")
            }
        }

        this.apply {
            position = newPosition
            scale = newScale
            rotation = newRotation
            latRange = newLatRange
            lonRange = newLonRange
            scaleRange = newScaleRange
            tileBaseSize = newTileBaseSize
            scaleFactor = newScaleFactor
            multisampleCount = newMultisampleCount
        }

        map?.let {
            // animate location
            val locationJob = launch {
                animate(
                    typeConverter = TwoWayConverter<GeoCoords, AnimationVector2D>(
                        convertFromVector = { vector ->
                            GeoCoords(
                                lat = vector.v1.toDouble(),
                                lon = vector.v2.toDouble()
                            )
                        },
                        convertToVector = { coordinates ->
                            AnimationVector2D(
                                v1 = coordinates.lat.toFloat(),
                                v2 = coordinates.lon.toFloat()
                            )
                        }
                    ),
                    initialValue = it.position,
                    targetValue = newPosition,
                    initialVelocity = GeoCoords(0.0001, 0.0001),
                    animationSpec = tween(durationMillis = animationDuration),
                ) { value, _ ->
                    it.position = value
                }
            }

            val scaleJob = launch {
                // animate zoom level
                animate(
                    initialValue = it.scale.toFloat(),
                    targetValue = newScale.toFloat(),
                    initialVelocity = 0.1f,
                    animationSpec = tween(durationMillis = animationDuration),
                ) { value, _ ->
                    it.scale = value.toDouble()
                }
            }

            val rotationJob = launch {
                // animate rotation
                animate(
                    initialValue = radToDeg(it.rotation).toFloat(),
                    targetValue = newRotation.toFloat(),
                    initialVelocity = 0.1f,
                    animationSpec = tween(durationMillis = animationDuration),
                ) { value, _ ->
                    it.rotation = degToRad(value.toDouble())
                }
            }

            listOf(locationJob, scaleJob, rotationJob).joinAll()
        }

        isAnimating = false
    }

    override fun toString(): String {
        return "RoverMapState(" +
                "position=$position, " +
                "latRange=$latRange, " +
                "lonRange=$lonRange, " +
                "scale=$scale, " +
                "scaleRange=$scaleRange, " +
                "rotation=$rotation, " +
                "showUserLocation=$showUserLocation, " +
                "userLocation=$userLocation, " +
                "tileBaseSize=$tileBaseSize, " +
                "multisampleCount=$multisampleCount, " +
                "scaleFactor=$scaleFactor, " +
                "isAnimating=$isAnimating, " +
                "map=$map, " +
                "locationLayer=$locationLayer, " +
                "animationDuration=$animationDuration)"
    }


    companion object {
        @Parcelize
        data class State(
            val lat: Double,
            val lon: Double,
            val scale: FracZoomLevel,
            val scaleRangeMin: Double,
            val scaleRangeMax: Double,
            val latRangeMin: Double,
            val latRangeMax: Double,
            val lonRangeMin: Double,
            val lonRangeMax: Double,
            val showUserLocation: Boolean,
            val tileBaseSize: FracPoint,
            val multisampleCount: UInt,
            val scaleFactor: Float,
            val rotation: Double
        ): Parcelable

        val Saver: Saver<RoverMapState, State> = Saver(
            save = {
                State(
                    lat = it.position.lat,
                    lon = it.position.lon,
                    scale = it.scale,
                    scaleRangeMin = it.scaleRange.start,
                    scaleRangeMax = it.scaleRange.endInclusive,
                    latRangeMin = it.latRange.start,
                    latRangeMax = it.latRange.endInclusive,
                    lonRangeMin = it.lonRange.start,
                    lonRangeMax = it.lonRange.endInclusive,
                    showUserLocation = it.showUserLocation,
                    tileBaseSize = it.tileBaseSize,
                    multisampleCount = it.multisampleCount,
                    scaleFactor = it.scaleFactor,
                    rotation = it.rotation
                )
            },
            restore = {
                RoverMapState(
                    initialPosition = GeoCoords(lat = it.lat, lon = it.lon),
                    initialScale = it.scale,
                    initialScaleRange = it.scaleRangeMin..it.scaleRangeMax,
                    initialLatRange = it.latRangeMin..it.latRangeMax,
                    initialLonRange = it.lonRangeMin..it.lonRangeMax,
                    initialShowUserLocation = it.showUserLocation,
                    initialTileBaseSize = it.tileBaseSize,
                    initialMultisampleCount = it.multisampleCount,
                    initialScaleFactor = it.scaleFactor,
                    initialRotation = it.rotation
                )
            }
        )
    }
}
