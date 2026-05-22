package com.vikas.facegate.domain.usecase


import com.vikas.facegate.domain.model.AccessDecision
import com.vikas.facegate.domain.model.FaceResult
import com.vikas.facegate.util.Constants
import javax.inject.Inject

/**
 * Decides what AccessDecision to emit given the current
 * face detection results and the current state.
 *
 * This is the only place where the transition rules live.
 * ViewModel calls this — it doesn't make decisions itself.
 */
class EvaluateFaceUseCase @Inject constructor() {

    operator fun invoke(
        faces: List<FaceResult>,
        currentState: AccessDecision
    ): AccessDecision {

        // No face visible
        if (faces.isEmpty()) {
            return when (currentState) {
                // Already in a terminal state — keep it until reset
                is AccessDecision.Granted -> currentState
                is AccessDecision.Denied  -> currentState
                // Otherwise go back to Idle
                else -> AccessDecision.Idle
            }
        }

        val face = faces.first() // use the largest/most confident face

        return when (currentState) {

            // Idle → Scanning when any face appears
            is AccessDecision.Idle -> AccessDecision.Scanning

            // Scanning → LivenessChallenge when confidence is high enough
            is AccessDecision.Scanning -> {
                if (face.score >= Constants.FACE_CONFIDENCE_THRESHOLD) {
                    AccessDecision.LivenessChallenge
                } else {
                    AccessDecision.Scanning // keep scanning
                }
            }

            // LivenessChallenge — liveness logic added in Phase 5
            // For now: pass immediately if both eyes are open
            is AccessDecision.LivenessChallenge -> {
                val leftOpen  = face.leftEyeOpenProbability  ?: 0f
                val rightOpen = face.rightEyeOpenProbability ?: 0f
                val eyesOpen  = leftOpen  > Constants.LIVENESS_BLINK_THRESHOLD &&
                        rightOpen > Constants.LIVENESS_BLINK_THRESHOLD
                if (eyesOpen) {
                    AccessDecision.Granted
                } else {
                    AccessDecision.LivenessChallenge
                }
            }

            // Terminal states — held until ViewModel resets them
            is AccessDecision.Granted -> currentState
            is AccessDecision.Denied  -> currentState
        }
    }
}