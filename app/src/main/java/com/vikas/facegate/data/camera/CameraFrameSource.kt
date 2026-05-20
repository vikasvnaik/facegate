package com.vikas.facegate.data.camera

import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraFrameSource @Inject constructor() {

    private var imageReader: ImageReader? = null

    fun getImageReaderSurface(size: Size): Surface {
        imageReader?.close()
        imageReader = ImageReader.newInstance(
            size.width,
            size.height,
            ImageFormat.YUV_420_888,
            2
        )
        return imageReader!!.surface
    }

    /**
     * Emits one Image per camera frame.
     * We use RENDEZVOUS buffer to ensure we don't leak Images in the flow channel.
     */
    fun frameFlow(): Flow<Image> = callbackFlow {
        val reader = imageReader
            ?: error("Call getImageReaderSurface() before frameFlow()")

        val listener = ImageReader.OnImageAvailableListener { imgReader ->
            val image = imgReader.acquireLatestImage() ?: return@OnImageAvailableListener

            // trySend only succeeds if a collector is ready.
            // If the detector is busy, it fails, and we close the image immediately.
            val result = trySend(image)
            if (result.isFailure) {
                image.close()
            }
        }

        reader.setOnImageAvailableListener(listener, Handler(Looper.getMainLooper()))

        awaitClose {
            reader.setOnImageAvailableListener(null, null)
            // Note: imageReader lifecycle is managed by getImageReaderSurface
        }
    }.buffer(Channel.RENDEZVOUS) // CRITICAL: No internal buffering for Image objects
}