package com.example.clipshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Floating TYPE_ACCESSIBILITY_OVERLAY crop editor styled like Circle to Search:
 * - Rounded glowing gradient frame with corner arcs and edge pill handles.
 * - Smooth resizing from all 4 edges (sides) AND all 4 corners, plus dragging from inside.
 */
class CropOverlayManager(
    private val context: Context,
    private val fullBitmap: Bitmap,
    private val detectedUrl: String?,
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

        val selectionView = CropSelectionView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Top Instruction Pill
        val topHint = TextView(context).apply {
            text = "Drag corners, edges, or draw anywhere"
            textSize = 13f
            setTextColor(Color.parseColor("#F0F6FC"))
            background = roundedPill("#CC161B22", "#388BFD")
            setPadding(32, 16, 32, 16)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = 110
            }
        }

        // Bottom Floating Action Bar
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 20, 24, 36)
            background = roundedPill("#E6121212", "#30363D")
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = 24
                rightMargin = 24
                bottomMargin = 56
            }
        }

        if (!detectedUrl.isNullOrBlank()) {
            val urlBtn = Button(context).apply {
                text = "🔗 Copy Web Link: ${detectedUrl.take(36)}${if (detectedUrl.length > 36) "..." else ""}"
                isAllCaps = false
                textSize = 12f
                setTextColor(Color.parseColor("#58A6FF"))
                background = roundedPill("#162238", "#1F6FEB")
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
            background = roundedPill("#25171A", "#4A252B")
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
            background = roundedPill("#21262D", "#30363D")
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
            background = roundedPill("#238636", "#2EA043")
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

    private fun roundedPill(fillHex: String, strokeHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f
            setColor(Color.parseColor(fillHex))
            setStroke(2, Color.parseColor(strokeHex))
        }
    }

    private class CropSelectionView(context: Context) : View(context) {
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
            alpha = 90
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

        private val cropRect = RectF()
        private var initialized = false

        private var activeMode = MODE_NONE
        private var lastX = 0f
        private var lastY = 0f

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
            private const val CORNER_HIT = 60f
            private const val EDGE_HIT = 52f
            private const val MIN_SIZE = 120f
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
            // 1. Darken outside area
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            // 2. Cut out rounded Circle-to-Search style window
            val r = min(CORNER_RADIUS, min(cropRect.width(), cropRect.height()) / 2f)
            canvas.drawRoundRect(cropRect, r, r, clearPaint)

            // 3. Soft gradient outer glow + crisp gradient border
            canvas.drawRoundRect(cropRect, r, r, glowPaint)
            canvas.drawRoundRect(cropRect, r, r, borderPaint)

            // 4. Crisp white corner arcs (Circle to Search / Smart Select style)
            val arcBox = r * 2f
            val arcSweep = 90f
            // Top-Left
            canvas.drawArc(
                cropRect.left, cropRect.top, cropRect.left + arcBox, cropRect.top + arcBox,
                180f, arcSweep, false, cornerArcPaint
            )
            // Top-Right
            canvas.drawArc(
                cropRect.right - arcBox, cropRect.top, cropRect.right, cropRect.top + arcBox,
                270f, arcSweep, false, cornerArcPaint
            )
            // Bottom-Right
            canvas.drawArc(
                cropRect.right - arcBox, cropRect.bottom - arcBox, cropRect.right, cropRect.bottom,
                0f, arcSweep, false, cornerArcPaint
            )
            // Bottom-Left
            canvas.drawArc(
                cropRect.left, cropRect.bottom - arcBox, cropRect.left + arcBox, cropRect.bottom,
                90f, arcSweep, false, cornerArcPaint
            )

            // 5. Subtle edge pill handles so dragging from any side is intuitive
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
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x.coerceIn(0f, width.toFloat())
            val y = event.y.coerceIn(0f, height.toFloat())

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = x
                    lastY = y
                    activeMode = when {
                        // Corners first
                        near(x, y, cropRect.left, cropRect.top) -> MODE_TL
                        near(x, y, cropRect.right, cropRect.top) -> MODE_TR
                        near(x, y, cropRect.left, cropRect.bottom) -> MODE_BL
                        near(x, y, cropRect.right, cropRect.bottom) -> MODE_BR
                        // Edges (ضلوع المستطيل)
                        abs(x - cropRect.left) <= EDGE_HIT && y in (cropRect.top..cropRect.bottom) -> MODE_LEFT
                        abs(x - cropRect.right) <= EDGE_HIT && y in (cropRect.top..cropRect.bottom) -> MODE_RIGHT
                        abs(y - cropRect.top) <= EDGE_HIT && x in (cropRect.left..cropRect.right) -> MODE_TOP
                        abs(y - cropRect.bottom) <= EDGE_HIT && x in (cropRect.left..cropRect.right) -> MODE_BOTTOM
                        // Inside body -> move entire frame
                        cropRect.contains(x, y) -> MODE_MOVE
                        // Outside -> draw new frame
                        else -> {
                            cropRect.set(x, y, x + 1f, y + 1f)
                            MODE_NEW
                        }
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = x - lastX
                    val dy = y - lastY
                    when (activeMode) {
                        MODE_TL -> {
                            cropRect.left = min(x, cropRect.right - MIN_SIZE)
                            cropRect.top = min(y, cropRect.bottom - MIN_SIZE)
                        }
                        MODE_TR -> {
                            cropRect.right = max(x, cropRect.left + MIN_SIZE)
                            cropRect.top = min(y, cropRect.bottom - MIN_SIZE)
                        }
                        MODE_BL -> {
                            cropRect.left = min(x, cropRect.right - MIN_SIZE)
                            cropRect.bottom = max(y, cropRect.top + MIN_SIZE)
                        }
                        MODE_BR -> {
                            cropRect.right = max(x, cropRect.left + MIN_SIZE)
                            cropRect.bottom = max(y, cropRect.top + MIN_SIZE)
                        }
                        MODE_LEFT -> {
                            cropRect.left = min(x, cropRect.right - MIN_SIZE)
                        }
                        MODE_RIGHT -> {
                            cropRect.right = max(x, cropRect.left + MIN_SIZE)
                        }
                        MODE_TOP -> {
                            cropRect.top = min(y, cropRect.bottom - MIN_SIZE)
                        }
                        MODE_BOTTOM -> {
                            cropRect.bottom = max(y, cropRect.top + MIN_SIZE)
                        }
                        MODE_MOVE -> {
                            val w = cropRect.width()
                            val h = cropRect.height()
                            val nl = (cropRect.left + dx).coerceIn(0f, (width - w).coerceAtLeast(0f))
                            val nt = (cropRect.top + dy).coerceIn(0f, (height - h).coerceAtLeast(0f))
                            cropRect.set(nl, nt, nl + w, nt + h)
                        }
                        MODE_NEW -> {
                            cropRect.left = min(lastX, x)
                            cropRect.top = min(lastY, y)
                            cropRect.right = max(lastX, x)
                            cropRect.bottom = max(lastY, y)
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

        private fun near(x: Float, y: Float, cx: Float, cy: Float): Boolean {
            return abs(x - cx) <= CORNER_HIT && abs(y - cy) <= CORNER_HIT
        }
    }
}
