package com.vikas.facegate.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikas.facegate.domain.model.AccessDecision

@Composable
fun AccessDecisionOverlay(
    decision: AccessDecision,
    modifier: Modifier = Modifier
) {
    // Only show overlay for terminal states
    val isVisible = decision is AccessDecision.Granted ||
            decision is AccessDecision.Denied

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit  = fadeOut(),
        modifier = modifier
    ) {
        val (bg, icon, label) = when (decision) {
            is AccessDecision.Granted -> Triple(
                Color(0xCC00C853), "✓", "ACCESS GRANTED"
            )
            is AccessDecision.Denied -> Triple(
                Color(0xCCDD2C00), "✗",
                "ACCESS DENIED\n${(decision as? AccessDecision.Denied)?.reason ?: ""}"
            )
            else -> Triple(Color.Transparent, "", "")
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = icon,
                    fontSize = 80.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = label,
                    fontSize = 24.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}