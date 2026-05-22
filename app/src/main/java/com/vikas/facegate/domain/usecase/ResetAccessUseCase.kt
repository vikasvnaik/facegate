package com.vikas.facegate.domain.usecase

import com.vikas.facegate.domain.model.AccessDecision
import javax.inject.Inject

/**
 * Resets the access decision back to Idle.
 * Called after Granted/Denied display period expires.
 */
class ResetAccessUseCase @Inject constructor() {
    operator fun invoke(): AccessDecision = AccessDecision.Idle
}