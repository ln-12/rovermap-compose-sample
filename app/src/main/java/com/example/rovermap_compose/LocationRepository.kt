package com.example.rovermap_compose

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// this repository provides the current user location
class LocationRepository(context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    private suspend fun FusedLocationProviderClient.awaitLastLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            lastLocation.addOnSuccessListener { location ->
                continuation.resume(location)
            }.addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
        }

    suspend fun awaitLastLocation() = fusedLocationClient.awaitLastLocation()

    @SuppressLint("MissingPermission")
    private fun FusedLocationProviderClient.locationFlow(): Flow<Location> =
        callbackFlow {
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach {
                        try {
                            Log.d("LocationRepository", "New location: $it")
                            trySend(it)
                        } catch (e: Exception) {
                            println(e)
                        }
                    }
                }
            }

            Log.d("LocationRepository", "Requesting location updates")

            requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                close(e)
            }

            awaitClose {
                Log.d("LocationRepository", "Stopping location updates")

                removeLocationUpdates(callback)
            }
        }

    fun locationFlow() = fusedLocationClient.locationFlow()

    private val locationRequest = LocationRequest.create().apply {
        interval = 10000
        fastestInterval = 5000
        priority = PRIORITY_HIGH_ACCURACY
    }
}
