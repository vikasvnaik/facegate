package com.vikas.facegate.data.face

import android.media.Image
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.vikas.facegate.domain.model.FaceResult
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class FaceDetectorSource @Inject constructor() {

    // Configure ML Kit face detector.
    // PERFORMANCE_MODE_FAST is correct for real-time video.
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.1f) // Lowered from 0.15f to detect faces further away or smaller in frame
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(options)

    /**
     * Processes one camera Image and returns detected faces.
     */
    suspend fun detectFaces(
        image: Image,
        rotationDegrees: Int = 270
    ): List<FaceResult> = suspendCancellableCoroutine { continuation ->
        try {
            val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    continuation.resume(faces.map { it.toDomainModel() })
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
                .addOnCanceledListener {
                    continuation.cancel()
                }
        } catch (e: Exception) {
            Log.e("FaceDetectorSource", "Error creating InputImage", e)
            continuation.resume(emptyList())
        }
    }

    /**
     * Maps ML Kit's Face to domain model.
     */
    // Replace the toDomainModel() extension in FaceDetectorSource.kt
    private fun Face.toDomainModel(): FaceResult {
        val box = boundingBox
        return FaceResult(
            boundingBox = android.graphics.RectF(
                box.left.toFloat(),
                box.top.toFloat(),
                box.right.toFloat(),
                box.bottom.toFloat()
            ),
            leftEyeOpenProbability  = leftEyeOpenProbability,
            rightEyeOpenProbability = rightEyeOpenProbability,
            headEulerAngleY         = headEulerAngleY,
            score = if (trackingId != null) 1.0f else 0.7f
            // trackingId is assigned by ML Kit once it's confident
            // about a face across multiple frames — reliable signal
        )
    }

    fun close() = detector.close()
}