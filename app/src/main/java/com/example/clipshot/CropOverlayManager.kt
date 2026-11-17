package com.example.clipshot

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Smart Samsung-style Crop Overlay:
 * - Tap any UI card, image, or post to smoothly auto-snap the crop box to its exact bounds.
 * - Double-tap in empty space (outside the crop box) for instant Full Screen capture.
 * - Magnetic edge snapping + live circular pixel magnifier loupe while resizing.
 * - Floating draggable bottom action panel with smooth spring entrance animation.
 */
class CropOverlayManager(
    private val context: Context,
    private val fullBitmap: Bitmap,
    private val detectedUrl: String?,
    private val smartBounds: List<RectF> = emptyList(),
    private val onResultBitmap: (Bitmap) -> Unit,
    private val onCopyUrlOnly: (String) -> Unit,
    private val onCancel: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootLayout: FrameLayout? = null

    fun show() {
        val container = FrameLayout(context)

        val bgImage = ImageView(context).apply {
            setImageBitmap(fullBitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val selectionView = CropSelectionView(
            context = context,
            fullBitmap = fullBitmap,
            smartBounds = smartBounds,
            onDoubleTapFullScreen = {
                dismiss()
                onResultBitmap(fullBitmap)
            }
        ).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val topHint = TextView(context).apply {
            text = if (smartBounds.isNotEmpty()) {
                "Tap item to snap • Double-tap outside = Full"
            } else {
                "Drag edges to crop • Double-tap outside = Full"
            }
            textSize = 13f
            setTextColor(Color.parseColor("#F0F6FC"))
            background = roundedPill("#CC161B22", "#388BFD", 28f)
            setPadding(32, 16, 32, 16)
            alpha = 0f
            translationY = -40f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = 110
            }
        }

        val bottomBar = DraggablePanelLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 14, 24, 28)
            background = roundedPill("#EB14181F", "#30363D", 36f)
            elevation = 16f
            alpha = 0f
            translationY = 260f
            scaleX = 0.94f
            scaleY = 0.94f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = 28
                rightMargin = 28
                bottomMargin = 56
            }
        }

        val dragHandle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f
                setColor(Color.parseColor("#586069"))
            }
            layoutParams = LinearLayout.LayoutParams(110, 11).apply {
                bottomMargin = 18
            }
        }
        bottomBar.addView(dragHandle)

        if (!detectedUrl.isNullOrBlank()) {
            val urlBtn = Button(context).apply {
                text = "🔗 Copy Web Link: ${detectedUrl.take(36)}${if (detectedUrl.length > 36) "..." else ""}"
                isAllCaps = false
                textSize = 12f
                setTextColor(Color.parseColor("#58A6FF"))
                background = roundedPill("#162238", "#1F6FEB", 24f)
                setPadding(24, 12, 24, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 14
                }
                setOnClickListener {
                    dismiss()
                    onCopyUrlOnly(detectedUrl)
                }
            }
            bottomBar.addView(urlBtn)
        }

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val cancelBtn = Button(context).apply {
            text = "✕"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#FF6B6B"))
            background = roundedPill("#25171A", "#4A252B", 24f)
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 12
            }
            setOnClickListener {
                dismiss()
                onCancel()
            }
        }

        val fullBtn = Button(context).apply {
            text = "Full Screen"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#F0F6FC"))
            background = roundedPill("#21262D", "#30363D", 24f)
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                rightMargin = 12
            }
            setOnClickListener {
                dismiss()
                onResultBitmap(fullBitmap)
            }
        }

        val cropBtn = Button(context).apply {
            text = "Crop & Copy ✂️"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedPill("#238636", "#2EA043", 24f)
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.2f
            )
            setOnClickListener {
                val cropped = extractCroppedBitmap(selectionView)
                dismiss()
                onResultBitmap(cropped)
            }
        }

        btnRow.addView(cancelBtn)
        btnRow.addView(fullBtn)
        btnRow.addView(cropBtn)
        bottomBar.addView(btnRow)

        container.addView(bgImage)
        container.addView(selectionView)
        container.addView(topHint)
        container.addView(bottomBar)

        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        rootLayout = container
        windowManager.addView(container, params)

        topHint.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        bottomBar.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(340L)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    private fun extractCroppedBitmap(selectionView: CropSelectionView): Bitmap {
        val viewW = selectionView.width.toFloat().coerceAtLeast(1f)
        val viewH = selectionView.height.toFloat().coerceAtLeast(1f)
        val rect = selectionView.getSelectionRect()

        val scaleX = fullBitmap.width / viewW
        val scaleY = fullBitmap.height / viewH

        val left = (rect.left * scaleX).toInt().coerceIn(0, fullBitmap.width - 2)
        val top = (rect.top * scaleY).toInt().coerceIn(0, fullBitmap.height - 2)
        val right = (rect.right * scaleX).toInt().coerceIn(left + 1, fullBitmap.width)
        val bottom = (rect.bottom * scaleY).toInt().coerceIn(top + 1, fullBitmap.height)

        val cropW = (right - left).coerceAtLeast(10)
        val cropH = (bottom - top).coerceAtLeast(10)

        return if (cropW >= fullBitmap.width - 4 && cropH >= fullBitmap.height - 4) {
            fullBitmap
        } else {
            val cropped = Bitmap.createBitmap(fullBitmap, left, top, cropW, cropH)
            fullBitmap.recycle()
            cropped
        }
    }

    fun dismiss() {
        rootLayout?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        rootLayout = null
    }

    private fun roundedPill(fillHex: String, strokeHex: String, radiusPx: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(Color.parseColor(fillHex))
            setStroke(2, Color.parseColor(strokeHex))
        }
    }

    private class DraggablePanelLayout(context: Context) : LinearLayout(context) {
        private var startRawY = 0f
        private var startTransY = 0f

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = ev.rawY
                    startTransY = translationY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(ev.rawY - startRawY) > 16f) {
                        return true
                    }
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val parentH = (parent as? View)?.height?.toFloat() ?: 2000f
            val maxLift = -(parentH - height - 240f).coerceAtLeast(120f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    startTransY = translationY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startRawY
                    translationY = (startTransY + dy).coerceIn(maxLift, 48f)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (translationY > 0f) {
                        animate()
                            .translationY(0f)
                            .setDuration(220L)
                            .setInterpolator(OvershootInterpolator(1.1f))
                            .start()
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private class CropSelectionView(
        context: Context,
        private val fullBitmap: Bitmap,
        private val smartBounds: List<RectF>,
        private val onDoubleTapFullScreen: () -> Unit
    ) : View(context) {

        private val dimPaint = Paint().apply {
            color = Color.parseColor("#99000000")
            style = Paint.Style.FILL
        }
        private val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            isAntiAlias = true
        }
        private val glowPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            alpha = 95
            isAntiAlias = true
        }
        private val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            isAntiAlias = true
        }
        private val cornerArcPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        private val edgePillPaint = Paint().apply {
            color = Color.parseColor("#E6FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        private val loupeBorderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
        }
        private val loupeCrosshairPaint = Paint().apply {
            color = Color.parseColor("#388BFD")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }

        private val cropRect = RectF()
        private var initialized = false

        private var activeMode = MODE_NONE
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var movedDistance = 0f

        private var lastOutsideTapTime = 0L
        private var lastOutsideTapX = 0f
        private var lastOutsideTapY = 0f

        private var showLoupe = false
        private var loupeX = 0f
        private var loupeY = 0f
        private val loupePath = Path()
        private val loupeSrcRect = Rect()
        private val loupeDstRect = RectF()

        private var snapAnimator: ValueAnimator? = null

        companion object {
            private const val MODE_NONE = 0
            private const val MODE_MOVE = 1
            private const val MODE_TL = 2
            private const val MODE_TR = 3
            private const val MODE_BL = 4
            private const val MODE_BR = 5
            private const val MODE_LEFT = 6
            private const val MODE_TOP = 7
            private const val MODE_RIGHT = 8
            private const val MODE_BOTTOM = 9
            private const val MODE_NEW = 10

            private const val CORNER_RADIUS = 52f
            private const val CORNER_HIT = 64f
            private const val EDGE_HIT = 54f
            private const val MIN_SIZE = 120f
            private const val MAGNET_THRESHOLD = 22f
            private const val TAP_SLOP = 18f
            private const val DOUBLE_TAP_TIMEOUT = 320L
            private const val DOUBLE_TAP_SLOP = 70f
            private const val LOUPE_RADIUS = 115f
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                val gradient = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(
                        Color.parseColor("#4285F4"),
                        Color.parseColor("#388BFD"),
                        Color.parseColor("#3FB950"),
                        Color.parseColor("#A371F7")
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                borderPaint.shader = gradient
                glowPaint.shader = gradient

                if (!initialized) {
                    cropRect.set(w * 0.10f, h * 0.24f, w * 0.90f, h * 0.64f)
                    initialized = true
                }
            }
        }

        fun getSelectionRect(): RectF = RectF(cropRect)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            val r = min(CORNER_RADIUS, min(cropRect.width(), cropRect.height()) / 2f)
            canvas.drawRoundRect(cropRect, r, r, clearPaint)

            canvas.drawRoundRect(cropRect, r, r, glowPaint)
            canvas.drawRoundRect(cropRect, r, r, borderPaint)

            val arcBox = r * 2f
            canvas.drawArc(
                cropRect.left, cropRect.top, cropRect.left + arcBox, cropRect.top + arcBox,
                180f, 90f, false, cornerArcPaint
            )
            canvas.drawArc(
                cropRect.right - arcBox, cropRect.top, cropRect.right, cropRect.top + arcBox,
                270f, 90f, false, cornerArcPaint
            )
            canvas.drawArc(
                cropRect.right - arcBox, cropRect.bottom - arcBox, cropRect.right, cropRect.bottom,
                0f, 90f, false, cornerArcPaint
            )
            canvas.drawArc(
                cropRect.left, cropRect.bottom - arcBox, cropRect.left + arcBox, cropRect.bottom,
                90f, 90f, false, cornerArcPaint
            )

            val cx = cropRect.centerX()
            val cy = cropRect.centerY()
            val pillHalfW = min(36f, cropRect.width() * 0.16f)
            val pillHalfH = min(36f, cropRect.height() * 0.16f)

            if (cropRect.width() > MIN_SIZE * 1.2f) {
                canvas.drawLine(cx - pillHalfW, cropRect.top, cx + pillHalfW, cropRect.top, edgePillPaint)
                canvas.drawLine(cx - pillHalfW, cropRect.bottom, cx + pillHalfW, cropRect.bottom, edgePillPaint)
            }
            if (cropRect.height() > MIN_SIZE * 1.2f) {
                canvas.drawLine(cropRect.left, cy - pillHalfH, cropRect.left, cy + pillHalfH, edgePillPaint)
                canvas.drawLine(cropRect.right, cy - pillHalfH, cropRect.right, cy + pillHalfH, edgePillPaint)
            }

            if (showLoupe && width > 0 && height > 0) {
                drawMagnifierLoupe(canvas)
            }
        }

        private fun drawMagnifierLoupe(canvas: Canvas) {
            val scaleX = fullBitmap.width / width.toFloat()
            val scaleY = fullBitmap.height / height.toFloat()

            val bmpX = (loupeX * scaleX).toInt()
            val bmpY = (loupeY * scaleY).toInt()
            val sampleRadiusX = ((LOUPE_RADIUS / 1.9f) * scaleX).toInt().coerceAtLeast(20)
            val sampleRadiusY = ((LOUPE_RADIUS / 1.9f) * scaleY).toInt().coerceAtLeast(20)

            loupeSrcRect.set(
                (bmpX - sampleRadiusX).coerceIn(0, fullBitmap.width - 2),
                (bmpY - sampleRadiusY).coerceIn(0, fullBitmap.height - 2),
                (bmpX + sampleRadiusX).coerceIn(1, fullBitmap.width),
                (bmpY + sampleRadiusY).coerceIn(1, fullBitmap.height)
            )

            val centerX = loupeX.coerceIn(LOUPE_RADIUS + 20f, width - LOUPE_RADIUS - 20f)
            val centerY = if (loupeY > LOUPE_RADIUS * 2.5f + 120f) {
                loupeY - LOUPE_RADIUS - 90f
            } else {
                loupeY + LOUPE_RADIUS + 90f
            }

            loupeDstRect.set(
                centerX - LOUPE_RADIUS,
                centerY - LOUPE_RADIUS,
                centerX + LOUPE_RADIUS,
                centerY + LOUPE_RADIUS
            )

            loupePath.reset()
            loupePath.addCircle(centerX, centerY, LOUPE_RADIUS, Path.Direction.CW)

            canvas.save()
            canvas.clipPath(loupePath)
            canvas.drawBitmap(fullBitmap, loupeSrcRect, loupeDstRect, null)
            canvas.drawLine(centerX - 18f, centerY, centerX + 18f, centerY, loupeCrosshairPaint)
            canvas.drawLine(centerX, centerY - 18f, centerX, centerY + 18f, loupeCrosshairPaint)
            canvas.restore()

            canvas.drawCircle(centerX, centerY, LOUPE_RADIUS, loupeBorderPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x.coerceIn(0f, width.toFloat())
            val y = event.y.coerceIn(0f, height.toFloat())

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    downX = x
                    downY = y
                    lastX = x
                    lastY = y
                    movedDistance = 0f

                    activeMode = when {
                        near(x, y, cropRect.left, cropRect.top) -> MODE_TL
                        near(x, y, cropRect.right, cropRect.top) -> MODE_TR
                        near(x, y, cropRect.left, cropRect.bottom) -> MODE_BL
                        near(x, y, cropRect.right, cropRect.bottom) -> MODE_BR
                        abs(x - cropRect.left) <= EDGE_HIT && y in (cropRect.top..cropRect.bottom) -> MODE_LEFT
                        abs(x - cropRect.right) <= EDGE_HIT && y in (cropRect.top..cropRect.bottom) -> MODE_RIGHT
                        abs(y - cropRect.top) <= EDGE_HIT && x in (cropRect.left..cropRect.right) -> MODE_TOP
                        abs(y - cropRect.bottom) <= EDGE_HIT && x in (cropRect.left..cropRect.right) -> MODE_BOTTOM
                        cropRect.contains(x, y) -> MODE_MOVE
                        else -> MODE_NEW
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    movedDistance = hypot((x - downX).toDouble(), (y - downY).toDouble()).toFloat()
                    if (activeMode == MODE_NEW && movedDistance <= TAP_SLOP) {
                        return true
                    }

                    val dx = x - lastX
                    val dy = y - lastY

                    showLoupe = activeMode in setOf(
                        MODE_TL, MODE_TR, MODE_BL, MODE_BR,
                        MODE_LEFT, MODE_RIGHT, MODE_TOP, MODE_BOTTOM
                    )
                    loupeX = x
                    loupeY = y

                    when (activeMode) {
                        MODE_TL -> {
                            cropRect.left = snapX(min(x, cropRect.right - MIN_SIZE))
                            cropRect.top = snapY(min(y, cropRect.bottom - MIN_SIZE))
                        }
                        MODE_TR -> {
                            cropRect.right = snapX(max(x, cropRect.left + MIN_SIZE))
                            cropRect.top = snapY(min(y, cropRect.bottom - MIN_SIZE))
                        }
                        MODE_BL -> {
                            cropRect.left = snapX(min(x, cropRect.right - MIN_SIZE))
                            cropRect.bottom = snapY(max(y, cropRect.top + MIN_SIZE))
                        }
                        MODE_BR -> {
                            cropRect.right = snapX(max(x, cropRect.left + MIN_SIZE))
                            cropRect.bottom = snapY(max(y, cropRect.top + MIN_SIZE))
                        }
                        MODE_LEFT -> {
                            cropRect.left = snapX(min(x, cropRect.right - MIN_SIZE))
                        }
                        MODE_RIGHT -> {
                            cropRect.right = snapX(max(x, cropRect.left + MIN_SIZE))
                        }
                        MODE_TOP -> {
                            cropRect.top = snapY(min(y, cropRect.bottom - MIN_SIZE))
                        }
                        MODE_BOTTOM -> {
                            cropRect.bottom = snapY(max(y, cropRect.top + MIN_SIZE))
                        }
                        MODE_MOVE -> {
                            val w = cropRect.width()
                            val h = cropRect.height()
                            val nl = (cropRect.left + dx).coerceIn(0f, (width - w).coerceAtLeast(0f))
                            val nt = (cropRect.top + dy).coerceIn(0f, (height - h).coerceAtLeast(0f))
                            cropRect.set(nl, nt, nl + w, nt + h)
                        }
                        MODE_NEW -> {
                            cropRect.left = min(downX, x)
                            cropRect.top = min(downY, y)
                            cropRect.right = max(downX, x)
                            cropRect.bottom = max(downY, y)
                        }
                    }
                    if (activeMode != MODE_NEW) {
                        lastX = x
                        lastY = y
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    showLoupe = false

                    if (movedDistance <= TAP_SLOP) {
                        val now = System.currentTimeMillis()
                        val tapDist = hypot((x - lastOutsideTapX).toDouble(), (y - lastOutsideTapY).toDouble()).toFloat()

                        // Double-tap in empty space (outside the crop box) -> Instant Full Screen!
                        if (activeMode == MODE_NEW || (now - lastOutsideTapTime <= DOUBLE_TAP_TIMEOUT && tapDist <= DOUBLE_TAP_SLOP)) {
                            if (now - lastOutsideTapTime <= DOUBLE_TAP_TIMEOUT && tapDist <= DOUBLE_TAP_SLOP) {
                                lastOutsideTapTime = 0L
                                activeMode = MODE_NONE
                                onDoubleTapFullScreen()
                                return true
                            } else if (activeMode == MODE_NEW) {
                                lastOutsideTapTime = now
                                lastOutsideTapX = x
                                lastOutsideTapY = y
                            }
                        }

                        if (smartBounds.isNotEmpty()) {
                            val target = findBestSmartTarget(x, y)
                            if (target != null) {
                                activeMode = MODE_NONE
                                animateCropTo(target)
                                return true
                            }
                        }
                    }

                    if (cropRect.width() < MIN_SIZE) {
                        cropRect.right = min(width.toFloat(), cropRect.left + MIN_SIZE)
                    }
                    if (cropRect.height() < MIN_SIZE) {
                        cropRect.bottom = min(height.toFloat(), cropRect.top + MIN_SIZE)
                    }
                    activeMode = MODE_NONE
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun findBestSmartTarget(x: Float, y: Float): RectF? {
            val candidates = smartBounds
                .filter { it.contains(x, y) }
                .sortedBy { it.width() * it.height() }

            if (candidates.isEmpty()) return null
            for (cand in candidates) {
                val sameAsCurrent = abs(cand.left - cropRect.left) < 12f &&
                    abs(cand.top - cropRect.top) < 12f &&
                    abs(cand.right - cropRect.right) < 12f &&
                    abs(cand.bottom - cropRect.bottom) < 12f
                if (!sameAsCurrent) return cand
            }
            return candidates.first()
        }

        private fun animateCropTo(target: RectF) {
            val start = RectF(cropRect)
            snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 190L
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val t = anim.animatedValue as Float
                    cropRect.set(
                        start.left + (target.left - start.left) * t,
                        start.top + (target.top - start.top) * t,
                        start.right + (target.right - start.right) * t,
                        start.bottom + (target.bottom - start.bottom) * t
                    )
                    invalidate()
                }
                start()
            }
        }

        private fun snapX(rawX: Float): Float {
            for (b in smartBounds) {
                if (abs(rawX - b.left) <= MAGNET_THRESHOLD) return b.left
                if (abs(rawX - b.right) <= MAGNET_THRESHOLD) return b.right
            }
            return rawX
        }

        private fun snapY(rawY: Float): Float {
            for (b in smartBounds) {
                if (abs(rawY - b.top) <= MAGNET_THRESHOLD) return b.top
                if (abs(rawY - b.bottom) <= MAGNET_THRESHOLD) return b.bottom
            }
            return rawY
        }

        private fun near(x: Float, y: Float, cx: Float, cy: Float): Boolean {
            return abs(x - cx) <= CORNER_HIT && abs(y - cy) <= CORNER_HIT
        }
    }
}
