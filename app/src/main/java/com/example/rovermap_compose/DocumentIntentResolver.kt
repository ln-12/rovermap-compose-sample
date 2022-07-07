package com.example.rovermap_compose

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// this resolver starts a "select file" dialog and returns the (content) URI
class DocumentIntentResolver(private val registry: ActivityResultRegistry): DefaultLifecycleObserver {
    lateinit var resultLauncher: ActivityResultLauncher<Array<String>>
    private var continuation: CancellableContinuation<Uri?>? = null

    override fun onCreate(owner: LifecycleOwner) {
        resultLauncher = registry.register("document", owner, ActivityResultContracts.OpenDocument()) { result ->
            continuation?.resume(result)
            continuation = null
        }
    }

    suspend fun getFile(): Uri? {
        // if there is already an ongoing intent, skip this request
        if (this.continuation != null) { return null }

        return suspendCancellableCoroutine { continuation ->
            this.continuation = continuation

            resultLauncher.launch(arrayOf("*/*"))
        }
    }
}
