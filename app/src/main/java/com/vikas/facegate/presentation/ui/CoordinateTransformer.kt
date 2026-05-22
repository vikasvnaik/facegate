package com.vikas.facegate.presentation.ui

import android.graphics.RectF

/**
 * Transforms face bounding boxes from camera image space
 * to screen/canvas space.
 *
 * @param imageWidth  width of the YUV image from Camera2 (e.g. 640)
 * @param imageHeight height of the YUV image from Camera2 (e.g. 480)
 * @param screenWidth  actual canvas/view width in pixels
 * @param screenHeight actual canvas/view height in pixels
 * @param isFrontCamera true = mirror X axis (selfie camera)
 */
class CoordinateTransformer(
    private val imageWidth: Int   = 640,
    private val imageHeight: Int  = 480,
    private val screenWidth: Float,
    private val screenHeight: Float,
    private val isFrontCamera: Boolean = true
) {
    // Camera frame is rotated 90° in portrait mode.
    // That's why width and height are SWAPPED in the scale:
    //   scaleX maps camera HEIGHT → screen WIDTH
    //   scaleY maps camera WIDTH  → screen HEIGHT
    private val scaleX: Float = screenWidth  / imageHeight
    private val scaleY: Float = screenHeight / imageWidth

    fun transform(rect: RectF): RectF {
        val scaledLeft   = rect.left   * scaleX
        val scaledTop    = rect.top    * scaleY
        val scaledRight  = rect.right  * scaleX
        val scaledBottom = rect.bottom * scaleY

        return if (isFrontCamera) {
            // Mirror: flip X around screen centre
            // left  becomes screenWidth - scaledRight
            // right becomes screenWidth - scaledLeft
            RectF(
                screenWidth - scaledRight,  // mirrored left
                scaledTop,
                screenWidth - scaledLeft,   // mirrored right
                scaledBottom
            )
        } else {
            RectF(scaledLeft, scaledTop, scaledRight, scaledBottom)
        }
    }
}