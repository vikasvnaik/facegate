package com.vikas.facegate.presentation.viewmodel

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikas.facegate.data.camera.CameraRepository
import com.vikas.facegate.data.camera.CameraState
import com.vikas.facegate.data.face.FaceRepository
import com.vikas.facegate.domain.model.AccessDecision
import com.vikas.facegate.domain.model.FaceResult
import com.vikas.facegate.domain.model.PermissionState
import com.vikas.facegate.domain.usecase.EvaluateFaceUseCase
import com.vikas.facegate.domain.usecase.ResetAccessUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FaceGateViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val faceRepository: FaceRepository,
    private val evaluateFace: EvaluateFaceUseCase,
    private val resetAccess: ResetAccessUseCase
) : ViewModel() {

    private val _accessDecision = MutableStateFlow<AccessDecision>(AccessDecision.Idle)
    val accessDecision: StateFlow<AccessDecision> = _accessDecision.asStateFlow()

    private val _permissionState = MutableStateFlow<PermissionState?>(null)
    val permissionState: StateFlow<PermissionState?> = _permissionState.asStateFlow()

    private val _debugLog = MutableStateFlow("Waiting for permissions...")
    val debugLog: StateFlow<String> = _debugLog.asStateFlow()

    private val _faceResults = MutableStateFlow<List<FaceResult>>(emptyList())
    val faceResults: StateFlow<List<FaceResult>> = _faceResults.asStateFlow()

    val cameraState: StateFlow<CameraState> = cameraRepository.cameraState

    private var detectionJob: Job? = null
    private var resetJob: Job? = null

    private var rotationDegrees: Int = 270

    fun setRotationDegrees(degrees: Int) {
        rotationDegrees = degrees
    }

    fun onPermissionState(state: PermissionState) {
        _permissionState.value = state
        when (state) {
            is PermissionState.Granted -> {
                _debugLog.value = "Opening camera..."
                cameraRepository.open(viewModelScope)
                observeCameraState()
            }
            is PermissionState.Denied ->
                _debugLog.value = "Denied: ${state.permissions.joinToString()}"
            is PermissionState.Rationale ->
                _debugLog.value = "Please grant permissions"
        }
    }

    private fun observeCameraState() {
        viewModelScope.launch {
            cameraRepository.cameraState.collect { state ->
                if (state is CameraState.Error) {
                    _debugLog.value = "Camera error: ${state.message}"
                }
            }
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

    fun startPreview(surface: Surface) {
        cameraRepository.startPreview(viewModelScope, surface)
        viewModelScope.launch {
            delay(500)
            startFaceDetection()
        }
    }

    private fun startFaceDetection() {
        detectionJob?.cancel()
        detectionJob = viewModelScope.launch {
            faceRepository.faceResultFlow(
                cameraRepository.frameFlow(),
                rotationDegrees
            ).collect { faces ->
                _faceResults.value = faces
                advanceStateMachine(faces)
            }
        }
    }

    /**
     * Core state machine driver.
     * EvaluateFaceUseCase is pure — all transition logic lives there.
     * ViewModel only drives timing (reset delay) and side effects
     * (logging, future BLE trigger in Phase 4).
     */
    private fun advanceStateMachine(faces: List<FaceResult>) {
        val current  = _accessDecision.value
        val next     = evaluateFace(faces, current)

        // Only act on actual state changes
        if (next == current) return

        _accessDecision.value = next
        _debugLog.value = stateLabel(next)

        // Terminal states auto-reset after display period
        if (next is AccessDecision.Granted || next is AccessDecision.Denied) {
            scheduleReset()
        }
    }

    private fun scheduleReset() {
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(3_000L) // show result for 3 seconds
            _accessDecision.value = resetAccess()
            _debugLog.value = "Ready — show your face"
        }
    }

    private fun stateLabel(state: AccessDecision) = when (state) {
        is AccessDecision.Idle              -> "Ready — show your face"
        is AccessDecision.Scanning          -> "Face detected — scanning..."
        is AccessDecision.LivenessChallenge -> "Keep eyes open..."
        is AccessDecision.Granted           -> "Access granted"
        is AccessDecision.Denied            -> "Access denied: ${state.reason}"
    }

    fun stopPreview() {
        cameraRepository.stopPreview()
    }

    fun onSurfaceDestroyed() {
        cameraRepository.releasePreviewSurface()
    }

    fun closeCamera() {
        detectionJob?.cancel()
        resetJob?.cancel()
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