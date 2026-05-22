package com.vikas.facegate.presentation.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.vikas.facegate.domain.model.FaceResult

@Composable
fun FaceOverlay(
    faces: List<FaceResult>,
    modifier: Modifier = Modifier
) {
    // Paint for text labels — created once, not on every frame
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 36f
            isAntiAlias = true
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }
    }

    Canvas(modifier = modifier) {
        val transformer = CoordinateTransformer(
            screenWidth  = size.width,
            screenHeight = size.height,
            isFrontCamera = true
        )

        faces.forEach { face ->
            val screenRect = transformer.transform(face.boundingBox)
            drawFaceBox(screenRect, face, textPaint)
        }
    }
}

private fun DrawScope.drawFaceBox(
    rect: RectF,
    face: FaceResult,
    textPaint: Paint
) {
    val boxColor = when {
        // Both eyes open probability above threshold = likely live face
        (face.leftEyeOpenProbability ?: 0f) > 0.6f &&
                (face.rightEyeOpenProbability ?: 0f) > 0.6f -> Color(0xFF00C853) // green
        else -> Color(0xFFFF6D00)                                          // amber
    }

    // Draw bounding box
    drawRect(
        color = boxColor,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width(), rect.height()),
        style = Stroke(width = 3.dp.toPx())
    )

    // Corner accents — the Swiftlane-style UI touch
    val cornerLen = 20.dp.toPx()
    val stroke = Stroke(width = 4.dp.toPx())

    // Top-left corner
    drawLine(boxColor, Offset(rect.left, rect.top + cornerLen), Offset(rect.left, rect.top), strokeWidth = 4.dp.toPx())
    drawLine(boxColor, Offset(rect.left, rect.top), Offset(rect.left + cornerLen, rect.top), strokeWidth = 4.dp.toPx())

    // Top-right corner
    drawLine(boxColor, Offset(rect.right - cornerLen, rect.top), Offset(rect.right, rect.top), strokeWidth = 4.dp.toPx())
    drawLine(boxColor, Offset(rect.right, rect.top), Offset(rect.right, rect.top + cornerLen), strokeWidth = 4.dp.toPx())

    // Bottom-left corner
    drawLine(boxColor, Offset(rect.left, rect.bottom - cornerLen), Offset(rect.left, rect.bottom), strokeWidth = 4.dp.toPx())
    drawLine(boxColor, Offset(rect.left, rect.bottom), Offset(rect.left + cornerLen, rect.bottom), strokeWidth = 4.dp.toPx())

    // Bottom-right corner
    drawLine(boxColor, Offset(rect.right - cornerLen, rect.bottom), Offset(rect.right, rect.bottom), strokeWidth = 4.dp.toPx())
    drawLine(boxColor, Offset(rect.right, rect.bottom), Offset(rect.right, rect.bottom - cornerLen), strokeWidth = 4.dp.toPx())

    // Eye open label below box
    val leftEye  = face.leftEyeOpenProbability
    val rightEye = face.rightEyeOpenProbability
    if (leftEye != null && rightEye != null) {
        val label = "L:${"%.0f".format(leftEye * 100)}%  " +
                "R:${"%.0f".format(rightEye * 100)}%"
        drawContext.canvas.nativeCanvas.drawText(
            label,
            rect.left,
            rect.bottom + 44f,
            textPaint
        )
    }
}