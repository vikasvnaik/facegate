package com.vikas.facegate.presentation.viewmodel

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikas.facegate.data.camera.CameraRepository
import com.vikas.facegate.data.camera.CameraState
import com.vikas.facegate.data.camera.SessionState
import com.vikas.facegate.data.face.FaceRepository
import com.vikas.facegate.domain.model.AccessDecision
import com.vikas.facegate.domain.model.FaceResult
import com.vikas.facegate.domain.model.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FaceGateViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val faceRepository: FaceRepository
): ViewModel() {

    private val _accessDecision = MutableStateFlow<AccessDecision>(AccessDecision.Idle)
    val accessDecision: StateFlow<AccessDecision> = _accessDecision.asStateFlow()

    private val _permissionState = MutableStateFlow<PermissionState?>(null)
    val permissionState: StateFlow<PermissionState?> = _permissionState.asStateFlow()
    private val _debugLog = MutableStateFlow("Waiting...")
    val debugLog: StateFlow<String> = _debugLog.asStateFlow()

    private val _faceResults = MutableStateFlow<List<FaceResult>>(emptyList())
    val faceResult: StateFlow<List<FaceResult>> = _faceResults.asStateFlow()

    val cameraState: StateFlow<CameraState> = cameraRepository.cameraState
    val sessionState: StateFlow<SessionState> = cameraRepository.sessionState

    private var detectionJob: Job? = null

    fun onPermissionState(state: PermissionState) {
        _permissionState.value = state
        when(state) {
            is PermissionState.Granted  -> {
                _debugLog.value = "All permissions granted"
                openCamera()
            }
            is PermissionState.Denied   -> _debugLog.value = "Denied: ${state.permissions.joinToString()}"
            is PermissionState.Rationale -> _debugLog.value = "Please grant camera & BLE permissions"
        }
    }

    fun openCamera() {
        cameraRepository.open(viewModelScope)

        viewModelScope.launch {
            cameraRepository.cameraState.collect { state ->
                _debugLog.value = when(state) {
                    is CameraState.Opening -> "Opening camera..."
                    is CameraState.Opened  -> "Camera opened successfully"
                    is CameraState.Error   -> "Camera error: ${state.message}"
                    is CameraState.Disconnected -> "Camera disconnected"
                    is CameraState.Closed -> "Camera closed"

                }
            }
        }
    }

    private fun startFaceDetection() {
        detectionJob?.cancel()
        detectionJob = viewModelScope.launch {
            faceRepository.faceResultFlow(
                cameraRepository.frameFlow()
            ).collect { faces ->
                _faceResults.value = faces
                _debugLog.value = when {
                    faces.isEmpty() -> "No face detected"
                    faces.size == 1 -> "Face detected " +
                            "(eye L:${faces[0].leftEyeOpenProbability?.let {
                                "%.0f%%".format(it * 100)
                            } ?: "?"})"
                    else -> "${faces.size} faces detected"
                }
            }
        }
    }

    fun startPreview(previewSurface: Surface) {
        cameraRepository.startPreview(viewModelScope, previewSurface)
        viewModelScope.launch {
            cameraRepository.sessionState.first { it is SessionState.Ready }
            startFaceDetection()
        }
    }

    fun stopPreview() {
        cameraRepository.stopPreview()
    }

    fun onSurfaceDestroyed() {
        cameraRepository.releasePreviewSurface()
    }

    fun closeCamera() {
        detectionJob?.cancel()
        cameraRepository.close()
    }

    override fun onCleared() {
        super.onCleared()
        closeCamera()
    }
    fun log(msg: String) {
        _debugLog.value = msg
    }
}