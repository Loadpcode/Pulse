package com.xraypulse.app.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.xraypulse.app.model.ButtonShapeParams
import com.xraypulse.app.model.ConnectButtonShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

fun ConnectButtonShape.toComposeShape(
    params: ButtonShapeParams = ButtonShapeParams()
): Shape {
    val safe = params.coerced()
    return when (this) {
        ConnectButtonShape.SQUARE -> buildSquareShape(side = safe.squareSide)
        ConnectButtonShape.ROUNDED_SQUARE -> buildRoundedSquareShape(
            side = safe.roundedSquareSide,
            cornerRadius = safe.roundedSquareRadius
        )
        ConnectButtonShape.CIRCLE -> buildCircleShape(radius = safe.circleRadius)
        ConnectButtonShape.SQUIRCLE -> buildSquircleShape(
            radius = safe.squircleRadius,
            power = safe.squirclePower
        )
        ConnectButtonShape.COOKIE_4 -> buildCookieShape(
            lobes = 4,
            radius = safe.cookie4Radius,
            notchDepth = safe.cookie4Depth,
            steps = 128
        )
        ConnectButtonShape.COOKIE_6 -> buildCookieShape(
            lobes = 6,
            radius = safe.cookie6Radius,
            notchDepth = safe.cookie6Depth,
            steps = 144
        )
        ConnectButtonShape.COOKIE_9 -> buildCookieShape(
            lobes = 9,
            radius = safe.cookie9Radius,
            notchDepth = safe.cookie9Depth,
            steps = 180
        )
        ConnectButtonShape.ARCH -> buildArchShape(
            width = safe.archWidth,
            straightHeight = safe.archHeight
        )
        ConnectButtonShape.PILL -> buildPillShape(
            width = safe.pillWidth,
            height = safe.pillHeight
        )
        ConnectButtonShape.OVAL -> buildOvalShape(
            radiusX = safe.ovalRadiusX,
            radiusY = safe.ovalRadiusY
        )
    }
}

/**
 * Caches a precomputed unit-square [Path] (centered around (0, 0) within [-0.5, 0.5])
 * and caches the resulting [Outline.Generic] for the most recent [Size], avoiding
 * per-frame trigonometry and Path allocations during rotation or recomposition.
 */
private class CachedUnitPathShape(
    buildUnitPath: Path.() -> Unit
) : Shape {
    private val unitPath = Path().apply(buildUnitPath)
    private val scaleMatrix = Matrix()
    private var cachedWidth: Float = Float.NaN
    private var cachedHeight: Float = Float.NaN
    private var cachedOutline: Outline.Generic? = null

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val current = cachedOutline
        if (current != null && size.width == cachedWidth && size.height == cachedHeight) {
            return current
        }
        return synchronized(this) {
            val rechecked = cachedOutline
            if (rechecked != null && size.width == cachedWidth && size.height == cachedHeight) {
                return@synchronized rechecked
            }
            val minDim = min(size.width, size.height).coerceAtLeast(1f)
            scaleMatrix.reset()
            scaleMatrix.translate(size.width * 0.5f, size.height * 0.5f)
            scaleMatrix.scale(minDim, minDim, 1f)
            val scaledPath = Path().apply {
                addPath(unitPath)
                transform(scaleMatrix)
            }
            Outline.Generic(scaledPath).also {
                cachedWidth = size.width
                cachedHeight = size.height
                cachedOutline = it
            }
        }
    }
}

// 1. Square: -s/2 <= x <= s/2, -s/2 <= y <= s/2
private fun buildSquareShape(side: Float): Shape = CachedUnitPathShape {
    val half = side * 0.5f
    addRect(Rect(-half, -half, half, half))
}

// 2. Rounded Square: bounds [-s/2, s/2], corners (x - cx)^2 + (y - cy)^2 = r^2, cx, cy in {-s/2 + r, s/2 - r}
private fun buildRoundedSquareShape(side: Float, cornerRadius: Float): Shape = CachedUnitPathShape {
    val half = side * 0.5f
    val r = cornerRadius.coerceIn(0.02f, half * 0.98f)
    moveTo(-half + r, -half)
    lineTo(half - r, -half)
    arcTo(
        rect = Rect(half - 2f * r, -half, half, -half + 2f * r),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    lineTo(half, half - r)
    arcTo(
        rect = Rect(half - 2f * r, half - 2f * r, half, half),
        startAngleDegrees = 0f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    lineTo(-half + r, half)
    arcTo(
        rect = Rect(-half, half - 2f * r, -half + 2f * r, half),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    lineTo(-half, -half + r)
    arcTo(
        rect = Rect(-half, -half, -half + 2f * r, -half + 2f * r),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    close()
}

// 3. Circle: x(t) = R * cos(t), y(t) = R * sin(t), 0 <= t <= 2*PI
private fun buildCircleShape(radius: Float): Shape = CachedUnitPathShape {
    addOval(Rect(-radius, -radius, radius, radius))
}

// 4. Squircle: |x|^n + |y|^n = R^n
// x(t) = R * sign(cos(t)) * |cos(t)|^(2/n), y(t) = R * sign(sin(t)) * |sin(t)|^(2/n)
private fun buildSquircleShape(radius: Float, power: Float): Shape = CachedUnitPathShape {
    val r = radius.toDouble()
    val exp = 2.0 / power.toDouble().coerceAtLeast(1.0)
    val steps = 128
    for (i in 0 until steps) {
        val t = (2.0 * PI * i) / steps
        val c = cos(t)
        val s = sin(t)
        val x = (r * sign(c) * abs(c).pow(exp)).toFloat()
        val y = (r * sign(s) * abs(s).pow(exp)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

// 5, 6, 7. Cookie k: r(theta) = R - a/2 * (1 - cos(k * theta))
private fun buildCookieShape(
    lobes: Int,
    radius: Float,
    notchDepth: Float,
    steps: Int
): Shape = CachedUnitPathShape {
    val rMax = radius.toDouble()
    val halfA = notchDepth.toDouble() * 0.5
    for (i in 0 until steps) {
        val theta = (2.0 * PI * i) / steps
        val r = rMax - halfA * (1.0 - cos(lobes * theta))
        val x = (r * cos(theta)).toFloat()
        val y = (r * sin(theta)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

// 8. Arch: width w, straight height h, r = w/2, top semicircle x^2 + (y - h)^2 = r^2, H = h + r
private fun buildArchShape(width: Float, straightHeight: Float): Shape = CachedUnitPathShape {
    val r = width * 0.5f
    val h = straightHeight.coerceIn(0.12f, (0.96f - r).coerceAtLeast(0.12f))
    val totalH = h + r
    val top = -totalH * 0.5f
    val bottom = totalH * 0.5f
    val joinY = top + r

    moveTo(-r, joinY)
    arcTo(
        rect = Rect(-r, top, r, top + 2f * r),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false
    )
    lineTo(r, bottom)
    lineTo(-r, bottom)
    close()
}

// 9. Pill: w >= h, r = h/2, left cap (x + w/2 - r)^2 + y^2 = r^2, right cap (x - w/2 + r)^2 + y^2 = r^2
private fun buildPillShape(width: Float, height: Float): Shape = CachedUnitPathShape {
    val safeH = height.coerceIn(0.24f, width)
    val r = safeH * 0.5f
    val halfW = width * 0.5f

    moveTo(-halfW + r, -r)
    lineTo(halfW - r, -r)
    arcTo(
        rect = Rect(halfW - 2f * r, -r, halfW, r),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false
    )
    lineTo(-halfW + r, r)
    arcTo(
        rect = Rect(-halfW, -r, -halfW + 2f * r, r),
        startAngleDegrees = 90f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false
    )
    close()
}

// 10. Oval: x^2 / a^2 + y^2 / b^2 = 1; x(t) = a * cos(t), y(t) = b * sin(t)
private fun buildOvalShape(radiusX: Float, radiusY: Float): Shape = CachedUnitPathShape {
    addOval(Rect(-radiusX, -radiusY, radiusX, radiusY))
}
