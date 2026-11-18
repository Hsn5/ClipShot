package com.example.clipshot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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

    /**
     * Uses context.filesDir/vault so pinned screenshots are NEVER wiped by OS cache clearing.
     */
    private fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()
        return dir
    }

    @Synchronized
    fun purgeExpired(context: Context): List<VaultItem> {
        val (allItems, parseOk) = loadRawItemsSafe(context)
        // Never purge or delete files if JSON metadata failed to parse!
        if (!parseOk) {
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val vaultDir = getVaultDir(context)
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

        val (itemsRaw, parseOk) = loadRawItemsSafe(context)
        val items = if (parseOk) itemsRaw.toMutableList() else mutableListOf()
        items.add(0, newItem)
        saveRawItems(context, items)

        copyFileToClipboard(context, file)
        return newItem
    }

    @Synchronized
    fun togglePin(context: Context, id: String): Boolean {
        val (itemsRaw, parseOk) = loadRawItemsSafe(context)
        if (!parseOk) return false
        val items = itemsRaw.toMutableList()
        val target = items.find { it.id == id } ?: return false
        target.isPinned = !target.isPinned
        saveRawItems(context, items)
        return target.isPinned
    }

    @Synchronized
    fun deleteItem(context: Context, id: String) {
        val (itemsRaw, parseOk) = loadRawItemsSafe(context)
        if (!parseOk) return
        val vaultDir = getVaultDir(context)
        val items = itemsRaw.toMutableList()
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
        val (itemsRaw, parseOk) = loadRawItemsSafe(context)
        if (!parseOk) return
        val vaultDir = getVaultDir(context)
        val remaining = mutableListOf<VaultItem>()
        for (item in itemsRaw) {
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

    private fun loadRawItemsSafe(context: Context): Pair<List<VaultItem>, Boolean> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val list = mutableListOf<VaultItem>()
        return try {
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
            Pair(list, true)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), false)
        }
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
