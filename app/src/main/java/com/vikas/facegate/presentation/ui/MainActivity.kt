package com.vikas.facegate.presentation.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.vikas.facegate.domain.model.AccessDecision
import com.vikas.facegate.domain.model.PermissionState
import com.vikas.facegate.presentation.ui.theme.FaceGateTheme
import com.vikas.facegate.presentation.viewmodel.FaceGateViewModel
import com.vikas.facegate.util.AppPermissions
import com.vikas.facegate.util.permissionFlow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: FaceGateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: 0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        val rotationDegrees = when (rotation) {
            0 -> 270   // portrait
            1 -> 0     // landscape right
            2 -> 90    // reverse portrait
            3 -> 180   // landscape left
            else -> 270
        }
        viewModel.setRotationDegrees(rotationDegrees)

        // Initial check for permissions
        lifecycleScope.launch {
            permissionFlow(AppPermissions.all).collect { state ->
                viewModel.onPermissionState(state)
            }
        }

        setContent {
            FaceGateTheme {
                val log by viewModel.debugLog.collectAsState()
                val permissionState by viewModel.permissionState.collectAsState()
                
                MainContent(
                    log = log, 
                    permissionState = permissionState,
                    viewModel = viewModel,
                    onRequestPermissions = {
                        // Launch permission flow from the Activity context
                        lifecycleScope.launch {
                            permissionFlow(AppPermissions.all).collect { state ->
                                viewModel.onPermissionState(state)
                            }
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val state = viewModel.permissionState.value
        if(state is PermissionState.Granted) {
            viewModel.openCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.closeCamera()
    }
}

/**
 * Main Content layout.
 * Shows CameraPreview if granted, otherwise shows status/request UI.
 * Avoids opaque Surface backgrounds in the Granted state to ensure 
 * the CameraPreview (SurfaceView) is visible.
 */
@Composable
fun MainContent(
    log: String,
    permissionState: PermissionState?,
    viewModel: FaceGateViewModel?,
    onRequestPermissions: () -> Unit
) {
    // Root container: No opaque Surface here when permissions are granted
    Box(modifier = Modifier.fillMaxSize()) {
        when (permissionState) {
            is PermissionState.Granted -> {
                // 1. Camera Preview filling the background layer
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceReady = { holder ->
                        viewModel?.startPreview(holder.surface)
                    },
                    onSurfaceDestroyed = {
                        // Notify VM that the surface is gone to release hardware
                        viewModel?.onSurfaceDestroyed()
                    }
                )

                // Layer 2 — face bounding boxes (on top, transparent bg)
                val faces by viewModel!!.faceResults.collectAsState()
                FaceOverlay(
                    faces = faces,
                    modifier = Modifier.fillMaxSize()
                )

                // Layer 3 — state bar pinned to the bottom
                val decision by viewModel!!.accessDecision.collectAsState()
                StatusBar(
                    message = log,
                    decision = decision,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                )
            }

            is PermissionState.Denied,
            is PermissionState.Rationale -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onRequestPermissions) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            else -> {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBar(
    message: String,
    decision: AccessDecision,
    modifier: Modifier = Modifier
) {
    val bg = when (decision) {
        is AccessDecision.Scanning          -> Color(0xCC185FA5)
        is AccessDecision.LivenessChallenge -> Color(0xCCBA7517)
        is AccessDecision.Granted           -> Color(0xCC00C853)
        is AccessDecision.Denied            -> Color(0xCCDD2C00)
        else                                -> Color(0x99000000)
    }
    Box(
        modifier = modifier
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    FaceGateTheme {
        MainContent(
            log = "Waiting for permissions...",
            permissionState = PermissionState.Rationale,
            viewModel = null,
            onRequestPermissions = {}
        )
    }
}