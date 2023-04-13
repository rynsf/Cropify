package io.moyuru.cropify

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.lang.Float.max
import java.lang.Float.min

@Composable
fun Cropify(
  state: CropifyState,
  modifier: Modifier = Modifier,
  option: CropifyOption = CropifyOption()
) {
  val density = LocalDensity.current
  val tolerance = remember { density.run { 24.dp.toPx() } }
  var touchRegion = remember<TouchRegion?> { null }

  BoxWithConstraints(
    modifier = modifier
      .pointerInput(state.bitmap, option.frameAspectRatio) {
        detectDragGestures(
          onDragStart = { touchRegion = detectTouchRegion(it, state.frameRect, tolerance) },
          onDragEnd = { touchRegion = null }
        ) { change, dragAmount ->
          touchRegion?.let {
            when (it) {
              is TouchRegion.Vertex -> state.scaleFrameRect(it, option.frameAspectRatio, dragAmount, tolerance * 2)
              TouchRegion.Inside -> state.translateFrameRect(dragAmount)
            }
            change.consume()
          }
        }
      }
  ) {
    LaunchedEffect(state.bitmap, option.frameAspectRatio, constraints) {
      val canvasSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
      state.imageRect = calculateImageDst(state.bitmap, canvasSize)
      state.frameRect = calculateFrameRect(state.imageRect, canvasSize, option.frameAspectRatio)
    }
    ImageCanvas(
      bitmap = state.bitmap,
      offset = state.imageRect.topLeft,
      size = state.imageRect.size,
      option = option,
      modifier = Modifier.matchParentSize(),
    )
    ImageOverlay(
      offset = state.frameRect.topLeft,
      size = state.frameRect.size,
      tolerance = tolerance,
      option = option,
      modifier = Modifier.matchParentSize()
    )
  }
}

internal fun calculateImageDst(bitmap: ImageBitmap, canvasSize: Size): Rect {
  val size = calculateImageSize(bitmap.width, bitmap.height, canvasSize)
  return calculateImagePosition(size, canvasSize)
}

internal fun calculateImagePosition(imageSize: Size, canvasSize: Size): Rect {
  return Rect(
    Offset((canvasSize.width - imageSize.width) / 2, (canvasSize.height - imageSize.height) / 2),
    imageSize
  )
}

internal fun calculateImageSize(bitmapWidth: Int, bitmapHeight: Int, canvasSize: Size): Size {
  return if (bitmapWidth > bitmapHeight)
    Size(canvasSize.width, canvasSize.width * bitmapHeight / bitmapWidth.toFloat())
  else
    Size(canvasSize.height * bitmapWidth / bitmapHeight.toFloat(), canvasSize.height)
}

internal fun calculateFrameRect(
  imageRect: Rect,
  canvasSize: Size,
  frameAspectRatio: AspectRatio?,
): Rect {
  val shortSide = min(imageRect.width, imageRect.height)
  return if (frameAspectRatio == null) {
    Rect(center = imageRect.center, radius = shortSide * 0.8f / 2)
  } else {
    val scale = shortSide / max(imageRect.width, imageRect.width * frameAspectRatio.value)
    val size = Size(imageRect.width * scale * 0.8f, imageRect.width * scale * frameAspectRatio.value * 0.8f)
    Rect(Offset((canvasSize.width - size.width) / 2, (canvasSize.height - size.height) / 2), size)
  }
}

internal fun detectTouchRegion(tapPosition: Offset, frameRect: Rect, tolerance: Float): TouchRegion? {
  return when {
    Rect(frameRect.topLeft, tolerance).contains(tapPosition) -> TouchRegion.Vertex.TOP_LEFT
    Rect(frameRect.topRight, tolerance).contains(tapPosition) -> TouchRegion.Vertex.TOP_RIGHT
    Rect(frameRect.bottomLeft, tolerance).contains(tapPosition) -> TouchRegion.Vertex.BOTTOM_LEFT
    Rect(frameRect.bottomRight, tolerance).contains(tapPosition) -> TouchRegion.Vertex.BOTTOM_RIGHT
    Rect(frameRect.center, frameRect.width / 2 - tolerance).contains(tapPosition) -> TouchRegion.Inside
    else -> null
  }
}