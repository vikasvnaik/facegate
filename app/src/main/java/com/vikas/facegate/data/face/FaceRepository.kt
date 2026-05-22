package com.vikas.facegate.data.face

import android.media.Image
import android.util.Log
import com.vikas.facegate.domain.model.FaceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaceRepository @Inject constructor(
    private val faceDetectorSource: FaceDetectorSource
) {

    /**
     * Takes the raw camera frame flow and transforms it into
     * a flow of face detection results.
     */
    fun faceResultFlow(frameFlow: Flow<Image>, rotationDegrees: Int): Flow<List<FaceResult>> =
        frameFlow
            // We removed .conflate() here because it drops frames without closing them.
            // Dropping logic is now handled at the source in CameraFrameSource
            // using trySend + immediate image.close() on failure.
            .map { image ->
                try {
                    val results = faceDetectorSource.detectFaces(image, rotationDegrees)
                    results
                } catch (e: Exception) {
                    Log.e("FaceRepository", "ML Kit error", e)
                    emptyList()
                } finally {
                    image.close()
                }
            }
            .catch { e ->
                Log.e("FaceRepository", "Fatal flow error", e)
                emit(emptyList())
            }
            .flowOn(Dispatchers.Default)
}