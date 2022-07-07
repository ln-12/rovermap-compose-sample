package com.example.rovermap_compose.rovermap

import androidx.compose.runtime.AbstractApplier
import de.tubaf.rovermapgfx.View

internal interface MapNode {
    fun onAttached() {}
    fun onRemoved() {}
    fun onCleared() {}
}

private object MapNodeRoot : MapNode

internal class MapApplier(
    val map: View,
) : AbstractApplier<MapNode>(MapNodeRoot) {
    override fun onClear() {}

    override fun insertBottomUp(index: Int, instance: MapNode) {
        instance.onAttached()
    }

    override fun insertTopDown(index: Int, instance: MapNode) {}

    override fun move(from: Int, to: Int, count: Int) {}

    override fun remove(index: Int, count: Int) {}
}
