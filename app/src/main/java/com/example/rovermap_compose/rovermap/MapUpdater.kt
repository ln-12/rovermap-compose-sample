package com.example.rovermap_compose.rovermap

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import de.tubaf.rovermapgfx.View

internal class MapPropertiesNode(
    val map: View,
    roverMapState: RoverMapState,
    contentDescription: String?,
    var density: Density,
    var layoutDirection: LayoutDirection,
) : MapNode {
    init {
        roverMapState.setMap(map)
        if (contentDescription != null) {
            map.contentDescription = contentDescription
        }
    }

    var contentDescription = contentDescription
        set(value) {
            field = value
            map.contentDescription = contentDescription
        }

    var roverMapState = roverMapState
        set(value) {
            if (value == field) return
            field.setMap(null)
            field = value
            value.setMap(map)
        }

    override fun onAttached() {}

    override fun onRemoved() {
        roverMapState.setLocationLayer(null)
        roverMapState.setPOILayer(null)
        roverMapState.setMap(null)
    }

    override fun onCleared() {
        roverMapState.setLocationLayer(null)
        roverMapState.setPOILayer(null)
        roverMapState.setMap(null)
    }
}

internal val NoPadding = PaddingValues()

@SuppressLint("MissingPermission")
@Suppress("NOTHING_TO_INLINE")
@Composable
internal inline fun MapUpdater(
    contentDescription: String?,
    roverMapState: RoverMapState,
    contentPadding: PaddingValues = NoPadding,
) {
    val map = (currentComposer.applier as MapApplier).map
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    ComposeNode<MapPropertiesNode, MapApplier>(
        factory = {
            MapPropertiesNode(
                map = map,
                contentDescription = contentDescription,
                roverMapState = roverMapState,
                density = density,
                layoutDirection = layoutDirection,
            )
        }
    ) {
        // The node holds density and layoutDirection so that the updater blocks can be
        // non-capturing, allowing the compiler to turn them into singletons
        update(density) { this.density = it }
        update(layoutDirection) { this.layoutDirection = it }
        update(contentDescription) { this.contentDescription = it }

        set(contentPadding) {
            val node = this
            with(this.density) {
                map.setPadding(
                    it.calculateLeftPadding(node.layoutDirection).roundToPx(),
                    it.calculateTopPadding().roundToPx(),
                    it.calculateRightPadding(node.layoutDirection).roundToPx(),
                    it.calculateBottomPadding().roundToPx()
                )
            }
        }

        update(roverMapState) { this.roverMapState = it }
    }
}
