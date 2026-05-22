# FaceGate

A real-time face-based access control app for Android. Built as a learning project exploring Camera2, ML Kit, and Kotlin Flow — modelled on the kind of pipeline a device like Swiftlane's video intercom would run.

---

## What it does

Point the phone at your face. The app detects it, runs a basic liveness check (both eyes open), and displays an access granted or denied result. The whole decision cycle resets automatically after three seconds so it can be tested repeatedly.

The state machine:

```
Idle ──► Scanning ──► LivenessChallenge ──► Granted
                                        └──► Denied
```

---

## Architecture

Clean Architecture with three layers, each with a single responsibility:

```
presentation/          ← Compose UI + ViewModel
  ui/
    MainActivity       ← Entry point, rotation, permissions
    CameraPreview      ← SurfaceView wrapped in AndroidView
    FaceOverlay        ← Canvas bounding boxes over detected faces
    CoordinateTransformer ← Camera-space → screen-space mapping
    StatusBar          ← Coloured state indicator bar at the bottom
    AccessDecisionOverlay ← Full-screen Granted / Denied flash
  viewmodel/
    FaceGateViewModel  ← Drives the state machine, owns camera lifecycle

domain/
  model/
    AccessDecision     ← Sealed class: Idle | Scanning | LivenessChallenge | Granted | Denied
    FaceResult         ← Detected face data (box, eye probabilities, score)
  usecase/
    EvaluateFaceUseCase   ← Pure function: (faces, currentState) → nextState
    ResetAccessUseCase    ← Returns AccessDecision.Idle

data/
  camera/
    CameraDeviceManager   ← Opens the front camera via Camera2, emits CameraState as Flow
    CameraSessionManager  ← Creates a CaptureSession with repeating preview request
    CameraRepository      ← Coordinates device + session, exposes frameFlow()
    CameraFrameSource     ← ImageReader → Flow<Image> with backpressure handling
  face/
    FaceDetectorSource    ← ML Kit face detector, suspendCancellableCoroutine bridge
    FaceRepository        ← Transforms Flow<Image> → Flow<List<FaceResult>>
```

---

## Camera pipeline

Raw Camera2 is used directly (not CameraX's ImageAnalysis) to keep control over the frame pipeline.

```
Camera HAL
    │
    ├─► SurfaceView          (preview — 30 fps direct to display)
    │
    └─► ImageReader 640×480  (analysis frames — YUV_420_888)
            │
            │  OnImageAvailableListener (fires on main thread via Handler)
            │
            ▼
       callbackFlow  ──[RENDEZVOUS channel]──►  map { detectFaces(image) }
                                                    │  (Dispatchers.Default)
                                                    ▼
                                            Flow<List<FaceResult>>
                                                    │
                                                    ▼
                                           advanceStateMachine()
```

Two design choices worth noting:

**RENDEZVOUS channel** — `CameraFrameSource` uses `Channel.RENDEZVOUS` so that if the ML Kit detector is still processing the previous frame, the new frame is acquired and immediately closed rather than queued. This prevents the ImageReader's native buffers (maxImages = 2) from filling up, which would stall the entire camera pipeline including the preview surface.

**Explicit Handler** — `setOnImageAvailableListener` is called from a `Dispatchers.Default` coroutine (because `FaceRepository` applies `flowOn(Default)`). Default threads have no Looper, so `null` handler throws. An explicit `Handler(Looper.getMainLooper())` is passed instead.

---

## State machine

`EvaluateFaceUseCase` is a pure function — no side effects, no coroutines, fully unit-testable. The ViewModel calls it on every frame result and only acts when the state changes.

| From | To | Condition |
|---|---|---|
| Idle | Scanning | Any face detected |
| Scanning | LivenessChallenge | Face confidence score ≥ threshold |
| LivenessChallenge | Granted | Both eyes open probability > 0.4 |
| LivenessChallenge | LivenessChallenge | Eyes not open — hold state |
| Granted / Denied | Idle | Auto-reset after 3 seconds |
| Any | Idle | Face disappears (except terminal states) |

---

## Tech stack

| Layer | Library | Version |
|---|---|---|
| Language | Kotlin | 2.0.0 |
| Build | Android Gradle Plugin | 8.4.0 |
| DI | Hilt | 2.51.1 |
| Camera | Camera2 (via CameraX interop) | 1.3.4 |
| Face detection | ML Kit Face Detection | 16.1.6 |
| Async | Kotlin Coroutines + Flow | 1.8.1 |
| UI | Jetpack Compose + Material3 | BOM 2024.06.00 |
| Min SDK | Android 8.0 | API 26 |
| Target SDK | Android 15 | API 35 |
| JVM target | Java 17 | — |

---

## Getting started

1. Clone the repo and open in Android Studio Hedgehog or later.
2. Connect a physical device — Camera2 and ML Kit do not work reliably on emulators.
3. Run the app. Grant camera permission when prompted.
4. Hold the phone in portrait and look at the front camera.

No API keys or backend required. ML Kit face detection runs fully on-device.

---

## Permissions

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

The app requests this at runtime on first launch.

---

## Known limitations

- **Liveness** is a basic eye-open check, not a full anti-spoofing solution. A photo of a person with open eyes will pass.
- **BLE proximity** detection is planned but not yet implemented — the `Scanning` state is intended to gate on both face detection and BLE RSSI, but currently only checks the face.
- **Single face** — only the first detected face is evaluated. Multiple people in frame are ignored.
- **Fixed orientation** — designed for portrait mode. Rotation is accounted for at startup but the app does not handle runtime orientation changes.
