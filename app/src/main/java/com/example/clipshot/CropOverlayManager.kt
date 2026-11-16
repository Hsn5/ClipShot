package com.example.clipshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
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
 * Floating TYPE_ACCESSIBILITY_OVERLAY crop editor.
 * Runs directly on top of the active app with ZERO Activity launch and ZERO Home Screen exit!
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
            text = "Drag to select area • Tap Full for entire screen"
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

        // Optional URL Pill when a browser page URL is detected
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
        }
        private val borderPaint = Paint().apply {
            color = Color.parseColor("#388BFD")
            style = Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
        }
        private val handlePaint = Paint().apply {
            color = Color.parseColor("#3FB950")
            style = Paint.Style.FILL
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
            private const val MODE_NEW = 6
            private const val HANDLE_RADIUS = 48f
            private const val MIN_SIZE = 100f
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (!initialized && w > 0 && h > 0) {
                cropRect.set(w * 0.12f, h * 0.22f, w * 0.88f, h * 0.68f)
                initialized = true
            }
        }

        fun getSelectionRect(): RectF = RectF(cropRect)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
            canvas.drawRect(cropRect, clearPaint)
            canvas.drawRect(cropRect, borderPaint)

            val r = 14f
            canvas.drawCircle(cropRect.left, cropRect.top, r, handlePaint)
            canvas.drawCircle(cropRect.right, cropRect.top, r, handlePaint)
            canvas.drawCircle(cropRect.left, cropRect.bottom, r, handlePaint)
            canvas.drawCircle(cropRect.right, cropRect.bottom, r, handlePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x.coerceIn(0f, width.toFloat())
            val y = event.y.coerceIn(0f, height.toFloat())

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = x
                    lastY = y
                    activeMode = when {
                        near(x, y, cropRect.left, cropRect.top) -> MODE_TL
                        near(x, y, cropRect.right, cropRect.top) -> MODE_TR
                        near(x, y, cropRect.left, cropRect.bottom) -> MODE_BL
                        near(x, y, cropRect.right, cropRect.bottom) -> MODE_BR
                        cropRect.contains(x, y) -> MODE_MOVE
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
                        MODE_MOVE -> {
                            val w = cropRect.width()
                            val h = cropRect.height()
                            var nl = (cropRect.left + dx).coerceIn(0f, width - w)
                            var nt = (cropRect.top + dy).coerceIn(0f, height - h)
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
            return abs(x - cx) <= HANDLE_RADIUS && abs(y - cy) <= HANDLE_RADIUS
        }
    }
}
