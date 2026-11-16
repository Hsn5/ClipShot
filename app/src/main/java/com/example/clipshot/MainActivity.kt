package com.example.clipshot

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class VaultItem(
    val id: String,
    val fileName: String,
    val timestamp: Long,
    var isPinned: Boolean,
    val sourceUrl: String? = null
) {
    fun getRemainingMillis(now: Long = System.currentTimeMillis()): Long {
        val expiry = timestamp + TimeUnit.HOURS.toMillis(24)
        return (expiry - now).coerceAtLeast(0L)
    }

    fun getFormattedStatus(now: Long = System.currentTimeMillis()): String {
        if (isPinned) return "📌 Pinned (Kept Forever)"
        val rem = getRemainingMillis(now)
        if (rem <= 0L) return "Expired"
        val hours = TimeUnit.MILLISECONDS.toHours(rem)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(rem) % 60
        return if (hours > 0) {
            "⏳ Expires in ${hours}h ${minutes}m"
        } else {
            "⏳ Expires in ${minutes}m"
        }
    }
}

object ScreenshotVaultManager {
    private const val PREFS_NAME = "clipshot_vault_prefs"
    private const val KEY_ITEMS = "vault_items_json"
    private val TTL_MILLIS = TimeUnit.HOURS.toMillis(24)

    private fun getVaultDir(context: Context): File {
        val dir = File(context.cacheDir, "screenshots")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()
        return dir
    }

    @Synchronized
    fun purgeExpired(context: Context): List<VaultItem> {
        val now = System.currentTimeMillis()
        val vaultDir = getVaultDir(context)
        val allItems = loadRawItems(context)
        val validItems = mutableListOf<VaultItem>()
        var changed = false

        for (item in allItems) {
            val file = File(vaultDir, item.fileName)
            val isExpired = !item.isPinned && (now - item.timestamp >= TTL_MILLIS)
            if (isExpired || !file.exists()) {
                if (file.exists()) file.delete()
                changed = true
            } else {
                validItems.add(item)
            }
        }

        val trackedNames = validItems.map { it.fileName }.toSet()
        vaultDir.listFiles()?.forEach { f ->
            if (f.name != ".nomedia" && f.name !in trackedNames) {
                f.delete()
            }
        }

        if (changed) {
            saveRawItems(context, validItems)
        }
        return validItems.sortedByDescending { it.timestamp }
    }

    @Synchronized
    fun saveAndCopyToClipboard(context: Context, bitmap: Bitmap, sourceUrl: String? = null): VaultItem {
        purgeExpired(context)

        val now = System.currentTimeMillis()
        val id = now.toString()
        val fileName = "clip_$id.png"
        val vaultDir = getVaultDir(context)
        val file = File(vaultDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        val newItem = VaultItem(
            id = id,
            fileName = fileName,
            timestamp = now,
            isPinned = false,
            sourceUrl = sourceUrl
        )

        val items = loadRawItems(context).toMutableList()
        items.add(0, newItem)
        saveRawItems(context, items)

        copyFileToClipboard(context, file)
        return newItem
    }

    @Synchronized
    fun togglePin(context: Context, id: String): Boolean {
        val items = loadRawItems(context).toMutableList()
        val target = items.find { it.id == id } ?: return false
        target.isPinned = !target.isPinned
        saveRawItems(context, items)
        return target.isPinned
    }

    @Synchronized
    fun deleteItem(context: Context, id: String) {
        val vaultDir = getVaultDir(context)
        val items = loadRawItems(context).toMutableList()
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.id == id) {
                File(vaultDir, item.fileName).delete()
                iterator.remove()
            }
        }
        saveRawItems(context, items)
    }

    @Synchronized
    fun clearUnpinned(context: Context) {
        val vaultDir = getVaultDir(context)
        val items = loadRawItems(context).toMutableList()
        val remaining = mutableListOf<VaultItem>()
        for (item in items) {
            if (item.isPinned) {
                remaining.add(item)
            } else {
                File(vaultDir, item.fileName).delete()
            }
        }
        saveRawItems(context, remaining)
    }

    fun getFileForItem(context: Context, item: VaultItem): File {
        return File(getVaultDir(context), item.fileName)
    }

    fun copyItemToClipboard(context: Context, item: VaultItem): Boolean {
        val file = getFileForItem(context, item)
        if (!file.exists()) return false
        copyFileToClipboard(context, file)
        return true
    }

    fun copyTextToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Web URL", text))
    }

    fun saveToGallery(context: Context, item: VaultItem): Boolean {
        val file = getFileForItem(context, item)
        if (!file.exists()) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "ClipShot_${item.id}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClipShot")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false

            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun copyFileToClipboard(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.applicationContext.packageName}.fileprovider",
            file
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(context.contentResolver, "Screenshot", uri)
        clipboard.setPrimaryClip(clip)
    }

    private fun loadRawItems(context: Context): List<VaultItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val list = mutableListOf<VaultItem>()
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VaultItem(
                        id = obj.getString("id"),
                        fileName = obj.getString("fileName"),
                        timestamp = obj.getLong("timestamp"),
                        isPinned = obj.optBoolean("isPinned", false),
                        sourceUrl = obj.optString("sourceUrl", "").ifEmpty { null }
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveRawItems(context: Context, items: List<VaultItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("fileName", item.fileName)
                put("timestamp", item.timestamp)
                put("isPinned", item.isPinned)
                if (item.sourceUrl != null) put("sourceUrl", item.sourceUrl)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, arr.toString())
            .apply()
    }
}

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var vaultCountText: TextView
    private lateinit var vaultContainer: LinearLayout

    private val vaultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshVaultList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 56, 48, 64)
        }

        val titleText = TextView(this).apply {
            text = "ClipShot v7.0"
            textSize = 28f
            setTextColor(Color.parseColor("#FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }

        val subTitleText = TextView(this).apply {
            text = "Crop • Web URL • 24-Hour Private Vault"
            textSize = 14f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 36)
        }

        statusText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            background = roundedBg("#1E1E1E", "#2D2D2D", 24f)
            setPadding(32, 28, 32, 28)
        }

        val spacer1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 24)
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        val enableBtn = Button(this).apply {
            text = "Accessibility"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#238636", "#2EA043", 20f)
            setPadding(20, 24, 20, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 12
            }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val testBtn = Button(this).apply {
            text = "Capture & Crop"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#1F6FEB", "#388BFD", 20f)
            setPadding(20, 24, 20, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 12
            }
            setOnClickListener {
                if (ScreenshotAccessibilityService.instance != null) {
                    ScreenshotAccessibilityService.captureNow(delayMs = 200L, openCropOverlay = true)
                } else {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }

        buttonsRow.addView(enableBtn)
        buttonsRow.addView(testBtn)

        val vaultHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 48, 0, 20)
        }

        vaultCountText = TextView(this).apply {
            text = "24-Hour Screenshot Vault"
            textSize = 18f
            setTextColor(Color.parseColor("#FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val clearUnpinnedBtn = Button(this).apply {
            text = "Clear Unpinned"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#FF6B6B"))
            background = roundedBg("#25171A", "#4A252B", 16f)
            setPadding(24, 12, 24, 12)
            setOnClickListener {
                ScreenshotVaultManager.clearUnpinned(this@MainActivity)
                refreshVaultList()
                Toast.makeText(this@MainActivity, "Cleared unpinned screenshots", Toast.LENGTH_SHORT).show()
            }
        }

        vaultHeaderRow.addView(vaultCountText)
        vaultHeaderRow.addView(clearUnpinnedBtn)

        val vaultHintText = TextView(this).apply {
            text = "Triggers: 'clipshot' notification (Crop/Full) • 'clipurl' notification (Web Link only) • Hold Volume Down • Quick Tile."
            textSize = 12f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 0, 0, 24)
        }

        vaultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(titleText)
        root.addView(subTitleText)
        root.addView(statusText)
        root.addView(spacer1)
        root.addView(buttonsRow)
        root.addView(vaultHeaderRow)
        root.addView(vaultHintText)
        root.addView(vaultContainer)

        scrollView.addView(root)
        setContentView(scrollView)

        try {
            window.statusBarColor = Color.parseColor("#121212")
            window.navigationBarColor = Color.parseColor("#121212")
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        refreshVaultList()
        val filter = IntentFilter(ScreenshotAccessibilityService.ACTION_VAULT_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vaultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(vaultReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(vaultReceiver)
        } catch (_: Exception) {}
    }

    private fun updateServiceStatus() {
        val isActive = ScreenshotAccessibilityService.instance != null
        if (isActive) {
            statusText.text = "✅ Service Active (Crop • Web URL • Hold Vol Down • Quick Tile)"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            statusText.text = "⚠️ Service NOT enabled. Tap 'Accessibility' to enable ClipShot."
            statusText.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    private fun refreshVaultList() {
        vaultContainer.removeAllViews()
        val items = ScreenshotVaultManager.purgeExpired(this)
        val pinnedCount = items.count { it.isPinned }
        vaultCountText.text = "Vault (${items.size} saved • $pinnedCount pinned)"

        if (items.isEmpty()) {
            val emptyView = TextView(this).apply {
               text = "No screenshots in vault yet.\nCapture a screenshot and it will appear here for 24 hours."
                textSize = 14f
                setTextColor(Color.parseColor("#6E7681"))
                gravity = Gravity.CENTER
                background = roundedBg("#181818", "#262626", 24f)
                setPadding(40, 64, 40, 64)
            }
            vaultContainer.addView(emptyView)
            return
        }

        val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.US)

        for (item in items) {
            val card = buildVaultCard(item, dateFormat)
            vaultContainer.addView(card)
        }
    }

    private fun buildVaultCard(item: VaultItem, dateFormat: SimpleDateFormat): View {
        val borderHex = if (item.isPinned) "#D29922" else "#2D2D2D"
        val bgHex = if (item.isPinned) "#1C1914" else "#1A1A1A"

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(bgHex, borderHex, 24f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
        }

        val thumbView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(160, 240).apply {
                rightMargin = 24
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBg("#0D1117", "#30363D", 14f)
            setOnClickListener {
                showFullScreenViewer(item, dateFormat)
            }
        }

        val file = ScreenshotVaultManager.getFileForItem(this, item)
        if (file.exists()) {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 4
            }
            val thumbBitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
            thumbView.setImageBitmap(thumbBitmap)
        }

        val infoColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val timeText = TextView(this).apply {
            text = dateFormat.format(Date(item.timestamp))
            textSize = 15f
            setTextColor(Color.parseColor("#F0F6FC"))
        }

        val expiryText = TextView(this).apply {
            text = item.getFormattedStatus()
            textSize = 13f
            setTextColor(
                if (item.isPinned) Color.parseColor("#E3B341")
                else Color.parseColor("#8B949E")
            )
            setPadding(0, 6, 0, 12)
        }

        infoColumn.addView(timeText)
        infoColumn.addView(expiryText)

        // Show Source URL pill if captured from a browser
        if (!item.sourceUrl.isNullOrBlank()) {
            val urlBtn = Button(this).apply {
                text = "🔗 Copy URL: ${item.sourceUrl.removePrefix("https://").removePrefix("http://").take(26)}..."
                isAllCaps = false
                textSize = 11f
                setTextColor(Color.parseColor("#58A6FF"))
                background = roundedBg("#131D2E", "#1F6FEB", 12f)
                setPadding(16, 8, 16, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12
                }
                setOnClickListener {
                    ScreenshotVaultManager.copyTextToClipboard(this@MainActivity, item.sourceUrl)
                    Toast.makeText(this@MainActivity, "URL copied to clipboard 🔗", Toast.LENGTH_SHORT).show()
                }
            }
            infoColumn.addView(urlBtn)
        }

        // Row 1: View | Copy | Save
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
        }

        val viewBtn = Button(this).apply {
            text = "View"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#E0E0E0"))
            background = roundedBg("#262B33", "#3D444D", 14f)
            setPadding(20, 10, 20, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 10
            }
            setOnClickListener {
                showFullScreenViewer(item, dateFormat)
            }
        }

        val copyBtn = Button(this).apply {
            text = "Copy"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#1F6FEB", "#388BFD", 14f)
            setPadding(20, 10, 20, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 10
            }
            setOnClickListener {
                val ok = ScreenshotVaultManager.copyItemToClipboard(this@MainActivity, item)
                if (ok) {
                    Toast.makeText(this@MainActivity, "Copied to clipboard 📋", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val saveBtn = Button(this).apply {
            text = "Save"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#238636", "#2EA043", 14f)
            setPadding(20, 10, 20, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val saved = ScreenshotVaultManager.saveToGallery(this@MainActivity, item)
                val msg = if (saved) "Saved to Gallery (Pictures/ClipShot) 🖼️" else "Failed to save to Gallery"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        row1.addView(viewBtn)
        row1.addView(copyBtn)
        row1.addView(saveBtn)

        // Row 2: Pin | Delete
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val pinBtn = Button(this).apply {
            text = if (item.isPinned) "Pinned 📌" else "Pin 📌"
            isAllCaps = false
            textSize = 12f
            setTextColor(if (item.isPinned) Color.parseColor("#121212") else Color.parseColor("#E3B341"))
            background = if (item.isPinned) {
                roundedBg("#E3B341", "#F2CC60", 14f)
            } else {
                roundedBg("#2A2415", "#6E561B", 14f)
            }
            setPadding(22, 10, 22, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 10
            }
            setOnClickListener {
                val nowPinned = ScreenshotVaultManager.togglePin(this@MainActivity, item.id)
                val msg = if (nowPinned) "Pinned! Will never auto-delete 📌" else "Unpinned (24h timer resumed)"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                refreshVaultList()
            }
        }

        val delBtn = Button(this).apply {
            text = "Delete ✕"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.parseColor("#FF6B6B"))
            background = roundedBg("#25171A", "#4A252B", 14f)
            setPadding(20, 10, 20, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                ScreenshotVaultManager.deleteItem(this@MainActivity, item.id)
                refreshVaultList()
            }
        }

        row2.addView(pinBtn)
        row2.addView(delBtn)

        infoColumn.addView(row1)
        infoColumn.addView(row2)

        card.addView(thumbView)
        card.addView(infoColumn)
        return card
    }

    private fun showFullScreenViewer(item: VaultItem, dateFormat: SimpleDateFormat) {
        val file = ScreenshotVaultManager.getFileForItem(this, item)
        if (!file.exists()) {
            Toast.makeText(this, "Screenshot file not found", Toast.LENGTH_SHORT).show()
            refreshVaultList()
            return
        }

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val fullImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(16, 120, 16, 200)
            setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            setOnClickListener { dialog.dismiss() }
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#CC121212"))
            setPadding(36, 36, 36, 28)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        val titleLabel = TextView(this).apply {
            text = dateFormat.format(Date(item.timestamp))
            textSize = 15f
            setTextColor(Color.parseColor("#FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = Button(this).apply {
            text = "Close ✕"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#262B33", "#3D444D", 16f)
            setPadding(24, 12, 24, 12)
            setOnClickListener { dialog.dismiss() }
        }

        topBar.addView(titleLabel)
        topBar.addView(closeBtn)

        val bottomWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC121212"))
            setPadding(24, 20, 24, 36)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        if (!item.sourceUrl.isNullOrBlank()) {
            val urlActionBtn = Button(this).apply {
                text = "🌐 Open / Copy URL: ${item.sourceUrl.take(40)}"
                isAllCaps = false
                textSize = 12f
                setTextColor(Color.parseColor("#58A6FF"))
                background = roundedBg("#131D2E", "#1F6FEB", 16f)
                setPadding(24, 12, 24, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 14
                }
                setOnClickListener {
                    ScreenshotVaultManager.copyTextToClipboard(this@MainActivity, item.sourceUrl)
                    Toast.makeText(this@MainActivity, "URL copied 📋", Toast.LENGTH_SHORT).show()
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl)))
                    } catch (_: Exception) {}
                }
            }
            bottomWrapper.addView(urlActionBtn)
        }

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val viewerCopyBtn = Button(this).apply {
            text = "Copy 📋"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#1F6FEB", "#388BFD", 18f)
            setPadding(28, 16, 28, 16)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 12
            }
            setOnClickListener {
                if (ScreenshotVaultManager.copyItemToClipboard(this@MainActivity, item)) {
                    Toast.makeText(this@MainActivity, "Copied to clipboard 📋", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val viewerSaveBtn = Button(this).apply {
            text = "Save to Gallery 🖼️"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = roundedBg("#238636", "#2EA043", 18f)
            setPadding(28, 16, 28, 16)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                rightMargin = 12
            }
            setOnClickListener {
                val saved = ScreenshotVaultManager.saveToGallery(this@MainActivity, item)
                val msg = if (saved) "Saved to Gallery (Pictures/ClipShot) 🖼️" else "Failed to save to Gallery"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        val viewerPinBtn = Button(this).apply {
            text = if (item.isPinned) "Pinned 📌" else "Pin 📌"
            isAllCaps = false
            textSize = 14f
            setTextColor(if (item.isPinned) Color.parseColor("#121212") else Color.parseColor("#E3B341"))
            background = if (item.isPinned) {
                roundedBg("#E3B341", "#F2CC60", 18f)
            } else {
                roundedBg("#2A2415", "#6E561B", 18f)
            }
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f)
            setOnClickListener {
                val nowPinned = ScreenshotVaultManager.togglePin(this@MainActivity, item.id)
                item.isPinned = nowPinned
                text = if (nowPinned) "Pinned 📌" else "Pin 📌"
                setTextColor(if (nowPinned) Color.parseColor("#121212") else Color.parseColor("#E3B341"))
                background = if (nowPinned) {
                    roundedBg("#E3B341", "#F2CC60", 18f)
                } else {
                    roundedBg("#2A2415", "#6E561B", 18f)
                }
                refreshVaultList()
            }
        }

        bottomBar.addView(viewerCopyBtn)
        bottomBar.addView(viewerSaveBtn)
        bottomBar.addView(viewerPinBtn)
        bottomWrapper.addView(bottomBar)

        container.addView(fullImage)
        container.addView(topBar)
        container.addView(bottomWrapper)

        dialog.setContentView(container)
        dialog.show()
    }

    private fun roundedBg(fillHex: String, strokeHex: String, radiusPx: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(Color.parseColor(fillHex))
            setStroke(2, Color.parseColor(strokeHex))
        }
    }
}
startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
